import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.testing.logging.TestLogEvent
import java.net.URI

fun properties(key: String) = project.findProperty(key).toString()
group = properties("libraryGroup")
version = properties("libraryVersion")

plugins {
    java
    `java-library`
    `maven-publish`
    id("signing")
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.jetbrains.kotlin.jvm") version "1.9.20"
}

repositories {
    mavenCentral {
        metadataSources {
            mavenPom()
            artifact()
        }
    }
}

kotlin {
//    jvmToolchain(11)
    jvmToolchain(17)
}

val kotlin_version = "1.9.20"
val jetty_version = "11.0.17"
val scala_version = "2.13.8"
val skyenet_version = "1.0.26"
dependencies {
    implementation(group = "com.simiacryptus", name = "joe-penai", version = "1.0.27")

    implementation(group = "com.simiacryptus.skyenet", name = "core", version = skyenet_version)
    implementation(group = "com.simiacryptus.skyenet", name = "groovy", version = skyenet_version)
    implementation(group = "com.simiacryptus.skyenet", name = "kotlin", version = skyenet_version)
    implementation(group = "com.simiacryptus.skyenet", name = "scala", version = skyenet_version)
    implementation(group = "com.simiacryptus.skyenet", name = "webui", version = skyenet_version)

    implementation(group = "org.eclipse.jetty", name = "jetty-server", version = jetty_version)
    implementation(group = "com.vladsch.flexmark", name = "flexmark-all", version = "0.64.8")
    implementation(group = "org.eclipse.jetty.websocket", name = "websocket-jetty-server", version = jetty_version)
    implementation(group = "org.eclipse.jetty", name = "jetty-webapp", version = jetty_version)
    implementation(group = "commons-io", name = "commons-io", version = "2.11.0")
    implementation(group = "com.amazonaws", name = "aws-java-sdk", version = "1.12.454")

    implementation(group = "org.slf4j", name = "slf4j-api", version = "2.0.9")
    implementation(group = "ch.qos.logback", name = "logback-classic", version = "1.4.11")
    implementation(group = "ch.qos.logback", name = "logback-core", version = "1.4.11")
}

tasks.withType(ShadowJar::class.java).configureEach {
    archiveClassifier.set("")
    mergeServiceFiles()
    append("META-INF/kotlin_module")
}

tasks {
    compileKotlin {
        kotlinOptions {
            javaParameters = true
        }
    }
    compileTestKotlin {
        kotlinOptions {
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
            "--add-opens", "java.base/java.lang=ALL-UNNAMED"
        )
    }
    wrapper {
        gradleVersion = properties("gradleVersion")
    }
}

tasks.withType(ShadowJar::class.java).configureEach {
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

