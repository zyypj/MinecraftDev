/*
 * Minecraft Development for IntelliJ
 *
 * https://mcdev.io/
 *
 * Copyright (C) 2024 minecraft-dev
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, version 3.0 only.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.net.HttpURLConnection
import java.net.URI
import java.util.Properties
import java.util.zip.ZipFile
import org.cadixdev.gradle.licenser.header.HeaderStyle
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask

plugins {
    java
    idea
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
    id("org.cadixdev.licenser")
    id("org.jlleitschuh.gradle.ktlint")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs = listOf("-proc:none")
    options.release.set(17)
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        languageVersion = KotlinVersion.KOTLIN_2_0
        freeCompilerArgs = listOf("-Xjvm-default=all", "-Xjdk-release=17")
        optIn.add("kotlin.contracts.ExperimentalContracts")
    }
    kotlinDaemonJvmArguments.add("-Xmx2G")
}

repositories {
    maven("https://repo.denwav.dev/repository/maven-public/")
    maven("https://maven.fabricmc.net/") {
        content {
            includeModule("net.fabricmc", "mapping-io")
            includeModule("net.fabricmc", "fabric-loader")
        }
    }
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/") {
        content {
            includeGroup("org.spongepowered")
        }
    }
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
        content {
            includeGroup("org.spigotmc")
        }
    }
    maven("https://oss.sonatype.org/content/repositories/snapshots/") {
        content {
            includeGroup("net.md-5")
        }
    }

    intellijPlatform {
        defaultRepositories()
    }
}

val libs = the<LibrariesForLibs>()
dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.bundles.coroutines)

    testImplementation(libs.junit.api)
    testCompileOnly(libs.junit.vintage) // Hack to get tests to compile and run
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

intellijPlatform {
    sandboxContainer = layout.projectDirectory.dir(".sandbox")

    instrumentCode = false
    buildSearchableOptions = false
}

license {
    header.set(resources.text.fromFile(rootProject.layout.projectDirectory.file("copyright.txt")))
    style["flex"] = HeaderStyle.BLOCK_COMMENT.format
    style["bnf"] = HeaderStyle.BLOCK_COMMENT.format

    val endings = listOf("java", "kt", "kts", "groovy", "gradle.kts", "xml", "properties", "html", "flex", "bnf")
    include(endings.map { "**/*.$it" })
}

idea {
    module {
        excludeDirs.add(file(intellijPlatform.sandboxContainer.get()))
    }
}

tasks.withType<BaseKtLintCheckTask>().configureEach {
    workerMaxHeapSize = "512m"
}

tasks.runIde {
    maxHeapSize = "2G"
    jvmArgs("--add-exports=java.base/jdk.internal.vm=ALL-UNNAMED")
}

tasks.register("cleanSandbox", Delete::class) {
    group = "intellij"
    description = "Deletes the sandbox directory."
    delete(layout.projectDirectory.dir(".sandbox"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("format") {
    group = "minecraft"
    description = "Formats source code according to project style"
    dependsOn(tasks.licenseFormat, tasks.ktlintFormat)
}

// Analyze dependencies
val fileName = ".gradle/intellij-deps.json"
val jsonFile = file("$projectDir/$fileName")

val ideaVersion: String by project
val ideaVersionName: String by project

if (jsonFile.exists()) {
    val deps: DepList = jsonFile.bufferedReader().use { reader ->
        Gson().fromJson(reader, DepList::class.java)
    }
    if (ideaVersion != deps.intellijVersion || ideaVersionName != deps.intellijVersionName) {
        println("IntelliJ library sources file definition is out of date, deleting")
        jsonFile.delete()
    } else {
        dependencies {
            for ((groupId, artifactId, version) in deps.deps) {
                compileOnly(
                    group = groupId,
                    name = artifactId,
                    version = version
                )
            }
        }
    }
}

tasks.register("resolveIntellijLibSources") {
    group = "minecraft"
    val compileClasspath by project.configurations
    dependsOn(compileClasspath)

    doLast {
        val files = compileClasspath.resolvedConfiguration.files
        val deps = files.asSequence()
            .map { it.toPath() }
            .filter {
                it.map { part -> part.toString() }.containsAll(listOf("com.jetbrains.intellij.idea", "ideaIC", "lib"))
            }
            .filter { it.fileName.toString().endsWith(".jar") }
            .mapNotNull { lib ->
                val name = lib.fileName.toString()
                return@mapNotNull ZipFile(lib.toFile()).use { zipFile ->
                    val pomEntry = zipFile.stream()
                        .filter { entry ->
                            val entryName = entry.name
                            entryName.contains("META-INF/maven")
                                && entryName.split('/').any { name.contains(it) }
                                && entryName.endsWith("pom.properties")
                        }
                        .findFirst()
                        .orElse(null) ?: return@use null
                    return@use zipFile.getInputStream(pomEntry).use { input ->
                        val props = Properties()
                        props.load(input)
                        Dep(props["groupId"].toString(), props["artifactId"].toString(), props["version"].toString())
                    }
                }
            }.filter { dep ->
                // Check if this dependency is available in Maven Central
                val groupPath = dep.groupId.replace('.', '/')
                val (_, artifact, ver) = dep
                val url = "https://repo.maven.apache.org/maven2/$groupPath/$artifact/$ver/$artifact-$ver-sources.jar"
                return@filter with(URI.create(url).toURL().openConnection() as HttpURLConnection) {
                    try {
                        requestMethod = "GET"
                        val code = responseCode
                        return@with code in 200..299
                    } finally {
                        disconnect()
                    }
                }
            }.toList()

        val depList = DepList(ideaVersion, ideaVersionName, deps.sortedWith(compareBy<Dep> { it.groupId }.thenBy { it.artifactId }))
        jsonFile.parentFile.mkdirs()
        jsonFile.bufferedWriter().use { writer ->
            GsonBuilder().setPrettyPrinting().create().toJson(depList, writer)
        }
    }
}
