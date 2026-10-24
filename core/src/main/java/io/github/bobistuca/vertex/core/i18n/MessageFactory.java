package io.github.bobistuca.vertex.core.i18n;

import io.github.bobistuca.vertex.core.audit.Actor;
import io.github.bobistuca.vertex.core.audit.AuditOutcome;
import io.github.bobistuca.vertex.core.audit.AuditRecord;
import io.github.bobistuca.vertex.core.audit.AuditSeverity;
import io.github.bobistuca.vertex.core.client.ClientReport;
import io.github.bobistuca.vertex.core.client.Provenance;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.model.AggregatedAlert;
import io.github.bobistuca.vertex.core.model.Confidence;
import io.github.bobistuca.vertex.core.model.Detection;
import io.github.bobistuca.vertex.core.model.DetectionKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the player-facing messages from locale templates. Untrusted values are always passed as placeholders. */
public final class MessageFactory {
    private static final int MAX_HOVER_MEASUREMENTS = 12;

    private final MessageCatalog catalog;
    private final Settings.Appearance appearance;

    public MessageFactory(MessageCatalog catalog, Settings.Appearance appearance) {
        this.catalog = catalog;
        this.appearance = appearance;
    }

    public MessageCatalog catalog() {
        return catalog;
    }

    // ------------------------------------------------------------------ gameplay alerts

    /**
     * The chat line for one closed aggregation window, for example
     * {@code [Vertex] Player • AutoClickerPattern • 2x • ⚠ Suspicion: high}.
     */
    public Rich alertLine(AggregatedAlert alert, String ping, String tps, String mode) {
        Detection d = alert.first();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("player", d.playerName());
        p.put("check", d.checkId());
        p.put("count", Integer.toString(alert.count()));
        p.put("symbol", symbolFor(d.kind()));
        p.put("label", catalog.render("kind." + d.kind().name().toLowerCase(Locale.ROOT)).toPlainText());
        p.put("level", levelText(d));
        return catalog.render("alert.line", p).withHover(alertHover(alert, ping, tps, mode));
    }

    public Rich actionBar(String player, String check, int count) {
        return catalog.render("alert.actionbar", Map.of("player", player, "check", check, "count", Integer.toString(count)));
    }

    public Rich alertHover(AggregatedAlert alert, String ping, String tps, String mode) {
        Detection d = alert.first();
        List<Rich> lines = new ArrayList<>();
        lines.add(catalog.render("alert.hover.title", Map.of("check", d.checkId(),
                "kind", catalog.render("kind." + d.kind().name().toLowerCase(Locale.ROOT)).toPlainText())));
        String localizedReason = catalog.has("reason." + d.checkId())
                ? catalog.render("reason." + d.checkId()).toPlainText() : d.reason();
        lines.add(catalog.render("alert.hover.reason", Map.of("reason", localizedReason)));
        int shown = 0;
        for (Map.Entry<String, String> m : d.measurements().entrySet()) {
            if (shown++ >= MAX_HOVER_MEASUREMENTS) {
                break;
            }
            lines.add(catalog.render("alert.hover.measurement", Map.of("name", m.getKey(), "value", m.getValue())));
        }
        lines.add(catalog.render("alert.hover.context", Map.of("ping", ping, "tps", tps, "mode", mode)));
        long seconds = Math.max(1, alert.windowMillis() / 1000);
        lines.add(catalog.render("alert.hover.window", Map.of("count", Integer.toString(alert.count()),
                "seconds", Long.toString(seconds))));
        if (d.kind() == DetectionKind.SUSPICION) {
            lines.add(catalog.render("alert.hover.footer"));
        }
        return Rich.join(lines);
    }

    private String levelText(Detection d) {
        if (d.kind() == DetectionKind.ESTABLISHED) {
            return catalog.render("level.established").toPlainText();
        }
        Confidence c = d.confidence();
        return catalog.render("level." + c.name().toLowerCase(Locale.ROOT)).toPlainText();
    }

