# Vertex

> A Minecraft anti-cheat for Bukkit, Spigot, and Paper featuring packet analysis, aggregated staff alerts, and administrative auditing, without automatic punishments.

Vertex **observes**. It watches network packets and server state, records evidence, audits administrative changes
and sends **private** alerts to authorized staff. It never bans, kicks, mutes, freezes, teleports, applies
setbacks, cancels events, blocks packets, removes OP/permissions/tags/items/game modes or runs punishment
commands. There is no punishment engine in the code base.

```
⚠ [Vertex] Player • AutoClickerPattern • 2x • Suspicion: high          (en_US)
⚠ [Vertex] Jogador • AutoClickerPattern • 2x • Suspeita: alta           (pt_BR, default)
```

Colors: **Vertex** and highlights in light green `#86EFAC`, separators and accents in dark green `#166534`, player
names, measurements and explanations in white `#FFFFFF`. Labels never rely on color alone: `ℹ` information,
`⚠` suspicion, `✖` established contradiction, `⚙` administrative change. Hovering a chat alert shows the
measurements, the reason, latency, TPS and game mode.

## Contents

- [Installation](#installation)
- [Compatibility](#compatibility)
- [What Vertex checks](#what-vertex-checks)
- [Alerts and aggregation](#alerts-and-aggregation)
- [Commands and permissions](#commands-and-permissions)
- [Configuration and localization](#configuration-and-localization)
- [Administrative auditing](#administrative-auditing)
- [Client information on join](#client-information-on-join)
- [Limitations and false-positive handling](#limitations-and-false-positive-handling)
- [Building and testing](#building-and-testing)
- [License](#license)

## Installation

1. Use **Java 25** (required by Minecraft 26.1 and newer, so every supported server already runs on it).
2. Download `Vertex.jar` from the *Build* workflow artifact of the latest successful run on the
   [Actions tab](https://github.com/Bobistuca/Vertex/actions/workflows/build.yml), or build it yourself
   (`./gradlew build`, output in `plugin/build/libs/Vertex.jar`).
3. Put it in `plugins/` and start the server. **No other plugin is required.**
   [PacketEvents](https://github.com/retrooper/packetevents) is embedded and relocated into the jar, so it cannot
   conflict with other plugins that ship their own copy. LuckPerms is an optional integration.
4. Edit `plugins/Vertex/config.yml`, then run `/vertex reload`.

The plugin file name has no version suffix. The loader-mandatory version field in `plugin.yml` carries a
non-numeric build identifier (`build-<git commit>`), not a release label.

## Compatibility

Target: Minecraft Java Edition **26.1 through 26.3**, on **CraftBukkit**, **Spigot** and **Paper**.

Verified facts (see [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) for sources and the exact tested builds):

- PacketEvents **2.14.0** declares support for 26.1 through 26.3 (2.12.0: 26.1/26.1.1, 2.12.1: 26.1.2,
  2.13.0: 26.2, 2.14.0: 26.3). Its release notes name Spigot but not CraftBukkit, so CraftBukkit was verified at
  runtime instead of assumed: the embedded library loads, initializes and observes packets on the CraftBukkit
  builds in the matrix below.
- API baseline: `spigot-api 26.1-R0.1-SNAPSHOT` (the oldest target); `plugin.yml` uses `api-version: '26.1'`.
  Paper-only code lives in a separate adapter that is loaded reflectively and only when Paper is detected.
- Rich messages depend on the server: Paper uses native Adventure, Spigot uses BungeeCord chat components (hover
  details and action bar), and CraftBukkit has neither, so Vertex falls back to colored legacy text without hover or
  action bar and states this in `/vertex status`.
- If the packet library cannot start on a server, Vertex keeps running, disables the packet-driven checks, says so in
  the log and in `/vertex status`, and never advertises them as active.

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

Exact builds, the reported server version strings and every check are recorded in [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

## What Vertex checks

Only checks that are implemented and operating are listed. `/vertex status` shows which ones are active on your server.

| Check id | Kind | Evidence |
|---|---|---|
| `InvalidNumericValue` | established | Non-finite or out-of-world coordinates, non-finite rotation, pitch outside ±90 in a movement packet |
| `InvalidPacketField` | established | Hotbar slot outside 0–8, impossible container click slot/window id, negative entity id |
| `SelfAttack` | established | Attack on the player's own entity |
| `ImpossibleFlightToggle` | established | Client reports flying while the server never granted flight (with grace for packets in flight) |
| `InventoryWindowMismatch` | established | Container click while the server has no container open for the player (with grace) |
| `PacketRateAnomaly` | suspicion | Movement packets above the expected rate for consecutive seconds, ignoring catch-up bursts after a client stall |
| `PacketFlood` | suspicion | Total inbound packet rate far above normal for consecutive seconds |
| `AutoClickerPattern` | suspicion | Completed windows of attack timing with several independent regularity signals (see below) |
| `MovementSpeed` | suspicion | Horizontal movement per packet beyond the limit derived from the server-known speed attribute / flight speed |
| `MovementAscent` | suspicion | Upward movement beyond jump/step/jump-boost allowance |
| `MovementHover` | suspicion | Standing still in mid-air with nothing below and no server-known effect |
| `ReachDistance` | suspicion | Repeated attacks beyond the server-known interaction range plus latency tolerance |

*Established* means the packet or server-known state contradicts what an unmodified client can produce. *Suspicion*
is statistical and needs several samples, buffers and decays; it is never proof of a specific cheat.
See [docs/CONFIDENCE.md](docs/CONFIDENCE.md) for what the confidence labels mean (they are **not** probabilities).

### Auto-clicker analysis

There is no single CPS threshold. Vertex analyses a completed window of attack intervals (default 60) and looks at
several signals: interval consistency (coefficient of variation, dominant tick interval, entropy), repeated
timing patterns, and sub-tick regularity. At least two independent signal categories must agree and the mean rate
must be at least 8 CPS. Windows contaminated by packet batching, low TPS or high latency jitter are **skipped**, not
judged. Attack and swing packets that describe the same click are merged so a click is counted once, and swings
while breaking blocks are ignored. Each suspicious *completed window* is one incident.

The server only sees packets. It cannot see physical mouse clicks or hotkeys, and it does not claim to.

## Alerts and aggregation

Detections are never broadcast. Recipients are configured in `alerts.recipients`:

- operators (any game mode),
- players with `vertex.alerts`,
- players in configured game modes — **CREATIVE** by default; add `SPECTATOR` if wanted.

Game-mode eligibility only grants access to alerts; it never grants a command. Ordinary Survival/Adventure players
receive nothing unless separately authorized. The subject of an alert never receives their own alert. Every
recipient can switch alerts off with `/vertex alerts`.

Incidents are grouped by **player UUID and check id** inside a fixed window (default 2 s). Two qualifying incidents
produce one message containing `2x`, three produce `3x`, and so on. Chat messages cannot be edited after they are
sent, so the message is sent when the window closes; an optional action-bar counter (`alerts.action-bar.enabled`)
updates live while a window is open. The same underlying action observed by both a packet listener and an event
listener counts once. Totals stay exact in the evidence history even when the chat message is rate limited.

## Commands and permissions

`/vertex` (alias `/vx`). Portuguese subcommand names also work (`alertas`, `inspecionar`, `historico`, `recarregar`).

| Command | Permission | Purpose |
|---|---|---|
| `/vertex help` | none | Lists the commands you may use |
| `/vertex alerts` | `vertex.command.alerts`, `vertex.alerts`, or being an eligible recipient | Toggle your personal alerts |
| `/vertex inspect <player>` | `vertex.command.inspect` | Detection state, latency, grace, client information |
| `/vertex history [player] [n]` | `vertex.command.history` | Recent evidence |
| `/vertex status` | `vertex.command.status` | Health, active checks, platform capabilities, dependency status |
| `/vertex reload` | `vertex.command.reload` | Validate and reload the configuration |

Also: `vertex.alerts` (receive alerts), `vertex.command.*` and `vertex.*`. All default to operators.
Tab completion only offers what you may use.

## Configuration and localization

`plugins/Vertex/config.yml` is documented inline. Highlights: aggregation window, rate limits, recipients, grace
periods, every check's enablement, thresholds, minimum samples, buffers and decay, audit settings, storage limits,
sound and action-bar options, and the three palette colors.

`/vertex reload` validates the whole file first. If anything is invalid it lists the problems and **keeps the last
working configuration**.

Messages live in `plugins/Vertex/lang/pt_BR.yml` (default) and `en_US.yml`, written as MiniMessage templates; set
`language:` to switch. Add your own `<locale>.yml` to extend. Player-controlled values (names, client brands,
channels, commands) are always inserted as **escaped text**, so they cannot inject formatting, hover/click events
or fake log lines. Templates cannot create click events.

Evidence history and personal preferences are stored locally in `plugins/Vertex/data/` with asynchronous, bounded
writes and configurable retention (entries, bytes, days). Vertex has no telemetry and makes no network requests.

## Administrative auditing

Vertex audits observable changes to **OP status, game mode, permission groups (LuckPerms, optional), monitored
permission nodes, scoreboard tags, and teams/prefixes**, and keeps these entries separate from gameplay suspicions.

- It distinguishes **an attempted command**, **a confirmed state change** and **a repeated operation that changes
  nothing**. `op` on someone who already has OP, adding a tag they already have, or setting the current game mode
  again produce no escalation.
- Existing operators, groups, tags and modes are baselined at startup and on join: they are not violations.
- Before/after values are recorded. The actor is attributed **only** when a command observed on the server
  correlates reliably with the change; otherwise the source is reported as *unknown* and the affected player is
  never blamed.
- `audit.authorized-actors`, `audit.trusted-uuids` and `audit.monitored.*` are explicit player-UUID/permission
  based configuration. A GitHub account name has no meaning inside the game.
- Where no change event exists, a bounded round-robin reconciliation checks a few players per second.

A plugin cannot provide tamper-proof protection against someone who controls the server process, its files or the
console.

## Client information on join

After collecting what is available (or after a bounded timeout, 3 s by default) Vertex privately tells recipients the
player's name and UUID, the **reported** client brand, the protocol (reported) and the version name (inferred), the
registered plugin channels, and whether each value is reported, inferred or unknown. All of it is untrusted: a
modified client (Fabric, Forge, NeoForge, Lunar…) is **not** treated as cheating, and a "vanilla" brand does not
prove a clean client. A server cannot inspect a player's computer, JVM memory, loaded libraries or injected
software, so Vertex always reports **injection status: not verifiable from server-side data** and never claims
"injection detected" or "no injection". There is no client-side scanner.

## Limitations and false-positive handling

- Statistical checks are suspicions. They need samples, use buffers and decay, and are paused during bounded grace
  periods after join, teleport, respawn, world change, velocity/knockback/explosions, damage, game-mode and flight
  changes, and while TPS is below `grace.low-tps-threshold`.
- Limits come from server-authoritative state: the movement-speed attribute (already including potion and sprint
  modifiers), ability flight speed, jump boost, levitation, slow falling, vehicles, gliding, riptide,
  climbables/liquids/webs/honey/scaffolding, the entity interaction range attribute and latency.
- Creative flight and Spectator are not accused for having those abilities; operators are not exempt from every
  check — only the exemptions that relate to the actual ability apply.
- Latency, TPS and MSPT: Paper exposes TPS/MSPT; elsewhere Vertex reports a scheduler-derived tick rate (labelled as
  such) and MSPT as unknown. Unavailable values are shown as unknown, never invented.
- Packet batching and jitter can hide or mimic patterns; contaminated windows are skipped.
- Movement packets are sampled from the network thread while world state is sampled on the server thread every two
  ticks, so very short transitions are covered by grace periods rather than exact modelling.
- Minecraft 26.2 and 26.3 have no public protocol test client yet, so the live-packet test (a real client sending
  packets) is limited to 26.1.x; see the matrix above for exactly what was verified.

## Building and testing

```bash
./gradlew build                 # compiles, runs the unit tests, produces plugin/build/libs/Vertex.jar
./gradlew :core:test            # server-independent tests only
```

Requirements: JDK 25. The Gradle wrapper is included (Gradle 9.8.2, Kotlin DSL). Tests cover aggregation (`2x`),
duplicate observations, recipient rules and command access, auditing (baselines, no-ops, unknown actors), client
information (including hostile brands), auto-click timing, movement exemptions and grace, configuration reload,
storage bounds, localization parity and platform adapter selection.

Smoke tests on real servers run in GitHub Actions (`.github/workflows/smoke.yml`, started manually):
CraftBukkit and Spigot are built with Spigot's BuildTools, Paper comes from the PaperMC download API, and a
scripted session checks plugin loading, packet-listener initialization, commands, permissions, messages,
aggregation, reload and clean shutdown. See [docs/TESTING.md](docs/TESTING.md) to reproduce them locally.

## License

GNU General Public License v3.0 or later — see [LICENSE](LICENSE). The embedded PacketEvents library is GPL-3.0, which
determines the license of the combined jar. Dependency notices: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
Vertex is not affiliated with Mojang, Microsoft, PaperMC, SpigotMC or PacketEvents.
