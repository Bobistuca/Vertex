package io.github.bobistuca.vertex.core.audit;

/** Administrative state that Vertex audits. */
public enum AuditAspect {
    OP("Op"),
    GAME_MODE("GameMode"),
    PERMISSION_GROUP("PermissionGroup"),
    PERMISSION_NODE("PermissionNode"),
    TAG("ScoreboardTag"),
    TEAM("Team");

    private final String checkId;

    AuditAspect(String checkId) {
        this.checkId = checkId;
    }

    /** Stable English identifier used in evidence records, for example {@code Admin.Op}. */
    public String checkId() {
        return "Admin." + checkId;
    }
}
