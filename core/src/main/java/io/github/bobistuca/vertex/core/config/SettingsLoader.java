package io.github.bobistuca.vertex.core.config;

import io.github.bobistuca.vertex.core.staff.StaffGroups;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/** Parses and validates configuration text. Defaults mirror the shipped config.yml. */
public final class SettingsLoader {
    private static final Set<String> GAME_MODES = Set.of("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR");

    private SettingsLoader() { }

    /** Parses YAML safely (no arbitrary object construction, bounded aliases and size). */
    public static Map<?, ?> parseYaml(Reader reader) throws ConfigException {
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(10);
        options.setNestingDepthLimit(20);
        options.setCodePointLimit(1_000_000);
        options.setAllowDuplicateKeys(false);
        try (reader) {
            Object loaded = new Yaml(new SafeConstructor(options)).load(reader);
            if (loaded == null) {
                return Map.of();
            }
            if (loaded instanceof Map<?, ?> map) {
                return map;
            }
            throw new ConfigException(List.of("the document root must be a section"));
        } catch (YAMLException | IOException | ClassCastException e) {
            throw new ConfigException(List.of("invalid YAML: " + firstLine(e.getMessage())));
        }
    }

    /** Flat key/value reader for locale files. */
    public static Map<String, String> parseFlatMessages(Reader reader) throws ConfigException {
        Map<?, ?> tree = parseYaml(reader);
        Map<String, String> flat = new java.util.LinkedHashMap<>();
        flatten("", tree, flat);
        return flat;
    }

