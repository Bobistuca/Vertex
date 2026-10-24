package io.github.bobistuca.vertex.runtime;

import io.github.bobistuca.vertex.core.aggregate.AlertPipeline;
import io.github.bobistuca.vertex.core.audit.AdminAuditor;
import io.github.bobistuca.vertex.core.checks.Finding;
import io.github.bobistuca.vertex.core.checks.GraceState;
import io.github.bobistuca.vertex.core.checks.ReachEvaluator;
import io.github.bobistuca.vertex.core.client.ClientInfoCollector;
import io.github.bobistuca.vertex.core.client.ClientReport;
import io.github.bobistuca.vertex.core.client.Provenance;
import io.github.bobistuca.vertex.core.config.ConfigException;
import io.github.bobistuca.vertex.core.config.Settings;
import io.github.bobistuca.vertex.core.config.SettingsHolder;
import io.github.bobistuca.vertex.core.config.SettingsLoader;
import io.github.bobistuca.vertex.core.engine.AnalysisEngine;
import io.github.bobistuca.vertex.core.engine.Observation;
import io.github.bobistuca.vertex.core.i18n.MessageCatalog;
import io.github.bobistuca.vertex.core.i18n.MessageFactory;
import io.github.bobistuca.vertex.core.i18n.Rich;
import io.github.bobistuca.vertex.core.platform.Capabilities;
import io.github.bobistuca.vertex.core.platform.PlatformAdapters;
import io.github.bobistuca.vertex.core.platform.ServerMetrics;
import io.github.bobistuca.vertex.core.storage.EvidenceStore;
import io.github.bobistuca.vertex.core.storage.PreferenceStore;
import io.github.bobistuca.vertex.integration.LuckPermsBridge;
import io.github.bobistuca.vertex.listener.BukkitListeners;
import io.github.bobistuca.vertex.packet.PacketBridge;
import io.github.bobistuca.vertex.packet.PacketCounters;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Composition root: builds, wires, starts and stops every Vertex component. */
public final class VertexRuntime {
    private static final String[] BUNDLED_LOCALES = {"pt_BR", "en_US"};

    private final JavaPlugin plugin;
    private final Logger log;
    private final Path dataDir;
    private final PacketBridge packets;
    private final PacketCounters counters = new PacketCounters();
    private final TickRateMonitor tickMonitor = new TickRateMonitor();
    private final Map<UUID, ClientReport> lastClient = new LinkedHashMap<>();

    private SettingsHolder settings;
    private volatile MessageFactory messages;
    private EvidenceStore store;
    private PreferenceStore preferences;
    private ExecutorService preferenceExecutor;
    private AlertPipeline pipeline;
    private AnalysisEngine engine;
    private AnalysisThread analysis;
    private PlayerTracker tracker;
    private PlatformDetector.Detected platform;
    private ClientInfoCollector clientInfo;
    private AdminAuditor auditor;
    private AuditCoordinator audit;
    private AlertDispatcher dispatcher;
    private LuckPermsBridge luckPerms;
    private BukkitTask snapshotTask;
    private BukkitTask maintenanceTask;
    private int maintenanceCounter;
    private boolean running;

    public VertexRuntime(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.dataDir = plugin.getDataFolder().toPath();
        this.packets = new PacketBridge(plugin);
    }

    // ------------------------------------------------------------------ lifecycle

    public void load() {
        packets.load();
    }

