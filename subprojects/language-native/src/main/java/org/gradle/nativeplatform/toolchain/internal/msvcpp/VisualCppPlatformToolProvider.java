/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.internal.Transformers;
import org.gradle.internal.jvm.Jvm;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.internal.LinkerSpec;
import org.gradle.nativeplatform.internal.StaticLibraryArchiverSpec;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.platform.internal.OperatingSystemInternal;
import org.gradle.nativeplatform.toolchain.internal.AbstractPlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.CommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.DefaultMutableCommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.MutableCommandLineToolContext;
import org.gradle.nativeplatform.toolchain.internal.NativeCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.NativeCompilerFactory;
import org.gradle.nativeplatform.toolchain.internal.PCHUtils;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.compilespec.AssembleSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CPCHCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.CppPCHCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.compilespec.WindowsResourceCompileSpec;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolConfigurationInternal;

import java.io.File;
import java.util.List;
import java.util.Map;

class VisualCppPlatformToolProvider extends AbstractPlatformToolProvider {
    private final NativeCompilerFactory compilerFactory;
    private final Map<ToolType, CommandLineToolConfigurationInternal> commandLineToolConfigurations;
    private final VisualCppInstall visualCpp;
    private final WindowsSdk sdk;
    private final Ucrt ucrt;
    private final NativePlatformInternal targetPlatform;
    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;

    VisualCppPlatformToolProvider(NativeCompilerFactory compilerFactory, OperatingSystemInternal operatingSystem, Map<ToolType, CommandLineToolConfigurationInternal> commandLineToolConfigurations, VisualCppInstall visualCpp, WindowsSdk sdk, Ucrt ucrt, NativePlatformInternal targetPlatform, CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory) {
        super(operatingSystem);
        this.compilerFactory = compilerFactory;
        this.commandLineToolConfigurations = commandLineToolConfigurations;
        this.visualCpp = visualCpp;
        this.sdk = sdk;
        this.ucrt = ucrt;
        this.targetPlatform = targetPlatform;
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;
    }

    @Override
    public String getSharedLibraryLinkFileName(String libraryName) {
        return getSharedLibraryName(libraryName).replaceFirst("\\.dll$", ".lib");
    }

    @Override
    protected Compiler<CppCompileSpec> createCppCompiler() {
        File compilerExe = visualCpp.getCompiler(targetPlatform);
        CppCompiler compiler = new CppCompiler(compilerOutputFileNamingSchemeFactory, context("C++ compiler", compilerExe, commandLineToolConfigurations.get(ToolType.CPP_COMPILER)), addIncludePathAndDefinitions(CppCompileSpec.class), getObjectFileExtension(), true);
        return compilerFactory.incrementalAndParallelCompiler(compiler, NativeCompilerFactory.CPreprocessorDialect.StandardC, getObjectFileExtension());
    }

    @Override
    protected Compiler<CppPCHCompileSpec> createCppPCHCompiler() {
        File compilerExe = visualCpp.getCompiler(targetPlatform);
        CppPCHCompiler compiler = new CppPCHCompiler(compilerOutputFileNamingSchemeFactory, context("C++ PCH compiler", compilerExe, commandLineToolConfigurations.get(ToolType.CPP_COMPILER)), pchSpecTransforms(CppPCHCompileSpec.class), getPCHFileExtension(), true);
        return compilerFactory.incrementalAndParallelCompiler(compiler, NativeCompilerFactory.CPreprocessorDialect.StandardC, getPCHFileExtension());
    }

    @Override
    protected Compiler<CCompileSpec> createCCompiler() {
        File compilerExe = visualCpp.getCompiler(targetPlatform);
        CCompiler compiler = new CCompiler(compilerOutputFileNamingSchemeFactory, context("C compiler", compilerExe, commandLineToolConfigurations.get(ToolType.C_COMPILER)), addIncludePathAndDefinitions(CCompileSpec.class), getObjectFileExtension(), true);
        return compilerFactory.incrementalAndParallelCompiler(compiler, NativeCompilerFactory.CPreprocessorDialect.StandardC, getObjectFileExtension());
    }

    @Override
    protected Compiler<CPCHCompileSpec> createCPCHCompiler() {
        File compilerExe = visualCpp.getCompiler(targetPlatform);
        CPCHCompiler compiler = new CPCHCompiler(compilerOutputFileNamingSchemeFactory, context("C PCH compiler", compilerExe, commandLineToolConfigurations.get(ToolType.C_COMPILER)), pchSpecTransforms(CPCHCompileSpec.class), getPCHFileExtension(), true);
        return compilerFactory.incrementalAndParallelCompiler(compiler, NativeCompilerFactory.CPreprocessorDialect.StandardC, getPCHFileExtension());
    }

    @Override
    protected Compiler<AssembleSpec> createAssembler() {
        File assemblerExe = visualCpp.getAssembler(targetPlatform);
        Assembler assembler = new Assembler(compilerOutputFileNamingSchemeFactory, context(ToolType.ASSEMBLER.getToolName(), assemblerExe, commandLineToolConfigurations.get(ToolType.ASSEMBLER)), addIncludePathAndDefinitions(AssembleSpec.class), getObjectFileExtension(), false);
        return compilerFactory.compiler(assembler);
    }

