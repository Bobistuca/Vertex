package io.github.bobistuca.vertex.smoke;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CI-only helper. Without any real client it feeds the running Vertex pipeline through reflection, so the
 * aggregation, evidence storage and console logging are exercised on the real server. It never ships.
 */
public final class SmokeHelper extends JavaPlugin {
    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> scenario("SmokeSubject", UUID.fromString("00000000-0000-0000-0000-0000000000aa")), 200L);
        var command = getCommand("smoke");
        if (command != null) {
            command.setExecutor((sender, cmd, label, args) -> {
                scenario("SmokeSubject" + (++runs), UUID.randomUUID());
                sender.sendMessage("smoke scenario started");
                return true;
            });
        }
    }

    private int runs;

    private void scenario(String subjectName, UUID subject) {
        try {
            Plugin vertex = Bukkit.getPluginManager().getPlugin("Vertex");
            if (vertex == null || !vertex.isEnabled()) {
                getLogger().severe("SMOKE_HELPER_FAIL Vertex is not enabled");
                return;
            }
            ClassLoader loader = vertex.getClass().getClassLoader();
            Field runtimeField = vertex.getClass().getDeclaredField("runtime");
            runtimeField.setAccessible(true);
            Object runtime = runtimeField.get(vertex);
            Object pipeline = runtime.getClass().getMethod("pipeline").invoke(runtime);

            Class<?> detection = loader.loadClass("io.github.bobistuca.vertex.core.model.Detection");
            Class<?> kind = loader.loadClass("io.github.bobistuca.vertex.core.model.DetectionKind");
            Class<?> confidence = loader.loadClass("io.github.bobistuca.vertex.core.model.Confidence");
            Constructor<?> ctor = detection.getConstructors()[0];
            Method submit = pipeline.getClass().getMethod("submit", detection);

            Map<String, String> measurements = new LinkedHashMap<>();
            measurements.put("samples", "60");
            measurements.put("meanCps", "10.00");
            long now = System.currentTimeMillis();

            Object a = ctor.newInstance(subject, subjectName, "AutoClickerPattern", enumValue(kind, "SUSPICION"),
                    enumValue(confidence, "HIGH"), "smoke reason", measurements, now, null);
            Object duplicate = ctor.newInstance(subject, subjectName, "ReachDistance", enumValue(kind, "SUSPICION"),
                    enumValue(confidence, "MEDIUM"), "smoke reason", measurements, now, "attack:42");
            submit.invoke(pipeline, a);
            submit.invoke(pipeline, duplicate);
            submit.invoke(pipeline, duplicate); // the same action observed twice must count once
            Bukkit.getScheduler().runTaskLater(this, () -> invokeQuietly(submit, pipeline, a), 10L);
            Bukkit.getScheduler().runTaskLater(this, () -> getLogger().info("SMOKE_HELPER_DONE"), 80L);
            getLogger().info("SMOKE_HELPER_SUBMITTED");
        } catch (ReflectiveOperationException | RuntimeException e) {
            getLogger().severe("SMOKE_HELPER_FAIL " + e);
        }
    }

    private static void invokeQuietly(Method m, Object target, Object arg) {
        try {
            m.invoke(target, arg);
        } catch (ReflectiveOperationException ignored) {
            // The scenario log line reports the failure.
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> type, String name) {
        return Enum.valueOf((Class<Enum>) type, name);
    }
}
