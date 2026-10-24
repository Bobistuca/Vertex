# Compatibility and verification record

Generated from the `smoke-results` branch written by the Smoke workflow.

| Server | 26.1 | 26.2 | 26.3 |
|---|---|---|---|
| CraftBukkit | verified on 26.1.2 — BuildTools 201; Spigot build 4632; live client packets observed | verified on 26.2 — BuildTools 201; Spigot build 4648 | verified on 26.3 — BuildTools 201; Spigot build 4663 |
| Spigot | verified on 26.1.2 — BuildTools 201; Spigot build 4632; live client packets observed | verified on 26.2 — BuildTools 201; Spigot build 4648 | verified on 26.3 — BuildTools 201; Spigot build 4663 |
| Paper | verified on 26.1.2 — Paper 74 (STABLE); live client packets observed | verified on 26.2 — Paper 134 (STABLE) | verified on 26.3 — Paper 222 (STABLE) |

*verified* = the startup and functionality smoke test passed on that exact server build (Temurin Java 
25.0.4 on the Linux CI runner): plugin load, packet-listener initialization, commands, localized messages, 
aggregation, safe reload, administrative auditing, evidence persistence and clean shutdown. Minecraft 26.1 
was tested with the 26.1.2 builds; 26.1 and 26.1.1 share the same Spigot/CraftBukkit build but were not 
tested separately, and Paper 26.1/26.1.1 were not tested. Any combination or patch not listed is 
**unverified**. A real protocol client (permissions, alert recipients, a malformed packet reaching the 
listener) was only available for 26.1.2; on 26.2 and 26.3 the packet listener is verified to initialize 
and run, but no client traffic was observed. Not covered by the smoke tests: the live action-bar counter, 
alert sounds, tab completion, the LuckPerms integration and non-Linux hosts.

Tested commit: `06bdc6eca3e7b5afe69da97024bd6a0ac3b65b23` (Smoke workflow run 40138612225, 2026-10-24). The code that ships in `Vertex.jar` is unchanged by later documentation-only commits.

## Verified dependency facts

These were checked against the published artifacts and official endpoints (October 2026), not assumed:

| Item | Finding | Source |
|---|---|---|
| Java | Spigot BuildTools lists `javaVersions: [69, 70]` (Java 25/26) for 26.1, 26.2 and 26.3; Paper 26.1+ requires Java 25. Vertex is compiled with `--release 25`. | `https://hub.spigotmc.org/versions/<version>.json`, Paper documentation |
| Spigot API | `org.spigotmc:spigot-api` is published for `26.1`, `26.1.1`, `26.1.2`, `26.2` and `26.3` (`-R0.1-SNAPSHOT`). The pure `org.bukkit:bukkit` artifact stops at `1.20.5`, so the shared baseline is `spigot-api 26.1-R0.1-SNAPSHOT` and every use of Spigot-only API sits behind a runtime check. | `https://hub.spigotmc.org/nexus/content/repositories/snapshots/org/spigotmc/spigot-api/maven-metadata.xml` |
| Paper API | `io.papermc.paper:paper-api` `26.1.2.build.74-stable` is used for the Paper-only adapter. Paper builds exist for 26.1.1, 26.1.2, 26.2 and 26.3. | `https://repo.papermc.io/repository/maven-public/`, `https://fill.papermc.io/v3/projects/paper` |
| PacketEvents | `com.github.retrooper:packetevents-spigot:2.14.0` (CodeMC releases). Release notes: 2.12.0 adds 26.1/26.1.1, 2.12.1 marks 26.1.2 fully supported, 2.13.0 adds 26.2, 2.14.0 adds 26.3. CraftBukkit is not mentioned in the release notes; this is why CraftBukkit is verified at runtime below instead of assumed. | `https://github.com/retrooper/packetevents/releases`, `https://repo.codemc.io/repository/maven-releases/com/github/retrooper/packetevents-spigot/` |
| Lifecycle | `PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin))` and `load()` in `onLoad`, `init()` in `onEnable`, `terminate()` in `onDisable`. Embedded and relocated (`com.github.retrooper.packetevents`, `io.github.retrooper.packetevents`, `net.kyori`). The artifact's `plugin.yml` is not used. | PacketEvents development setup |
| Adventure | PacketEvents 2.14.0 depends on Adventure 4.26.1; Paper 26.1.2 ships Adventure 4.26.1; Spigot and CraftBukkit ship none. Vertex bundles Adventure + MiniMessage 4.26.1 (relocated) for template parsing and renders to Paper's native Adventure, to BungeeCord chat components (Spigot), or to legacy colored text (CraftBukkit). | Maven metadata of the artifacts above |
| Packet types | On 26.1 the protocol has a dedicated attack packet (`PacketType.Play.Client.ATTACK`); older clients through a translation layer still send `INTERACT_ENTITY` with the attack action. Vertex handles both. | PacketEvents 2.14.0 class listing, minecraft-data protocol for 26.1 |
| `api-version` | `'26.1'`, the oldest verified target. Setting it does not establish compatibility by itself; the matrix above does. | Spigot `plugin.yml` handling |

## Per-build records

## CraftBukkit 26.1.2

