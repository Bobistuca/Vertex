package io.github.bobistuca.vertex.core.checks;

import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.model.Confidence;
import io.github.bobistuca.vertex.core.model.DetectionKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Auto-clicker-like pattern analysis over completed windows of attack timing.
 *
 * <p>Limits: the server only observes packets. It does not see physical mouse clicks, hotkeys, or software, and
 * the vanilla client processes input once per client tick, so legitimate intervals cluster around multiples of
 * 50 ms. A window is suspicious only when several independent signal categories agree; a single CPS value never
 * decides anything. Attack and swing packets that describe the same click are merged so one click counts once.
 */
public final class ClickAnalyzer {
    public static final String CHECK_ID = "AutoClickerPattern";
    private static final double TICK_MS = 50.0;

    /** Which packet produced the click observation. */
    public enum Source { ATTACK, SWING }

    /** Measured properties of one completed window. */
    public record Stats(int samples, double meanMs, double stdMs, double cv, double meanCps,
                        double dominantTickShare, double tickEntropyBits, double periodMatchShare, int period,
                        double residualStdMs, double batchedShare) { }

    /** Outcome of a completed window. */
    public record WindowResult(Stats stats, boolean skipped, String skipReason, List<String> signals,
                               Optional<Finding> finding) { }

    private final Settings.AutoClicker config;
    private final long maxGapNanos;
    private final long dedupeNanos;
    private final List<Double> intervalsMs = new ArrayList<>();
    private final SuspicionBuffer buffer = new SuspicionBuffer();

    private boolean hasPrevious;
    private long previousNanos;
    private Source previousSource = Source.SWING;

    public ClickAnalyzer(Settings.AutoClicker config) {
        this.config = config;
        this.maxGapNanos = config.maxGapMillis() * 1_000_000L;
        this.dedupeNanos = (long) (config.dedupeMillis() * 1_000_000.0);
    }

    /**
     * Feeds one packet observation.
     *
     * @param contextBlocked true while the swing is explained by another action (for example block digging)
     * @param tps            server ticks per second, {@code NaN} if unknown
     * @param jitterMillis   recent latency deviation, {@code NaN} if unknown
     * @return a result when this click completed an analysis window
     */
    public Optional<WindowResult> accept(Source source, long nowNanos, boolean contextBlocked, double tps,
                                         double jitterMillis) {
        if (hasPrevious && source != previousSource && nowNanos - previousNanos >= 0
                && nowNanos - previousNanos < dedupeNanos) {
            // Same physical click reported by two packets: merge, never count twice.
            if (source == Source.ATTACK) {
                previousSource = Source.ATTACK;
            }
            return Optional.empty();
        }
        if (source == Source.SWING && (!config.includeAirSwings() || contextBlocked)) {
            return Optional.empty();
        }
        Optional<WindowResult> result = Optional.empty();
        if (hasPrevious) {
            long delta = nowNanos - previousNanos;
            if (delta < 0 || delta > maxGapNanos) {
                intervalsMs.clear();
            } else {
                intervalsMs.add(delta / 1_000_000.0);
                if (intervalsMs.size() >= config.windowSamples()) {
                    result = Optional.of(analyzeWindow(tps, jitterMillis));
                    intervalsMs.clear();
                }
            }
        }
        hasPrevious = true;
        previousNanos = nowNanos;
        previousSource = source;
        return result;
    }

    /** Forgets the current run (for example after a teleport or grace period). */
    public void reset() {
        intervalsMs.clear();
        hasPrevious = false;
        buffer.reset();
    }

