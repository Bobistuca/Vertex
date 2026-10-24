package io.github.bobistuca.vertex.core.support;

import io.github.bobistuca.vertex.core.config.ConfigException;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.config.SettingsLoader;
import io.github.bobistuca.vertex.core.i18n.MessageCatalog;
import io.github.bobistuca.vertex.core.i18n.MessageFactory;
import io.github.bobistuca.vertex.core.model.Confidence;
import io.github.bobistuca.vertex.core.model.Detection;
import io.github.bobistuca.vertex.core.model.DetectionKind;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public final class TestSupport {
    public static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    public static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    public static final long MS = 1_000_000L;

    private TestSupport() { }

    public static Reader resource(String path) {
        InputStream in = TestSupport.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("missing resource " + path);
        }
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }

    public static Map<String, String> locale(String name) {
        try {
            return SettingsLoader.parseFlatMessages(resource("lang/" + name + ".yml"));
        } catch (ConfigException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Settings shippedSettings() {
        try {
            return SettingsLoader.load(SettingsLoader.parseYaml(resource("config.yml")));
        } catch (ConfigException e) {
            throw new IllegalStateException(e.problems().toString(), e);
        }
    }

    public static MessageFactory messages(String locale) {
        Settings s = shippedSettings();
        return new MessageFactory(new MessageCatalog(locale(locale), locale("en_US"), s.appearance()), s.appearance());
    }

    public static Detection detection(UUID player, String name, String check, String dedup, long wall) {
        return new Detection(player, name, check, DetectionKind.SUSPICION, Confidence.HIGH, "test reason",
                Map.of("samples", "60", "meanCps", "10.00"), wall, dedup);
    }
}
