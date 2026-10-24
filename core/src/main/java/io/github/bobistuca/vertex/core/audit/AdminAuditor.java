package io.github.bobistuca.vertex.core.audit;

import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.i18n.Sanitizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Audits administrative state without enforcing anything.
 *
 * <p>It keeps a baseline (existing operators, game modes, tags, teams and groups are never violations),
 * correlates observed commands with later state changes to attribute an actor only when that is reliable, and
 * distinguishes an attempted command from a real change and from a repeated no-op. Not thread safe: call it from
 * the server thread. Entries exist for tamper evidence at best; a plugin cannot protect against someone who
 * controls the server process, its files or the console.
 */
public final class AdminAuditor {
    private static final int MAX_PENDING = 256;
    private static final int MAX_BASELINES = 20_000;

    private record Key(AuditAspect aspect, UUID subject) { }

    private static final class Attempt {
        final AuditAspect aspect;
        final String target;
        final boolean selector;
        final String desired;
        final Actor actor;
        final String command;
        final long nanos;
        final long wall;
        boolean consumed;

        Attempt(AuditAspect aspect, String target, boolean selector, String desired, Actor actor, String command,
                long nanos, long wall) {
            this.aspect = aspect;
            this.target = target;
            this.selector = selector;
            this.desired = desired;
            this.actor = actor;
            this.command = command;
            this.nanos = nanos;
            this.wall = wall;
        }
    }

    private final Supplier<Settings.Audit> config;
    private final Map<Key, Set<String>> baseline = new HashMap<>();
    private final List<Attempt> attempts = new ArrayList<>();
    private Set<UUID> knownOps;

    public AdminAuditor(Supplier<Settings.Audit> config) {
        this.config = config;
    }

    // ---------------------------------------------------------------- commands

    /** Registers the administrative commands found in a command line as attempts. */
    public List<AuditRecord> onCommand(Actor actor, String rawCommand, long nanos, long wall) {
        Settings.Audit cfg = config.get();
        List<AuditRecord> records = new ArrayList<>();
        if (!cfg.enabled()) {
            return records;
        }
        String senderName = actor.kind() == Actor.Kind.PLAYER ? actor.name() : null;
        String clean = Sanitizer.clean(rawCommand, 160);
        for (ParsedCommand parsed : CommandParser.parse(rawCommand, senderName)) {
            if (!monitored(cfg, parsed.aspect())) {
                continue;
            }
            if (attempts.size() >= MAX_PENDING) {
                attempts.remove(0);
            }
            attempts.add(new Attempt(parsed.aspect(), parsed.target(), parsed.selector(), parsed.desired(), actor,
                    clean, nanos, wall));
            records.add(new AuditRecord(parsed.aspect(), AuditOutcome.ATTEMPT, null,
                    parsed.target() == null ? "selector" : Sanitizer.clean(parsed.target(), 32), "", "", actor, true,
                    AuditSeverity.INFO, clean, wall));
        }
        return records;
    }

