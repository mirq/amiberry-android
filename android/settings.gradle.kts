pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

// Load local properties if they exist (for machine-specific settings)
val localPropertiesFile = file("gradle.local.properties")
if (localPropertiesFile.exists()) {
    val localProperties = java.util.Properties()
    localProperties.load(localPropertiesFile.inputStream())
    localProperties.forEach { key, value ->
        gradle.rootProject.extra.set(key.toString(), value)
        System.setProperty(key.toString(), value.toString())
    }
}

rootProject.name = "Amiberry"
include(":app")
