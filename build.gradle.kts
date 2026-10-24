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
            options.release = 25
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing", "-Werror"))
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
                showStandardStreams = false
            }
        }
    }
}
