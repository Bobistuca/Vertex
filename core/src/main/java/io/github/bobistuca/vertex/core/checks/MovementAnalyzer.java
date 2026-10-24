package io.github.bobistuca.vertex.core.checks;

import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.model.Confidence;
import io.github.bobistuca.vertex.core.model.DetectionKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Movement plausibility against server-known abilities and conditions. Limits are derived from the server's own
 * attribute values (which already include potion and sprint modifiers) with generous margins, suspicion is
 * accumulated in buffers, and every exemption that the server can establish suppresses the relevant part.
 */
public final class MovementAnalyzer {
    public static final String SPEED_ID = "MovementSpeed";
    public static final String ASCENT_ID = "MovementAscent";
    public static final String HOVER_ID = "MovementHover";

    private final Settings.Movement config;
    private final SuspicionBuffer speedBuffer = new SuspicionBuffer();
    private final SuspicionBuffer ascentBuffer = new SuspicionBuffer();
    private final SuspicionBuffer hoverBuffer = new SuspicionBuffer();

    private boolean hasLast;
    private double lastX;
    private double lastY;
    private double lastZ;
    private double groundY;
    private boolean groundKnown;
    private int stillInAir;

    public MovementAnalyzer(Settings.Movement config) {
        this.config = config;
    }

    public void reset() {
        hasLast = false;
        groundKnown = false;
        stillInAir = 0;
        speedBuffer.reset();
        ascentBuffer.reset();
        hoverBuffer.reset();
    }

    /**
     * @param suspended true while a grace period applies (the baseline is re-established)
     * @return findings produced by this packet (usually empty)
     */
    public List<Finding> onPosition(double x, double y, double z, boolean onGround, PlayerSnapshot snap,
                                    boolean suspended) {
        List<Finding> findings = new ArrayList<>(0);
        boolean exempt = suspended || snap.spectator() || snap.inVehicle() || snap.gliding() || snap.riptiding();
        if (exempt || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            rebase(x, y, z, onGround);
            return findings;
        }
        if (!hasLast) {
            rebase(x, y, z, onGround);
            return findings;
        }
        double dx = x - lastX;
        double dz = z - lastZ;
        double dy = y - lastY;
        double dh = Math.sqrt(dx * dx + dz * dz);
        if (dh > 10.0 || Math.abs(dy) > 10.0) {
            // Large displacement: an unreported teleport or correction. Re-baseline instead of judging.
            rebase(x, y, z, onGround);
            return findings;
        }

        checkHorizontal(dh, snap, findings);

        boolean verticalExempt = (snap.allowFlight() && snap.flying()) || snap.climbing() || snap.complexEnvironment()
                || snap.levitation() || snap.swimming() || snap.velocity() > 0.6;
        if (verticalExempt) {
            ascentBuffer.reset();
            hoverBuffer.reset();
            stillInAir = 0;
            groundKnown = false;
        } else {
            checkAscent(y, dy, onGround, snap, findings);
            checkHover(dy, onGround, snap, findings);
        }
        lastX = x;
        lastY = y;
        lastZ = z;
        return findings;
    }

    private void rebase(double x, double y, double z, boolean onGround) {
        if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) {
            hasLast = true;
            lastX = x;
            lastY = y;
            lastZ = z;
            groundKnown = onGround;
            groundY = y;
        } else {
            hasLast = false;
        }
        stillInAir = 0;
        speedBuffer.decay(config.bufferDecay());
    }

    private void checkHorizontal(double dh, PlayerSnapshot snap, List<Finding> out) {
        double limit = snap.flying()
                ? snap.flySpeed() * config.flyFactor() + 0.3
                : snap.movementSpeed() * config.speedFactor() + config.speedBase();
        if (dh > limit) {
            double excess = Math.min(5.0, 1.0 + (dh / limit - 1.0) * 2.0);
            if (speedBuffer.add(excess, config.bufferThreshold())) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("horizontalPerPacket", Values.f(dh, 3));
                m.put("allowedPerPacket", Values.f(limit, 3));
                m.put("movementSpeedAttribute", Values.f(snap.movementSpeed(), 4));
                m.put("flying", Boolean.toString(snap.flying()));
                m.put("gameMode", snap.gameMode());
                m.put("tps", Values.f(snap.tps(), 1));
                m.put("pingMs", snap.pingMillis() < 0 ? "unknown" : Integer.toString(snap.pingMillis()));
                out.add(new Finding(SPEED_ID, DetectionKind.SUSPICION, dh > limit * 2 ? Confidence.HIGH : Confidence.MEDIUM,
                        "Repeated horizontal displacement per movement packet exceeded the limit derived from the "
                                + "server-known speed attribute and flight state.", m));
            }
        } else {
            speedBuffer.decay(config.bufferDecay());
        }
    }

    private void checkAscent(double y, double dy, boolean onGround, PlayerSnapshot snap, List<Finding> out) {
        double jump = snap.jumpBoostLevel() * config.jumpBoostPerLevel();
        double maxUp = config.maxStepUp() + jump;
        boolean violated = false;
        double rise = 0;
        if (dy > maxUp) {
            violated = true;
        }
        if (onGround) {
            groundY = y;
            groundKnown = true;
        } else if (groundKnown) {
            rise = y - groundY;
            if (rise > config.airRiseLimit() + jump * 1.7) {
                violated = true;
            }
        }
        if (violated) {
            if (ascentBuffer.add(2.0, config.bufferThreshold() / 2.0)) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("verticalPerPacket", Values.f(dy, 3));
                m.put("allowedPerPacket", Values.f(maxUp, 3));
                m.put("riseSinceGround", Values.f(rise, 3));
                m.put("allowedRise", Values.f(config.airRiseLimit() + jump * 1.7, 3));
                m.put("jumpBoostLevel", Integer.toString(snap.jumpBoostLevel()));
                m.put("gameMode", snap.gameMode());
                out.add(new Finding(ASCENT_ID, DetectionKind.SUSPICION, Confidence.MEDIUM,
                        "Upward movement exceeded what a jump, step or server-known effect allows.", m));
            }
        } else {
            ascentBuffer.decay(config.bufferDecay());
        }
    }

    private void checkHover(double dy, boolean onGround, PlayerSnapshot snap, List<Finding> out) {
        boolean hovering = !onGround && Math.abs(dy) < 0.005 && !snap.supportedBelow() && !snap.slowFalling();
        if (hovering) {
            stillInAir++;
            if (stillInAir >= config.hoverPackets()) {
                stillInAir = 0;
                if (hoverBuffer.add(1.0, 2.0)) {
                    Map<String, String> m = new LinkedHashMap<>();
                    m.put("stationaryAirPackets", Integer.toString(config.hoverPackets()));
                    m.put("supportedBelow", "false");
                    m.put("gameMode", snap.gameMode());
                    out.add(new Finding(HOVER_ID, DetectionKind.SUSPICION, Confidence.MEDIUM,
                            "The client stayed vertically still in mid-air with nothing below it and no "
                                    + "server-known effect that explains it.", m));
                }
            }
        } else {
            stillInAir = 0;
            hoverBuffer.decay(config.bufferDecay());
        }
    }
}
