package io.github.bobistuca.vertex.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.recipient.CommandAccess;
import io.github.bobistuca.vertex.core.recipient.CommandAccess.Sub;
import io.github.bobistuca.vertex.core.recipient.RecipientPolicy;
import io.github.bobistuca.vertex.core.recipient.RecipientView;
import io.github.bobistuca.vertex.core.support.TestSupport;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class RecipientAccessTest {

    private static RecipientPolicy defaultPolicy() {
        Settings.Recipients r = TestSupport.shippedSettings().alerts().recipients();
        return new RecipientPolicy(r.operators(), r.permission(), r.gameModes());
    }

    @Test
    void defaultConfigurationEnablesCreativeAndExcludesSpectator() {
        assertEquals(Set.of("CREATIVE"), TestSupport.shippedSettings().alerts().recipients().gameModes());
    }

    @Test
    void operatorsReceiveAlertsInAnyGameMode() {
        RecipientPolicy p = defaultPolicy();
        for (String mode : new String[] {"SURVIVAL", "ADVENTURE", "SPECTATOR", "CREATIVE"}) {
            assertTrue(p.shouldReceive(new RecipientView(true, false, mode, false)), mode);
        }
    }

    @Test
    void permissionHoldersReceiveAlertsInAnyGameMode() {
        assertTrue(defaultPolicy().shouldReceive(new RecipientView(false, true, "SURVIVAL", false)));
    }

    @Test
    void creativePlayersReceiveAlertsButOrdinarySurvivalAndAdventurePlayersDoNot() {
        RecipientPolicy p = defaultPolicy();
        assertTrue(p.shouldReceive(new RecipientView(false, false, "CREATIVE", false)));
        assertFalse(p.shouldReceive(new RecipientView(false, false, "SURVIVAL", false)));
        assertFalse(p.shouldReceive(new RecipientView(false, false, "ADVENTURE", false)));
        assertFalse(p.shouldReceive(new RecipientView(false, false, "SPECTATOR", false)));
    }

    @Test
    void spectatorCanBeAddedThroughConfiguration() {
        RecipientPolicy p = new RecipientPolicy(true, true, Set.of("creative", "spectator"));
        assertTrue(p.shouldReceive(new RecipientView(false, false, "SPECTATOR", false)));
    }

    @Test
    void personalToggleOverridesEligibility() {
        assertFalse(defaultPolicy().shouldReceive(new RecipientView(true, true, "CREATIVE", true)));
        assertTrue(defaultPolicy().isEligible(new RecipientView(true, true, "CREATIVE", true)));
    }

    @Test
    void recipientRulesCanBeDisabled() {
        RecipientPolicy p = new RecipientPolicy(false, false, Set.of());
        assertFalse(p.shouldReceive(new RecipientView(true, true, "CREATIVE", false)));
    }

    @Test
    void creativeEligibilityNeverGrantsAdministrativeCommands() {
        Predicate<String> none = node -> false;
        boolean eligibleCreative = true;
        assertTrue(CommandAccess.canUse(Sub.HELP, none, eligibleCreative));
        assertTrue(CommandAccess.canUse(Sub.ALERTS, none, eligibleCreative), "personal toggle only");
        assertFalse(CommandAccess.canUse(Sub.INSPECT, none, eligibleCreative));
        assertFalse(CommandAccess.canUse(Sub.HISTORY, none, eligibleCreative));
        assertFalse(CommandAccess.canUse(Sub.STATUS, none, eligibleCreative));
        assertFalse(CommandAccess.canUse(Sub.RELOAD, none, eligibleCreative));
    }

    @Test
    void ordinarySurvivalPlayersCannotUseAnythingBeyondHelp() {
        Predicate<String> none = node -> false;
        assertTrue(CommandAccess.canUse(Sub.HELP, none, false));
        for (Sub sub : new Sub[] {Sub.ALERTS, Sub.INSPECT, Sub.HISTORY, Sub.STATUS, Sub.RELOAD}) {
            assertFalse(CommandAccess.canUse(sub, none, false), sub.name());
        }
    }

    @Test
    void explicitPermissionsUnlockOnlyTheirOwnSubcommand() {
        Predicate<String> historyOnly = "vertex.command.history"::equals;
        assertTrue(CommandAccess.canUse(Sub.HISTORY, historyOnly, false));
        assertFalse(CommandAccess.canUse(Sub.INSPECT, historyOnly, false));
        assertFalse(CommandAccess.canUse(Sub.RELOAD, historyOnly, false));
        Predicate<String> alertsPerm = "vertex.alerts"::equals;
        assertTrue(CommandAccess.canUse(Sub.ALERTS, alertsPerm, false));
        assertFalse(CommandAccess.canUse(Sub.STATUS, alertsPerm, false));
    }
}
