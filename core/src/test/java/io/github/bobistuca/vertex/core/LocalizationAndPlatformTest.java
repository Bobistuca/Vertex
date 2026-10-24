package io.github.bobistuca.vertex.core;

import static io.github.bobistuca.vertex.core.support.TestSupport.ALICE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bobistuca.vertex.core.audit.Actor;
import io.github.bobistuca.vertex.core.audit.AuditAspect;
import io.github.bobistuca.vertex.core.audit.AuditOutcome;
import io.github.bobistuca.vertex.core.audit.AuditRecord;
import io.github.bobistuca.vertex.core.audit.AuditSeverity;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.i18n.MessageFactory;
import io.github.bobistuca.vertex.core.i18n.Rich;
import io.github.bobistuca.vertex.core.i18n.RichSpan;
import io.github.bobistuca.vertex.core.model.AggregatedAlert;
import io.github.bobistuca.vertex.core.model.Detection;
import io.github.bobistuca.vertex.core.model.DetectionKind;
import io.github.bobistuca.vertex.core.model.Confidence;
import io.github.bobistuca.vertex.core.platform.Capabilities;
import io.github.bobistuca.vertex.core.platform.CheckCatalog;
import io.github.bobistuca.vertex.core.support.TestSupport;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LocalizationAndPlatformTest {
    private static final Pattern TAG = Pattern.compile("<([a-z_]+)>");

    private static Set<String> placeholders(String template) {
        Set<String> out = new TreeSet<>();
        Matcher m = TAG.matcher(template);
        while (m.find()) {
            String name = m.group(1);
            if (!Set.of("primary", "secondary", "text", "bold", "italic", "underlined", "reset", "newline", "br").contains(name)) {
                out.add(name);
            }
        }
        return out;
    }

    @Test
    void bothLocalesDefineTheSameKeysAndPlaceholders() {
        Map<String, String> pt = TestSupport.locale("pt_BR");
        Map<String, String> en = TestSupport.locale("en_US");
        assertEquals(new TreeSet<>(en.keySet()), new TreeSet<>(pt.keySet()), "message keys must match");
        for (String key : en.keySet()) {
            assertEquals(placeholders(en.get(key)), placeholders(pt.get(key)), "placeholders differ for " + key);
            assertFalse(pt.get(key).isBlank(), key);
            assertFalse(en.get(key).isBlank(), key);
        }
    }

    @Test
    void everyCheckHasALocalizedReasonInBothLocales() {
        for (String locale : new String[] {"pt_BR", "en_US"}) {
            Map<String, String> m = TestSupport.locale(locale);
            for (String id : CheckCatalog.allIds()) {
                assertTrue(m.containsKey("reason." + id), locale + " lacks reason." + id);
            }
        }
    }

    @Test
    void englishFileHasNoPortugueseLeftovers() {
        for (String value : TestSupport.locale("en_US").values()) {
            assertFalse(value.matches("(?s).*[áàâãéêíóôõúç].*"), value);
        }
    }

    @Test
    void alertLineMatchesTheDocumentedLayoutAndPalette() {
        Detection d = new Detection(ALICE, "Jogador", "AutoClickerPattern", DetectionKind.SUSPICION, Confidence.HIGH,
                "reason", Map.of("samples", "60"), 1L, null);
        AggregatedAlert alert = new AggregatedAlert(d, d, 2, 2000, false);
        Rich line = TestSupport.messages("pt_BR").alertLine(alert, "30 ms", "20.0", "SURVIVAL");

        assertTrue(line.toPlainText().replace("⚠ ", "").contains("[Vertex] Jogador • AutoClickerPattern • 2x • Suspeita: alta"),
                line.toPlainText());
        Settings s = TestSupport.shippedSettings();
        Set<Integer> palette = Set.of(0x86EFAC, 0x166534, 0xFFFFFF);
        for (RichSpan span : line.spans()) {
            assertTrue(span.hover() != null, "hover details are attached");
            if (!span.text().isBlank()) {
                assertTrue(palette.contains(span.rgb()), "color outside the identity palette: " + span);
            }
        }
        RichSpan name = line.spans().stream().filter(x -> x.text().contains("Vertex")).findFirst().orElseThrow();
        assertEquals(0x86EFAC, name.rgb());
        assertTrue(name.bold());
        RichSpan player = line.spans().stream().filter(x -> x.text().equals("Jogador")).findFirst().orElseThrow();
        assertEquals(0xFFFFFF, player.rgb());
        String hover = line.spans().getFirst().hover().toPlainText();
        assertTrue(hover.contains("samples") && hover.contains("30 ms") && hover.contains("Motivo"), hover);
        assertEquals(0x86EFAC, s.appearance().primary().equals("#86EFAC") ? 0x86EFAC : 0);
    }

    @Test
    void establishedAndAdministrativeMessagesUseDistinctLabelsAndSymbols() {
        MessageFactory en = TestSupport.messages("en_US");
        Detection est = new Detection(ALICE, "P", "InvalidNumericValue", DetectionKind.ESTABLISHED, Confidence.HIGH, "r", Map.of(), 1, null);
        String established = en.alertLine(new AggregatedAlert(est, est, 1, 2000, false), "-", "-", "-").toPlainText();
        Detection sus = new Detection(ALICE, "P", "MovementSpeed", DetectionKind.SUSPICION, Confidence.MEDIUM, "r", Map.of(), 1, null);
        String suspicion = en.alertLine(new AggregatedAlert(sus, sus, 1, 2000, false), "-", "-", "-").toPlainText();
        AuditRecord rec = new AuditRecord(AuditAspect.OP, AuditOutcome.CHANGED, ALICE, "P", "false", "true", Actor.UNKNOWN,
                false, AuditSeverity.ATTENTION, "", 1);
        String admin = en.adminLine(rec).toPlainText();

        assertTrue(established.contains("✖") && established.contains("Established contradiction"), established);
        assertTrue(suspicion.contains("⚠") && suspicion.contains("Suspicion"), suspicion);
        assertTrue(admin.contains("⚙") && admin.contains("Administrative attention") && admin.contains("unknown"), admin);
        assertFalse(admin.toLowerCase().contains("cheat"), "audit entries are not gameplay suspicions");
    }

    @Test
    void noTemplateKeyIsMissingWhenRenderingEveryFactoryPath() {
        for (String locale : new String[] {"pt_BR", "en_US"}) {
            MessageFactory f = TestSupport.messages(locale);
            for (AuditAspect aspect : AuditAspect.values()) {
                for (AuditOutcome outcome : AuditOutcome.values()) {
                    AuditRecord r = new AuditRecord(aspect, outcome, ALICE, "P", "a", "b", Actor.CONSOLE, true,
                            AuditSeverity.INFO, "op P", 1);
                    String text = f.adminLine(r).toPlainText() + f.adminLine(r).spans().getFirst().hover().toPlainText();
                    assertFalse(text.contains("!"), locale + " " + aspect + " " + text);
                }
            }
            for (DetectionKind kind : DetectionKind.values()) {
                for (String check : CheckCatalog.allIds()) {
                    Detection d = new Detection(ALICE, "P", check, kind, Confidence.LOW, "r", Map.of("a", "b"), 1, null);
                    String text = f.alertLine(new AggregatedAlert(d, d, 3, 2000, false), "1 ms", "20.0", "SURVIVAL").toPlainText();
                    assertFalse(text.contains("!"), locale + " " + check + " " + text);
                }
            }
        }
    }

    @Test
    void playerNamesCannotInjectMarkupIntoAlerts() {
        Detection d = new Detection(ALICE, "<click:run_command:'/op x'>Evil\n§4", "A", DetectionKind.SUSPICION, Confidence.LOW,
                "<red>r", Map.of("<b>k", "<red>v\nnewline"), 1, null);
        Rich line = TestSupport.messages("en_US").alertLine(new AggregatedAlert(d, d, 1, 2000, false), "-", "-", "-");
        assertTrue(line.toPlainText().contains("<click:run_command"), "shown literally");
        assertFalse(line.toPlainText().contains("\n"));
        assertFalse(line.toPlainText().contains("§"));
        Set<Integer> palette = Set.of(0x86EFAC, 0x166534, 0xFFFFFF);
        assertTrue(line.spans().stream().filter(s -> !s.text().isBlank()).allMatch(s -> palette.contains(s.rgb())));
    }

    @Test
    void legacyTextKeepsTheColorsWhenRichMessagingIsUnavailable() {
        Rich r = TestSupport.messages("en_US").alertLine(
                new AggregatedAlert(TestSupport.detection(ALICE, "P", "A", null, 1), TestSupport.detection(ALICE, "P", "A", null, 1), 2, 2000, false),
                "-", "-", "-");
        String legacy = r.toLegacyText();
        assertTrue(legacy.contains("§x§8§6§e§f§a§c"), legacy);
        assertTrue(legacy.contains("2x"));
    }

    @Test
    void platformAdaptersAreSelectedByVerifiedCapabilities() {
        assertEquals(Capabilities.Messaging.PAPER_NATIVE, new Capabilities(true, true, true, true, true, true, true).messaging());
        assertEquals(Capabilities.Messaging.SPIGOT_BUNGEE, new Capabilities(false, false, true, true, false, true, true).messaging());
        assertEquals(Capabilities.Messaging.LEGACY_TEXT, new Capabilities(false, false, false, false, false, true, true).messaging(),
                "CraftBukkit without Spigot API falls back to legacy colored text");
        assertEquals(Capabilities.Messaging.SPIGOT_BUNGEE, new Capabilities(true, false, true, true, true, true, true).messaging(),
                "a Paper-like server without native Adventure still renders through the Spigot adapter");
        assertEquals(Capabilities.Messaging.LEGACY_TEXT, new Capabilities(false, false, true, false, false, true, true).messaging());
        assertFalse(new Capabilities(false, false, false, false, false, true, true).richMessaging());
        assertEquals("server-api", new Capabilities(true, true, true, true, true, true, true).metricsSource());
        assertEquals("scheduler-derived", new Capabilities(false, false, true, true, false, true, true).metricsSource());
    }

    @Test
    void checksAreNeverAdvertisedAsActiveWhenTheirCapabilityIsMissing() {
        Settings settings = TestSupport.shippedSettings();
        var withoutPackets = CheckCatalog.status(settings, new Capabilities(false, false, true, true, false, false, true));
        assertTrue(withoutPackets.stream().filter(e -> e.id().equals("AutoClickerPattern")).noneMatch(CheckCatalog.Entry::active));
        assertTrue(withoutPackets.stream().filter(e -> e.id().equals("ReachDistance")).allMatch(CheckCatalog.Entry::active),
                "reach only needs server events");
        var withoutEvents = CheckCatalog.status(settings, new Capabilities(false, false, true, true, false, true, false));
        assertTrue(withoutEvents.stream().filter(e -> e.id().equals("ReachDistance")).noneMatch(CheckCatalog.Entry::active));
        assertTrue(withoutEvents.stream().filter(e -> e.id().equals("InvalidNumericValue")).allMatch(CheckCatalog.Entry::active));
    }
}
