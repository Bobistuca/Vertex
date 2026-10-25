// Paper-only adapter. It is compiled against the Paper API and loaded reflectively, only when Paper is detected
// at runtime, so no class that must load on CraftBukkit or Spigot references it.
plugins {
    `java-library`
}

val paperApiVersion = providers.gradleProperty("paperApiVersion").get()

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

// The adapter's own bytecode targets Java 21 (--release 21), but the Paper API it compiles against is published for
// Java 25 only. Only the compile classpath is relaxed so the newest API can be read; the adapter itself is checked
// to stay at class-file major version 65 by the Build workflow.
configurations.compileClasspath {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly(project(":core"))
}
