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

package org.gradle.nativeplatform.test.xctest

import org.gradle.integtests.fixtures.SourceFile
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.ToolChainRequirement
import org.gradle.nativeplatform.fixtures.app.XCTestCaseElement
import org.gradle.nativeplatform.fixtures.app.XCTestSourceElement
import org.gradle.nativeplatform.fixtures.app.XCTestSourceFileElement
import org.gradle.testing.AbstractTestFrameworkIntegrationTest
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.junit.Assume.assumeTrue

@Requires([TestPrecondition.SWIFT_SUPPORT, TestPrecondition.NOT_WINDOWS])
class XCTestTestFrameworkIntegrationTest extends AbstractTestFrameworkIntegrationTest {
    def setup() {
        def toolChain = AvailableToolChains.getToolChain(ToolChainRequirement.SWIFT)
        assumeTrue(toolChain != null && toolChain.isAvailable())

        File initScript = file("init.gradle") << """
allprojects { p ->
    apply plugin: ${toolChain.pluginClass}

    model {
          toolChains {
            ${toolChain.buildScriptConfig}
          }
    }
}
"""
        executer.beforeExecute({
            usingInitScript(initScript)
        })

        settingsFile << "rootProject.name = 'app'"
        buildFile << """
            apply plugin: 'xctest'
        """
    }

    @Override
    void createPassingFailingTest() {
        def testBundle = new SwiftXCTestTestFrameworkBundle()
        testBundle.writeToProject(testDirectory)

    }

    @Override
    void createEmptyProject() {
        file("src/test/swift/NoTests.swift") << """
            func someSwiftCode() {
            }
        """
    }

    @Override
    void renameTests() {
        def newTest = file("src/test/swift/NewTest.swift")
        file("src/test/swift/SomeOtherTest.swift").renameTo(newTest)
        newTest.text = newTest.text.replaceAll("SomeOtherTest", "NewTest")
        if (OperatingSystem.current().linux) {
            def linuxMain = file("src/test/swift/main.swift")
            linuxMain.text = linuxMain.text.replaceAll("SomeOtherTest", "NewTest")
        }
    }

    @Override
    String getTestTaskName() {
        return "xcTest"
    }

    @Override
    String getPassingTestCaseName() {
        return "testPass"
    }

    @Override
    String getFailingTestCaseName() {
        return "testFail"
    }

    class SwiftXCTestTestFrameworkBundle extends XCTestSourceElement {
        List<XCTestSourceFileElement> testSuites = [
            new XCTestSourceFileElement() {
                String testSuiteName = "SomeTest"
                List<XCTestCaseElement> testCases = [testCase("testFail", FAILING_TEST, true)]
                String moduleName = "AppTest"
            }.withImport(libcModuleName),

            new XCTestSourceFileElement() {
                String testSuiteName = "SomeOtherTest"
                List<XCTestCaseElement> testCases = [testCase("testPass", "XCTAssert(true)")]
                String moduleName = "AppTest"
            },
        ]

        @Override
        List<SourceFile> getFiles() {
            super.files
        }

        private static final String FAILING_TEST = """
            fflush(stdout)
            fputs("some error output\\n", stderr)
            fflush(stderr)
            XCTAssert(false, "test failure message")
        """

        private static String getLibcModuleName() {
            if (OperatingSystem.current().macOsX) {
                return "Darwin"
            }
            return "Glibc"
        }
    }
}
