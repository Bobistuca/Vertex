package io.github.bobistuca.vertex.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bobistuca.vertex.core.checks.ClickAnalyzer;
import io.github.bobistuca.vertex.core.checks.ClickAnalyzer.Source;
import io.github.bobistuca.vertex.core.checks.ClickAnalyzer.WindowResult;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.support.TestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ClickAnalyzerTest {
    private static final long MS = 1_000_000L;

    private static Settings.AutoClicker config() {
        return TestSupport.shippedSettings().checks().autoClicker();
    }

    /** Feeds the given intervals (ms) as attack clicks; returns every completed window. */
    private static List<WindowResult> feed(ClickAnalyzer a, double[] intervals, double tps, double jitter) {
        List<WindowResult> out = new ArrayList<>();
        double t = 1000;
        a.accept(Source.ATTACK, (long) (t * MS), false, tps, jitter);
        for (double d : intervals) {
            t += d;
            Optional<WindowResult> r = a.accept(Source.ATTACK, (long) (t * MS), false, tps, jitter);
            r.ifPresent(out::add);
        }
        return out;
    }

    private static double[] intervals(int count, java.util.function.DoubleSupplier next) {
        double[] out = new double[count];
        for (int i = 0; i < count; i++) {
            out[i] = next.getAsDouble();
        }
        return out;
    }

    @Test
    void humanLikeFastClickingWithTickQuantizationIsNeverFlaggedAcrossManyWindows() {
        Random rnd = new Random(42);
        ClickAnalyzer a = new ClickAnalyzer(config());
        double[] data = intervals(60 * 200, () -> {
            double p = rnd.nextDouble();
            int ticks = p < 0.2 ? 1 : p < 0.65 ? 2 : p < 0.9 ? 3 : 4;
            return ticks * 50.0 + rnd.nextGaussian() * 4.0;
        });
        List<WindowResult> windows = feed(a, data, 20.0, 6.0);
        assertEquals(200, windows.size());
        assertTrue(windows.stream().noneMatch(w -> w.finding().isPresent()), "legitimate timing must not be flagged");
    }

    @Test
    void aConstantMachineLikeIntervalIsFlaggedByASuspiciousCompletedWindow() {
        Random rnd = new Random(7);
        ClickAnalyzer a = new ClickAnalyzer(config());
        double[] data = intervals(60 * 3, () -> 100.0 + rnd.nextGaussian() * 0.6);
        List<WindowResult> windows = feed(a, data, 20.0, 6.0);
        assertEquals(3, windows.size());
        assertTrue(windows.stream().allMatch(w -> w.finding().isPresent()));
        var finding = windows.getFirst().finding().orElseThrow();
        assertEquals("AutoClickerPattern", finding.checkId());
        assertEquals("60", finding.measurements().get("samples"));
        assertTrue(finding.measurements().containsKey("meanCps"));
        assertTrue(finding.reason().contains("statistical suspicion"));
    }

    @Test
    void anExactlyRepeatingTickPatternIsDetectedEvenWhenVarianceLooksHuman() {
        Random rnd = new Random(11);
        ClickAnalyzer a = new ClickAnalyzer(config());
        int[] pattern = {2, 3};
        int[] idx = {0};
        double[] data = intervals(60, () -> pattern[idx[0]++ % 2] * 50.0 + rnd.nextGaussian() * 0.4);
        WindowResult w = feed(a, data, 20.0, 5.0).getFirst();
        assertTrue(w.signals().stream().anyMatch(s -> s.startsWith("repeating-pattern")), w.signals().toString());
        assertTrue(w.finding().isPresent());
    }

    @Test
    void slowRegularClickingBelowTheMeanCpsGateIsNotSuspicious() {
        ClickAnalyzer a = new ClickAnalyzer(config());
        List<WindowResult> windows = feed(a, intervals(60, () -> 200.0), 20.0, 5.0);
        assertTrue(windows.getFirst().finding().isEmpty(), "5 CPS is never evidence on its own");
    }

    @Test
    void aHighCpsAloneIsNotEnoughWithoutRegularity() {
        Random rnd = new Random(3);
        ClickAnalyzer a = new ClickAnalyzer(config());
        // about 14 CPS jitter clicking with irregular tick counts
        WindowResult w = feed(a, intervals(60, () -> (rnd.nextBoolean() ? 50.0 : rnd.nextBoolean() ? 100.0 : 150.0) + rnd.nextGaussian() * 5), 20.0, 8.0).getFirst();
        assertTrue(w.finding().isEmpty(), w.stats().toString());
    }

    @Test
    void packetBatchingFromLagSkipsTheWindowInsteadOfAccusing() {
        ClickAnalyzer a = new ClickAnalyzer(config());
        int[] i = {0};
        double[] data = intervals(60, () -> (i[0]++ % 4 == 0) ? 1.0 : 100.0);
        WindowResult w = feed(a, data, 20.0, 5.0).getFirst();
        assertTrue(w.skipped());
        assertEquals("batched-delivery", w.skipReason());
        assertTrue(w.finding().isEmpty());
    }

    @Test
    void lowTpsAndHighJitterSkipWindows() {
        double[] constant = intervals(60, () -> 100.0);
        WindowResult lowTps = feed(new ClickAnalyzer(config()), constant, 12.0, 5.0).getFirst();
        assertEquals("low-tps", lowTps.skipReason());
        WindowResult jitter = feed(new ClickAnalyzer(config()), constant, 20.0, 200.0).getFirst();
        assertEquals("high-latency-jitter", jitter.skipReason());
        assertTrue(lowTps.finding().isEmpty() && jitter.finding().isEmpty());
    }

    @Test
    void attackAndSwingPacketsOfTheSameClickAreCountedOnce() {
        ClickAnalyzer a = new ClickAnalyzer(config());
        double t = 1000;
        int completedAtClick = -1;
        // Alternate the packet order (swing first, then attack) to cover both protocol orderings.
        for (int click = 0; click < 200 && completedAtClick < 0; click++) {
            boolean swingFirst = click % 2 == 0;
            Source first = swingFirst ? Source.SWING : Source.ATTACK;
            Source second = swingFirst ? Source.ATTACK : Source.SWING;
            Optional<WindowResult> r1 = a.accept(first, (long) (t * MS), false, 20.0, 5.0);
            Optional<WindowResult> r2 = a.accept(second, (long) ((t + 2) * MS), false, 20.0, 5.0);
            if (r1.isPresent() || r2.isPresent()) {
                completedAtClick = click;
            }
            t += 100;
        }
        // 60 intervals need 61 distinct clicks (indexes 0..60), so the window completes at click index 60.
        assertEquals(60, completedAtClick);
    }

    @Test
    void swingsAreIgnoredByDefaultAndDuringBlockBreaking() {
        Settings.AutoClicker cfg = config();
        ClickAnalyzer ignored = new ClickAnalyzer(cfg);
        double t = 0;
        for (int i = 0; i < 200; i++, t += 100) {
            assertTrue(ignored.accept(Source.SWING, (long) (t * MS), false, 20.0, 5.0).isEmpty());
        }
        Settings relaxed = TestSupport.shippedSettings();
        Settings.AutoClicker withSwings = new Settings.AutoClicker(true, cfg.windowSamples(), cfg.maxGapMillis(),
                cfg.minMeanCps(), cfg.batchMillis(), cfg.maxBatchedShare(), cfg.dominantTickShare(),
                cfg.maxTickEntropyBits(), cfg.periodMatchShare(), cfg.maxPeriod(), cfg.maxCv(), cfg.maxResidualMillis(),
                cfg.requiredSignals(), true, cfg.dedupeMillis(), cfg.minTps(), cfg.maxJitterMillis(),
                cfg.bufferThreshold(), cfg.bufferDecay());
        ClickAnalyzer digging = new ClickAnalyzer(withSwings);
        t = 0;
        for (int i = 0; i < 200; i++, t += 100) {
            assertTrue(digging.accept(Source.SWING, (long) (t * MS), true, 20.0, 5.0).isEmpty(), "mining swings are explained");
        }
        ClickAnalyzer air = new ClickAnalyzer(withSwings);
        t = 0;
        int windows = 0;
        for (int i = 0; i < 200; i++, t += 100) {
            if (air.accept(Source.SWING, (long) (t * MS), false, 20.0, 5.0).isPresent()) {
                windows++;
            }
        }
        assertFalse(windows == 0, "air swings can be analyzed when enabled");
        assertEquals(20, relaxed.checks().autoClicker().windowSamples() / 3);
    }

    @Test
    void aLongPauseEndsTheRunSoWindowsOnlyCoverSustainedBehavior() {
        ClickAnalyzer a = new ClickAnalyzer(config());
        double t = 0;
        int completed = 0;
        for (int burst = 0; burst < 10; burst++) {
            for (int i = 0; i < 30; i++, t += 100) {
                if (a.accept(Source.ATTACK, (long) (t * MS), false, 20.0, 5.0).isPresent()) {
                    completed++;
                }
            }
            t += 2000; // pause longer than max-gap-ms
        }
        assertEquals(0, completed);
    }
}
