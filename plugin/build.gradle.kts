plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.6.1"
}

val packetEventsVersion = providers.gradleProperty("packetEventsVersion").get()
val spigotApiVersion = providers.gradleProperty("spigotApiVersion").get()
val luckPermsApiVersion = providers.gradleProperty("luckPermsApiVersion").get()
val junitVersion = providers.gradleProperty("junitVersion").get()

repositories {
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
        content { includeGroup("org.spigotmc") }
    }
    maven("https://repo.codemc.io/repository/maven-releases/") {
        content { includeGroup("com.github.retrooper") }
    }
}

dependencies {
    // Oldest verified API baseline. Spigot-only and Paper-only APIs live behind runtime capability checks.
    compileOnly("org.spigotmc:spigot-api:$spigotApiVersion")
    compileOnly("net.luckperms:api:$luckPermsApiVersion")

    implementation(project(":core"))
    implementation("com.github.retrooper:packetevents-spigot:$packetEventsVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The Paper adapter needs the server's native Adventure classes, so it must not go through the relocation that
// shading applies to net.kyori. Its compiled classes are embedded as ".class.bin" resources (which shading leaves
// untouched) and loaded on demand by EmbeddedClassLoader, only when Paper is detected at runtime.
val embeddedRoot = layout.buildDirectory.dir("generated/embedded-resources")
val embedPaperAdapter = tasks.register<Copy>("embedPaperAdapter") {
    val paperJar = project(":paper").tasks.named<Jar>("jar")
    dependsOn(paperJar)
    from(paperJar.flatMap { it.archiveFile }.map { zipTree(it) })
    include("io/github/bobistuca/vertex/paper/**/*.class")
    rename { "$it.bin" }
    into(embeddedRoot.map { it.dir("embedded/paper") })
}
sourceSets.main {
    resources.srcDir(embeddedRoot)
}

tasks.processResources {
    dependsOn(embedPaperAdapter)
    val buildId = project.version.toString()
    inputs.property("buildId", buildId)
    filesMatching("plugin.yml") {
        expand("version" to buildId)
    }
}

tasks.jar {
    archiveClassifier = "plain"
}

val relocationRoot = "io.github.bobistuca.vertex.libs"

tasks.shadowJar {
    archiveBaseName = "Vertex"
    archiveVersion = ""
    archiveClassifier = ""
    // Service descriptors (Adventure providers) must be merged and relocated; no unsafe minimization.
    mergeServiceFiles()
    relocate("com.github.retrooper.packetevents", "$relocationRoot.packetevents.api")
    relocate("io.github.retrooper.packetevents", "$relocationRoot.packetevents.platform")
    relocate("net.kyori", "$relocationRoot.kyori")
    relocate("org.yaml.snakeyaml", "$relocationRoot.snakeyaml")
    // Gson is provided by every supported server; never bundled.
    dependencies {
        exclude(dependency("com.google.code.gson:gson"))
    }
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
