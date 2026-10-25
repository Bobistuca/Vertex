package io.github.bobistuca.vertex.core.engine;

/** Operational state of one check for one player. A check that never ran is never reported as clean. */
public enum CheckStatus {
    /** Evaluated at least once; no violation is not the same as "never ran". */
    ACTIVE,
    /** Enabled, but the required packets or samples have not arrived yet. */
    INSUFFICIENT_DATA,
    /** The player bypasses the gameplay checks (the reason is shown separately). */
    EXEMPT,
    /** Paused by a bounded grace period after a legitimate server-side event. */
    GRACE_PERIOD,
    /** The server or the packet library cannot provide what the check needs. */
    UNSUPPORTED,
    /** The check threw an exception; it is skipped for that observation and counted. */
    ERROR,
    /** Disabled in config.yml. */
    DISABLED
}
