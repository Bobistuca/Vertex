package io.github.bobistuca.vertex.core.recipient;

import java.util.function.Predicate;

/**
 * Permission rules for {@code /vertex} subcommands. Administrative subcommands require explicit permissions
 * (operators have them by default through plugin.yml). Alert eligibility by game mode never unlocks them.
 */
public final class CommandAccess {

    /** Subcommands and their permission nodes. */
    public enum Sub {
        HELP(null),
        ALERTS("vertex.command.alerts"),
        INSPECT("vertex.command.inspect"),
        DIAGNOSE("vertex.command.diagnose"),
        TESTMODE("vertex.command.testmode"),
        HISTORY("vertex.command.history"),
        STATUS("vertex.command.status"),
        RELOAD("vertex.command.reload");

        private final String permission;

        Sub(String permission) {
            this.permission = permission;
        }

        public String permission() {
            return permission;
        }
    }

    private CommandAccess() { }

    /**
     * @param sub            requested subcommand
     * @param hasPermission  tests a permission node for the sender
     * @param eligibleForAlerts whether the sender currently qualifies as an alert recipient; this only unlocks the
     *                       personal alert toggle, nothing else
     */
    public static boolean canUse(Sub sub, Predicate<String> hasPermission, boolean eligibleForAlerts) {
        return switch (sub) {
            case HELP -> true;
            case ALERTS -> hasPermission.test(sub.permission()) || hasPermission.test("vertex.alerts") || eligibleForAlerts;
            default -> hasPermission.test(sub.permission());
        };
    }
}
