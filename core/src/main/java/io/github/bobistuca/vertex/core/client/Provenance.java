package io.github.bobistuca.vertex.core.client;

/** How trustworthy the origin of a client value is. */
public enum Provenance {
    /** The client or the network layer stated it (still untrusted). */
    REPORTED,
    /** Derived by Vertex from another reported value. */
    INFERRED,
    /** Not available. */
    UNKNOWN
}