    @Override
    protected Compiler<?> createObjectiveCppCompiler() {
        throw unavailableTool("Objective-C++ is not available on the Visual C++ toolchain");
    }

    @Override
    protected Compiler<?> createObjectiveCCompiler() {
        throw unavailableTool("Objective-C is not available on the Visual C++ toolchain");
    }

    @Override
    protected Compiler<WindowsResourceCompileSpec> createWindowsResourceCompiler() {
        File resourceCompilerExe = sdk.getResourceCompiler(targetPlatform);
        String objectFileExtension = ".res";
        WindowsResourceCompiler compiler = new WindowsResourceCompiler(compilerOutputFileNamingSchemeFactory, context("Windows resource compiler", resourceCompilerExe, commandLineToolConfigurations.get(ToolType.WINDOW_RESOURCES_COMPILER)), addIncludePathAndDefinitions(WindowsResourceCompileSpec.class), objectFileExtension, false);
        return compilerFactory.incrementalAndParallelCompiler(compiler, NativeCompilerFactory.CPreprocessorDialect.StandardC, objectFileExtension);
    }

    @Override
    protected Compiler<LinkerSpec> createLinker() {
        File linkerExe = visualCpp.getLinker(targetPlatform);
        LinkExeLinker linker = new LinkExeLinker(context(ToolType.LINKER.getToolName(), linkerExe, commandLineToolConfigurations.get(ToolType.LINKER)), addLibraryPath());
        return compilerFactory.compiler(linker);
    }

    @Override
    protected Compiler<StaticLibraryArchiverSpec> createStaticLibraryArchiver() {
        File archiverExe = visualCpp.getArchiver(targetPlatform);
        LibExeStaticLibraryArchiver archiver = new LibExeStaticLibraryArchiver(context(ToolType.STATIC_LIB_ARCHIVER.getToolName(), archiverExe, commandLineToolConfigurations.get(ToolType.STATIC_LIB_ARCHIVER)), Transformers.<StaticLibraryArchiverSpec>noOpTransformer());
        return compilerFactory.compiler(archiver);
    }

    private CommandLineToolContext context(String toolName, File executable, CommandLineToolConfigurationInternal commandLineToolConfiguration) {
         MutableCommandLineToolContext invocationContext = new DefaultMutableCommandLineToolContext(toolName, executable);
        // The visual C++ tools use the path to find other executables
        // TODO:ADAM - restrict this to the specific path for the target tool
        invocationContext.addPath(visualCpp.getPath(targetPlatform));
        invocationContext.addPath(sdk.getBinDir(targetPlatform));
        // Clear environment variables that might effect cl.exe & link.exe
        clearEnvironmentVars(invocationContext, "INCLUDE", "CL", "LIBPATH", "LINK", "LIB");

        invocationContext.setArgAction(commandLineToolConfiguration.getArgAction());
        return invocationContext;
    }

    private void clearEnvironmentVars(MutableCommandLineToolContext invocation, String... names) {
        // TODO: This check should really be done in the compiler process
        Map<String, ?> environmentVariables = Jvm.current().getInheritableEnvironmentVariables(System.getenv());
        for (String name : names) {
            Object value = environmentVariables.get(name);
            if (value != null) {
                VisualCppToolChain.LOGGER.warn("Ignoring value '{}' set for environment variable '{}'.", value, name);
                invocation.addEnvironmentVar(name, "");
            }
        }
    }

    private <T extends NativeCompileSpec> Transformer<T, T> pchSpecTransforms(final Class<T> type) {
        return new Transformer<T, T>() {
            @Override
            public T transform(T original) {
                List<Transformer<T, T>> transformers = Lists.newArrayList();
                transformers.add(PCHUtils.getHeaderToSourceFileTransformer(type));
                transformers.add(addIncludePathAndDefinitions(type));

                T next = original;
                for (Transformer<T, T> transformer : transformers) {
                    next = transformer.transform(next);
                }
                return next;
            }
        };
    }

    private <T extends NativeCompileSpec> Transformer<T, T> addIncludePathAndDefinitions(Class<T> type) {
        return new Transformer<T, T>() {
            public T transform(T original) {
                original.include(visualCpp.getIncludePath(targetPlatform));
                original.include(sdk.getIncludeDirs());
                if (ucrt != null) {
                    original.include(ucrt.getIncludeDirs());
                }
                for (Map.Entry<String, String> definition : visualCpp.getDefinitions(targetPlatform).entrySet()) {
                    original.define(definition.getKey(), definition.getValue());
                }
                return original;
            }
        };
    }

    private Transformer<LinkerSpec, LinkerSpec> addLibraryPath() {
        return new Transformer<LinkerSpec, LinkerSpec>() {
            public LinkerSpec transform(LinkerSpec original) {
                if (ucrt == null) {
                    original.libraryPath(visualCpp.getLibraryPath(targetPlatform), sdk.getLibDir(targetPlatform));
                } else {
                    original.libraryPath(visualCpp.getLibraryPath(targetPlatform), sdk.getLibDir(targetPlatform), ucrt.getLibDir(targetPlatform));
                }
                return original;
            }
        };
    }

    public String getPCHFileExtension() {
        return ".pch";
    }
}
