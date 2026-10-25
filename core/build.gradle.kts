plugins {
    `java-library`
}

val adventureVersion = providers.gradleProperty("adventureVersion").get()
val snakeYamlVersion = providers.gradleProperty("snakeYamlVersion").get()

dependencies {
    // Used only to parse configurable MiniMessage templates into a neutral rich-text model.
    api("net.kyori:adventure-api:$adventureVersion")
    api("net.kyori:adventure-text-minimessage:$adventureVersion")
    api("org.yaml:snakeyaml:$snakeYamlVersion")
}
