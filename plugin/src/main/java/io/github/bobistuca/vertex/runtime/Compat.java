package io.github.bobistuca.vertex.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

/**
 * Version-tolerant access to Bukkit API that changed shape between Minecraft 1.21 and 26.x. Vertex is compiled
 * against the newest API but must load on 1.21 servers, so anything that was renamed, or that changed from a class
 * to an interface (which breaks compiled call sites), is reached through reflection and cached.
 */
final class Compat {
    private static final Attribute MOVEMENT_SPEED = attribute("MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED");
    private static final Attribute INTERACTION_RANGE = attribute("ENTITY_INTERACTION_RANGE",
            "PLAYER_ENTITY_INTERACTION_RANGE");
    private static final Method VIEW_TYPE = viewTypeMethod();

    private Compat() { }

    private static Attribute attribute(String... names) {
        for (String name : names) {
            try {
                Field field = Attribute.class.getField(name);
                Object value = field.get(null);
                if (value instanceof Attribute attribute) {
                    return attribute;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Try the next historical name.
            }
        }
        return null;
    }

    private static Method viewTypeMethod() {
        try {
            return Class.forName("org.bukkit.inventory.InventoryView").getMethod("getType");
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /** Current movement-speed attribute value (modifiers included), or the vanilla default when unavailable. */
    static double movementSpeed(Player player) {
        return value(player, MOVEMENT_SPEED, 0.1);
    }

    /** Entity interaction range; vanilla default 3.0 on servers that predate the attribute. */
    static double interactionRange(Player player) {
        return value(player, INTERACTION_RANGE, 3.0);
    }

    static boolean hasInteractionRangeAttribute() {
        return INTERACTION_RANGE != null;
    }

    private static double value(Player player, Attribute attribute, double fallback) {
        if (attribute == null) {
            return fallback;
        }
        try {
            AttributeInstance instance = player.getAttribute(attribute);
            return instance == null ? fallback : instance.getValue();
        } catch (RuntimeException | LinkageError e) {
            return fallback;
        }
    }

    /** True when a container other than the player's own inventory is open (InventoryView is a class on 1.21). */
    static boolean containerOpen(Player player) {
        try {
            Object view = player.getOpenInventory();
            if (view == null || VIEW_TYPE == null) {
                return false;
            }
            Object type = VIEW_TYPE.invoke(view);
            return type != InventoryType.CRAFTING && type != InventoryType.CREATIVE && type != InventoryType.PLAYER;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }
}
