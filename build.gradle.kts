import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
    `java-library`
    `maven-publish`
    id("signing")
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
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

kotlin {
    compilerOptions {
        javaParameters = true
    }
    jvmToolchain(17)
}

val jetty_version = "11.0.18"
val skyenet_version = "1.0.44"
val scala_version = "2.13.8"
val spark_version = "3.5.0"
val jackson_version = "2.15.3"
val jupiter_version = "5.10.1"
dependencies {
    implementation("org.postgresql:postgresql:42.7.1")

    implementation(group = "com.simiacryptus", name = "jo-penai", version = "1.0.42")

    implementation(group = "com.simiacryptus.skyenet", name = "core", version = skyenet_version)
    implementation(group = "com.simiacryptus.skyenet", name = "groovy", version = skyenet_version)
    implementation(group = "com.simiacryptus.skyenet", name = "kotlin", version = skyenet_version)
    implementation(group = "com.simiacryptus.skyenet", name = "scala", version = skyenet_version)
    implementation(group = "com.simiacryptus.skyenet", name = "webui", version = skyenet_version)

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
    implementation(platform("software.amazon.awssdk:bom:2.21.9"))
    implementation(group = "software.amazon.awssdk", name = "aws-sdk-java", version = "2.21.29")
    implementation(group = "software.amazon.awssdk", name = "sso", version = "2.21.29")

    implementation(group = "org.slf4j", name = "slf4j-api", version = "2.0.9")
    implementation(group = "ch.qos.logback", name = "logback-classic", version = "1.4.11")
    implementation(group = "ch.qos.logback", name = "logback-core", version = "1.4.11")

    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = jupiter_version)
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-params", version = jupiter_version)
    testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = jupiter_version)
}

tasks {
    compileKotlin {
        compilerOptions {
            javaParameters = true
        }
    }
    compileTestKotlin {
        compilerOptions {
            javaParameters = true
        }
    }
    test {
        useJUnitPlatform()
        systemProperty("surefire.useManifestOnlyJar", "false")
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        jvmArgs(
            "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
            "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED"
        )
    }
    wrapper {
        gradleVersion = properties("gradleVersion")
    }
}

tasks.withType(ShadowJar::class.java).configureEach {
    archiveClassifier.set("")
    mergeServiceFiles()
    append("META-INF/kotlin_module")
    isZip64 = true

    archiveClassifier.set("")
    mergeServiceFiles()
    append("META-INF/kotlin_module")

    exclude("**/META-INF/*.SF")
    exclude("**/META-INF/*.DSA")
    exclude("**/META-INF/*.RSA")
    exclude("**/META-INF/*.MF")
    exclude("META-INF/versions/9/module-info.class")

    manifest {
        attributes(
            "Main-Class" to "com.simiacryptus.skyenet.AppServer"
        )
    }
}

