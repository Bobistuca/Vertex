package io.github.bobistuca.vertex.core.staff;

import io.github.bobistuca.vertex.core.config.Settings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The configured administrative groups, resolved to real group identifiers. Matching is exact and case-insensitive
 * on the group identifier; there is no substring matching, and prefixes, nicknames, tags or client data are never
 * consulted.
 */
public final class StaffGroups {
    private static final Pattern GROUP_ID = Pattern.compile("[a-z0-9_.\\-]{1,64}");

    private final Set<String> ids;

    private StaffGroups(Set<String> ids) {
        this.ids = Set.copyOf(ids);
    }

    /** Resolves labels through the explicit mappings; unmapped entries are used as group identifiers. */
    public static StaffGroups of(Settings.Staff config) {
        Set<String> ids = new LinkedHashSet<>();
        for (String entry : config.groups()) {
            List<String> mapped = mapping(config.mappings(), entry);
            if (mapped != null) {
                for (String id : mapped) {
                    ids.add(id.strip().toLowerCase(Locale.ROOT));
                }
            } else {
                ids.add(entry.strip().toLowerCase(Locale.ROOT));
            }
        }
        return new StaffGroups(ids);
    }

    /** Problems that make the staff section unusable (for example a label with spaces and no mapping). */
    public static List<String> validate(Settings.Staff config) {
        List<String> problems = new ArrayList<>();
        for (String entry : config.groups()) {
            List<String> mapped = mapping(config.mappings(), entry);
            List<String> candidates = mapped != null ? mapped : List.of(entry);
            if (mapped != null && mapped.isEmpty()) {
                problems.add("staff.luckperms.mappings: '" + entry + "' maps to no group");
            }
            for (String candidate : candidates) {
                if (!GROUP_ID.matcher(candidate.strip().toLowerCase(Locale.ROOT)).matches()) {
                    problems.add("staff.luckperms: '" + candidate + "' is not a valid group identifier"
                            + (mapped == null ? " (add a mapping for labels)" : ""));
                }
            }
        }
        return problems;
    }

    private static List<String> mapping(Map<String, List<String>> mappings, String label) {
        for (Map.Entry<String, List<String>> e : mappings.entrySet()) {
            if (e.getKey().strip().equalsIgnoreCase(label.strip())) {
                return e.getValue();
            }
        }
        return null;
    }

    public Set<String> ids() {
        return ids;
    }

    /** The first configured group present in the player's (inherited) groups, if any. */
    public Optional<String> match(Collection<String> memberOf) {
        if (memberOf == null) {
            return Optional.empty();
        }
        for (String group : memberOf) {
            String id = group.toLowerCase(Locale.ROOT);
            if (ids.contains(id)) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }
}
