package io.github.bobistuca.vertex.core.audit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Recognizes the vanilla and LuckPerms administrative commands that map to audited state. */
public final class CommandParser {
    private CommandParser() { }

    /**
     * @param raw        command line with or without the leading slash
     * @param senderName name of the sender when it is a player (used for self-targeting commands), else null
     */
    public static List<ParsedCommand> parse(String raw, String senderName) {
        List<ParsedCommand> out = new ArrayList<>(1);
        if (raw == null) {
            return out;
        }
        String line = raw.strip();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        String[] a = line.split("\\s+");
        if (a.length == 0 || a[0].isEmpty()) {
            return out;
        }
        String label = a[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        switch (label) {
            case "op" -> {
                if (a.length >= 2) out.add(target(AuditAspect.OP, a[1], "true"));
            }
            case "deop" -> {
                if (a.length >= 2) out.add(target(AuditAspect.OP, a[1], "false"));
            }
            case "gamemode" -> {
                if (a.length >= 2) {
                    String mode = normalizeMode(a[1]);
                    String who = a.length >= 3 ? a[2] : senderName;
                    if (who != null) out.add(target(AuditAspect.GAME_MODE, who, mode));
                }
            }
            case "tag" -> {
                if (a.length >= 4 && (a[2].equalsIgnoreCase("add") || a[2].equalsIgnoreCase("remove"))) {
                    String sign = a[2].equalsIgnoreCase("add") ? "+" : "-";
                    out.add(target(AuditAspect.TAG, a[1], sign + a[3]));
                }
            }
            case "team" -> {
                if (a.length >= 3 && a[1].equalsIgnoreCase("join")) {
                    if (a.length >= 4) {
                        for (int i = 3; i < a.length; i++) out.add(target(AuditAspect.TEAM, a[i], "join:" + a[2]));
                    } else if (senderName != null) {
                        out.add(target(AuditAspect.TEAM, senderName, "join:" + a[2]));
                    }
                } else if (a.length >= 3 && a[1].equalsIgnoreCase("leave")) {
                    for (int i = 2; i < a.length; i++) out.add(target(AuditAspect.TEAM, a[i], "leave"));
                } else if (a.length >= 4 && a[1].equalsIgnoreCase("modify")
                        && (a[3].equalsIgnoreCase("prefix") || a[3].equalsIgnoreCase("suffix"))) {
                    out.add(new ParsedCommand(AuditAspect.TEAM, null, true, "modify:" + a[2]));
                }
            }
            case "lp", "luckperms" -> parseLuckPerms(a, out);
            default -> { }
        }
        return out;
    }

    private static void parseLuckPerms(String[] a, List<ParsedCommand> out) {
        if (a.length >= 5 && a[1].equalsIgnoreCase("user")) {
            String sub = a[3].toLowerCase(Locale.ROOT);
            if (sub.equals("parent")) {
                out.add(target(AuditAspect.PERMISSION_GROUP, a[2], null));
            } else if (sub.equals("permission") || sub.equals("perm")) {
                out.add(target(AuditAspect.PERMISSION_NODE, a[2], null));
            }
        }
    }

    private static ParsedCommand target(AuditAspect aspect, String who, String desired) {
        boolean selector = who.startsWith("@");
        return new ParsedCommand(aspect, selector ? null : who, selector, desired);
    }

    /** Maps the accepted vanilla spellings to an upper-case mode name; unknown text is returned upper-cased. */
    public static String normalizeMode(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "survival", "s", "0" -> "SURVIVAL";
            case "creative", "c", "1" -> "CREATIVE";
            case "adventure", "a", "2" -> "ADVENTURE";
            case "spectator", "sp", "3" -> "SPECTATOR";
            default -> text.toUpperCase(Locale.ROOT);
        };
    }
}
