package io.github.bobistuca.vertex.core.audit;

import java.util.UUID;

/**
 * One audit entry. Audit entries are kept separate from gameplay suspicions.
 *
 * @param actorEstablished whether the actor was established by an API or a reliable command correlation
 * @param command          the correlated command text (sanitized) or empty
 */
public record AuditRecord(AuditAspect aspect, AuditOutcome outcome, UUID subjectId, String subjectName,
                          String before, String after, Actor actor, boolean actorEstablished,
                          AuditSeverity severity, String command, long wallClockMillis) { }
