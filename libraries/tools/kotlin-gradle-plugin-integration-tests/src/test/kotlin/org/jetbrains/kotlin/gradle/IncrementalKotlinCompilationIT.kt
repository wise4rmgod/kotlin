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
        val project = createIncrementalProjectWithCache()

        project.build("build", "--build-cache") {
            assertSuccessful()
            assertTasksExecuted(":app:compileKotlin", ":lib:compileKotlin")
        }

        project.projectDir.resolve("build-cache").exists()

        //clean kotlin build cache
        project.projectDir.resolve("lib").resolve("build")
            .resolve("kotlin").resolve("compileKotlin").deleteRecursively()

        //local cache doesn't copy output
        project.build("build", "--build-cache") {
            assertSuccessful()
            assertTasksUpToDate(":app:compileKotlin")
            assertTasksGetFromCache(":lib:compileKotlin")
        }
    }

    @Test
    fun compileKotlinRestoreBuildDirAfterFail() {
        val project = createIncrementalProjectWithCache()

        project.build("build", "--build-cache") {
            assertSuccessful()
            assertTasksExecuted(":app:compileKotlin", ":lib:compileKotlin")
        }

        project.projectDir.resolve("lib").resolve("src").resolve("main").resolve("kotlin")
            .resolve("bar").resolve("B.kt").modify { it.replace("A", "UNKNOWN_CLASS") }

        project.build("build", "--build-cache") {
            assertFailed()
        }

        project.projectDir.resolve("lib").resolve("src").resolve("main").resolve("kotlin")
            .resolve("bar").resolve("B.kt").modify { it.replace("UNKNOWN_CLASS", "A") }

        //local cache doesn't copy output
        project.build("build", "--build-cache") {
            assertSuccessful()
        }
    }

    @Test
    fun testBuildHistoryRelocation() {
        val project = createIncrementalProjectWithCache()

        project.build("build", "--build-cache") {
            assertSuccessful()
            assertTasksExecuted(":app:compileKotlin", ":lib:compileKotlin")
        }

        val buildCache = project.projectDir.resolve("build-cache")
        buildCache.exists()

        val project2 = createIncrementalProjectWithCache()
        buildCache.copyRecursively(project2.projectDir.resolve("build-cache"))

        project2.build("build", "--build-cache") {
            assertSuccessful()
            assertTasksGetFromCache(":app:compileKotlin", ":lib:compileKotlin")
        }

    }

    private fun createIncrementalProjectWithCache(): Project {
        val project = Project("incrementalMultiproject")
        project.setupWorkingDir()

        project.gradleSettingsScript().modify { "$it\n$localBuildCacheSettings" }
        return project
    }

    fun CompiledProject.assertTasksGetFromCache(vararg tasks: String) {
        for (task in tasks) {
            assertContains("$task FROM-CACHE")
        }
    }
}