package io.github.bobistuca.vertex.core.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/** Personal alert preferences: the set of players who switched their alerts off. Persisted asynchronously. */
public final class PreferenceStore {
    private static final int MAX_ENTRIES = 50_000;

    private final Path file;
    private final ExecutorService executor;
    private final Set<UUID> optedOut = ConcurrentHashMap.newKeySet();

    public PreferenceStore(Path file, ExecutorService executor) {
        this.file = file;
        this.executor = executor;
    }

    public void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (optedOut.size() >= MAX_ENTRIES) {
                    break;
                }
                try {
                    optedOut.add(UUID.fromString(line.trim()));
                } catch (IllegalArgumentException ignored) {
                    // Skip corrupt lines.
                }
            }
        } catch (IOException ignored) {
            // Preferences are optional; defaults apply.
        }
    }

    public boolean isOptedOut(UUID id) {
        return optedOut.contains(id);
    }

    /** Sets the preference and persists it in the background. Returns the new opted-out state. */
    public boolean setOptedOut(UUID id, boolean value) {
        if (value) {
            if (optedOut.size() < MAX_ENTRIES) {
                optedOut.add(id);
            }
        } else {
            optedOut.remove(id);
        }
        List<String> snapshot = new ArrayList<>();
        optedOut.forEach(u -> snapshot.add(u.toString()));
        try {
            executor.execute(() -> save(snapshot));
        } catch (RuntimeException ignored) {
            // Shutting down; the change stays in memory.
        }
        return value;
    }

    /** Toggles and returns whether the player is now opted out. */
    public boolean toggle(UUID id) {
        return setOptedOut(id, !isOptedOut(id));
    }

    private void save(List<String> lines) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Best effort.
        }
    }
}
