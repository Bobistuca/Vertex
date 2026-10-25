package io.github.bobistuca.vertex.core.staff;

/** Why a player bypasses the gameplay checks. */
public record Exemption(Source source, String detail) {
    public enum Source { LUCKPERMS_GROUP, PERMISSION }

    public static final String BYPASS_PERMISSION = "vertex.bypass.gameplay";
}