    private static void flatten(String prefix, Map<?, ?> node, Map<String, String> out) {
        for (Map.Entry<?, ?> entry : node.entrySet()) {
            String key = prefix + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?> child) {
                flatten(key + ".", child, out);
            } else if (entry.getValue() != null) {
                out.put(key, String.valueOf(entry.getValue()));
            }
        }
    }

    public static Settings defaults() {
        try {
            return load(Map.of());
        } catch (ConfigException e) {
            throw new IllegalStateException("built-in defaults are invalid", e);
        }
    }

    public static Settings load(Map<?, ?> root) throws ConfigException {
        ConfigTree t = new ConfigTree(root);
        String language = t.text("language", "pt_BR", 16);
        if (!language.matches("[A-Za-z]{2}_[A-Za-z]{2}")) {
            t.errors().add("language: expected a locale such as pt_BR or en_US");
            language = "pt_BR";
        }

        ConfigTree ap = t.section("appearance");
        ConfigTree sym = ap.section("symbols");
        Settings.Appearance appearance = new Settings.Appearance(
                ap.hexColor("primary", "#86EFAC"),
                ap.hexColor("secondary", "#166534"),
                ap.hexColor("text", "#FFFFFF"),
                sym.text("info", "ℹ", 4), sym.text("suspicion", "⚠", 4),
                sym.text("established", "✖", 4), sym.text("admin", "⚙", 4));

        ConfigTree al = t.section("alerts");
        ConfigTree rl = al.section("rate-limit");
        ConfigTree ab = al.section("action-bar");
        ConfigTree so = al.section("sound");
        ConfigTree rc = al.section("recipients");
        Set<String> modes = new LinkedHashSet<>();
        for (String mode : rc.textList("game-modes", List.of("CREATIVE"), 8, 16)) {
            String up = mode.toUpperCase(Locale.ROOT);
            if (!GAME_MODES.contains(up)) {
                t.errors().add("alerts.recipients.game-modes: unknown game mode " + safe(mode));
            } else {
                modes.add(up);
            }
        }
        Settings.Alerts alerts = new Settings.Alerts(
                al.longValue("aggregation-window-ms", 2000, 200, 60_000),
                rl.integer("max-per-player-per-minute", 30, 0, 10_000),
                rl.integer("max-per-recipient-per-minute", 60, 0, 10_000),
                al.bool("log-to-console", true), al.bool("notify-subject", false),
                new Settings.ActionBar(ab.bool("enabled", false)),
                new Settings.Sound(so.bool("enabled", false), so.text("name", "block.note_block.pling", 64),
                        so.decimal("volume", 0.6, 0.0, 2.0), so.decimal("pitch", 1.4, 0.5, 2.0)),
                new Settings.Recipients(rc.bool("operators", true), rc.bool("permission", true),
                        rc.bool("staff", true), modes));

        ConfigTree ci = t.section("client-info");
        Settings.ClientInfo clientInfo = new Settings.ClientInfo(
                ci.bool("enabled", true), ci.longValue("timeout-ms", 3000, 500, 30_000),
                ci.integer("max-channels", 16, 0, 64));

        ConfigTree gr = t.section("grace");
        Settings.Grace grace = new Settings.Grace(
                gr.longValue("join-ms", 5000, 0, 60_000), gr.longValue("teleport-ms", 2000, 0, 60_000),
                gr.longValue("respawn-ms", 3000, 0, 60_000), gr.longValue("world-change-ms", 3000, 0, 60_000),
                gr.longValue("velocity-ms", 1500, 0, 60_000), gr.longValue("damage-ms", 1000, 0, 60_000),
                gr.longValue("game-mode-ms", 2000, 0, 60_000), gr.longValue("flight-ms", 2000, 0, 60_000), gr.longValue("rank-change-ms", 5000, 0, 60_000),
                gr.decimal("low-tps-threshold", 15.0, 1.0, 20.0));

        ConfigTree ck = t.section("checks");
        ConfigTree pr = ck.section("packet-rate");
        ConfigTree ac = ck.section("auto-clicker");
        ConfigTree mv = ck.section("movement");
        ConfigTree rh = ck.section("reach");
        Settings.Checks checks = new Settings.Checks(
                new Settings.InvalidValues(ck.section("invalid-values").bool("enabled", true)),
                new Settings.InvalidFields(ck.section("invalid-fields").bool("enabled", true)),
                new Settings.PacketRate(pr.bool("enabled", true), pr.integer("window-limit", 26, 21, 1000),
                        pr.integer("sustained-windows", 5, 2, 100), pr.longValue("stall-grace-ms", 2500, 0, 60_000),
                        pr.integer("flood-limit", 400, 50, 100_000), pr.integer("flood-sustained-windows", 3, 1, 100)),
                new Settings.AutoClicker(ac.bool("enabled", true), ac.integer("window-samples", 60, 20, 500),
                        ac.longValue("max-gap-ms", 500, 100, 5000), ac.decimal("min-mean-cps", 8.0, 2.0, 40.0),
                        ac.decimal("batch-ms", 3.0, 0.0, 20.0), ac.decimal("max-batched-share", 0.10, 0.0, 1.0),
                        ac.decimal("dominant-tick-share", 0.80, 0.3, 1.0),
                        ac.decimal("max-tick-entropy-bits", 0.90, 0.0, 4.0),
                        ac.decimal("period-match-share", 0.92, 0.5, 1.0), ac.integer("max-period", 8, 1, 20),
                        ac.decimal("max-cv", 0.18, 0.01, 1.0), ac.decimal("max-residual-ms", 1.2, 0.1, 20.0),
                        ac.integer("required-signals", 2, 1, 3), ac.bool("include-air-swings", false),
                        ac.decimal("dedupe-ms", 25.0, 0.0, 49.0), ac.decimal("min-tps", 18.0, 1.0, 20.0),
                        ac.decimal("max-jitter-ms", 40.0, 1.0, 1000.0),
                        ac.decimal("buffer-threshold", 1.0, 0.5, 20.0), ac.decimal("buffer-decay", 0.5, 0.0, 10.0)),
                new Settings.Movement(mv.bool("enabled", true), mv.decimal("speed-factor", 3.4, 1.0, 50.0),
                        mv.decimal("speed-base", 0.03, 0.0, 1.0), mv.decimal("fly-factor", 14.0, 5.0, 200.0),
                        mv.decimal("max-step-up", 0.62, 0.3, 5.0), mv.decimal("jump-boost-per-level", 0.35, 0.0, 2.0),
                        mv.decimal("air-rise-limit", 1.45, 1.0, 10.0), mv.integer("hover-packets", 14, 5, 200),
                        mv.decimal("sustained-factor", 2.9, 1.0, 50.0), mv.integer("sustained-packets", 20, 8, 200),
                        mv.decimal("slippery-multiplier", 2.2, 1.0, 10.0),
                        mv.decimal("buffer-threshold", 6.0, 1.0, 200.0), mv.decimal("buffer-decay", 0.25, 0.0, 10.0)),
                new Settings.Reach(rh.bool("enabled", true), rh.decimal("tolerance", 0.15, 0.0, 5.0),
                        rh.longValue("latency-cap-ms", 250, 0, 2000), rh.longValue("processing-delay-ms", 60, 0, 1000),
                        rh.decimal("creative-bonus", 2.0, 0.0, 10.0),
                        rh.decimal("buffer-threshold", 3.0, 1.0, 50.0), rh.decimal("buffer-decay", 0.5, 0.0, 10.0)),
                new Settings.Inventory(ck.section("inventory").bool("enabled", true),
                        ck.section("inventory").longValue("window-grace-ms", 1500, 0, 60_000)),
                new Settings.FlightToggle(ck.section("flight-toggle").bool("enabled", true)));

        ConfigTree au = t.section("audit");
        ConfigTree mo = au.section("monitored");
        Settings.Audit audit = new Settings.Audit(
                au.bool("enabled", true), au.integer("op-interval-ticks", 100, 20, 12_000),
                au.integer("reconcile-per-run", 4, 1, 200),
                au.longValue("event-correlation-ms", 3000, 100, 60_000),
                au.longValue("reconcile-correlation-ms", 10_000, 500, 120_000),
                uuids(t, "audit.trusted-uuids", au.textList("trusted-uuids", List.of(), 500, 40)),
                uuids(t, "audit.authorized-actors", au.textList("authorized-actors", List.of(), 500, 40)),
                au.bool("console-authorized", true),
                new Settings.Monitored(mo.bool("op", true), mo.bool("game-mode", true),
                        mo.bool("permission-groups", true), mo.bool("tags", true), mo.bool("teams", true)),
                Set.copyOf(au.textList("sensitive-tags", List.of(), 200, 64)),
                au.textList("monitored-permissions", List.of(), 100, 128),
                au.bool("notify-authorized-changes", false), au.bool("notify-no-change", false));

        ConfigTree sf = t.section("staff");
        ConfigTree lp = sf.section("luckperms");
        Settings.Staff staff = new Settings.Staff(sf.bool("bypass-permission", true), sf.bool("permission-wildcards", false),
                sf.bool("operators-exempt", false), lp.bool("enabled", true), lp.bool("exempt-groups", true),
                lp.textList("groups", List.of("Mod", "Mod Plus", "Administrator", "Administrador", "Dono", "Owner"), 100, 64),
                lp.hasKey("mappings") ? lp.textListMap("mappings", 100, 20, 64)
                        : java.util.Map.of("Mod Plus", List.of("modplus", "mod_plus", "mod-plus")));
        t.errors().addAll(StaffGroups.validate(staff));

        ConfigTree tm = t.section("test-mode");
        Settings.TestMode testMode = new Settings.TestMode(tm.bool("enabled", true), tm.integer("duration-minutes", 15, 1, 240));

        ConfigTree st = t.section("storage");
        Settings.Storage storage = new Settings.Storage(
                st.integer("max-entries", 5000, 100, 1_000_000),
                st.longValue("max-bytes", 5_000_000, 100_000, 500_000_000),
                st.integer("retention-days", 14, 1, 3650), st.integer("queue-capacity", 2048, 64, 100_000),
                st.integer("memory-per-player", 100, 10, 10_000), st.integer("memory-global", 500, 50, 100_000));

        if (!t.errors().isEmpty()) {
            throw new ConfigException(new ArrayList<>(t.errors()));
        }
        return new Settings(language, appearance, alerts, clientInfo, grace, checks, audit, staff, testMode, storage);
    }

    private static Set<UUID> uuids(ConfigTree t, String path, List<String> raw) {
        Set<UUID> out = new LinkedHashSet<>();
        for (String value : raw) {
            try {
                out.add(UUID.fromString(value.trim()));
            } catch (IllegalArgumentException e) {
                t.errors().add(path + ": not a valid UUID: " + safe(value));
            }
        }
        return out;
    }

    private static String safe(String value) {
        String trimmed = value.length() > 40 ? value.substring(0, 40) + "…" : value;
        return trimmed.replaceAll("[\\p{Cntrl}]", "?");
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "unreadable";
        }
        int nl = message.indexOf('\n');
        return nl < 0 ? message : message.substring(0, nl);
    }
}
