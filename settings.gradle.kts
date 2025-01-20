rootProject.name = "SkyenetApps"

includeBuild("../SkyeNet/")
includeBuild("../jo-penai/")

pluginManagement {
    val kotlinVersion = "1.9.22"
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("org.jetbrains.kotlin")) {
                useVersion(kotlinVersion)
            }
        }
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}