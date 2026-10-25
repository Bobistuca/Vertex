package io.github.bobistuca.vertex.core.recipient;

import java.util.Locale;
import java.util.Set;

/**
 * Decides who may receive private alerts. Game-mode eligibility only grants access to alerts; it never grants
 * administrative commands (see {@link CommandAccess}).
 */
public final class RecipientPolicy {
    private final boolean operators;
    private final boolean permission;
    private final boolean staff;
    private final Set<String> gameModes;

    public RecipientPolicy(boolean operators, boolean permission, boolean staff, Set<String> gameModes) {
        this.operators = operators;
        this.permission = permission;
        this.staff = staff;
        this.gameModes = Set.copyOf(gameModes.stream().map(m -> m.toUpperCase(Locale.ROOT)).toList());
    }

    /** Eligibility ignoring the personal toggle. */
    public boolean isEligible(RecipientView view) {
        if (operators && view.operator()) {
            return true;
        }
        if (permission && view.hasAlertPermission()) {
            return true;
        }
        if (staff && view.administrativeStaff()) {
            return true;
        }
        return view.gameMode() != null && gameModes.contains(view.gameMode().toUpperCase(Locale.ROOT));
    }

    /** Eligibility and personal preference. */
    public boolean shouldReceive(RecipientView view) {
        return isEligible(view) && !view.optedOut();
    }

    public Set<String> gameModes() {
        return gameModes;
    }
}
