// Paper-only adapter. It is compiled against the Paper API and loaded reflectively, only when Paper is detected
// at runtime, so no class that must load on CraftBukkit or Spigot references it.
plugins {
    `java-library`
}

val paperApiVersion = providers.gradleProperty("paperApiVersion").get()

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly(project(":core"))
}
