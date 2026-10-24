package io.github.bobistuca.vertex.core.config;

import java.io.Reader;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Holds the last working configuration. A failed reload never replaces it. */
public final class SettingsHolder {

    /** Outcome of a reload attempt. */
    public record ReloadResult(boolean applied, List<String> problems) { }

    private final AtomicReference<Settings> current;

    public SettingsHolder(Settings initial) {
        this.current = new AtomicReference<>(initial);
    }

    public Settings get() {
        return current.get();
    }

    /** Parses, validates and swaps atomically; on any failure keeps the previous settings. */
    public ReloadResult reload(Supplier<Reader> source) {
        try {
            Settings next = SettingsLoader.load(SettingsLoader.parseYaml(source.get()));
            current.set(next);
            return new ReloadResult(true, List.of());
        } catch (ConfigException e) {
            return new ReloadResult(false, e.problems());
        } catch (RuntimeException e) {
            return new ReloadResult(false, List.of("unexpected error: " + e.getClass().getSimpleName()));
        }
    }
}
