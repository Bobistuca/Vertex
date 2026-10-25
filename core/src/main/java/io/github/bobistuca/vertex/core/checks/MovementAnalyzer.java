package io.github.bobistuca.vertex.core.checks;

import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.model.Confidence;
import io.github.bobistuca.vertex.core.model.DetectionKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Movement plausibility against server-known abilities and conditions.
 *
 * <p>Horizontal limits are derived from the server's own movement-speed attribute (potion, sprint and enchantment
 * modifiers included) with a margin, calibrated on vanilla values: sprint-jumping sustains about 7.1 blocks/s
 * (0.356 per tick) at the default sprint attribute 0.13, i.e. about 2.74 blocks per tick per unit of attribute.
 * Two independent signals are used: a per-packet limit (burst) and a sustained average over a window of moving
 * packets that spans several jumps (the b-hop signature). Jumping repeatedly is legitimate and is never evidence
 * by itself: only speed beyond what the attributes allow is.
 */
public final class MovementAnalyzer {
    public static final String SPEED_ID = "MovementSpeed";
    public static final String SUSTAINED_ID = "MovementSustainedSpeed";
    public static final String ASCENT_ID = "MovementAscent";
    public static final String HOVER_ID = "MovementHover";

    /** Sprint multiplies the movement attribute by this factor (vanilla: +30%). */
    private static final double SPRINT_MULTIPLIER = 1.3;
    /** Liquids, webs, climbables and similar blocks change friction; keep a wide margin there. */
    private static final double COMPLEX_MARGIN = 1.8;
    private static final double MOVING_THRESHOLD = 0.02;

    private final Settings.Movement config;
    private final SuspicionBuffer speedBuffer = new SuspicionBuffer();
    private final SuspicionBuffer sustainedBuffer = new SuspicionBuffer();
    private final SuspicionBuffer ascentBuffer = new SuspicionBuffer();
    private final SuspicionBuffer hoverBuffer = new SuspicionBuffer();

    private boolean hasLast;
    private double lastX;
    private double lastY;
    private double lastZ;
    private double groundY;
    private boolean groundKnown;
    private boolean lastOnGround;
    private int stillInAir;

    private final double[] window;
    private int windowFill;
    private int hops;
    private long evaluatedPackets;
    private long sustainedEvaluations;

    public MovementAnalyzer(Settings.Movement config) {
        this.config = config;
        this.window = new double[config.sustainedPackets()];
    }

    public void reset() {
        hasLast = false;
        groundKnown = false;
        stillInAir = 0;
        windowFill = 0;
        hops = 0;
        speedBuffer.reset();
        sustainedBuffer.reset();
        ascentBuffer.reset();
        hoverBuffer.reset();
    }

    /** Moving packets collected for the next sustained-speed evaluation. */
    public int sustainedFill() {
        return windowFill;
    }

    public int sustainedWindow() {
        return window.length;
    }

    public long sustainedEvaluations() {
        return sustainedEvaluations;
    }

    public long evaluatedPackets() {
        return evaluatedPackets;
    }

    public String bufferSummary() {
        return String.format(java.util.Locale.ROOT, "speed=%.2f sustained=%.2f ascent=%.2f hover=%.2f",
                speedBuffer.value(), sustainedBuffer.value(), ascentBuffer.value(), hoverBuffer.value());
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
        evaluatedPackets++;
        if (lastOnGround && !onGround) {
            hops++;
        }
        lastOnGround = onGround;

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
            lastOnGround = onGround;
        } else {
            hasLast = false;
        }
        stillInAir = 0;
        windowFill = 0;
        hops = 0;
        speedBuffer.decay(config.bufferDecay());
    }

    /** Speed unit (blocks per tick) allowed for the player's current abilities, before margins. */
    private double unit(PlayerSnapshot snap, boolean sustained) {
        if (snap.flying()) {
            return config.flyFactor() * snap.flySpeed() * (snap.sprinting() ? 2.0 : 1.0) * (sustained ? 0.9 : 1.0);
        }
        // The sprint state reaches the server slightly before the next snapshot: assume sprinting is possible.
        double attr = snap.sprinting() ? snap.movementSpeed() : snap.movementSpeed() * SPRINT_MULTIPLIER;
        return attr * (sustained ? config.sustainedFactor() : config.speedFactor());
    }

    private double margin(PlayerSnapshot snap) {
        double m = 1.0;
        if (snap.slipperyBelow()) m *= config.slipperyMultiplier();
        if (snap.complexEnvironment()) m *= COMPLEX_MARGIN;
        return m;
    }

    private void checkHorizontal(double dh, PlayerSnapshot snap, List<Finding> out) {
        double margin = margin(snap);
        double limit = unit(snap, false) * margin + config.speedBase();
        if (dh > limit) {
            double excess = Math.min(5.0, 1.0 + (dh / limit - 1.0) * 2.0);
            if (speedBuffer.add(excess, config.bufferThreshold())) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("horizontalPerPacket", Values.f(dh, 3));
                m.put("allowedPerPacket", Values.f(limit, 3));
                m.put("movementSpeedAttribute", Values.f(snap.movementSpeed(), 4));
                m.put("sprinting", Boolean.toString(snap.sprinting()));
                m.put("flying", Boolean.toString(snap.flying()));
                m.put("gameMode", snap.gameMode());
                m.put("tps", Values.f(snap.tps(), 1));
                m.put("pingMs", snap.pingMillis() < 0 ? "unknown" : Integer.toString(snap.pingMillis()));
                out.add(new Finding(SPEED_ID, DetectionKind.SUSPICION, dh > limit * 1.6 ? Confidence.HIGH : Confidence.MEDIUM,
                        "Repeated horizontal displacement per movement packet exceeded the limit derived from the "
                                + "server-known speed attribute and flight state.", m));
            }
        } else {
            speedBuffer.decay(config.bufferDecay());
        }

        if (dh >= MOVING_THRESHOLD && !snap.flying()) {
            window[windowFill++] = dh;
            if (windowFill == window.length) {
                evaluateSustained(snap, margin, out);
            }
        }
    }

    private void evaluateSustained(PlayerSnapshot snap, double margin, List<Finding> out) {
        double sum = 0;
        for (double v : window) {
            sum += v;
        }
        double average = sum / window.length;
        sustainedEvaluations++;
        int jumps = hops;
        windowFill = 0;
        hops = 0;
        double allowed = unit(snap, true) * margin + config.speedBase();
        if (average > allowed) {
            double excess = 1.0 + Math.min(2.0, (average / allowed - 1.0) * 4.0);
            if (sustainedBuffer.add(excess, 2.0)) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("averageHorizontalPerPacket", Values.f(average, 3));
                m.put("allowedAverage", Values.f(allowed, 3));
                m.put("windowPackets", Integer.toString(window.length));
                m.put("jumpsInWindow", Integer.toString(jumps));
                m.put("movementSpeedAttribute", Values.f(snap.movementSpeed(), 4));
                m.put("slipperyBelow", Boolean.toString(snap.slipperyBelow()));
                m.put("gameMode", snap.gameMode());
                out.add(new Finding(SUSTAINED_ID, DetectionKind.SUSPICION,
                        average > allowed * 1.3 ? Confidence.HIGH : Confidence.MEDIUM,
                        "Average horizontal speed over " + window.length + " moving packets stayed above what the "
                                + "server-known attributes allow, including across jumps. Jumping alone is legitimate; "
                                + "sustained speed beyond the attribute-derived limit is not.", m));
            }
        } else {
            sustainedBuffer.decay(config.bufferDecay());
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
