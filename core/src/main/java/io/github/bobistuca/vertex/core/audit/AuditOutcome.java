package io.github.bobistuca.vertex.core.audit;

/** The three situations that must never be conflated. */
public enum AuditOutcome {
    /** A command was seen. It says nothing yet about whether state changed. */
    ATTEMPT,
    /** Observable state differs from the previous known value. */
    CHANGED,
    /** The operation was attempted but the state already had the requested value. */
    NO_CHANGE
}
