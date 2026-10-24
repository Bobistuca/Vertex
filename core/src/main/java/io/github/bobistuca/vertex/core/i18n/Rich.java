package io.github.bobistuca.vertex.core.i18n;

import java.util.ArrayList;
import java.util.List;

/** Neutral rich-text model rendered by platform messengers (Paper native, Spigot BungeeCord, legacy text). */
public final class Rich {
    private static final Rich EMPTY = new Rich(List.of());

    private final List<RichSpan> spans;

    public Rich(List<RichSpan> spans) {
        this.spans = List.copyOf(spans);
    }

    public static Rich empty() {
        return EMPTY;
    }

    public static Rich plain(String text) {
        return new Rich(List.of(new RichSpan(text, -1, false, false, false, null)));
    }

    public List<RichSpan> spans() {
        return spans;
    }

    public boolean isEmpty() {
        return spans.isEmpty();
    }

    /** Attaches hover text to every span (spans that already carry hover text are left unchanged). */
    public Rich withHover(Rich hover) {
        List<RichSpan> out = new ArrayList<>(spans.size());
        for (RichSpan s : spans) {
            out.add(s.hover() != null ? s
                    : new RichSpan(s.text(), s.rgb(), s.bold(), s.italic(), s.underlined(), hover));
        }
        return new Rich(out);
    }

    public Rich plus(Rich other) {
        List<RichSpan> out = new ArrayList<>(spans);
        out.addAll(other.spans);
        return new Rich(out);
    }

    public static Rich join(List<Rich> lines) {
        List<RichSpan> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                out.add(new RichSpan("\n", -1, false, false, false, null));
            }
            out.addAll(lines.get(i).spans);
        }
        return new Rich(out);
    }

    public String toPlainText() {
        StringBuilder sb = new StringBuilder();
        for (RichSpan s : spans) {
            sb.append(s.text());
        }
        return sb.toString();
    }

    /**
     * Legacy section-sign text with hexadecimal colors (understood by clients since 1.16). Hover text cannot be
     * represented and is dropped; this is the documented fallback when rich messaging is unavailable.
     */
    public String toLegacyText() {
        StringBuilder sb = new StringBuilder();
        for (RichSpan s : spans) {
            sb.append("§r");
            if (s.rgb() >= 0) {
                String hex = String.format("%06x", s.rgb());
                sb.append("§x");
                for (char c : hex.toCharArray()) {
                    sb.append('§').append(c);
                }
            }
            if (s.bold()) {
                sb.append("§l");
            }
            if (s.italic()) {
                sb.append("§o");
            }
            if (s.underlined()) {
                sb.append("§n");
            }
            sb.append(s.text());
        }
        return sb.toString();
    }
}
