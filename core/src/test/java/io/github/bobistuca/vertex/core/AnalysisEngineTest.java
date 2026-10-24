package io.github.bobistuca.vertex.core;

import static io.github.bobistuca.vertex.core.support.TestSupport.ALICE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bobistuca.vertex.core.checks.GraceState;
import io.github.bobistuca.vertex.core.checks.PlayerSnapshot;
import io.github.bobistuca.vertex.core.engine.AnalysisEngine;
import io.github.bobistuca.vertex.core.engine.Observation;
import io.github.bobistuca.vertex.core.engine.PlayerContext;
import io.github.bobistuca.vertex.core.model.Detection;
import io.github.bobistuca.vertex.core.model.DetectionKind;
import io.github.bobistuca.vertex.core.support.TestSupport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisEngineTest {
    private static final long MS = 1_000_000L;

    private static final io.github.bobistuca.vertex.core.config.Settings SETTINGS = TestSupport.shippedSettings();

    private final List<Detection> sink = new ArrayList<>();
    private final GraceState grace = new GraceState();
    private PlayerContext context = new PlayerContext(PlayerSnapshot.survivalDefaults(), grace);

    private AnalysisEngine engine() {
        return new AnalysisEngine(() -> SETTINGS, id -> context, sink::add, () -> 1_700_000_000_000L);
    }

    @Test
    void anEstablishedContradictionIsReportedFromASinglePacketWithMeasurements() {
        AnalysisEngine e = engine();
        e.process(new Observation.Flying(ALICE, "Alice", 100_000 * MS, true, Double.NaN, 64, 0, true, 0, 0, true));
        assertEquals(1, sink.size());
        Detection d = sink.getFirst();
        assertEquals("InvalidNumericValue", d.checkId());
        assertEquals(DetectionKind.ESTABLISHED, d.kind());
        assertEquals(1_700_000_000_000L, d.wallClockMillis());
        assertTrue(d.measurements().containsKey("x"));
        assertTrue(!d.reason().isEmpty());
        assertEquals(1L, e.report(ALICE).orElseThrow().incidentsByCheck().get("InvalidNumericValue"));
    }

    @Test
    void statisticalChecksDoNotAccuseFromOnePacket() {
        AnalysisEngine e = engine();
        e.process(new Observation.Flying(ALICE, "Alice", 100_000 * MS, true, 0, 64, 0, false, 0, 0, true));
        e.process(new Observation.Flying(ALICE, "Alice", 100_050 * MS, true, 9.0, 64, 0, false, 0, 0, true));
        e.process(new Observation.Attack(ALICE, "Alice", 100_060 * MS, 123));
        assertTrue(sink.isEmpty());
    }

    @Test
    void aFailingContextLookupNeverDisruptsTheCaller() {
        AnalysisEngine e = new AnalysisEngine(() -> SETTINGS, id -> {
            throw new IllegalStateException("boom");
        }, sink::add, System::currentTimeMillis);
        e.process(new Observation.Swing(ALICE, "Alice", 1));
        e.process(new Observation.Flying(ALICE, "Alice", 2, true, 0, 0, 0, false, 0, 0, true));
        assertEquals(2, e.failures());
        assertTrue(sink.isEmpty());
    }

    @Test
    void unknownPlayersAreIgnoredAndQuitCleansTheSession() {
        context = null;
        AnalysisEngine e = engine();
        e.process(new Observation.Swing(ALICE, "Alice", 1));
        assertEquals(0, e.trackedPlayers());
        context = new PlayerContext(PlayerSnapshot.survivalDefaults(), grace);
        e.process(new Observation.Swing(ALICE, "Alice", 2));
        assertEquals(1, e.trackedPlayers());
        e.process(new Observation.Quit(ALICE, "Alice", 3));
        assertEquals(0, e.trackedPlayers());
    }

    @Test
    void autoClickPatternsFlowThroughTheEngine() {
        AnalysisEngine e = engine();
        long t = 100_000;
        for (int i = 0; i < 70; i++, t += 100) {
            e.process(new Observation.Attack(ALICE, "Alice", t * MS + (i % 2), 500));
        }
        assertTrue(sink.stream().anyMatch(d -> d.checkId().equals("AutoClickerPattern")), "constant 10 CPS window");
    }

    @Test
    void joinGraceSuspendsTimingChecks() {
        AnalysisEngine e = engine();
        grace.joined(100_000 * MS);
        long t = 100_100;
        for (int i = 0; i < 70; i++, t += 100) {
            e.process(new Observation.Attack(ALICE, "Alice", t * MS, 500));
        }
        assertTrue(sink.isEmpty(), "inside the join grace period nothing is judged");
    }

    @Test
    void invalidInventoryAndAbilityStatesRespectServerKnownState() {
        AnalysisEngine e = engine();
        e.process(new Observation.ClickWindow(ALICE, "Alice", 200_000 * MS, 3, 5));
        assertEquals("InventoryWindowMismatch", sink.getLast().checkId());
        e.process(new Observation.Abilities(ALICE, "Alice", 200_001 * MS, true));
        assertEquals("ImpossibleFlightToggle", sink.getLast().checkId());
        e.process(new Observation.HeldSlot(ALICE, "Alice", 200_002 * MS, 12));
        assertEquals("InvalidPacketField", sink.getLast().checkId());
        int before = sink.size();
        grace.windowChanged(200_100 * MS);
        e.process(new Observation.ClickWindow(ALICE, "Alice", 200_200 * MS, 3, 5));
        assertEquals(before, sink.size(), "a window that just changed is tolerated");
    }
}
