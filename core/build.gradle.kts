plugins {
    `java-library`
}

val adventureVersion = providers.gradleProperty("adventureVersion").get()
val snakeYamlVersion = providers.gradleProperty("snakeYamlVersion").get()
val junitVersion = providers.gradleProperty("junitVersion").get()

dependencies {
    // Used only to parse configurable MiniMessage templates into a neutral rich-text model.
    api("net.kyori:adventure-api:$adventureVersion")
    api("net.kyori:adventure-text-minimessage:$adventureVersion")
    api("org.yaml:snakeyaml:$snakeYamlVersion")

    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