    public void enable() {
        plugin.saveDefaultConfig();
        for (String locale : BUNDLED_LOCALES) {
            Path target = dataDir.resolve("lang").resolve(locale + ".yml");
            if (!Files.exists(target)) {
                plugin.saveResource("lang/" + locale + ".yml", false);
            }
        }
        settings = new SettingsHolder(readSettingsOrDefaults());
        messages = buildMessages(settings.get(), new ArrayList<>());

        store = new EvidenceStore(dataDir.resolve("data").resolve("evidence.log"), () -> settings.get().storage());
        store.load(System.currentTimeMillis());
        preferenceExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Vertex-Preferences");
            t.setDaemon(true);
            return t;
        });
        preferences = new PreferenceStore(dataDir.resolve("data").resolve("preferences.txt"), preferenceExecutor);
        preferences.load();

        pipeline = new AlertPipeline(() -> settings.get(), store, System::nanoTime);
        tracker = new PlayerTracker(() -> settings.get(), new PlayerTracker.TickValues() {
            @Override
            public double tps() {
                return metrics().tps();
            }

            @Override
            public int ping(Player player) {
                return packets.ping(player);
            }
        });
        engine = new AnalysisEngine(() -> settings.get(), tracker::context, pipeline::submit, System::currentTimeMillis);
        analysis = new AnalysisThread(engine, 20_000);
        analysis.start();

        clientInfo = new ClientInfoCollector(settings.get().clientInfo().timeoutMillis(),
                settings.get().clientInfo().maxChannels());
        packets.start(analysis::submit, counters,
                (id, payload, nanos) -> clientInfo.brandPayload(id, payload, nanos));
        platform = new PlatformDetector(plugin.getClass().getClassLoader(), log)
                .detect(tickMonitor, packets.state() == PacketBridge.State.RUNNING);

        auditor = new AdminAuditor(() -> settings.get().audit());
        dispatcher = new AlertDispatcher(plugin, () -> messages, () -> settings.get(), preferences, pipeline,
                () -> platform.adapters().messenger(), tracker);
        audit = new AuditCoordinator(plugin, auditor, () -> settings.get(), () -> messages, pipeline, dispatcher);

        Bukkit.getPluginManager().registerEvents(new BukkitListeners(this), plugin);
        startLuckPerms();

        long nanos = System.nanoTime();
        for (Player p : Bukkit.getOnlinePlayers()) {
            tracker.join(p, nanos - 60_000_000_000L); // already online: the join grace has long passed
        }
        audit.baselineAll(nanos, System.currentTimeMillis());

        snapshotTask = Bukkit.getScheduler().runTaskTimer(plugin, this::snapshotTick, 1L, 2L);
        maintenanceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::maintenanceTick, 5L, 5L);
        running = true;
        logStartup();
    }

    public void disable() {
        running = false;
        if (snapshotTask != null) snapshotTask.cancel();
        if (maintenanceTask != null) maintenanceTask.cancel();
        if (luckPerms != null) luckPerms.close();
        packets.stop();
        if (analysis != null) analysis.stop();
        if (pipeline != null) {
            pipeline.drainAll(); // records the evidence of windows that were still open
        }
        if (store != null) store.close();
        if (preferenceExecutor != null) {
            preferenceExecutor.shutdown();
            try {
                preferenceExecutor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info(plain("startup.shutdown", Map.of()));
    }

    private void startLuckPerms() {
        if (!LuckPermsBridge.pluginEnabled()) {
            return;
        }
        try {
            luckPerms = new LuckPermsBridge(plugin, (id, groups) -> audit.onGroupsChanged(id, groups));
            if (!luckPerms.start()) {
                luckPerms = null;
            }
        } catch (LinkageError e) {
            luckPerms = null;
        }
    }

    // ------------------------------------------------------------------ periodic work

    private void snapshotTick() {
        long now = System.nanoTime();
        tickMonitor.tick(now);
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                tracker.refresh(p, now);
            } catch (RuntimeException e) {
                // A single player's failure must not stop snapshots for everyone else.
            }
        }
    }

    private void maintenanceTick() {
        long now = System.nanoTime();
        long wall = System.currentTimeMillis();
        pipeline.drainDue().forEach(dispatcher::deliver);
        if (settings.get().alerts().actionBar().enabled()) {
            pipeline.drainLive().forEach(dispatcher::actionBar);
        } else {
            pipeline.drainLive();
        }
        for (UUID id : clientInfo.awaitingBrand()) {
            String brand = platform.adapters().clientBrand().apply(id);
            if (brand != null && !brand.isEmpty()) {
                clientInfo.brandText(id, brand, Provenance.REPORTED);
            }
        }
        if (settings.get().clientInfo().enabled()) {
            for (ClientReport report : clientInfo.poll(now)) {
                lastClient.put(report.playerId(), report);
                if (lastClient.size() > 500) {
                    lastClient.remove(lastClient.keySet().iterator().next());
                }
                pipeline.recordClient(report, wall);
                dispatcher.deliverLines(messages.clientReport(report), true);
            }
        } else {
            clientInfo.poll(now);
        }
        if (++maintenanceCounter % 4 == 0) { // every 20 ticks
            audit.tick(now, wall);
            for (Player p : Bukkit.getOnlinePlayers()) {
                analysis.submit(new Observation.SecondTick(p.getUniqueId(), p.getName(), now, counters.drain(p.getUniqueId())));
            }
        }
    }

    // ------------------------------------------------------------------ events delegated by the listeners

    public void playerJoined(Player player) {
        long now = System.nanoTime();
        tracker.join(player, now);
        audit.baselinePlayer(player);
        if (luckPerms != null) {
            var groups = luckPerms.groupsOf(player.getUniqueId());
            if (groups != null) {
                audit.baselineGroups(player.getUniqueId(), groups);
            }
        }
        clientInfo.begin(player.getUniqueId(), player.getName(), now);
        ClientVersion version = packets.clientVersion(player);
        if (version != null) {
            clientInfo.protocol(player.getUniqueId(), version.getProtocolVersion(), version.getReleaseName());
        }
    }

    public void playerLeft(Player player) {
        UUID id = player.getUniqueId();
        analysis.submit(new Observation.Quit(id, player.getName(), System.nanoTime()));
        pipeline.clearPlayer(id);
        pipeline.forgetRecipient(id);
        tracker.remove(id);
        clientInfo.remove(id);
        audit.forget(id);
        counters.remove(id);
    }

    public void channelRegistered(UUID id, String channel) {
        clientInfo.channel(id, channel);
    }

    /** Reach needs server-side entity state, so it is evaluated on the server thread from the damage event. */
    public void reachSample(Player attacker, org.bukkit.entity.Entity target, double distance, double range) {
        UUID id = attacker.getUniqueId();
        GraceState grace = tracker.grace(id);
        var ctx = tracker.context(id);
        ReachEvaluator evaluator = tracker.reach(id);
        Settings cfg = settings.get();
        if (!cfg.checks().reach().enabled() || grace == null || ctx == null || evaluator == null) {
            return;
        }
        long now = System.nanoTime();
        if (grace.active(now, cfg.grace(), ctx.snapshot().tps()) != null || ctx.snapshot().spectator()) {
            return;
        }
        Finding finding = evaluator.evaluate(distance, range, ctx.snapshot().creative(), ctx.snapshot().pingMillis(),
                target.getType().name()).orElse(null);
        if (finding != null) {
            engine.record(id, attacker.getName(), finding, "reach:" + target.getEntityId() + ":" + (now / 50_000_000L));
        }
    }

    // ------------------------------------------------------------------ configuration

    private Settings readSettingsOrDefaults() {
        try (Reader reader = Files.newBufferedReader(dataDir.resolve("config.yml"), StandardCharsets.UTF_8)) {
            return SettingsLoader.load(SettingsLoader.parseYaml(reader));
        } catch (ConfigException e) {
            log.warning("config.yml is invalid; using built-in defaults: " + e.getMessage());
        } catch (IOException e) {
            log.warning("config.yml could not be read; using built-in defaults.");
        }
        return SettingsLoader.defaults();
    }

    /** Safe reload: the previous configuration and messages stay active when anything fails validation. */
    public SettingsHolder.ReloadResult reload() {
        List<String> problems = new ArrayList<>();
        SettingsHolder.ReloadResult result = settings.reload(() -> {
            try {
                return Files.newBufferedReader(dataDir.resolve("config.yml"), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("config.yml could not be read");
            }
        });
        if (!result.applied()) {
            return result;
        }
        MessageFactory next = buildMessages(settings.get(), problems);
        if (!problems.isEmpty()) {
            return new SettingsHolder.ReloadResult(false, problems);
        }
        messages = next;
        return result;
    }

    private MessageFactory buildMessages(Settings s, List<String> problems) {
        Map<String, String> fallback = loadLocale("en_US", problems, true);
        Map<String, String> selected = s.language().equals("en_US") ? fallback : loadLocale(s.language(), problems, false);
        if (selected == null) {
            selected = fallback;
        }
        return new MessageFactory(new MessageCatalog(selected, fallback, s.appearance()), s.appearance());
    }

    /** Reads plugins/Vertex/lang/&lt;locale&gt;.yml, falling back to the bundled copy. */
    private Map<String, String> loadLocale(String locale, List<String> problems, boolean bundledOnly) {
        Path file = dataDir.resolve("lang").resolve(locale + ".yml");
        if (!bundledOnly && Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                return SettingsLoader.parseFlatMessages(reader);
            } catch (ConfigException | IOException e) {
                problems.add("lang/" + locale + ".yml: " + (e instanceof ConfigException ce ? ce.getMessage() : "unreadable"));
                return null;
            }
        }
        try (InputStream in = plugin.getResource("lang/" + locale + ".yml")) {
            if (in == null) {
                if (!bundledOnly) {
                    problems.add("lang/" + locale + ".yml was not found");
                }
                return bundledOnly ? Map.of() : null;
            }
            return SettingsLoader.parseFlatMessages(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (ConfigException | IOException e) {
            problems.add("bundled lang/" + locale + ".yml is unreadable");
            return bundledOnly ? Map.of() : null;
        }
    }

    // ------------------------------------------------------------------ startup log

    private void logStartup() {
        Capabilities caps = platform.capabilities();
        console(messages.catalog().render("startup.banner"));
        console(messages.catalog().render("startup.platform", Map.of("platform", platform.serverName() == null ? "?" : platform.serverName(),
                "minecraft", platform.minecraftVersion(), "java", System.getProperty("java.version"))));
        console(messages.catalog().render("startup.packets", Map.of("state", packets.state().name().toLowerCase()
                + (packets.failure().isEmpty() ? "" : " (" + packets.failure() + ")"))));
        console(messages.catalog().render("startup.messaging", Map.of("state",
                platform.adapters().messenger().id() + (caps.richMessaging() ? "" : " (hover and action bar unavailable)"))));
        console(messages.catalog().render("startup.metrics", Map.of("state", metrics().source()
                + (Double.isNaN(metrics().tps()) ? " (TPS unknown yet)" : ""))));
        if (!caps.packetObservation()) {
            console(messages.catalog().render("startup.limited", Map.of("what", "packet observation")));
        }
    }

    private void console(Rich rich) {
        Bukkit.getConsoleSender().sendMessage(rich.toLegacyText());
    }

    private String plain(String key, Map<String, String> placeholders) {
        return messages == null ? "Vertex stopped." : messages.catalog().render(key, placeholders).toPlainText();
    }

    // ------------------------------------------------------------------ accessors for commands and listeners

    public JavaPlugin plugin() { return plugin; }
    public Settings settings() { return settings.get(); }
    public MessageFactory messages() { return messages; }
    public PreferenceStore preferences() { return preferences; }
    public AlertDispatcher dispatcher() { return dispatcher; }
    public AlertPipeline pipeline() { return pipeline; }
    public AnalysisEngine engine() { return engine; }
    public AnalysisThread analysis() { return analysis; }
    public EvidenceStore store() { return store; }
    public PlayerTracker tracker() { return tracker; }
    public PacketBridge packets() { return packets; }
    public PlatformDetector.Detected platform() { return platform; }
    public AuditCoordinator audit() { return audit; }
    public AdminAuditor auditor() { return auditor; }
    public ClientReport lastClientReport(UUID id) { return lastClient.get(id); }
    public boolean luckPermsActive() { return luckPerms != null; }
    public boolean running() { return running; }

    public ServerMetrics metrics() {
        return platform == null ? tickMonitor : platform.adapters().metrics();
    }

    public PlatformAdapters adapters() {
        return platform.adapters();
    }
}
