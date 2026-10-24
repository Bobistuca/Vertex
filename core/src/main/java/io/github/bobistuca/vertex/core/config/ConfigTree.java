package io.github.bobistuca.vertex.core.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Defensive typed reader over a parsed YAML tree. Every problem is recorded instead of thrown. */
public final class ConfigTree {
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    private final Map<?, ?> root;
    private final String prefix;
    private final List<String> errors;

    public ConfigTree(Map<?, ?> root) {
        this(root == null ? Map.of() : root, "", new ArrayList<>());
    }

    private ConfigTree(Map<?, ?> root, String prefix, List<String> errors) {
        this.root = root;
        this.prefix = prefix;
        this.errors = errors;
    }

    public List<String> errors() {
        return errors;
    }

    public ConfigTree section(String key) {
        Object value = root.get(key);
        if (value == null) {
            return new ConfigTree(Map.of(), prefix + key + ".", errors);
        }
        if (value instanceof Map<?, ?> map) {
            return new ConfigTree(map, prefix + key + ".", errors);
        }
        errors.add(prefix + key + ": expected a section");
        return new ConfigTree(Map.of(), prefix + key + ".", errors);
    }

    public boolean bool(String key, boolean fallback) {
        Object value = root.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        errors.add(prefix + key + ": expected true or false");
        return fallback;
    }

    public int integer(String key, int fallback, int min, int max) {
        Object value = root.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n && !(value instanceof Double) && !(value instanceof Float)) {
            long v = n.longValue();
            if (v < min || v > max) {
                errors.add(prefix + key + ": must be between " + min + " and " + max);
                return fallback;
            }
            return (int) v;
        }
        errors.add(prefix + key + ": expected a whole number");
        return fallback;
    }

    public long longValue(String key, long fallback, long min, long max) {
        Object value = root.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n && !(value instanceof Double) && !(value instanceof Float)) {
            long v = n.longValue();
            if (v < min || v > max) {
                errors.add(prefix + key + ": must be between " + min + " and " + max);
                return fallback;
            }
            return v;
        }
        errors.add(prefix + key + ": expected a whole number");
        return fallback;
    }

    public double decimal(String key, double fallback, double min, double max) {
        Object value = root.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            double v = n.doubleValue();
            if (!Double.isFinite(v) || v < min || v > max) {
                errors.add(prefix + key + ": must be between " + min + " and " + max);
                return fallback;
            }
            return v;
        }
        errors.add(prefix + key + ": expected a number");
        return fallback;
    }

    public String text(String key, String fallback, int maxLength) {
        Object value = root.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof String s && s.length() <= maxLength) {
            return s;
        }
        errors.add(prefix + key + ": expected text of at most " + maxLength + " characters");
        return fallback;
    }

    public String hexColor(String key, String fallback) {
        String value = text(key, fallback, 7);
        if (!HEX.matcher(value).matches()) {
            errors.add(prefix + key + ": expected a color such as #86EFAC");
            return fallback;
        }
        return value.toUpperCase(Locale.ROOT);
    }

    public List<String> textList(String key, List<String> fallback, int maxItems, int maxLength) {
        Object value = root.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof List<?> list) {
            if (list.size() > maxItems) {
                errors.add(prefix + key + ": at most " + maxItems + " entries");
                return fallback;
            }
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String s && s.length() <= maxLength) {
                    out.add(s);
                } else if (item instanceof Number || item instanceof Boolean) {
                    out.add(String.valueOf(item));
                } else {
                    errors.add(prefix + key + ": entries must be short text values");
                    return fallback;
                }
            }
            return out;
        }
        errors.add(prefix + key + ": expected a list");
        return fallback;
    }
}
