package io.github.bobistuca.vertex.core.storage;

import io.github.bobistuca.vertex.core.config.Settings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Bounded local evidence history. Writes happen on a dedicated single worker thread through a bounded queue;
 * a full queue drops the disk write (counted) but never blocks the caller. Memory views are bounded per player and
 * globally. File size, entry count and age are bounded by retention settings.
 */
public final class EvidenceStore implements AutoCloseable {
    private final Path file;
    private final Supplier<Settings.Storage> config;
    private final ThreadPoolExecutor writer;
    private final Object memoryLock = new Object();
    private final Deque<EvidenceEntry> global = new ArrayDeque<>();
    private final Map<UUID, Deque<EvidenceEntry>> perPlayer = new HashMap<>();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong corrupt = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private long fileBytes;
    private long fileLines;

    public EvidenceStore(Path file, Supplier<Settings.Storage> config) {
        this.file = file;
        this.config = config;
        this.writer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(config.get().queueCapacity()), r -> {
                    Thread t = new Thread(r, "Vertex-Storage");
                    t.setDaemon(true);
                    return t;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** Loads and prunes the stored history. Call once at startup, before use. */
    public void load(long nowMillis) {
        Settings.Storage cfg = config.get();
        List<EvidenceEntry> kept = new ArrayList<>();
        if (Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    EvidenceCodec.decode(line).ifPresentOrElse(kept::add, corrupt::incrementAndGet);
                }
            } catch (IOException | RuntimeException e) {
                failures.incrementAndGet();
            }
        }
        long cutoff = nowMillis - Duration.ofDays(cfg.retentionDays()).toMillis();
        kept.removeIf(e -> e.wallClockMillis() < cutoff);
        if (kept.size() > cfg.maxEntries()) {
            kept = new ArrayList<>(kept.subList(kept.size() - cfg.maxEntries(), kept.size()));
        }
        synchronized (memoryLock) {
            for (EvidenceEntry e : kept) {
                remember(e, cfg);
            }
        }
        rewrite(kept);
    }

    /** Records an entry: memory immediately, disk asynchronously. */
    public void add(EvidenceEntry entry) {
        Settings.Storage cfg = config.get();
        synchronized (memoryLock) {
            remember(entry, cfg);
        }
        try {
            writer.execute(() -> append(entry));
        } catch (RejectedExecutionException e) {
            dropped.incrementAndGet();
        }
    }

    private void remember(EvidenceEntry entry, Settings.Storage cfg) {
        global.addLast(entry);
        while (global.size() > cfg.memoryGlobal()) {
            global.removeFirst();
        }
        if (entry.playerId() != null) {
            Deque<EvidenceEntry> list = perPlayer.computeIfAbsent(entry.playerId(), k -> new ArrayDeque<>());
            list.addLast(entry);
            while (list.size() > cfg.memoryPerPlayer()) {
                list.removeFirst();
            }
            if (perPlayer.size() > 2000) {
                perPlayer.keySet().stream().findFirst().ifPresent(perPlayer::remove);
            }
        }
    }

    /** Most recent entries first. {@code playerId == null} returns the global history. */
    public List<EvidenceEntry> recent(UUID playerId, int limit) {
        synchronized (memoryLock) {
            Deque<EvidenceEntry> source = playerId == null ? global : perPlayer.getOrDefault(playerId, new ArrayDeque<>());
            List<EvidenceEntry> out = new ArrayList<>(Math.min(limit, source.size()));
            var it = source.descendingIterator();
            while (it.hasNext() && out.size() < limit) {
                out.add(it.next());
            }
            return out;
        }
    }

    private void append(EvidenceEntry entry) {
        try {
            Files.createDirectories(file.getParent());
            String line = EvidenceCodec.encode(entry) + "\n";
            byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
            Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            fileBytes += bytes.length;
            fileLines++;
            written.incrementAndGet();
            Settings.Storage cfg = config.get();
            if (fileBytes > cfg.maxBytes() || fileLines > (long) (cfg.maxEntries() * 1.25)) {
                compact(cfg);
            }
        } catch (IOException | RuntimeException e) {
            failures.incrementAndGet();
        }
    }

    private void compact(Settings.Storage cfg) {
        List<EvidenceEntry> kept = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                EvidenceCodec.decode(line).ifPresent(kept::add);
            }
        } catch (IOException e) {
            failures.incrementAndGet();
            return;
        }
        long cutoff = System.currentTimeMillis() - Duration.ofDays(cfg.retentionDays()).toMillis();
        kept.removeIf(e -> e.wallClockMillis() < cutoff);
        int keep = cfg.maxEntries();
        while (kept.size() > keep || estimate(kept) > cfg.maxBytes() * 0.8) {
            if (kept.isEmpty()) {
                break;
            }
            kept.remove(0);
        }
        rewrite(kept);
    }

    private static long estimate(List<EvidenceEntry> list) {
        long total = 0;
        for (EvidenceEntry e : list) {
            total += EvidenceCodec.encode(e).length() + 1L;
        }
        return total;
    }

    private void rewrite(List<EvidenceEntry> entries) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            StringBuilder sb = new StringBuilder();
            for (EvidenceEntry e : entries) {
                sb.append(EvidenceCodec.encode(e)).append('\n');
            }
            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            Files.write(tmp, bytes);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            fileBytes = bytes.length;
            fileLines = entries.size();
        } catch (IOException | RuntimeException e) {
            failures.incrementAndGet();
        }
    }

    public long dropped() {
        return dropped.get();
    }

    public long written() {
        return written.get();
    }

    public long failures() {
        return failures.get();
    }

    public long corruptLines() {
        return corrupt.get();
    }

    public int queued() {
        return writer.getQueue().size();
    }

    /** Waits for pending writes, then stops the worker. */
    @Override
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException e) {
            writer.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
