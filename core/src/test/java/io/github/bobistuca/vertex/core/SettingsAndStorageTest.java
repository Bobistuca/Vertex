package io.github.bobistuca.vertex.core;

import static io.github.bobistuca.vertex.core.support.TestSupport.ALICE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bobistuca.vertex.core.config.ConfigException;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.config.SettingsHolder;
import io.github.bobistuca.vertex.core.config.SettingsLoader;
import io.github.bobistuca.vertex.core.storage.EvidenceCodec;
import io.github.bobistuca.vertex.core.storage.EvidenceEntry;
import io.github.bobistuca.vertex.core.storage.EvidenceStore;
import io.github.bobistuca.vertex.core.storage.PreferenceStore;
import io.github.bobistuca.vertex.core.support.TestSupport;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsAndStorageTest {

    @Test
    void shippedConfigurationIsValidAndMatchesBuiltInDefaults() throws Exception {
        Settings shipped = TestSupport.shippedSettings();
        Settings defaults = SettingsLoader.defaults();
        assertEquals(defaults, shipped, "config.yml must mirror the built-in defaults");
        assertEquals("pt_BR", shipped.language());
        assertEquals(2000, shipped.alerts().windowMillis());
        assertEquals("#86EFAC", shipped.appearance().primary());
        assertEquals("#166534", shipped.appearance().secondary());
        assertEquals("#FFFFFF", shipped.appearance().text());
    }

    @Test
    void invalidValuesAreReportedTogetherAndNeverApplied() {
        ConfigException e = assertThrows(ConfigException.class, () -> SettingsLoader.load(Map.of(
                "language", "portuguese",
                "appearance", Map.of("primary", "green"),
                "alerts", Map.of("aggregation-window-ms", -5, "recipients", Map.of("game-modes", List.of("CREATIVE", "FLYING"))),
                "audit", Map.of("trusted-uuids", List.of("not-a-uuid")),
                "checks", Map.of("movement", Map.of("speed-factor", "fast")))));
        assertTrue(e.problems().size() >= 6, e.problems().toString());
    }

    @Test
    void reloadKeepsTheLastWorkingConfigurationWhenValidationFails() {
        SettingsHolder holder = new SettingsHolder(TestSupport.shippedSettings());
        var ok = holder.reload(() -> new StringReader("alerts:\n  aggregation-window-ms: 5000\n"));
        assertTrue(ok.applied());
        assertEquals(5000, holder.get().alerts().windowMillis());

        var bad = holder.reload(() -> new StringReader("alerts:\n  aggregation-window-ms: 5\n"));
        assertFalse(bad.applied());
        assertFalse(bad.problems().isEmpty());
        assertEquals(5000, holder.get().alerts().windowMillis(), "previous valid settings stay active");

        var broken = holder.reload(() -> new StringReader("alerts: [unclosed"));
        assertFalse(broken.applied());
        assertEquals(5000, holder.get().alerts().windowMillis());

        var evil = holder.reload(() -> new StringReader("x: !!java.lang.ProcessBuilder [[\"touch\", \"/tmp/pwned\"]]\n"));
        assertFalse(evil.applied(), "arbitrary object construction is refused");
    }

    @Test
    void yamlAliasBombsAndBadRootsAreRejected() {
        String bomb = "a: &a [x,x,x,x,x,x,x,x,x,x]\nb: &b [*a,*a,*a,*a,*a,*a,*a,*a,*a,*a]\nc: &c [*b,*b,*b,*b,*b,*b,*b,*b,*b,*b]\n"
                + "d: &d [*c,*c,*c,*c,*c,*c,*c,*c,*c,*c]\ne: &e [*d,*d,*d,*d,*d,*d,*d,*d,*d,*d]\n";
        assertThrows(ConfigException.class, () -> SettingsLoader.parseYaml(new StringReader(bomb)));
        assertThrows(ConfigException.class, () -> SettingsLoader.parseYaml(new StringReader("- a\n- b\n")));
    }

    private static EvidenceEntry entry(String reason, long wall, int count) {
        return new EvidenceEntry(wall, "DETECTION", ALICE, "Alice", "AutoClickerPattern", "SUSPICION", "HIGH", count,
                false, reason, Map.of("k", "v"));
    }

    @Test
    void evidenceSurvivesRestartAndForgedControlCharactersCannotCreateExtraRecords(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("data").resolve("evidence.log");
        long now = System.currentTimeMillis();
        Settings s = TestSupport.shippedSettings();
        try (EvidenceStore store = new EvidenceStore(file, s::storage)) {
            store.load(now);
            store.add(entry("normal", now, 2));
            store.add(entry("evil\n" + EvidenceCodec.encode(entry("forged", now, 99)) + "\tTAB\\", now + 1, 1));
        }
        assertEquals(2, Files.readAllLines(file).size(), "newlines in values are escaped");

        try (EvidenceStore reopened = new EvidenceStore(file, s::storage)) {
            reopened.load(now + 2);
            List<EvidenceEntry> recent = reopened.recent(ALICE, 10);
            assertEquals(2, recent.size());
            assertEquals(1, recent.getFirst().count());
            assertTrue(recent.getFirst().reason().startsWith("evil\n"));
            assertEquals(2, recent.get(1).count());
            assertEquals(0, reopened.corruptLines());
        }
    }

    @Test
    void historyIsBoundedByEntriesAgeAndSize(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("evidence.log");
        long now = System.currentTimeMillis();
        Settings s = SettingsLoader.load(Map.of("storage", Map.of("max-entries", 100, "retention-days", 1,
                "memory-per-player", 10, "memory-global", 50)));
        try (EvidenceStore store = new EvidenceStore(file, s::storage)) {
            store.load(now);
            for (int i = 0; i < 400; i++) {
                store.add(entry("r" + i, now + i, 1));
            }
            assertEquals(10, store.recent(ALICE, 1000).size());
            assertEquals(50, store.recent(null, 1000).size());
        }
        assertTrue(Files.readAllLines(file).size() <= 125 + 1, "file stays near the configured limit: " + Files.readAllLines(file).size());

        long old = now - 3L * 24 * 3600 * 1000;
        Files.writeString(file, EvidenceCodec.encode(entry("ancient", old, 1)) + "\ngarbage line\n");
        try (EvidenceStore store = new EvidenceStore(file, s::storage)) {
            store.load(now);
            assertTrue(store.recent(ALICE, 10).isEmpty(), "expired entries are pruned");
            assertEquals(1, store.corruptLines());
        }
    }

    @Test
    void personalPreferencesPersist(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("preferences.txt");
        var executor = Executors.newSingleThreadExecutor();
        PreferenceStore prefs = new PreferenceStore(file, executor);
        assertFalse(prefs.isOptedOut(ALICE));
        assertTrue(prefs.toggle(ALICE));
        assertTrue(prefs.isOptedOut(ALICE));
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));

        var second = Executors.newSingleThreadExecutor();
        PreferenceStore reloaded = new PreferenceStore(file, second);
        reloaded.load();
        assertTrue(reloaded.isOptedOut(ALICE));
        assertFalse(reloaded.toggle(ALICE));
        second.shutdown();
        assertTrue(second.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
    }
}