    private WindowResult analyzeWindow(double tps, double jitterMillis) {
        Stats stats = computeStats(intervalsMs);
        if (!Double.isNaN(tps) && tps < config.minTps()) {
            return skipped(stats, "low-tps");
        }
        if (!Double.isNaN(jitterMillis) && jitterMillis > config.maxJitterMillis()) {
            return skipped(stats, "high-latency-jitter");
        }
        if (stats.batchedShare() > config.maxBatchedShare()) {
            return skipped(stats, "batched-delivery");
        }
        if (stats.meanCps() < config.minMeanCps()) {
            buffer.decay(config.bufferDecay());
            return new WindowResult(stats, false, "", List.of(), Optional.empty());
        }

        List<String> signals = new ArrayList<>();
        int categories = 0;
        List<String> consistency = new ArrayList<>();
        if (stats.cv() <= config.maxCv()) consistency.add("low-variance");
        if (stats.dominantTickShare() >= config.dominantTickShare()) consistency.add("dominant-interval");
        if (stats.tickEntropyBits() <= config.maxTickEntropyBits()) consistency.add("low-entropy");
        if (!consistency.isEmpty()) {
            categories++;
            signals.add("consistency:" + String.join("+", consistency));
        }
        if (stats.periodMatchShare() >= config.periodMatchShare()) {
            categories++;
            signals.add("repeating-pattern:period=" + stats.period());
        }
        boolean jitterKnown = !Double.isNaN(jitterMillis);
        if (jitterKnown && stats.residualStdMs() <= config.maxResidualMillis()) {
            categories++;
            signals.add("sub-tick-regularity");
        }

        if (categories < config.requiredSignals()) {
            buffer.decay(config.bufferDecay());
            return new WindowResult(stats, false, "", signals, Optional.empty());
        }
        if (!buffer.add(1.0, config.bufferThreshold())) {
            return new WindowResult(stats, false, "", signals, Optional.empty());
        }
        Confidence confidence = categories >= 3 ? Confidence.HIGH
                : categories == 2 ? Confidence.MEDIUM : Confidence.LOW;
        Map<String, String> m = new LinkedHashMap<>();
        m.put("samples", Integer.toString(stats.samples()));
        m.put("meanCps", Values.f(stats.meanCps(), 2));
        m.put("meanIntervalMs", Values.f(stats.meanMs(), 1));
        m.put("coefficientOfVariation", Values.f(stats.cv(), 3));
        m.put("dominantTickShare", Values.f(stats.dominantTickShare(), 2));
        m.put("tickEntropyBits", Values.f(stats.tickEntropyBits(), 2));
        m.put("periodMatchShare", Values.f(stats.periodMatchShare(), 2));
        m.put("period", Integer.toString(stats.period()));
        m.put("residualStdMs", Values.f(stats.residualStdMs(), 2));
        m.put("signals", String.join(",", signals));
        Finding finding = new Finding(CHECK_ID, DetectionKind.SUSPICION, confidence,
                "A completed window of " + stats.samples() + " attack intervals showed " + categories
                        + " independent regularity signals. Server-observed packets cannot show physical clicks, "
                        + "so this is a statistical suspicion, not proof of a specific tool.", m);
        return new WindowResult(stats, false, "", signals, Optional.of(finding));
    }

    private WindowResult skipped(Stats stats, String reason) {
        return new WindowResult(stats, true, reason, List.of(), Optional.empty());
    }

    /** Pure statistics, exposed for tests. */
    public static Stats computeStats(List<Double> intervals) {
        int n = intervals.size();
        double sum = 0;
        int batched = 0;
        for (double d : intervals) {
            sum += d;
            if (d < 3.0) {
                batched++;
            }
        }
        double mean = sum / n;
        double var = 0;
        for (double d : intervals) {
            var += (d - mean) * (d - mean);
        }
        double std = Math.sqrt(var / n);
        double cv = mean > 0 ? std / mean : 0;

        int[] ticks = new int[n];
        Map<Integer, Integer> counts = new TreeMap<>();
        double residualSum = 0;
        double[] residuals = new double[n];
        for (int i = 0; i < n; i++) {
            int t = Math.max(1, (int) Math.round(intervals.get(i) / TICK_MS));
            ticks[i] = t;
            counts.merge(t, 1, Integer::sum);
            residuals[i] = intervals.get(i) - t * TICK_MS;
            residualSum += residuals[i];
        }
        int dominant = 0;
        double entropy = 0;
        for (int c : counts.values()) {
            dominant = Math.max(dominant, c);
            double p = (double) c / n;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        double residualMean = residualSum / n;
        double residualVar = 0;
        for (double r : residuals) {
            residualVar += (r - residualMean) * (r - residualMean);
        }

        int bestPeriod = 1;
        double bestShare = 0;
        int maxPeriod = Math.min(8, n - 1);
        for (int p = 1; p <= maxPeriod; p++) {
            int matches = 0;
            for (int i = p; i < n; i++) {
                if (ticks[i] == ticks[i - p]) {
                    matches++;
                }
            }
            double share = (double) matches / (n - p);
            if (share > bestShare + 1e-9) {
                bestShare = share;
                bestPeriod = p;
            }
        }
        return new Stats(n, mean, std, cv, mean > 0 ? 1000.0 / mean : 0, (double) dominant / n, Math.max(0, entropy),
                bestShare, bestPeriod, Math.sqrt(residualVar / n), (double) batched / n);
    }
}
