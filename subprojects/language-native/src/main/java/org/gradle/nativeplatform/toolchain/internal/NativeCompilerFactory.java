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

package org.gradle.nativeplatform.toolchain.internal;

import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.nativeplatform.internal.BinaryToolSpec;

public interface NativeCompilerFactory {
    enum CPreprocessorDialect {
        StandardC, Gcc
    }

    /**
     * Creates a {@link Compiler} for a compiler that runs one or more command-line tools. The provided compiler implementation is responsible for taking care of incremental compile, if relevant.
     */
    <T extends BinaryToolSpec> Compiler<T> compiler(CommandLineToolBackedCompiler<T> compiler);

    /**
     * Creates a {@link Compiler} implementation that takes care of incremental compile for a language that uses C preprocessor header files, and where each source file is compiled separately.
     */
    <T extends NativeCompileSpec> Compiler<T> incrementalAndParallelCompiler(CommandLineToolBackedCompiler<T> compiler, CPreprocessorDialect dialect, String outputFileSuffix);
}
