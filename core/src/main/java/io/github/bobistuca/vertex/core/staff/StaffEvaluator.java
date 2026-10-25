package io.github.bobistuca.vertex.core.staff;

import io.github.bobistuca.vertex.core.config.Settings;
import java.util.Optional;

/**
 * Pure decision: does this player bypass the gameplay checks, and why? Nothing here is implicit: operators are
 * exempt only with {@code staff.operators-exempt}, groups only with {@code staff.luckperms.exempt-groups}, and a
 * wildcard-only grant of the bypass permission only with {@code staff.permission-wildcards}.
 */
public final class StaffEvaluator {
    private StaffEvaluator() { }

    public static StaffDecision evaluate(Settings.Staff config, StaffGroups groups, StaffFacts facts) {
        Optional<String> group = config.luckPerms() ? groups.match(facts.memberOf()) : Optional.empty();
        String note = "";
        Optional<Exemption> exemption = Optional.empty();
        if (config.luckPermsExempt() && group.isPresent()) {
            exemption = Optional.of(new Exemption(Exemption.Source.LUCKPERMS_GROUP, group.get()));
        } else if (config.bypassPermission() && facts.permission() == PermissionGrant.EXPLICIT) {
            exemption = Optional.of(new Exemption(Exemption.Source.PERMISSION, Exemption.BYPASS_PERMISSION));
        } else if (config.bypassPermission() && facts.permission() == PermissionGrant.WILDCARD_ONLY) {
            if (config.wildcardBypass()) {
                exemption = Optional.of(new Exemption(Exemption.Source.PERMISSION, Exemption.BYPASS_PERMISSION));
            } else {
                note = "bypass permission is only granted through a wildcard and is ignored (staff.permission-wildcards: false)";
            }
        }
        if (exemption.isEmpty() && config.operatorsExempt() && facts.operator()) {
            exemption = Optional.of(new Exemption(Exemption.Source.OPERATOR, "op"));
        }
        return new StaffDecision(group, exemption, note);
    }
}
