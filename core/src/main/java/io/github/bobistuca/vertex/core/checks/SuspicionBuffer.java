package io.github.bobistuca.vertex.core.checks;

/** Accumulates suspicion, decays it on clean samples, and fires when a threshold is reached. */
public final class SuspicionBuffer {
    private double value;

    /** Adds evidence; returns true when the threshold is reached (the buffer then drops to half the threshold). */
    public boolean add(double amount, double threshold) {
        value += amount;
        if (value >= threshold) {
            value = threshold / 2.0;
            return true;
        }
        return false;
    }

    public void decay(double amount) {
        value = Math.max(0.0, value - amount);
    }

    public void reset() {
        value = 0.0;
    }

    public double value() {
        return value;
    }
}
