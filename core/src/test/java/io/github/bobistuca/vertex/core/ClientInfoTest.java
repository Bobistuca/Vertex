package io.github.bobistuca.vertex.core;

import static io.github.bobistuca.vertex.core.support.TestSupport.ALICE;
import static io.github.bobistuca.vertex.core.support.TestSupport.BOB;
import static io.github.bobistuca.vertex.core.support.TestSupport.MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bobistuca.vertex.core.client.BrandParser;
import io.github.bobistuca.vertex.core.client.ClientInfoCollector;
import io.github.bobistuca.vertex.core.client.ClientReport;
import io.github.bobistuca.vertex.core.client.Provenance;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.i18n.MessageFactory;
import io.github.bobistuca.vertex.core.i18n.Rich;
import io.github.bobistuca.vertex.core.i18n.RichSpan;
import io.github.bobistuca.vertex.core.support.TestSupport;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClientInfoTest {

    private static byte[] brandPayload(String brand) {
        byte[] text = brand.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len = text.length;
        while ((len & ~0x7F) != 0) {
            out.write((len & 0x7F) | 0x80);
            len >>>= 7;
        }
        out.write(len);
        out.writeBytes(text);
        return out.toByteArray();
    }

    @Test
    void reportIsReleasedWhenBrandAndProtocolArriveWithProvenance() {
        ClientInfoCollector c = new ClientInfoCollector(3000, 16);
        c.begin(ALICE, "Alice", 0);
        assertTrue(c.poll(100 * MS).isEmpty());
        c.brandPayload(ALICE, brandPayload("fabric"), 0);
        c.protocol(ALICE, 775, "26.1");
        c.channel(ALICE, "minecraft:brand");
        List<ClientReport> ready = c.poll(200 * MS);

        assertEquals(1, ready.size());
        ClientReport r = ready.getFirst();
        assertEquals("fabric", r.brand());
        assertEquals(Provenance.REPORTED, r.brandProvenance());
        assertEquals(Provenance.REPORTED, r.protocolProvenance());
        assertEquals(Provenance.INFERRED, r.versionProvenance());
        assertFalse(r.timedOut());
        assertEquals("not-verifiable", ClientReport.INJECTION_STATUS);
    }

    @Test
    void configurationPhaseDataThatArrivesBeforeTheJoinEventIsKept() {
        ClientInfoCollector c = new ClientInfoCollector(3000, 16);
        c.brandPayload(ALICE, brandPayload("lunar"), 0);
        assertTrue(c.poll(100 * MS).isEmpty(), "not released before the player joined");
        c.begin(ALICE, "Alice", 500 * MS);
        c.protocol(ALICE, 775, "26.1");
        ClientReport r = c.poll(600 * MS).getFirst();
        assertEquals("lunar", r.brand());
        assertEquals("Alice", r.playerName());
        // A connection that never joins is dropped silently.
        c.brandPayload(BOB, brandPayload("x"), 0);
        assertTrue(c.poll(40_000 * MS).isEmpty());
        assertEquals(0, c.pendingCount());
    }

    @Test
    void missingValuesAreUnknownAfterTheBoundedTimeout() {
        ClientInfoCollector c = new ClientInfoCollector(3000, 16);
        c.begin(ALICE, "Alice", 0);
        assertTrue(c.poll(2999 * MS).isEmpty());
        ClientReport r = c.poll(3000 * MS).getFirst();
        assertTrue(r.timedOut());
        assertEquals(Provenance.UNKNOWN, r.brandProvenance());
        assertEquals(Provenance.UNKNOWN, r.protocolProvenance());
        assertEquals("", r.brand());
        assertEquals(-1, r.protocol());
        assertEquals(0, c.pendingCount());
    }

    @Test
    void channelListIsBounded() {
        ClientInfoCollector c = new ClientInfoCollector(10, 3);
        c.begin(ALICE, "Alice", 0);
        for (int i = 0; i < 50; i++) {
            c.channel(ALICE, "mod:channel" + i);
        }
        assertEquals(3, c.poll(20 * MS).getFirst().channels().size());
    }

    @Test
    void maliciousBrandCannotInjectFormattingCommandsOrForgedLines() {
        String evil = "<red><bold>[Vertex]</bold></red>\n§4HACKER§r <click:run_command:'/op me'>x</click>";
        ClientInfoCollector c = new ClientInfoCollector(10, 16);
        c.begin(ALICE, "Ali<gold>ce", 0);
        c.brandPayload(ALICE, brandPayload(evil), 0);
        c.protocol(ALICE, 775, "26.1");
        ClientReport report = c.poll(1 * MS).getFirst();

        assertFalse(report.brand().contains("\n"));
        assertFalse(report.brand().contains("§"));

        Settings s = TestSupport.shippedSettings();
        Set<Integer> palette = Set.of(0x86EFAC, 0x166534, 0xFFFFFF, -1);
        MessageFactory messages = TestSupport.messages("en_US");
        for (Rich line : messages.clientReport(report)) {
            assertFalse(line.toPlainText().contains("\n"), "a value must not forge an extra line");
            for (RichSpan span : line.spans()) {
                assertTrue(span.text().isBlank() || palette.contains(span.rgb()), "unexpected color " + Integer.toHexString(span.rgb()));
                assertEquals(null, span.hover());
            }
        }
        String brandLine = messages.clientReport(report).get(1).toPlainText();
        assertTrue(brandLine.contains("<red>"), "markup is shown literally: " + brandLine);
        assertTrue(brandLine.contains("<click:"), brandLine);
        assertEquals(s.appearance().primary(), "#86EFAC");
    }

    @Test
    void injectionIsNeverClaimedOrDenied() {
        ClientInfoCollector c = new ClientInfoCollector(10, 16);
        c.begin(ALICE, "Alice", 0);
        c.brandPayload(ALICE, brandPayload("vanilla"), 0);
        c.protocol(ALICE, 775, "26.1");
        ClientReport r = c.poll(1 * MS).getFirst();
        for (String locale : new String[] {"en_US", "pt_BR"}) {
            String text = String.join("\n", TestSupport.messages(locale).clientReport(r).stream().map(Rich::toPlainText).toList())
                    .toLowerCase();
            assertFalse(text.contains("injection detected") || text.contains("injeção detectada"), text);
            assertFalse(text.contains("no injection") || text.contains("sem injeção") || text.contains("clean"), text);
            assertTrue(text.contains("not verifiable") || text.contains("não verificável"), text);
        }
    }

    @Test
    void brandParserHandlesMalformedPayloads() {
        assertEquals("", BrandParser.parse(null));
        assertEquals("", BrandParser.parse(new byte[0]));
        assertEquals("", BrandParser.parse(new byte[2000]));
        assertEquals("abc", BrandParser.parse("abc".getBytes(StandardCharsets.UTF_8)));
        assertEquals("forge", BrandParser.parse(brandPayload("forge")));
        assertTrue(BrandParser.parse(new byte[] {(byte) 0xFF, (byte) 0xFE, 0x41}).length() <= BrandParser.MAX_BRAND_CHARS + 1);
        assertEquals(BrandParser.MAX_BRAND_CHARS + 1, BrandParser.parse(brandPayload("x".repeat(300))).length());
    }
}
