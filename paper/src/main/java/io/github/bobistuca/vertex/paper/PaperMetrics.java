package io.github.bobistuca.vertex.paper;

import io.github.bobistuca.vertex.core.platform.ServerMetrics;
import org.bukkit.Bukkit;

/** TPS and MSPT from the Paper server API. */
final class PaperMetrics implements ServerMetrics {
    @Override
    public double mspt() {
        try {
            double v = Bukkit.getAverageTickTime();
            return v > 0 ? v : Double.NaN;
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    @Override
    public double tps() {
        double mspt = mspt();
        if (!Double.isNaN(mspt)) {
            return Math.min(20.0, 1000.0 / mspt);
        }
        try {
            double[] tps = Bukkit.getTPS();
            return tps.length == 0 ? Double.NaN : Math.min(20.0, tps[0]);
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    @Override
    public String source() {
        return "server-api";
    }
}
