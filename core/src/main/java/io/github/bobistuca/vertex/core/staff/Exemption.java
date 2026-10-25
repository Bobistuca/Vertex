package io.github.bobistuca.vertex.core.staff;

/** Why a player bypasses the gameplay checks. */
public record Exemption(Source source, String detail) {
    public enum Source { LUCKPERMS_GROUP, PERMISSION, OPERATOR }

    public static final String BYPASS_PERMISSION = "vertex.bypass.gameplay";

    /** Human-readable reason for diagnostics. */
    public String describe() {
        return switch (source) {
            case LUCKPERMS_GROUP -> "LuckPerms group '" + detail + "' (staff.luckperms.exempt-groups)";
            case PERMISSION -> "permission " + detail;
            case OPERATOR -> "operator status (staff.operators-exempt)";
        };
    }
}
