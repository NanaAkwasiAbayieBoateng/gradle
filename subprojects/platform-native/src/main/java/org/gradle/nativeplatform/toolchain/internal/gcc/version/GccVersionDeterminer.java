/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.nativeplatform.toolchain.internal.gcc.version;

import com.google.common.base.Joiner;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.io.NullOutputStream;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal;
import org.gradle.nativeplatform.platform.internal.Architectures;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.TreeVisitor;
import org.gradle.util.VersionNumber;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Given a File pointing to an (existing) gcc/g++/clang/clang++ binary, extracts the version number and default architecture by running with -dM -E and scraping the output.
 */
public class GccVersionDeterminer implements CompilerMetaDataProvider {
    private static final Pattern DEFINE_PATTERN = Pattern.compile("\\s*#define\\s+(\\S+)\\s+(.*)");
    private final ExecActionFactory execActionFactory;
    private final CompilerType compilerType;

    public static GccVersionDeterminer forGcc(ExecActionFactory execActionFactory) {
        return new GccVersionDeterminer(execActionFactory, CompilerType.GCC);
    }

    public static GccVersionDeterminer forClang(ExecActionFactory execActionFactory) {
        return new GccVersionDeterminer(execActionFactory, CompilerType.CLANG);
    }

    GccVersionDeterminer(ExecActionFactory execActionFactory, CompilerType compilerType) {
        this.execActionFactory = execActionFactory;
        this.compilerType = compilerType;
    }

    @Override
    public GccVersionResult getGccMetaData(File gccBinary, List<String> args) {
        List<String> allArgs = new ArrayList<String>(args);
        allArgs.add("-dM");
        allArgs.add("-E");
        allArgs.add("-");
        String output = transform(gccBinary, allArgs);
        if (output == null) {
            return new BrokenResult(String.format("Could not determine %s version: failed to execute %s %s.", compilerType.getDescription(), gccBinary.getName(), Joiner.on(' ').join(allArgs)));
        }
        return transform(output, gccBinary);
    }

    @Override
    public CompilerType getCompilerType() {
        return compilerType;
    }

    private String transform(File gccBinary, List<String> args) {
        ExecAction exec = execActionFactory.newExecAction();
        exec.executable(gccBinary.getAbsolutePath());
        exec.setWorkingDir(gccBinary.getParentFile());
        exec.args(args);
        StreamByteBuffer buffer = new StreamByteBuffer();
        exec.setStandardOutput(buffer.getOutputStream());
        exec.setErrorOutput(NullOutputStream.INSTANCE);
        exec.setIgnoreExitValue(true);
        ExecResult result = exec.execute();

        int exitValue = result.getExitValue();
        if (exitValue == 0) {
            return buffer.readAsString();
        } else {
            return null;
        }
    }

    private GccVersionResult transform(String output, File gccBinary) {
        BufferedReader reader = new BufferedReader(new StringReader(output));
        String line;
        Map<String, String> defines = new HashMap<String, String>();
        try {
            while ((line = reader.readLine()) != null) {
                Matcher matcher = DEFINE_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    return new BrokenResult(String.format("Could not determine %s version: %s produced unexpected output.", compilerType.getDescription(), gccBinary.getName()));
                }
                defines.put(matcher.group(1), matcher.group(2));
            }
        } catch (IOException e) {
            // Should not happen reading from a StringReader
            throw new UncheckedIOException(e);
        }
        if (!defines.containsKey("__GNUC__")) {
            return new BrokenResult(String.format("Could not determine %s version: %s produced unexpected output.", compilerType.getDescription(), gccBinary.getName()));
        }
        int major;
        int minor;
        int patch;
        switch (compilerType) {
            case CLANG:
                if (!defines.containsKey("__clang__")) {
                    return new BrokenResult(String.format("%s appears to be GCC rather than Clang. Treating it as GCC.", gccBinary.getName()));
                }
                major = toInt(defines.get("__clang_major__"));
                minor = toInt(defines.get("__clang_minor__"));
                patch = toInt(defines.get("__clang_patchlevel__"));
                break;
            case GCC:
                if (defines.containsKey("__clang__")) {
                    return new BrokenResult(String.format("XCode %s is a wrapper around Clang. Treating it as Clang and not GCC.", gccBinary.getName()));
                }
                major = toInt(defines.get("__GNUC__"));
                minor = toInt(defines.get("__GNUC_MINOR__"));
                patch = toInt(defines.get("__GNUC_PATCHLEVEL__"));
                break;
            default:
                throw new GradleException("Unknown compiler type " + compilerType);
        }
        final ArchitectureInternal architecture = determineArchitecture(defines);
        return new DefaultGccVersionResult(new VersionNumber(major, minor, patch, null), architecture);
    }

    private ArchitectureInternal determineArchitecture(Map<String, String> defines) {
        boolean i386 = defines.containsKey("__i386__");
        boolean amd64 = defines.containsKey("__amd64__");
        final ArchitectureInternal architecture;
        if (i386) {
            architecture = Architectures.forInput("i386");
        } else if (amd64) {
            architecture = Architectures.forInput("amd64");
        } else {
            architecture = DefaultNativePlatform.getCurrentArchitecture();
        }
        return architecture;
    }

    private int toInt(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static class DefaultGccVersionResult implements GccVersionResult {
        private final VersionNumber scrapedVersion;
        private final ArchitectureInternal architecture;

        public DefaultGccVersionResult(VersionNumber scrapedVersion, ArchitectureInternal architecture) {
            this.scrapedVersion = scrapedVersion;
            this.architecture = architecture;
        }

        @Override
        public VersionNumber getVersion() {
            return scrapedVersion;
        }

        @Override
        public ArchitectureInternal getDefaultArchitecture() {
            return architecture;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void explain(TreeVisitor<? super String> visitor) {
        }
    }

    private static class BrokenResult implements GccVersionResult {
        private final String message;

        private BrokenResult(String message) {
            this.message = message;
        }

        @Override
        public VersionNumber getVersion() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ArchitectureInternal getDefaultArchitecture() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node(message);
        }
    }
}
