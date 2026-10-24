package io.github.bobistuca.vertex.core.audit;

public enum AuditSeverity {
    /** Recorded for review; authorized actor, trusted subject or a non-escalating change. */
    INFO,
    /** An escalating change by an actor that is not authorized, or whose source is unknown. */
    ATTENTION
}
