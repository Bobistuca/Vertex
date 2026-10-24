package io.github.bobistuca.vertex.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bobistuca.vertex.core.checks.Finding;
import io.github.bobistuca.vertex.core.checks.PacketRateTracker;
import io.github.bobistuca.vertex.core.checks.ProtocolValidator;
import io.github.bobistuca.vertex.core.checks.ReachEvaluator;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.model.DetectionKind;
import io.github.bobistuca.vertex.core.support.TestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PacketChecksTest {
    private static final long MS = 1_000_000L;

    private static List<Finding> sendRate(PacketRateTracker t, double perSecond, int seconds, long startMs, boolean suspended) {
        List<Finding> out = new ArrayList<>();
        double gap = 1000.0 / perSecond;
        long total = (long) (perSecond * seconds);
        for (long i = 0; i < total; i++) {
            long at = startMs + (long) (i * gap);
            t.onMovementPacket(at * MS, suspended).ifPresent(out::add);
        }
        return out;
    }

    @Test
    void normalMovementRateNeverTriggers() {
        PacketRateTracker t = new PacketRateTracker(TestSupport.shippedSettings().checks().packetRate());
        assertTrue(sendRate(t, 20, 120, 0, false).isEmpty());
    }

    @Test
    void sustainedExcessRateIsASuspicionAfterConsecutiveWindows() {
        PacketRateTracker t = new PacketRateTracker(TestSupport.shippedSettings().checks().packetRate());
        sendRate(t, 20, 3, 0, false);
        List<Finding> f = sendRate(t, 45, 12, 3000, false);
        assertFalse(f.isEmpty());
        assertEquals("PacketRateAnomaly", f.getFirst().checkId());
        assertEquals(DetectionKind.SUSPICION, f.getFirst().kind());
    }

    @Test
    void aBurstThatFollowsAClientStallIsNotAnAnomaly() {
        PacketRateTracker t = new PacketRateTracker(TestSupport.shippedSettings().checks().packetRate());
        sendRate(t, 20, 3, 0, false);
        // 2.5 s of silence (lag spike), then the client flushes a short catch-up burst.
        List<Finding> f = sendRate(t, 40, 2, 5500, false);
        assertTrue(f.isEmpty());
    }

    @Test
    void graceSuspensionIgnoresTheRate() {
        PacketRateTracker t = new PacketRateTracker(TestSupport.shippedSettings().checks().packetRate());
        assertTrue(sendRate(t, 60, 15, 0, true).isEmpty());
    }

    @Test
    void floodNeedsConsecutiveSecondsAboveTheLimit() {
        Settings.PacketRate cfg = TestSupport.shippedSettings().checks().packetRate();
        PacketRateTracker t = new PacketRateTracker(cfg);
        assertTrue(t.onSecond(1000, false).isEmpty());
        assertTrue(t.onSecond(100, false).isEmpty());
        assertTrue(t.onSecond(1000, false).isEmpty());
        assertTrue(t.onSecond(1000, false).isEmpty());
        Optional<Finding> f = t.onSecond(1000, false);
        assertTrue(f.isPresent());
        assertEquals("PacketFlood", f.get().checkId());
    }

    @Test
    void invalidNumericValuesAreEstablishedContradictions() {
        Finding nan = ProtocolValidator.validateLocation(true, Double.NaN, 64, 0, false, 0, 0).orElseThrow();
        assertEquals(DetectionKind.ESTABLISHED, nan.kind());
        assertEquals("InvalidNumericValue", nan.checkId());
        assertEquals("NaN".toLowerCase(), nan.measurements().get("x").toLowerCase().replace("unknown", "nan"));
        assertTrue(ProtocolValidator.validateLocation(true, 0, Double.NEGATIVE_INFINITY, 0, false, 0, 0).isPresent());
        assertTrue(ProtocolValidator.validateLocation(true, 4.0E7, 64, 0, false, 0, 0).isPresent());
        assertTrue(ProtocolValidator.validateLocation(false, 0, 0, 0, true, Float.NaN, 0).isPresent());
        assertTrue(ProtocolValidator.validateLocation(false, 0, 0, 0, true, 0, 91f).isPresent());
        assertTrue(ProtocolValidator.validateLocation(true, 100.5, 64, -3000, true, 720f, -90f).isEmpty());
        assertTrue(ProtocolValidator.validateLocation(false, Double.NaN, 0, 0, false, Float.NaN, 0).isEmpty(),
                "fields the packet does not carry are not judged");
    }

    @Test
    void invalidFieldsAndImpossibleStates() {
        assertTrue(ProtocolValidator.validateHeldSlot(9).isPresent());
        assertTrue(ProtocolValidator.validateHeldSlot(-1).isPresent());
        assertTrue(ProtocolValidator.validateHeldSlot(8).isEmpty());
        assertTrue(ProtocolValidator.validateClickSlot(1, -999).isEmpty());
        assertTrue(ProtocolValidator.validateClickSlot(1, -500).isPresent());
        assertTrue(ProtocolValidator.validateClickSlot(-4, 3).isPresent());
        assertEquals("InvalidPacketField", ProtocolValidator.validateEntityId(-5, 10).orElseThrow().checkId());
        assertEquals("SelfAttack", ProtocolValidator.validateEntityId(10, 10).orElseThrow().checkId());
        assertTrue(ProtocolValidator.validateEntityId(11, 10).isEmpty());
        assertTrue(ProtocolValidator.validateFlightToggle(true, false, false).isPresent());
        assertTrue(ProtocolValidator.validateFlightToggle(true, true, false).isEmpty(), "creative/permitted flight");
        assertTrue(ProtocolValidator.validateFlightToggle(true, false, true).isEmpty(), "packet in flight during a change");
        assertTrue(ProtocolValidator.validateWindow(5, false, false).isPresent());
        assertTrue(ProtocolValidator.validateWindow(5, false, true).isEmpty());
        assertTrue(ProtocolValidator.validateWindow(5, true, false).isEmpty());
        assertTrue(ProtocolValidator.validateWindow(0, false, false).isEmpty());
    }

    @Test
    void reachUsesTheServerKnownRangeLatencyAndBufferedEvidence() {
        Settings.Reach cfg = TestSupport.shippedSettings().checks().reach();
        ReachEvaluator r = new ReachEvaluator(cfg);
        assertEquals(3.0 + 0.5, r.allowed(3.0, false, 0), 1e-9);
        assertEquals(3.0 + 2.0 + 0.5, r.allowed(3.0, true, 0), 1e-9, "creative range bonus");
        assertTrue(r.allowed(3.0, false, 100) > r.allowed(3.0, false, 0), "latency widens tolerance");
        assertEquals(r.allowed(3.0, false, 300), r.allowed(3.0, false, 5000), 1e-9, "latency allowance is capped");

        assertTrue(r.evaluate(3.2, 3.0, false, 20, "PLAYER").isEmpty());
        assertTrue(r.evaluate(5.0, 3.0, false, 20, "PLAYER").isEmpty(), "one long hit is buffered, not an accusation");
        Finding f = r.evaluate(5.0, 3.0, false, 20, "PLAYER").orElseThrow();
        assertEquals("ReachDistance", f.checkId());
        assertEquals("unknown", f.measurements().get("pingMs").equals("20") ? "unknown" : "wrong");
        assertEquals("unknown", new ReachEvaluator(cfg).allowed(3.0, false, -1) == 3.5 ? "unknown" : "wrong",
                "an unknown ping adds no latency allowance");
    }
}
