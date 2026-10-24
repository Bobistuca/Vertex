package io.github.bobistuca.vertex.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bobistuca.vertex.core.checks.Finding;
import io.github.bobistuca.vertex.core.checks.GraceState;
import io.github.bobistuca.vertex.core.checks.MovementAnalyzer;
import io.github.bobistuca.vertex.core.checks.PlayerSnapshot;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.support.TestSupport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MovementTest {
    private static final long MS = 1_000_000L;

    private static Settings settings() {
        return TestSupport.shippedSettings();
    }

    private static PlayerSnapshot snap(String mode, boolean allowFlight, boolean flying, boolean gliding, boolean vehicle,
                                       double speed, int jumpBoost, boolean levitation, boolean supported) {
        return new PlayerSnapshot(mode, 7, allowFlight, flying, gliding, false, false, vehicle, false, speed, 0.05f,
                jumpBoost, levitation, false, false, supported, 0.0, false, 20.0, 40, 3.0);
    }

    private static List<Finding> walk(MovementAnalyzer a, PlayerSnapshot s, int packets, double stepX, double stepY,
                                      boolean onGround, boolean suspended) {
        List<Finding> all = new ArrayList<>();
        double x = 0;
        double y = 64;
        for (int i = 0; i < packets; i++) {
            x += stepX;
            y += stepY;
            all.addAll(a.onPosition(x, y, 0, onGround, s, suspended));
        }
        return all;
    }

    @Test
    void ordinaryWalkingAndSprintJumpingAreNotFlagged() {
        MovementAnalyzer a = new MovementAnalyzer(settings().checks().movement());
        PlayerSnapshot sprinting = snap("SURVIVAL", false, false, false, false, 0.13, 0, false, true);
        assertTrue(walk(a, sprinting, 400, 0.28, 0, true, false).isEmpty());
    }

    @Test
    void sustainedHorizontalSpeedBeyondTheServerKnownLimitIsASuspicion() {
        MovementAnalyzer a = new MovementAnalyzer(settings().checks().movement());
        List<Finding> findings = walk(a, snap("SURVIVAL", false, false, false, false, 0.1, 0, false, true), 200, 1.6, 0, true, false);
        assertFalse(findings.isEmpty());
        assertEquals("MovementSpeed", findings.getFirst().checkId());
        assertTrue(findings.getFirst().measurements().containsKey("allowedPerPacket"));
    }

    @Test
    void aSinglePacketSpikeNeverAccuses() {
        MovementAnalyzer a = new MovementAnalyzer(settings().checks().movement());
        PlayerSnapshot s = snap("SURVIVAL", false, false, false, false, 0.1, 0, false, true);
        List<Finding> findings = new ArrayList<>(walk(a, s, 50, 0.2, 0, true, false));
        findings.addAll(a.onPosition(50 * 0.2 + 3.0, 64, 0, true, s, false));
        findings.addAll(walk(a, s, 50, 0.2, 0, true, false));
        assertTrue(findings.isEmpty());
    }

    @Test
    void creativeFlightIsLegitimateBeforeSurvivalIsNot() {
        MovementAnalyzer creative = new MovementAnalyzer(settings().checks().movement());
        PlayerSnapshot flying = snap("CREATIVE", true, true, false, false, 0.1, 0, false, false);
        assertTrue(walk(creative, flying, 300, 0.9, 0.3, false, false).isEmpty(), "creative flight is a granted ability");

        MovementAnalyzer survival = new MovementAnalyzer(settings().checks().movement());
        PlayerSnapshot notFlying = snap("SURVIVAL", false, false, false, false, 0.1, 0, false, false);
        assertFalse(walk(survival, notFlying, 300, 0.9, 0.3, false, false).isEmpty());
    }

    @Test
    void spectatorVehiclesAndGlidingAreExempt() {
        for (PlayerSnapshot s : List.of(
                snap("SPECTATOR", false, true, false, false, 0.1, 0, false, false),
                snap("SURVIVAL", false, false, false, true, 0.1, 0, false, false),
                snap("SURVIVAL", false, false, true, false, 0.1, 0, false, false))) {
            MovementAnalyzer a = new MovementAnalyzer(settings().checks().movement());
            assertTrue(walk(a, s, 300, 2.0, 0.5, false, false).isEmpty());
        }
    }

    @Test
    void gracePeriodsSuspendChecksAndReestablishTheBaseline() {
        MovementAnalyzer a = new MovementAnalyzer(settings().checks().movement());
        PlayerSnapshot s = snap("SURVIVAL", false, false, false, false, 0.1, 0, false, true);
        assertTrue(walk(a, s, 300, 3.0, 0, true, true).isEmpty(), "teleport or knockback grace");
        // After grace the first packet re-baselines; walking normally afterwards is clean.
        assertTrue(a.onPosition(900.0, 64, 0, true, s, false).isEmpty());
        assertTrue(walk(a, s, 100, 0.2, 0, true, false).isEmpty());
    }

    @Test
    void graceStateExpiresAndReportsReasons() {
        Settings.Grace g = settings().grace();
        GraceState state = new GraceState();
        assertEquals(null, state.active(10_000 * MS, g, 20.0));
        state.teleported(10_000 * MS);
        assertEquals(GraceState.Reason.TELEPORT, state.active(10_500 * MS, g, 20.0));
        assertEquals(null, state.active(10_000 * MS + (g.teleportMillis() + 1) * MS, g, 20.0));
        state.velocityChanged(20_000 * MS);
        assertEquals(GraceState.Reason.VELOCITY, state.active(20_100 * MS, g, 20.0));
        assertEquals(GraceState.Reason.LOW_TPS, state.active(50_000 * MS, g, 10.0));
        assertEquals(null, state.active(50_000 * MS, g, Double.NaN), "unknown TPS never suppresses by itself");
    }

    @Test
    void jumpBoostAndLevitationExplainAscentOtherwiseItIsASuspicion() {
        MovementAnalyzer plain = new MovementAnalyzer(settings().checks().movement());
        List<Finding> climb = walk(plain, snap("SURVIVAL", false, false, false, false, 0.1, 0, false, false), 80, 0.0, 0.9, false, false);
        assertTrue(climb.stream().anyMatch(f -> f.checkId().equals("MovementAscent")));

        MovementAnalyzer levitating = new MovementAnalyzer(settings().checks().movement());
        assertTrue(walk(levitating, snap("SURVIVAL", false, false, false, false, 0.1, 0, true, false), 80, 0.0, 0.9, false, false).isEmpty());

        MovementAnalyzer boosted = new MovementAnalyzer(settings().checks().movement());
        List<Finding> jump = new ArrayList<>();
        PlayerSnapshot s = snap("SURVIVAL", false, false, false, false, 0.1, 2, false, true);
        jump.addAll(boosted.onPosition(0, 64, 0, true, s, false));
        jump.addAll(boosted.onPosition(0, 64.75, 0, false, s, false));
        assertTrue(jump.isEmpty());
    }

    @Test
    void hoveringInMidAirIsOnlyFlaggedWithoutSupportOrServerKnownEffects() {
        MovementAnalyzer hover = new MovementAnalyzer(settings().checks().movement());
        List<Finding> findings = walk(hover, snap("SURVIVAL", false, false, false, false, 0.1, 0, false, false), 100, 0.0, 0.0, false, false);
        assertTrue(findings.stream().anyMatch(f -> f.checkId().equals("MovementHover")));

        MovementAnalyzer supported = new MovementAnalyzer(settings().checks().movement());
        assertTrue(walk(supported, snap("SURVIVAL", false, false, false, false, 0.1, 0, false, true), 100, 0.0, 0.0, false, false).isEmpty());
    }

    @Test
    void nonFiniteCoordinatesNeverBreakTheAnalyzer() {
        MovementAnalyzer a = new MovementAnalyzer(settings().checks().movement());
        PlayerSnapshot s = PlayerSnapshot.survivalDefaults();
        assertTrue(a.onPosition(Double.NaN, 1, 1, true, s, false).isEmpty());
        assertTrue(a.onPosition(Double.POSITIVE_INFINITY, 1, 1, true, s, false).isEmpty());
        assertTrue(a.onPosition(1, 1, 1, true, s, false).isEmpty());
    }
}
