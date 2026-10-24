package io.github.bobistuca.vertex.core.audit;

/**
 * A recognized administrative command.
 *
 * @param target   target player name, or {@code null} when it is a selector or cannot be resolved
 * @param selector true when the target is a selector such as {@code @a}
 * @param desired  the requested value when it can be known, otherwise empty
 */
public record ParsedCommand(AuditAspect aspect, String target, boolean selector, String desired) { }