- Server build: `BuildTools 201; Spigot build 4632`
- Reported by the server: `This server is running CraftBukkit version 4632-Bukkit-690a402 (MC: 26.1.2) (Implementing API version 26.1.2-R0.1-SNAPSHOT)`
- Result: 42 passed, 0 failed, 0 skipped
  - info: `messaging-capability` bukkit-legacy-text | hover=false | actionbar=false
  - info: `metrics-capability` scheduler-derived | tps=unknown | mspt=unknown
  - info: `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

## CraftBukkit 26.2

- Server build: `BuildTools 201; Spigot build 4648`
- Reported by the server: `This server is running CraftBukkit version 4648-Bukkit-96ae8cd (MC: 26.2) (Implementing API version 26.2-R0.1-SNAPSHOT)`
- Result: 32 passed, 0 failed, 1 skipped
  - info: `messaging-capability` bukkit-legacy-text | hover=false | actionbar=false
  - info: `metrics-capability` scheduler-derived | tps=unknown | mspt=unknown
  - skipped: `real-client-packets` no protocol client available for this Minecraft version
  - info: `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

## CraftBukkit 26.3

- Server build: `BuildTools 201; Spigot build 4663`
- Reported by the server: `This server is running CraftBukkit version 4663-Bukkit-c4db4c3 (MC: 26.3) (Implementing API version 26.3-R0.1-SNAPSHOT)`
- Result: 32 passed, 0 failed, 1 skipped
  - info: `messaging-capability` bukkit-legacy-text | hover=false | actionbar=false
  - info: `metrics-capability` scheduler-derived | tps=unknown | mspt=unknown
  - skipped: `real-client-packets` no protocol client available for this Minecraft version
  - info: `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

## Spigot 26.1.2

- Server build: `BuildTools 201; Spigot build 4632`
- Reported by the server: `This server is running Spigot version 4632-Spigot-566f972-690a402 (MC: 26.1.2) (Implementing API version 26.1.2-R0.1-SNAPSHOT)`
- Result: 42 passed, 0 failed, 0 skipped
  - info: `messaging-capability` spigot-bungee-chat | hover=true | actionbar=true
  - info: `metrics-capability` scheduler-derived | tps=unknown | mspt=unknown
  - info: `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

## Spigot 26.2

- Server build: `BuildTools 201; Spigot build 4648`
- Reported by the server: `This server is running Spigot version 4648-Spigot-8db49a2-96ae8cd (MC: 26.2) (Implementing API version 26.2-R0.1-SNAPSHOT)`
- Result: 32 passed, 0 failed, 1 skipped
  - info: `messaging-capability` spigot-bungee-chat | hover=true | actionbar=true
  - info: `metrics-capability` scheduler-derived | tps=unknown | mspt=unknown
  - skipped: `real-client-packets` no protocol client available for this Minecraft version
  - info: `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

## Spigot 26.3

- Server build: `BuildTools 201; Spigot build 4663`
- Reported by the server: `This server is running Spigot version 4663-Spigot-bed90fc-c4db4c3 (MC: 26.3) (Implementing API version 26.3-R0.1-SNAPSHOT)`
- Result: 32 passed, 0 failed, 1 skipped
  - info: `messaging-capability` spigot-bungee-chat | hover=true | actionbar=true
  - info: `metrics-capability` scheduler-derived | tps=unknown | mspt=unknown
  - skipped: `real-client-packets` no protocol client available for this Minecraft version
  - info: `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

## Paper 26.1.2

- Server build: `Paper 74 (STABLE) sha256=1d70b1dab9cf4a6de615209a536f3a45a2186240253c428213ce2188ab95e5f7`
- Reported by the server: `[22:31:48 INFO]: This server is running Paper version 26.1.2-74-ver/26.1.2@e4e17fc (2026-07-06T16:51:09Z) (Implementing API version 26.1.2.build.74-stable)`
- Result: 42 passed, 0 failed, 0 skipped
  - info: `messaging-capability` paper-adventure | hover=true | actionbar=true
  - info: `metrics-capability` server-api | tps=20.0 | mspt=0.4
  - info: `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

## Paper 26.2

- Server build: `Paper 134 (STABLE) sha256=3cce0a4922baf79bd8e1275ee5ff9f073f24675770e09b0d4be9442d513e7a6b`
- Reported by the server: `[22:31:20 INFO]: This server is running Paper version 26.2-134-ver/26.2@3e24be0 (2026-10-14T18:33:55Z) (Implementing API version 26.2.build.134-stable)`
- Result: 32 passed, 0 failed, 1 skipped
  - info: `messaging-capability` paper-adventure | hover=true | actionbar=true
  - info: `metrics-capability` server-api | tps=20.0 | mspt=0.4
  - skipped: `real-client-packets` no protocol client available for this Minecraft version
  - info: `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

## Paper 26.3

- Server build: `Paper 222 (STABLE) sha256=f649771a2190214306fb0cfba22c7b2cbac5df6308b1158309593603ed07d488`
- Reported by the server: `[22:31:18 INFO]: This server is running Paper version 26.3-222-main@e3ba7b1 (2026-10-24T13:18:21Z) (Implementing API version 26.3.build.222-stable)`
- Result: 32 passed, 0 failed, 1 skipped
  - info: `messaging-capability` paper-adventure | hover=true | actionbar=true
  - info: `metrics-capability` server-api | tps=20.0 | mspt=0.4
  - skipped: `real-client-packets` no protocol client available for this Minecraft version
  - info: `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

