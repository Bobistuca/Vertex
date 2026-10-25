// Root build: shared conventions only. Runtime code lives in :core (no server API) and :plugin (server integration).
subprojects {
    group = "io.github.bobistuca.vertex"
    // Plugin loaders require a version string. Use a non-numeric internal build identifier, never a release label.
    version = providers.environmentVariable("VERTEX_BUILD_ID").orElse("development").get()

    repositories {
        // CI uses Maven Central. VERTEX_MAVEN_MIRROR lets a restricted environment point at a Central mirror.
        val mirror = providers.environmentVariable("VERTEX_MAVEN_MIRROR").orNull
        if (mirror != null) maven(mirror) else mavenCentral()
    }

    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion = JavaLanguageVersion.of(25)
        }
        tasks.withType<JavaCompile>().configureEach {
            // Shared baseline: Java 21 bytecode so the one jar loads on Minecraft 1.21 servers. The JDK 25
            // toolchain only supplies the newest class files to compile against; --release 21 hides newer JDK APIs.
            options.release = 21
            options.encoding = "UTF-8"
            options.compilerArgs.add("-Xlint:all,-serial,-processing")
            // The server-independent core must compile without any warning; integration modules report them.
            if (project.name == "core") options.compilerArgs.add("-Werror")
        }
    }
}
