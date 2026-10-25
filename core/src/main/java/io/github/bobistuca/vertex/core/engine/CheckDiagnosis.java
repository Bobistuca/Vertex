package io.github.bobistuca.vertex.core.engine;

/**
 * Diagnostic snapshot of one check.
 *
 * @param evaluated number of times the check ran on an observation
 * @param samples   samples collected towards the next evaluation window (0 when not applicable)
 * @param needed    samples a window needs (0 when not applicable)
 * @param incidents incidents generated for this player
 * @param buffer    current suspicion buffer values (text), empty when none
 * @param note      the missing input or the reason for the status, empty when nothing to add
 */
public record CheckDiagnosis(String id, CheckStatus status, long evaluated, int samples, int needed, long incidents,
                             String buffer, String note) { }
