package io.github.bobistuca.vertex.core.storage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Line-based, escaped text format. Tabs, newlines and backslashes inside values are escaped, so no value can
 * forge an additional field or a whole extra record.
 */
public final class EvidenceCodec {
    private static final String VERSION = "V1";
    private static final int MAX_FIELD = 1024;

    private EvidenceCodec() { }

    public static String encode(EvidenceEntry e) {
        StringBuilder data = new StringBuilder();
        for (Map.Entry<String, String> kv : e.data().entrySet()) {
            if (data.length() > 0) {
                data.append('\u001F');
            }
            data.append(esc(kv.getKey())).append('\u001E').append(esc(kv.getValue()));
        }
        return String.join("\t", VERSION, Long.toString(e.wallClockMillis()), esc(e.type()),
                e.playerId() == null ? "-" : e.playerId().toString(), esc(e.playerName()), esc(e.checkId()),
                esc(e.kind()), esc(e.confidence()), Integer.toString(e.count()), e.suppressed() ? "1" : "0",
                esc(e.reason()), data.toString());
    }

    public static Optional<EvidenceEntry> decode(String line) {
        try {
            String[] f = line.split("\t", -1);
            if (f.length != 12 || !VERSION.equals(f[0])) {
                return Optional.empty();
            }
            Map<String, String> data = new LinkedHashMap<>();
            if (!f[11].isEmpty()) {
                for (String pair : f[11].split("\u001F", -1)) {
                    int sep = pair.indexOf('\u001E');
                    if (sep > 0) {
                        data.put(unesc(pair.substring(0, sep)), unesc(pair.substring(sep + 1)));
                    }
                }
            }
            UUID id = "-".equals(f[3]) ? null : UUID.fromString(f[3]);
            return Optional.of(new EvidenceEntry(Long.parseLong(f[1]), unesc(f[2]), id, unesc(f[4]), unesc(f[5]),
                    unesc(f[6]), unesc(f[7]), Integer.parseInt(f[8]), "1".equals(f[9]), unesc(f[10]), data));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        String v = s.length() > MAX_FIELD ? s.substring(0, MAX_FIELD) : s;
        StringBuilder out = new StringBuilder(v.length() + 8);
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\u001E' -> out.append("\\e");
                case '\u001F' -> out.append("\\u");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    static String unesc(String s) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 't' -> out.append('\t');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 'e' -> out.append('\u001E');
                    case 'u' -> out.append('\u001F');
                    default -> out.append(n);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
