rootProject.name = "SkyenetApps"

includeBuild("../AiCoderProject/SkyeNet/")
includeBuild("../AiCoderProject/jo-penai/")
 pluginManagement {
     plugins {
        id("org.jetbrains.kotlin.jvm") version "1.9.22" apply false
     }
 }