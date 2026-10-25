package io.github.bobistuca.vertex.core.staff;

import java.util.Optional;

/**
 * Result of the staff evaluation.
 *
 * @param group     the configured administrative group the player belongs to (recognition; independent of exemption)
 * @param exemption set only when the player really bypasses the gameplay checks
 * @param note      explanation of an ignored grant, empty otherwise
 */
public record StaffDecision(Optional<String> group, Optional<Exemption> exemption, String note) { }