    private String symbolFor(DetectionKind kind) {
        return switch (kind) {
            case ESTABLISHED -> appearance.symbolEstablished();
            case SUSPICION -> appearance.symbolSuspicion();
            case ADMIN -> appearance.symbolAdmin();
        };
    }

    // ------------------------------------------------------------------ administrative audit

    public Rich adminLine(AuditRecord r) {
        String aspect = catalog.render("admin.aspect." + r.aspect().checkId()).toPlainText();
        Map<String, String> p = new LinkedHashMap<>();
        p.put("symbol", r.severity() == AuditSeverity.ATTENTION ? appearance.symbolAdmin() : appearance.symbolInfo());
        p.put("subject", r.subjectName());
        p.put("aspect", aspect);
        p.put("before", r.before().isEmpty() ? "-" : r.before());
        p.put("after", r.after().isEmpty() ? "-" : r.after());
        p.put("actor", actorText(r.actor()));
        String key;
        if (r.outcome() == AuditOutcome.NO_CHANGE) {
            key = "admin.nochange";
            p.put("label", catalog.render("admin.label.nochange").toPlainText());
        } else {
            key = "admin.line";
            p.put("label", catalog.render(r.severity() == AuditSeverity.ATTENTION
                    ? "admin.label.attention" : "admin.label.info").toPlainText());
        }
        return catalog.render(key, p).withHover(adminHover(r, aspect));
    }

    public Rich adminHover(AuditRecord r, String aspect) {
        List<Rich> lines = new ArrayList<>();
        String outcome = catalog.render("admin.outcome." + switch (r.outcome()) {
            case ATTEMPT -> "attempt";
            case CHANGED -> "changed";
            case NO_CHANGE -> "nochange";
        }).toPlainText();
        lines.add(catalog.render("admin.hover.title", Map.of("aspect", aspect, "outcome", outcome)));
        lines.add(catalog.render("admin.hover.source", Map.of("actor", actorText(r.actor()),
                "established", catalog.render(r.actorEstablished() ? "admin.actor.established"
                        : "admin.actor.notestablished").toPlainText())));
        if (!r.command().isEmpty()) {
            lines.add(catalog.render("admin.hover.command", Map.of("command", r.command())));
        }
        lines.add(catalog.render("admin.hover.note"));
        return Rich.join(lines);
    }

    public String actorText(Actor actor) {
        return switch (actor.kind()) {
            case CONSOLE -> catalog.render("admin.actor.console").toPlainText();
            case UNKNOWN -> catalog.render("admin.actor.unknown").toPlainText();
            case PLAYER -> actor.name();
        };
    }

    // ------------------------------------------------------------------ client information

    public List<Rich> clientReport(ClientReport r) {
        List<Rich> lines = new ArrayList<>();
        lines.add(catalog.render("client.header", Map.of("symbol", appearance.symbolInfo(), "player", r.playerName(),
                "uuid", r.playerId().toString())));
        lines.add(catalog.render("client.brand", Map.of("brand", r.brand().isEmpty() ? unknownWord() : r.brand(),
                "provenance", provenance(r.brandProvenance()))));
        lines.add(catalog.render("client.protocol", Map.of("protocol",
                r.protocol() < 0 ? unknownWord() : Integer.toString(r.protocol()),
                "provenance", provenance(r.protocolProvenance()))));
        lines.add(catalog.render("client.version", Map.of("version",
                r.versionName().isEmpty() ? unknownWord() : r.versionName(),
                "provenance", provenance(r.versionProvenance()))));
        if (!r.channels().isEmpty()) {
            lines.add(catalog.render("client.channels", Map.of("channels", String.join(", ", r.channels()),
                    "provenance", provenance(Provenance.REPORTED))));
        }
        lines.add(catalog.render("client.injection"));
        if (r.timedOut()) {
            lines.add(catalog.render("client.timeout"));
        }
        lines.add(catalog.render("client.note"));
        return lines;
    }

    private String unknownWord() {
        return catalog.render("client.none").toPlainText();
    }

    private String provenance(Provenance p) {
        return catalog.render("provenance." + p.name().toLowerCase(Locale.ROOT)).toPlainText();
    }
}
