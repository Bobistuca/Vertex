// Test-only helper plugin used by the CI smoke tests. It is never part of Vertex.jar.
plugins {
    `java-library`
}

val spigotApiVersion = providers.gradleProperty("spigotApiVersion").get()

repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
        content { includeGroup("org.spigotmc") }
    }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:$spigotApiVersion")
}

tasks.processResources {
    inputs.property("v", project.version.toString())
    filesMatching("plugin.yml") { expand("version" to project.version.toString()) }
}

tasks.jar {
    archiveBaseName = "VertexSmokeHelper"
    archiveVersion = ""
}
