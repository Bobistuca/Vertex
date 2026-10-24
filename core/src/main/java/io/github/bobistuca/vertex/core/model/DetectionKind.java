package io.github.bobistuca.vertex.core.model;

/** Separates established protocol contradictions from statistical suspicions. */
public enum DetectionKind {
    /** A contradiction that the protocol or server-known state establishes (not producible by an unmodified client). */
    ESTABLISHED,
    /** A statistical or heuristic suspicion. It is evidence to review, never proof of a specific cheat. */
    SUSPICION,
    /** An administrative state change record (kept separate from gameplay suspicions). */
    ADMIN
}
