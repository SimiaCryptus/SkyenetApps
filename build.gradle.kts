import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
  java
  `java-library`
  `maven-publish`
  id("signing")
  id("com.github.johnrengelman.shadow") version "8.1.1"
  kotlin("jvm") version "2.0.20"
  war
  id("org.beryx.runtime") version "1.13.0"
  application
}

application {
  mainClass.set("com.simiacryptus.skyenet.AppServer")
}

runtime {
  options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
  modules.set(
    listOf(
      "java.base", "java.desktop", "java.logging", "java.naming", "java.net.http", "java.sql", "jdk.crypto.ec"
    )
  )
}

allprojects {
  if (project != rootProject) {
    apply(plugin = "org.jetbrains.kotlin.jvm")
  }
  java {
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(17))
    }
  }
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      javaParameters = true
      jvmTarget = "17"
    }
  }
}

fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

repositories {
  mavenCentral {
    metadataSources {
      mavenPom()
      artifact()
    }
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

val jetty_version = "11.0.24"
val skyenet_version = "1.2.22"
val scala_version = "2.13.9"
val jackson_version = "2.17.2"
val jupiter_version = "5.10.1"
val logback_version = "1.5.13"
dependencies {
  implementation("org.apache.xmlgraphics:batik-transcoder:1.14")
  implementation("org.apache.xmlgraphics:batik-codec:1.14")

  implementation("org.openjfx:javafx-swing:17")
  implementation("org.openjfx:javafx-graphics:17")
  implementation("org.openjfx:javafx-base:17")

  implementation("org.postgresql:postgresql:42.7.2")

  implementation(group = "com.simiacryptus", name = "jo-penai", version = "1.1.13")

  implementation("org.apache.commons:commons-text:1.11.0")

  implementation(group = "com.simiacryptus.skyenet", name = "core", version = skyenet_version)
  implementation(group = "com.simiacryptus.skyenet", name = "groovy", version = skyenet_version)
  implementation(group = "com.simiacryptus.skyenet", name = "kotlin", version = skyenet_version)
  implementation(group = "com.simiacryptus.skyenet", name = "scala", version = skyenet_version)
  implementation(group = "com.simiacryptus.skyenet", name = "webui", version = skyenet_version)

  implementation("org.openapitools:openapi-generator:7.3.0")
  implementation("org.openapitools:openapi-generator-cli:7.3.0")

  implementation("org.postgresql:postgresql:42.7.2")

  implementation("com.zaxxer:HikariCP:5.1.0")
  implementation("org.seleniumhq.selenium:selenium-chrome-driver:4.16.1")
  implementation(group = "software.amazon.awssdk", name = "aws-sdk-java", version = "2.27.23")
  implementation("org.jsoup:jsoup:1.18.1")

  implementation("com.google.api-client:google-api-client:1.35.2")
  implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
  implementation("com.google.apis:google-api-services-gmail:v1-rev110-1.25.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

  implementation(group = "com.fasterxml.jackson.core", name = "jackson-databind", version = jackson_version)
  implementation(group = "com.fasterxml.jackson.core", name = "jackson-annotations", version = jackson_version)
  implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin", version = jackson_version)

  implementation(kotlin("stdlib"))
  implementation(group = "com.google.guava", name = "guava", version = "32.1.3-jre")
  implementation(group = "org.eclipse.jetty", name = "jetty-server", version = jetty_version)
  implementation(group = "org.eclipse.jetty", name = "jetty-webapp", version = jetty_version)
  implementation(group = "org.eclipse.jetty.websocket", name = "websocket-jetty-server", version = jetty_version)
  implementation(group = "org.apache.httpcomponents.client5", name = "httpclient5-fluent", version = "5.2.3")
  implementation(group = "com.google.code.gson", name = "gson", version = "2.10.1")
  implementation(group = "com.h2database", name = "h2", version = "2.2.224")

  implementation(group = "org.scala-lang", name = "scala-library", version = scala_version)
  implementation(group = "org.scala-lang", name = "scala-compiler", version = scala_version)
  implementation(group = "org.scala-lang", name = "scala-reflect", version = scala_version)

  implementation(group = "commons-io", name = "commons-io", version = "2.15.0")
  implementation(group = "com.vladsch.flexmark", name = "flexmark-all", version = "0.64.8")
  implementation(platform("software.amazon.awssdk:bom:2.27.23"))
  implementation(group = "software.amazon.awssdk", name = "aws-sdk-java", version = "2.21.29")
  implementation(group = "software.amazon.awssdk", name = "sso", version = "2.21.29")

  implementation(group = "org.slf4j", name = "slf4j-api", version = "2.0.16")
  implementation(group = "ch.qos.logback", name = "logback-classic", version = logback_version)
  implementation(group = "ch.qos.logback", name = "logback-core", version = logback_version)

  testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = jupiter_version)
  testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-params", version = jupiter_version)
  testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = jupiter_version)
}

tasks {
  test {
    useJUnitPlatform()
    systemProperty("surefire.useManifestOnlyJar", "false")
    testLogging {
      events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
      exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    jvmArgs(
      "--add-opens",
      "java.base/java.lang.reflect=ALL-UNNAMED",
      "--add-opens",
      "java.base/java.util=ALL-UNNAMED",
      "--add-opens",
      "java.base/java.lang=ALL-UNNAMED",
      "--add-opens",
      "java.base/sun.nio.ch=ALL-UNNAMED"
    )
  }
}

tasks.war {
  archiveClassifier.set("")
  from(sourceSets.main.get().output)
  from(project.configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  isZip64 = true
  manifest {
    attributes(
      "Main-Class" to "com.simiacryptus.skyenet.AppServer"
    )
  }
}
tasks.withType<ShadowJar> {
  archiveClassifier.set("all")
  mergeServiceFiles()
  isZip64 = true
  manifest {
    attributes(
      "Main-Class" to "com.simiacryptus.skyenet.AppServer"
    )
  }
}

tasks.register<Exec>("createAppImage") {
  dependsOn("runtime")
  doFirst {
      layout.buildDirectory.dir("jpackage").get().asFile.apply {
          doFirst {
              project.exec {
                  commandLine(
                      "jpackage",
                      "--type",
                      "deb",
                      "--app-image",
                      layout.buildDirectory.dir("jpackage/SkyenetApps").get().asFile.absolutePath,
                      "--dest",
                      layout.buildDirectory.dir("jpackage").get().asFile.absolutePath,
                      "--name",
                      "SkyenetApps",
                      "--linux-shortcut",
                      "--linux-menu-group",
                      "Development"
                  )
              }
          }
          commandLine(
              listOf(
                  "jpackage",
                  "--input",
                  layout.buildDirectory.dir("libs").get().asFile.path,
                  "--main-jar",
                  "${project.name}-${project.version}-all.jar",
                  "--main-class",
                  "com.simiacryptus.skyenet.AppServer",
                  "--dest",
                  layout.buildDirectory.dir("jpackage").get().asFile.path,
                  "--copyright",
                  "Copyright © 2024 SimiaCryptus",
                  "--description",
                  "Skyenet Applications Suite",
                  "--type",
                  "app-image"
              )
          )
      }

      tasks.register("packageDeb") {
          dependsOn("createAppImage")
          onlyIf { System.getProperty("os.name").lowercase().contains("linux") }
      }
      tasks.register("packageDmg") {
          dependsOn("createAppImage")
          doFirst {
              project.exec {
                  commandLine(
                      "jpackage", "--type", "dmg", "--app-image", layout.buildDirectory.dir("jpackage/SkyenetApps").get().asFile.apply {
                          if (exists()) {
                              deleteRecursively()
                          }
                      }.absolutePath, "--dest", layout.buildDirectory.dir("jpackage").get().asFile.absolutePath, "--name", "SkyenetApps"
                  )
              }
          }
          onlyIf { System.getProperty("os.name").lowercase().contains("mac") }
      }
      tasks.register("packageMsi") {
          dependsOn("createAppImage")
          doFirst {
              // Diagnostic logging: check whether the app image directory exists
              val appImageDir = layout.buildDirectory.dir("jpackage/SkyenetApps").get().asFile
              appImageDir.mkdirs()

              project.exec {
                  workingDir = layout.buildDirectory.dir("jpackage").get().asFile
                  isIgnoreExitValue = false
                  standardOutput = System.out
                  errorOutput = System.err
                  commandLine(
                      "jpackage",
                      "--verbose",
                      "--type", "msi",
                      "--app-image", layout.buildDirectory.dir("jpackage/SkyenetApps").get().asFile.apply { mkdirs() }.absolutePath,
                      "--name", "SkyenetApps",
                      "--vendor", "SimiaCryptus",
                      "--app-version", "${project.version}",
                      "--icon", layout.projectDirectory.dir("src/main/resources/app-icon.ico").asFile.absolutePath,
                      "--win-dir-chooser",
                      "--win-menu",
                      "--win-shortcut",
                      "--win-per-user-install",
                      "--resource-dir", layout.projectDirectory.dir("src/main/resources").asFile.absolutePath
                  )
              }
              onlyIf {
                  val isWindows = System.getProperty("os.name").lowercase().contains("windows")
                  if (!isWindows) {
                      logger.warn("Skipping MSI packaging - Windows OS required")
                  }
                  isWindows
              }
              outputs.dir(layout.buildDirectory.dir("jpackage"))
          }
          tasks.register("package") {
              val os = System.getProperty("os.name").lowercase()
              when {
                  os.contains("linux") -> dependsOn("packageDeb")
                  os.contains("mac") -> dependsOn("packageDmg")
                  os.contains("windows") -> dependsOn("packageMsi")
              }
              description = "Creates platform-specific packages"
              group = "distribution"
          }

          tasks.named("build") {
              dependsOn(tasks.war)
              dependsOn(tasks.shadowJar)
              dependsOn(tasks.named("package"))
          }

          fun log(appImageDir: File) {
              println("Diagnostic: Checking if app image directory exists at: ${appImageDir.absolutePath}")
              if (!appImageDir.exists()) {
                  println("Diagnostic Error: App image directory does not exist. Listing its parent directory contents:")
                  val parentDir = (appImageDir.parentFile) ?: return
                  if (parentDir.exists()) parentDir.listFiles()?.forEach { file ->
                      println(" - ${file.name} [${if (file.isDirectory) "dir" else "file"}]")
                  }
                  else {
                      log(parentDir)
                      return
                  }
              } else {
                  println("Diagnostic: App image directory exists.")
              }
          }
      }
  }
}