pluginManagement {
    repositories {
        // A restricted environment may point at a Maven Central mirror; CI uses the plugin portal directly.
        val mirror = providers.environmentVariable("VERTEX_MAVEN_MIRROR").orNull
        if (mirror != null) maven(mirror)
        gradlePluginPortal()
    }
}

rootProject.name = "Vertex"

include("core", "paper", "plugin", "smoke:helper")
