package io.github.bobistuca.vertex.core;

import static io.github.bobistuca.vertex.core.support.TestSupport.ALICE;
import static io.github.bobistuca.vertex.core.support.TestSupport.BOB;
import static io.github.bobistuca.vertex.core.support.TestSupport.MS;
import static io.github.bobistuca.vertex.core.support.TestSupport.detection;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bobistuca.vertex.core.aggregate.AlertAggregator;
import io.github.bobistuca.vertex.core.aggregate.AlertPipeline;
import io.github.bobistuca.vertex.core.aggregate.DuplicateGuard;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.config.SettingsLoader;
import io.github.bobistuca.vertex.core.i18n.MessageFactory;
import io.github.bobistuca.vertex.core.model.AggregatedAlert;
import io.github.bobistuca.vertex.core.storage.EvidenceEntry;
import io.github.bobistuca.vertex.core.storage.EvidenceStore;
import io.github.bobistuca.vertex.core.support.TestSupport;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AlertAggregationTest {

    private static AlertAggregator newAggregator() {
        return new AlertAggregator(2000, 100, new DuplicateGuard(10_000 * MS, 1000));
    }

    @Test
    void twoIncidentsInsideTheWindowProduceOneAlertWithCountTwo() {
        AlertAggregator agg = newAggregator();
        agg.offer(detection(ALICE, "Alice", "AutoClickerPattern", null, 1), 0);
        agg.offer(detection(ALICE, "Alice", "AutoClickerPattern", null, 2), 700 * MS);

        assertTrue(agg.drainDue(1500 * MS).isEmpty(), "the window is still open");
        List<AggregatedAlert> due = agg.drainDue(2000 * MS);

        assertEquals(1, due.size());
        assertEquals(2, due.getFirst().count());
        MessageFactory messages = TestSupport.messages("pt_BR");
        String line = messages.alertLine(due.getFirst(), "42 ms", "20.0", "SURVIVAL").toPlainText();
        assertTrue(line.contains("2x"), line);
        assertTrue(line.contains("[Vertex]") && line.contains("Alice") && line.contains("AutoClickerPattern"), line);
        assertTrue(line.contains("Suspeita"), line);
    }

    @Test
    void threeIncidentsProduceThreeX() {
        AlertAggregator agg = newAggregator();
        for (int i = 0; i < 3; i++) {
            agg.offer(detection(ALICE, "Alice", "MovementSpeed", null, i), i * 100 * MS);
        }
        AggregatedAlert alert = agg.drainDue(2500 * MS).getFirst();
        assertEquals(3, alert.count());
        assertTrue(TestSupport.messages("en_US").alertLine(alert, "-", "-", "-").toPlainText().contains("3x"));
    }

    @Test
    void duplicateObservationOfTheSameActionIsCountedOnce() {
        AlertAggregator agg = newAggregator();
        var first = agg.offer(detection(ALICE, "Alice", "ReachDistance", "attack:77", 1), 0);
        // The packet listener and the event listener both report the same attack.
        var second = agg.offer(detection(ALICE, "Alice", "ReachDistance", "attack:77", 2), 5 * MS);
        var third = agg.offer(detection(ALICE, "Alice", "ReachDistance", "attack:78", 3), 60 * MS);

        assertEquals(AlertAggregator.Outcome.NEW_GROUP, first.outcome());
        assertEquals(AlertAggregator.Outcome.DUPLICATE, second.outcome());
        assertEquals(AlertAggregator.Outcome.JOINED_GROUP, third.outcome());
        assertEquals(2, agg.drainDue(3000 * MS).getFirst().count());
    }

    @Test
    void differentPlayersAndChecksAreGroupedSeparately() {
        AlertAggregator agg = newAggregator();
        agg.offer(detection(ALICE, "Alice", "A", null, 1), 0);
        agg.offer(detection(ALICE, "Alice", "B", null, 1), 0);
        agg.offer(detection(BOB, "Bob", "A", null, 1), 0);
        List<AggregatedAlert> due = agg.drainDue(2000 * MS);
        assertEquals(3, due.size());
        assertTrue(due.stream().allMatch(a -> a.count() == 1));
    }

    @Test
    void anIncidentAfterTheWindowStartsANewWindowAndKeepsTheOldTotals() {
        AlertAggregator agg = newAggregator();
        agg.offer(detection(ALICE, "Alice", "A", null, 1), 0);
        agg.offer(detection(ALICE, "Alice", "A", null, 2), 100 * MS);
        agg.offer(detection(ALICE, "Alice", "A", null, 3), 2500 * MS); // window closed but not yet drained
        List<AggregatedAlert> due = agg.drainAll();
        assertEquals(2, due.size());
        assertEquals(2, due.get(0).count());
        assertEquals(1, due.get(1).count());
    }

    @Test
    void stateIsBoundedAndSessionCleanupKeepsAccurateTotals() {
        AlertAggregator small = new AlertAggregator(2000, 2, new DuplicateGuard(10_000 * MS, 10));
        small.offer(detection(ALICE, "Alice", "A", null, 1), 0);
        small.offer(detection(ALICE, "Alice", "B", null, 1), 0);
        var dropped = small.offer(detection(ALICE, "Alice", "C", null, 1), 0);
        assertEquals(AlertAggregator.Outcome.DROPPED_FULL, dropped.outcome());
        assertEquals(1, small.droppedGroups());

        small.offer(detection(ALICE, "Alice", "A", null, 2), 10 * MS);
        List<AggregatedAlert> removed = small.clearPlayer(ALICE);
        assertEquals(2, removed.size());
        assertEquals(2, removed.stream().filter(a -> a.first().checkId().equals("A")).findFirst().orElseThrow().count());
        assertEquals(0, small.openGroups());
    }

    @Test
    void rateLimitedAlertsAreSuppressedInChatButEvidenceKeepsTheTotal(@TempDir Path dir) throws Exception {
        Settings base = TestSupport.shippedSettings();
        Settings limited = SettingsLoader.load(Map.of("alerts", Map.of("rate-limit", Map.of("max-per-player-per-minute", 1))));
        assertEquals(1, limited.alerts().maxPerPlayerPerMinute());
        assertEquals(2000, base.alerts().windowMillis());

        AtomicLong clock = new AtomicLong();
        EvidenceStore store = new EvidenceStore(dir.resolve("evidence.log"), limited::storage);
        AlertPipeline pipeline = new AlertPipeline(() -> limited, store, clock::get);

        pipeline.submit(detection(ALICE, "Alice", "A", null, 1));
        pipeline.submit(detection(ALICE, "Alice", "A", null, 2));
        pipeline.submit(detection(ALICE, "Alice", "B", null, 3));
        clock.set(3000 * MS);
        List<AggregatedAlert> deliverable = pipeline.drainDue();

        assertEquals(1, deliverable.size(), "only one chat alert per player and minute");
        assertEquals(1, pipeline.suppressed());
        List<EvidenceEntry> history = store.recent(ALICE, 10);
        assertEquals(2, history.size());
        assertEquals(3, history.stream().mapToInt(EvidenceEntry::count).sum(), "evidence totals stay accurate");
        assertTrue(history.stream().anyMatch(EvidenceEntry::suppressed));
        store.close();
        assertTrue(pipeline.allowForRecipient(ALICE));
    }
}
