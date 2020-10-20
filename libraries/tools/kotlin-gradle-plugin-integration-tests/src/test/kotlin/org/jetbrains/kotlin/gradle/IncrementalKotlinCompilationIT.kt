/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.gradle.util.modify
import org.junit.Test
import kotlin.test.assertTrue

class IncrementalKotlinCompilationIT : BaseGradleIT() {
    val localBuildCacheSettings =
        "buildCache {\n" +
            "    local {\n" +
            "        System.out.println(\"Build dir folder stored in \" + rootDir.canonicalPath)\n" +
            "        directory = new File(rootDir, \"build-cache\")\n" +
            "    }\n" +
            "}"
    @Test
    fun compileKotlinWithGradleCache() {
        val project = Project("incrementalMultiproject")
        project.setupWorkingDir()

        project.gradleSettingsScript().modify { "$it\n$localBuildCacheSettings" }

        project.build("build", "--build-cache") {
            assertSuccessful()
            assertTasksExecuted(":app:compileKotlin", ":lib:compileKotlin")
        }

        project.projectFile("build-cache").exists()

        project.projectFile("lib/build/kotlin/compiler").delete()

        //local cache doesn't copy output
        project.build("build", "--build-cache") {
            assertSuccessful()
            assertTasksUpToDate(":app:compileKotlin")
            assertTasksGetFromCache(":lib:compileKotlin")
        }
    }

    @Test
    fun compileKotlinRestoreBuildDirAfterFail() {
        val project = Project("incrementalMultiproject")
        project.setupWorkingDir()

        project.gradleSettingsScript().modify { "$it\n$localBuildCacheSettings" }

        project.build("build", "--build-cache") {
            assertSuccessful()
            assertTasksExecuted(":app:compileKotlin", ":lib:compileKotlin")
        }

        project.projectFile("lib/src/main/kotlin/bar/B.kt").modify{ it.replace("A", "UNKNOWN_CLASS") }

        project.build("build", "--build-cache") {
            assertFailed()
        }

        project.projectFile("lib/src/main/kotlin/bar/B.kt").modify{ it.replace("UNKNOWN_CLASS", "A") }

        //local cache doesn't copy output
        project.build("build", "--build-cache") {
            assertSuccessful()
            assertTasksGetFromCache(":app:compileKotlin")
            assertTasksGetFromCache(":lib:compileKotlin")
        }
    }

    fun CompiledProject.assertTasksGetFromCache(vararg tasks: String) {
        for (task in tasks) {
            assertContains("$task FROM CACHE")
        }
    }
}