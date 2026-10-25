package io.github.bobistuca.vertex.core.staff;

import io.github.bobistuca.vertex.core.config.Settings;
import java.util.Collection;
import java.util.Optional;

/** Pure decision: does this player bypass the gameplay checks right now? */
public final class StaffEvaluator {
    private StaffEvaluator() { }

    /**
     * @param memberOf group identifiers the player belongs to (inherited, in the active contexts), or {@code null}
     *                 when group data is unavailable (LuckPerms missing or the user is not loaded)
     * @return the exemption, or empty when the player is monitored normally
     */
    public static Optional<Exemption> evaluate(Settings.Staff config, StaffGroups groups, boolean hasBypassPermission,
                                               Collection<String> memberOf) {
        if (!config.exemptFromGameplayChecks()) {
            return Optional.empty();
        }
        if (config.luckPerms()) {
            Optional<String> group = groups.match(memberOf);
            if (group.isPresent()) {
                return Optional.of(new Exemption(Exemption.Source.LUCKPERMS_GROUP, group.get()));
            }
        }
        if (hasBypassPermission) {
            return Optional.of(new Exemption(Exemption.Source.PERMISSION, Exemption.BYPASS_PERMISSION));
        }
        return Optional.empty();
    }
}