    /** Resolves attempts whose correlation window passed without a state change into {@code NO_CHANGE}. */
    public List<AuditRecord> sweep(long nanos, long wall) {
        Settings.Audit cfg = config.get();
        long limit = Math.max(cfg.eventCorrelationMillis(), cfg.reconcileCorrelationMillis()) * 1_000_000L;
        List<AuditRecord> out = new ArrayList<>();
        Iterator<Attempt> it = attempts.iterator();
        while (it.hasNext()) {
            Attempt at = it.next();
            if (nanos - at.nanos >= limit) {
                it.remove();
                if (!at.consumed) {
                    out.add(new AuditRecord(at.aspect, AuditOutcome.NO_CHANGE, null,
                            at.target == null ? "selector" : Sanitizer.clean(at.target, 32), "", "", at.actor, true,
                            AuditSeverity.INFO, at.command, wall));
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- baseline and observation

    /** Records a value without producing any audit entry (startup, join). */
    public void baseline(AuditAspect aspect, UUID subject, Set<String> values) {
        if (baseline.size() < MAX_BASELINES || baseline.containsKey(new Key(aspect, subject))) {
            baseline.put(new Key(aspect, subject), new TreeSet<>(values));
        }
    }

    public void forget(UUID subject) {
        baseline.keySet().removeIf(k -> k.subject().equals(subject));
    }

    public boolean hasBaseline(AuditAspect aspect, UUID subject) {
        return baseline.containsKey(new Key(aspect, subject));
    }

    /**
     * Compares the observed values with the baseline. The first observation of a subject only establishes the
     * baseline. {@code fromEvent} selects the (shorter) event correlation window instead of the reconciliation one.
     */
    public List<AuditRecord> observe(AuditAspect aspect, UUID subject, String name, Set<String> current,
                                     boolean fromEvent, long nanos, long wall) {
        Settings.Audit cfg = config.get();
        if (!cfg.enabled() || !monitored(cfg, aspect)) {
            return List.of();
        }
        Key key = new Key(aspect, subject);
        Set<String> previous = baseline.get(key);
        Set<String> now = new TreeSet<>(current);
        if (previous == null) {
            baseline(aspect, subject, now);
            return List.of();
        }
        if (previous.equals(now)) {
            return List.of();
        }
        baseline.put(key, now);
        List<AuditRecord> out = new ArrayList<>();
        if (aspect == AuditAspect.TAG || aspect == AuditAspect.PERMISSION_GROUP || aspect == AuditAspect.PERMISSION_NODE) {
            Set<String> added = new TreeSet<>(now);
            added.removeAll(previous);
            Set<String> removed = new TreeSet<>(previous);
            removed.removeAll(now);
            for (String item : added) {
                out.add(changed(cfg, aspect, subject, name, "", item, "+" + item, fromEvent, nanos, wall, true));
            }
            for (String item : removed) {
                out.add(changed(cfg, aspect, subject, name, item, "", "-" + item, fromEvent, nanos, wall, false));
            }
        } else {
            out.add(changed(cfg, aspect, subject, name, join(previous), join(now), join(now), fromEvent, nanos, wall,
                    gains(aspect, previous, now, cfg)));
        }
        return out;
    }

    /**
     * Operator reconciliation. {@code current} maps operator UUIDs to names. The first call establishes the
     * baseline of existing operators.
     */
    public List<AuditRecord> observeOperators(Map<UUID, String> current, boolean fromEvent, long nanos, long wall) {
        Settings.Audit cfg = config.get();
        if (!cfg.enabled() || !cfg.monitored().op()) {
            return List.of();
        }
        if (knownOps == null) {
            knownOps = new HashSet<>(current.keySet());
            return List.of();
        }
        List<AuditRecord> out = new ArrayList<>();
        for (Map.Entry<UUID, String> e : current.entrySet()) {
            if (!knownOps.contains(e.getKey())) {
                out.add(changed(cfg, AuditAspect.OP, e.getKey(), e.getValue(), "false", "true", "true", fromEvent,
                        nanos, wall, true));
            }
        }
        for (UUID id : knownOps) {
            if (!current.containsKey(id)) {
                out.add(changed(cfg, AuditAspect.OP, id, "", "true", "false", "false", fromEvent, nanos, wall, false));
            }
        }
        knownOps = new HashSet<>(current.keySet());
        return out;
    }

    // ---------------------------------------------------------------- internals

    private AuditRecord changed(Settings.Audit cfg, AuditAspect aspect, UUID subject, String name, String before,
                                String after, String desiredToken, boolean fromEvent, long nanos, long wall,
                                boolean gaining) {
        long window = (fromEvent ? cfg.eventCorrelationMillis() : cfg.reconcileCorrelationMillis()) * 1_000_000L;
        Attempt match = null;
        String safeName = Sanitizer.clean(name, 32);
        for (Attempt at : attempts) {
            if (at.aspect != aspect || nanos - at.nanos < 0 || nanos - at.nanos > window) {
                continue;
            }
            boolean targetMatches = at.selector || (at.target != null && at.target.equalsIgnoreCase(name));
            if (!targetMatches) {
                continue;
            }
            if (at.desired != null && aspect == AuditAspect.GAME_MODE && !at.desired.equalsIgnoreCase(after)) {
                continue;
            }
            if (at.desired != null && (aspect == AuditAspect.OP) && !at.desired.equalsIgnoreCase(after)) {
                continue;
            }
            if (match == null || at.nanos > match.nanos) {
                match = at;
            }
        }
        Actor actor = match == null ? Actor.UNKNOWN : match.actor;
        if (match != null) {
            match.consumed = true;
        }
        boolean trusted = subject != null && cfg.trustedUuids().contains(subject);
        boolean authorized = (actor.kind() == Actor.Kind.CONSOLE && cfg.consoleAuthorized())
                || (actor.kind() == Actor.Kind.PLAYER && actor.id() != null && cfg.authorizedActors().contains(actor.id()));
        boolean sensitive = aspect != AuditAspect.TAG || isSensitiveTag(cfg, desiredToken);
        AuditSeverity severity = gaining && sensitive && !trusted && !authorized
                ? AuditSeverity.ATTENTION : AuditSeverity.INFO;
        return new AuditRecord(aspect, AuditOutcome.CHANGED, subject, safeName, Sanitizer.clean(before, 64),
                Sanitizer.clean(after, 64), actor, match != null, severity, match == null ? "" : match.command, wall);
    }

    private static boolean isSensitiveTag(Settings.Audit cfg, String token) {
        if (cfg.sensitiveTags().isEmpty()) {
            return false;
        }
        String tag = token.startsWith("+") || token.startsWith("-") ? token.substring(1) : token;
        return cfg.sensitiveTags().contains(tag);
    }

    private static boolean gains(AuditAspect aspect, Set<String> before, Set<String> after, Settings.Audit cfg) {
        if (aspect == AuditAspect.GAME_MODE) {
            String to = after.isEmpty() ? "" : after.iterator().next().toUpperCase(Locale.ROOT);
            String from = before.isEmpty() ? "" : before.iterator().next().toUpperCase(Locale.ROOT);
            return (to.equals("CREATIVE") || to.equals("SPECTATOR")) && !from.equals(to);
        }
        return false;
    }

    private static boolean monitored(Settings.Audit cfg, AuditAspect aspect) {
        Settings.Monitored m = cfg.monitored();
        return switch (aspect) {
            case OP -> m.op();
            case GAME_MODE -> m.gameMode();
            case PERMISSION_GROUP, PERMISSION_NODE -> m.permissionGroups();
            case TAG -> m.tags();
            case TEAM -> m.teams();
        };
    }

    private static String join(Set<String> set) {
        return String.join(",", set);
    }

    /** Visible for diagnostics: number of unresolved attempts. */
    public int pendingAttempts() {
        return attempts.size();
    }

    /** Visible for diagnostics. */
    public Map<String, Integer> baselineSizes() {
        Map<String, Integer> sizes = new LinkedHashMap<>();
        for (Key k : baseline.keySet()) {
            sizes.merge(k.aspect().name(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(sizes);
    }
}
