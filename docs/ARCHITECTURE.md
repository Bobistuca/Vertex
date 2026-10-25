# Architecture

```
core/     Server-independent logic. No Bukkit, Spigot or Paper classes. Fully unit tested.
  config/     typed, validated settings; safe reload holder
  i18n/       locale catalogs, MiniMessage -> neutral Rich text, message factory, input sanitizing
  checks/     protocol validator, click analyzer, packet-rate tracker, movement analyzer, reach evaluator,
              grace state, suspicion buffers
  engine/     single-threaded analysis engine that turns immutable observations into detections
  aggregate/  per player+check aggregation window, duplicate guard, rate limits, alert pipeline
  audit/      baselines, command correlation, attempt / change / no-change classification
  client/     join-time client information collector (brand parser, provenance)
  recipient/  alert recipient rules and command access rules
  storage/    bounded evidence history and personal preferences
  staff/      configured staff groups, exemption registry and evaluator
  platform/   capability record, check catalogue, messenger / metrics interfaces
plugin/   Bukkit-facing code, packaged (shaded) into Vertex.jar
  packet/     PacketEvents lifecycle (load / init / terminate) and the observe-only packet listener
  runtime/    composition root, scheduler tasks, snapshots, dispatcher, audit coordinator, platform detection
  listener/   Bukkit events (MONITOR priority, never cancel anything)
  command/    /vertex
  messaging/  Spigot (BungeeCord chat) and legacy-text messengers
  integration/ optional LuckPerms group auditing
paper/    Paper-only adapter (native Adventure, TPS/MSPT, client brand). Compiled against the Paper API,
          embedded as .class.bin resources and loaded reflectively only when Paper is detected.
```

## Threads

| Thread | Work |
|---|---|
| Network (Netty) | `VertexPacketListener` copies a few primitives into an immutable `Observation` and offers it to a bounded queue. It never touches Bukkit, never cancels or edits a packet, and swallows its own failures. |
| `Vertex-Analysis` | Runs `AnalysisEngine` (all packet-driven checks). Reads immutable `PlayerSnapshot` values and volatile `GraceState` timestamps; produces `Detection` records. |
| Server thread | Snapshots (every 2 ticks), event handlers, reach check, alert delivery, audit reconciliation, commands. |
| `Vertex-Storage` | Appends evidence to disk through a bounded queue; compaction enforces entry / byte / age limits. |

Interval measurements use `System.nanoTime()`; evidence records carry ordinary wall-clock timestamps.

## Why the Paper adapter is embedded

PacketEvents needs Adventure, and Spigot/CraftBukkit have none, so Adventure is bundled and relocated to
`io.github.bobistuca.vertex.libs.kyori`. On Paper the *server's* Adventure must be used to send native components, so
the Paper adapter must not be relocated. Its classes are stored as `embedded/paper/**.class.bin` (shading ignores
them) and `EmbeddedClassLoader` defines them on demand, with the plugin class loader as parent. Capability detection
refers to server classes by names assembled at run time so that shading cannot rewrite them.
