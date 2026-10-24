package io.github.bobistuca.vertex.core;

import static io.github.bobistuca.vertex.core.support.TestSupport.ALICE;
import static io.github.bobistuca.vertex.core.support.TestSupport.BOB;
import static io.github.bobistuca.vertex.core.support.TestSupport.MS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bobistuca.vertex.core.audit.Actor;
import io.github.bobistuca.vertex.core.audit.AdminAuditor;
import io.github.bobistuca.vertex.core.audit.AuditAspect;
import io.github.bobistuca.vertex.core.audit.AuditOutcome;
import io.github.bobistuca.vertex.core.audit.AuditRecord;
import io.github.bobistuca.vertex.core.audit.AuditSeverity;
import io.github.bobistuca.vertex.core.audit.CommandParser;
import io.github.bobistuca.vertex.core.audit.ParsedCommand;
import io.github.bobistuca.vertex.core.config.ConfigException;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.config.SettingsLoader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdminAuditorTest {
    private static final long WALL = 1_700_000_000_000L;
    private static final Actor ADMIN = Actor.player(BOB, "Admin");

    private static Settings.Audit config(Map<String, Object> audit) {
        try {
            return SettingsLoader.load(Map.of("audit", audit)).audit();
        } catch (ConfigException e) {
            throw new IllegalStateException(e.problems().toString());
        }
    }

    private static AdminAuditor auditor() {
        Settings.Audit cfg = config(Map.of());
        return new AdminAuditor(() -> cfg);
    }

    private static List<AuditRecord> changed(List<AuditRecord> in) {
        return in.stream().filter(r -> r.outcome() == AuditOutcome.CHANGED).toList();
    }

    @Test
    void existingOperatorsAreBaselineNotViolations() {
        AdminAuditor a = auditor();
        assertTrue(a.observeOperators(Map.of(ALICE, "Alice", BOB, "Admin"), false, 0, WALL).isEmpty());
        assertTrue(a.observeOperators(Map.of(ALICE, "Alice", BOB, "Admin"), false, 5000 * MS, WALL).isEmpty());
    }

    @Test
    void opCommandAgainstAnExistingOperatorIsAnAttemptThatChangesNothing() {
        AdminAuditor a = auditor();
        a.observeOperators(Map.of(ALICE, "Alice"), false, 0, WALL);

        List<AuditRecord> attempts = a.onCommand(Actor.CONSOLE, "op Alice", 1000 * MS, WALL);
        assertEquals(1, attempts.size());
        assertEquals(AuditOutcome.ATTEMPT, attempts.getFirst().outcome());

        assertTrue(a.observeOperators(Map.of(ALICE, "Alice"), false, 2000 * MS, WALL).isEmpty(),
                "no new privilege escalation for a repeated OP");
        List<AuditRecord> swept = a.sweep(20_000 * MS, WALL);
        assertEquals(1, swept.size());
        assertEquals(AuditOutcome.NO_CHANGE, swept.getFirst().outcome());
        assertEquals(AuditSeverity.INFO, swept.getFirst().severity());
    }

    @Test
    void anActualOpChangeRecordsBeforeAfterAndTheConsoleActor() {
        AdminAuditor a = auditor();
        a.observeOperators(Map.of(), false, 0, WALL);
        a.onCommand(Actor.CONSOLE, "op Alice", 1000 * MS, WALL);

        List<AuditRecord> out = changed(a.observeOperators(Map.of(ALICE, "Alice"), false, 3000 * MS, WALL));
        assertEquals(1, out.size());
        AuditRecord r = out.getFirst();
        assertEquals("false", r.before());
        assertEquals("true", r.after());
        assertEquals(Actor.Kind.CONSOLE, r.actor().kind());
        assertTrue(r.actorEstablished());
        assertEquals(AuditSeverity.INFO, r.severity(), "console is authorized by default");
        assertTrue(a.sweep(30_000 * MS, WALL).isEmpty(), "a consumed attempt must not become a no-change entry");
    }

    @Test
    void aChangeWithoutAnyCorrelatedCommandHasAnUnknownActorAndDoesNotBlameTheSubject() {
        AdminAuditor a = auditor();
        a.observeOperators(Map.of(), false, 0, WALL);
        AuditRecord r = changed(a.observeOperators(Map.of(ALICE, "Alice"), false, 3000 * MS, WALL)).getFirst();
        assertEquals(Actor.Kind.UNKNOWN, r.actor().kind());
        assertFalse(r.actorEstablished());
        assertEquals(AuditSeverity.ATTENTION, r.severity());
        assertEquals(ALICE, r.subjectId());
    }

    @Test
    void anOperatorChangeIsNotAttributedWhenTheCommandWasTooOld() {
        AdminAuditor a = auditor();
        a.observeOperators(Map.of(), false, 0, WALL);
        a.onCommand(Actor.CONSOLE, "op Alice", 1000 * MS, WALL);
        AuditRecord r = changed(a.observeOperators(Map.of(ALICE, "Alice"), false, 60_000 * MS, WALL)).getFirst();
        assertEquals(Actor.Kind.UNKNOWN, r.actor().kind());
    }

    @Test
    void settingTheCurrentGameModeAgainIsNotAChange() {
        AdminAuditor a = auditor();
        a.baseline(AuditAspect.GAME_MODE, ALICE, Set.of("CREATIVE"));
        a.onCommand(ADMIN, "gamemode creative Alice", 1000 * MS, WALL);
        assertTrue(a.observe(AuditAspect.GAME_MODE, ALICE, "Alice", Set.of("CREATIVE"), true, 1100 * MS, WALL).isEmpty());
        assertEquals(AuditOutcome.NO_CHANGE, a.sweep(30_000 * MS, WALL).getFirst().outcome());
    }

    @Test
    void gameModeChangesAreAttributedAndClassifiedByAuthorization() {
        Settings.Audit cfg = config(Map.of("authorized-actors", List.of(BOB.toString())));
        AdminAuditor a = new AdminAuditor(() -> cfg);
        a.baseline(AuditAspect.GAME_MODE, ALICE, Set.of("SURVIVAL"));
        a.onCommand(ADMIN, "gamemode creative Alice", 1000 * MS, WALL);
        AuditRecord authorized = a.observe(AuditAspect.GAME_MODE, ALICE, "Alice", Set.of("CREATIVE"), true, 1100 * MS, WALL).getFirst();
        assertEquals(AuditSeverity.INFO, authorized.severity());
        assertEquals("Admin", authorized.actor().name());

        AdminAuditor strict = new AdminAuditor(() -> config(Map.of()));
        strict.baseline(AuditAspect.GAME_MODE, ALICE, Set.of("SURVIVAL"));
        strict.onCommand(ADMIN, "gamemode creative Alice", 1000 * MS, WALL);
        AuditRecord unauthorized = strict.observe(AuditAspect.GAME_MODE, ALICE, "Alice", Set.of("CREATIVE"), true, 1100 * MS, WALL).getFirst();
        assertEquals(AuditSeverity.ATTENTION, unauthorized.severity());
        assertEquals("SURVIVAL", unauthorized.before());
        assertEquals("CREATIVE", unauthorized.after());
    }

    @Test
    void trustedSubjectsAreNeverFlaggedForGainingPrivileges() {
        Settings.Audit cfg = config(Map.of("trusted-uuids", List.of(ALICE.toString())));
        AdminAuditor a = new AdminAuditor(() -> cfg);
        a.baseline(AuditAspect.GAME_MODE, ALICE, Set.of("SURVIVAL"));
        AuditRecord r = a.observe(AuditAspect.GAME_MODE, ALICE, "Alice", Set.of("SPECTATOR"), true, 0, WALL).getFirst();
        assertEquals(AuditSeverity.INFO, r.severity());
    }

    @Test
    void firstObservationOfAPlayerEstablishesTheBaselineWithoutAnEntry() {
        AdminAuditor a = auditor();
        assertTrue(a.observe(AuditAspect.GAME_MODE, ALICE, "Alice", Set.of("CREATIVE"), false, 0, WALL).isEmpty());
        assertTrue(a.observe(AuditAspect.TAG, ALICE, "Alice", Set.of("staff", "vip"), false, 0, WALL).isEmpty());
        a.forget(ALICE);
        assertFalse(a.hasBaseline(AuditAspect.TAG, ALICE));
    }

    @Test
    void assigningAnExistingTagChangesNothingButANewTagIsRecordedWithBeforeAndAfter() {
        AdminAuditor a = auditor();
        a.baseline(AuditAspect.TAG, ALICE, Set.of("staff"));
        a.onCommand(Actor.CONSOLE, "tag Alice add staff", 1000 * MS, WALL);
        assertTrue(a.observe(AuditAspect.TAG, ALICE, "Alice", Set.of("staff"), false, 2000 * MS, WALL).isEmpty());
        assertEquals(AuditOutcome.NO_CHANGE, a.sweep(30_000 * MS, WALL).getFirst().outcome());

        a.onCommand(Actor.CONSOLE, "tag Alice add builder", 40_000 * MS, WALL);
        List<AuditRecord> out = a.observe(AuditAspect.TAG, ALICE, "Alice", Set.of("staff", "builder"), false, 41_000 * MS, WALL);
        assertEquals(1, out.size());
        assertEquals("builder", out.getFirst().after());
        assertEquals("", out.getFirst().before());
        assertEquals(Actor.Kind.CONSOLE, out.getFirst().actor().kind());
    }

    @Test
    void sensitiveTagsAddedByUnauthorizedActorsNeedAttentionOthersDoNot() {
        Settings.Audit cfg = config(Map.of("sensitive-tags", List.of("admin")));
        AdminAuditor a = new AdminAuditor(() -> cfg);
        a.baseline(AuditAspect.TAG, ALICE, Set.of());
        assertEquals(AuditSeverity.INFO,
                a.observe(AuditAspect.TAG, ALICE, "Alice", Set.of("vip"), false, 0, WALL).getFirst().severity());
        assertEquals(AuditSeverity.ATTENTION,
                a.observe(AuditAspect.TAG, ALICE, "Alice", Set.of("vip", "admin"), false, 1, WALL).getFirst().severity());
    }

    @Test
    void auditCanBeDisabledOrNarrowedByMonitoredSettings() {
        Settings.Audit off = config(Map.of("enabled", false));
        AdminAuditor a = new AdminAuditor(() -> off);
        assertTrue(a.onCommand(Actor.CONSOLE, "op Alice", 0, WALL).isEmpty());
        Settings.Audit noGameMode = config(Map.of("monitored", Map.of("game-mode", false)));
        AdminAuditor b = new AdminAuditor(() -> noGameMode);
        b.baseline(AuditAspect.GAME_MODE, ALICE, Set.of("SURVIVAL"));
        assertTrue(b.observe(AuditAspect.GAME_MODE, ALICE, "Alice", Set.of("CREATIVE"), true, 0, WALL).isEmpty());
    }

    @Test
    void untrustedTextInNamesAndCommandsIsSanitizedInRecords() {
        AdminAuditor a = auditor();
        a.baseline(AuditAspect.TAG, ALICE, Set.of());
        a.onCommand(Actor.CONSOLE, "tag Alice add x\n[Vertex] forged", 0, WALL);
        AuditRecord r = a.observe(AuditAspect.TAG, ALICE, "Ali\nce§c", Set.of("x"), false, 1, WALL).getFirst();
        assertFalse(r.subjectName().contains("\n") || r.subjectName().contains("§"));
    }

    @Test
    void commandParserRecognizesVanillaAndLuckPermsCommands() {
        assertEquals(new ParsedCommand(AuditAspect.OP, "Steve", false, "true"), CommandParser.parse("/op Steve", null).getFirst());
        assertEquals("false", CommandParser.parse("minecraft:deop Steve", null).getFirst().desired());
        assertEquals("CREATIVE", CommandParser.parse("gamemode c Steve", null).getFirst().desired());
        assertEquals("Self", CommandParser.parse("gamemode 1", "Self").getFirst().target());
        assertTrue(CommandParser.parse("gamemode survival @a", null).getFirst().selector());
        assertEquals("+vip", CommandParser.parse("tag Steve add vip", null).getFirst().desired());
        assertEquals(AuditAspect.PERMISSION_GROUP, CommandParser.parse("lp user Steve parent add admin", null).getFirst().aspect());
        assertTrue(CommandParser.parse("say op Steve", null).isEmpty());
        assertTrue(CommandParser.parse("", null).isEmpty());
        assertTrue(CommandParser.parse(null, null).isEmpty());
    }
}
