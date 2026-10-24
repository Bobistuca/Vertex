package io.github.bobistuca.vertex.core.platform;

import io.github.bobistuca.vertex.core.checks.ClickAnalyzer;
import io.github.bobistuca.vertex.core.checks.MovementAnalyzer;
import io.github.bobistuca.vertex.core.checks.PacketRateTracker;
import io.github.bobistuca.vertex.core.checks.ProtocolValidator;
import io.github.bobistuca.vertex.core.checks.ReachEvaluator;
import io.github.bobistuca.vertex.core.config.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Lists every implemented check and whether it is really operating. Nothing is advertised that is not active. */
public final class CheckCatalog {

    /** One entry of the status output. */
    public record Entry(String id, boolean active, String inactiveReason) { }

    private record Def(String id, boolean needsPackets, boolean needsEvents, Predicate<Settings.Checks> enabled) { }

    private static final List<Def> DEFS = List.of(
            new Def(ProtocolValidator.INVALID_NUMERIC, true, false, c -> c.invalidValues().enabled()),
            new Def(ProtocolValidator.INVALID_FIELD, true, false, c -> c.invalidFields().enabled()),
            new Def(ProtocolValidator.SELF_ATTACK, true, false, c -> c.invalidFields().enabled()),
            new Def(ProtocolValidator.FLIGHT_TOGGLE, true, false, c -> c.flightToggle().enabled()),
            new Def(ProtocolValidator.WINDOW_MISMATCH, true, true, c -> c.inventory().enabled()),
            new Def(PacketRateTracker.MOVEMENT_CHECK_ID, true, false, c -> c.packetRate().enabled()),
            new Def(PacketRateTracker.FLOOD_CHECK_ID, true, false, c -> c.packetRate().enabled()),
            new Def(ClickAnalyzer.CHECK_ID, true, false, c -> c.autoClicker().enabled()),
            new Def(MovementAnalyzer.SPEED_ID, true, true, c -> c.movement().enabled()),
            new Def(MovementAnalyzer.ASCENT_ID, true, true, c -> c.movement().enabled()),
            new Def(MovementAnalyzer.HOVER_ID, true, true, c -> c.movement().enabled()),
            new Def(ReachEvaluator.CHECK_ID, false, true, c -> c.reach().enabled()));

    private CheckCatalog() { }

    public static List<String> allIds() {
        return DEFS.stream().map(Def::id).toList();
    }

    public static List<Entry> status(Settings settings, Capabilities caps) {
        List<Entry> out = new ArrayList<>();
        for (Def d : DEFS) {
            if (!d.enabled().test(settings.checks())) {
                out.add(new Entry(d.id(), false, "disabled in config.yml"));
            } else if (d.needsPackets() && !caps.packetObservation()) {
                out.add(new Entry(d.id(), false, "packet observation unavailable"));
            } else if (d.needsEvents() && !caps.eventObservation()) {
                out.add(new Entry(d.id(), false, "server events unavailable"));
            } else {
                out.add(new Entry(d.id(), true, ""));
            }
        }
        return out;
    }
}
