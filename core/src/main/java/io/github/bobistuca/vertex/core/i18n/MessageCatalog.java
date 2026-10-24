package io.github.bobistuca.vertex.core.i18n;

import io.github.bobistuca.vertex.core.config.Settings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Locale-backed message templates. The selected locale falls back to en_US, then to the key itself. */
public final class MessageCatalog {
    private static final String PREFIX_TOKEN = "{prefix}";

    private final Map<String, String> selected;
    private final Map<String, String> fallback;
    private final RichRenderer renderer;

    public MessageCatalog(Map<String, String> selected, Map<String, String> fallback, Settings.Appearance appearance) {
        this.selected = Map.copyOf(selected);
        this.fallback = Map.copyOf(fallback);
        this.renderer = new RichRenderer(appearance);
    }

    public String raw(String key) {
        String value = selected.get(key);
        if (value == null) {
            value = fallback.get(key);
        }
        if (value == null) {
            return "<text>!" + key + "!</text>";
        }
        if (value.contains(PREFIX_TOKEN) && !"prefix".equals(key)) {
            String prefix = selected.getOrDefault("prefix", fallback.getOrDefault("prefix", "[Vertex]"));
            value = value.replace(PREFIX_TOKEN, prefix);
        }
        return value;
    }

    public boolean has(String key) {
        return selected.containsKey(key) || fallback.containsKey(key);
    }

    /** Renders a template; values in {@code placeholders} are sanitized and inserted as escaped text. */
    public Rich render(String key, Map<String, String> placeholders) {
        Map<String, String> safe = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            safe.put(e.getKey(), Sanitizer.clean(e.getValue(), 512));
        }
        return renderer.render(raw(key), safe);
    }

    public Rich render(String key) {
        return render(key, Map.of());
    }

    public Rich renderLines(List<Rich> lines) {
        return Rich.join(lines);
    }

    /** Keys available in the selected locale (used by tests and diagnostics). */
    public Map<String, String> selectedMessages() {
        return selected;
    }
}
