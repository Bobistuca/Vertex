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
- [Who is checked: exemptions, operators and test mode](#who-is-checked-exemptions-operators-and-test-mode)
- [Diagnostics](#diagnostics)
- [What Vertex checks](#what-vertex-checks)
- [Alerts and aggregation](#alerts-and-aggregation)
- [Commands and permissions](#commands-and-permissions)
- [Configuration and localization](#configuration-and-localization)
- [Administrative auditing](#administrative-auditing)
- [ViaVersion and ViaBackwards](#viaversion-and-viabackwards)
- [Client information on join](#client-information-on-join)
- [Limitations and false-positive handling](#limitations-and-false-positive-handling)
- [Building and testing](#building-and-testing)
- [License](#license)

## Installation

1. Use the Java version your server requires: **Java 21** for Minecraft 1.21.x, **Java 25** for 26.x. `Vertex.jar` is
   one file whose own bytecode is Java 21 (class-file major 65, enforced by the `Build` workflow), so the same jar
   loads on both.
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

Two independent matrices: the **server runtime** Vertex runs on, and the **client protocol** of the players that
connect. The packet library observes the connection *after* ViaVersion translated it, so a client older or newer than
the server is seen with the server's packet layout; the client's original version comes from the ViaVersion API.

**Server runtimes (target):** Minecraft Java Edition **1.21.x** and **26.1, 26.2, 26.3** on **CraftBukkit**, **Spigot**
and **Paper**. `plugin.yml` declares `api-version: '1.21'`; one `Vertex.jar` is used for all of them.

**Client protocols (target):** the vanilla protocol of the server version, plus older clients through the optional
ViaVersion/ViaBackwards plugins. Nobody is exempted for using Via.

What is *verified* and what is not is recorded, per exact build and with the date and workflow run, in
[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md). Anything not listed there is **unverified**, even if it is in the
target lists above. Highlights of how compatibility is achieved:

- **Java 21 baseline with isolated newer adapters.** All modules compile with `--release 21`. The Paper API is
  published for Java 25 only, so it is read on the *compile classpath only*; the Paper adapter itself is Java 21
  bytecode and is loaded reflectively only when Paper is detected. The `Build` workflow scans every class in the
  shaded jar (including the embedded dependencies) and fails if any has a class-file major version above 65.
- **API differences between 1.21 and 26.x** (attribute constants that were renamed, `InventoryView` which changed
  from a class to an interface) are reached through cached reflection in one place (`runtime/Compat`), so the same
  compiled call sites work on both.
- **PacketEvents 2.14.0** is embedded and relocated (no second plugin, no conflict with other copies). It handles the
  dedicated attack packet of 26.x and the interact-entity attack of 1.21.x.
- **Rich messages** depend on the server: Paper uses native Adventure, Spigot uses BungeeCord chat components, and
  CraftBukkit has neither, so Vertex falls back to colored legacy text and says so in `/vertex status`.
- If the packet library cannot start on a server, Vertex keeps running, reports `UNSUPPORTED` for every packet check
  in `/vertex diagnose`, and never advertises them as active.

## Who is checked: exemptions, operators and test mode

Monitoring, **receiving alerts** and **being exempt** are three separate things.

| Question | Answer |
|---|---|
| Does receiving alerts exempt a player? | **Never.** Recipients (operators, `vertex.alerts`, Creative, staff groups) are checked like everyone else. A player does not receive alerts about themselves unless `alerts.notify-subject` is on or their test mode is active. |
| Does being an operator exempt a player? | **No.** OP alone never disables detection. The opt-in `staff.operators-exempt: true` (default `false`) exists for servers that want it, and `/vertex diagnose` then reports `OPERATOR` as the reason. |
| Does the permission `vertex.bypass.gameplay` exempt? | Only when granted **by that exact node** (default: nobody). `*` and `vertex.*` also make Bukkit's `hasPermission` return true, so a wildcard-only grant is **ignored** unless `staff.permission-wildcards: true`; diagnostics show `WILDCARD_ONLY` and the note explaining why. |
| Do the LuckPerms staff ranks exempt? | Yes, by documented default: members of `staff.luckperms.groups` (Mod, Mod Plus, Administrator, Administrador, Dono, Owner) are exempt while `staff.luckperms.exempt-groups: true`. Set it to `false` to keep recognizing the ranks (for alerts and audit) while still checking them. |

Groups are resolved through the **official LuckPerms API**: direct and **inherited** groups in the player's **active
contexts**, honouring **temporary** memberships (Vertex never changes stored groups). Entries are real group ids
(exact, case-insensitive; no substring matching) or labels in `staff.luckperms.mappings`, so "Mod Plus" maps to
whichever of `modplus`, `mod_plus`, `mod-plus` exists. Chat prefixes, nicknames, tags and client-supplied data are
never used. Recognition grants no OP, wildcard, Creative or command permission.

An exempt player produces no violations, counters, aggregated alerts or suspect state. When a rank is gained, pending
gameplay alerts and buffers are discarded (stored evidence is kept); when it is removed or expires, monitoring restarts
with fresh samples after `grace.rank-change-ms`. **Administrative auditing** of OP, group, tag, team and game-mode
changes is separate and keeps running for everyone, exempt or not.

### Test mode

`/vertex testmode <player> on|off` (permission `vertex.command.testmode`; the console may use it) forces the checks on
for **that one player**, even if they are an operator, hold a staff rank or the bypass permission:

- it does **not** change OP, ranks, permissions or game mode, and affects nobody else;
- it uses the real pipeline: no violation is ever manufactured, legitimate physics (Creative flight, elytra, vehicles)
  is still respected, and a player who does nothing suspicious produces nothing;
- the player also receives alerts about themselves while it is active, so a tester sees their own results;
- it ends by itself after `test-mode.duration-minutes` (default 15), on logout and on restart, and shows in
  `/vertex diagnose` with the remaining time.

**Testing your own account while keeping OP/staff** (Survival mode, so Creative flight is not what you test):

1. Stay as you are (OP, rank, permissions unchanged). From the console or as an operator: `/vertex testmode <you> on`.
2. Switch your own game mode to Survival (`/gamemode survival`); test cheats are not meaningful in Creative.
3. Enable the module in your client, use it for at least 30–60 seconds (the speed check needs about a second of
   consecutive movement, the click analysis a window of 60 clicks, reach a few attempts at a target out of range).
4. Run `/vertex diagnose <you>`. Each check shows `ACTIVE`, or the exact missing input (`INSUFFICIENT_DATA`), or why it
   is not evaluating. Alerts appear in chat/console as `⚠ [Vertex] <you> • <Check> • 2x`.
5. `/vertex testmode <you> off` when finished, or let it expire.

If a test shows nothing, read the diagnose output before concluding anything: a check that is `EXEMPT`, in
`GRACE_PERIOD` or `INSUFFICIENT_DATA` is not "clean", it is not evaluating.

## Diagnostics

`/vertex diagnose <player>` (permission `vertex.command.diagnose`) is private, read-only and bounded (counters and
cached values, no packet log). It shows:

- build id, server implementation and Minecraft version, the server protocol, the client's **original** protocol
  (from ViaVersion when present) next to what the packet library sees, packet library status and Via status;
- OP, game mode, resolved LuckPerms groups, the bypass-permission grant and the note explaining it, the **exact
  exemption reason** (or `none`), test-mode state and the active grace period;
- recent packet counts by type, the time since the last packet and observations dropped by the bounded queue;
- reach counters (attempts queued, unresolved targets, evaluations, buffer);
- pending aggregation windows and the number of eligible alert recipients;
- one line per check with its state and counters: `ACTIVE`, `INSUFFICIENT_DATA` (with the missing input and
  `samples/needed`), `EXEMPT`, `GRACE_PERIOD`, `UNSUPPORTED`, `ERROR` (with the last error) or `DISABLED`.

A missing input disables only the check that needs it, and says so. `/vertex status` and `/vertex inspect` show the
same exemption reason and dependency status.

## What Vertex checks

Only checks that are implemented and operating are listed; `/vertex status` and `/vertex diagnose` show which ones are
active on your server. The full description of each one — what it observes, what counts as evidence, its exemptions
and its limits — is in [docs/CHECKS.md](docs/CHECKS.md).

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
| `MovementSustainedSpeed` | suspicion | **Bunny-hop signature:** average speed over many packets above what a sprint-jump chain can sustain |
| `MovementAscent` | suspicion | Upward movement beyond jump/step/jump-boost allowance |
| `MovementHover` | suspicion | Standing still in mid-air with nothing below and no server-known effect |
| `ReachDistance` | suspicion | Repeated **attack attempts** (applied or not) from beyond the server-known interaction range plus derived latency uncertainty, measured eye-to-hitbox |

*Established* means the packet or server-known state contradicts what an unmodified client can produce. *Suspicion*
is statistical and needs several samples, buffers and decays; it is never proof of a specific cheat.
See [docs/CONFIDENCE.md](docs/CONFIDENCE.md) for what the confidence labels mean (they are **not** probabilities).

Vertex does **not** detect by client name: Meteor Client, or any other client, is only a regression target for the
generic checks above. A "Blast"-style module, or any module that only changes client-side state, is detected only if it
produces one of the observable contradictions listed here; Vertex has no detector named after it.

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
receive nothing unless separately authorized. The subject of an alert does not receive it unless `alerts.notify-subject` is on or their test mode is active. Every
recipient can switch alerts off with `/vertex alerts`.

Incidents are grouped by **player UUID and check id** inside a fixed window (default 2 s). Two qualifying incidents
produce one message containing `2x`, three produce `3x`, and so on. Chat messages cannot be edited after they are
sent, so the message is sent when the window closes; an optional action-bar counter (`alerts.action-bar.enabled`)
updates live while a window is open. The same underlying action observed by both a packet listener and an event
listener counts once. Totals stay exact in the evidence history even when the chat message is rate limited.

## Commands and permissions

`/vertex` (alias `/vx`). Portuguese subcommand names also work (`alertas`, `inspecionar`, `diagnosticar`, `teste`, `historico`, `recarregar`).

| Command | Permission | Purpose |
|---|---|---|
| `/vertex help` | none | Lists the commands you may use |
| `/vertex alerts` | `vertex.command.alerts`, `vertex.alerts`, or being an eligible recipient | Toggle your personal alerts |
| `/vertex inspect <player>` | `vertex.command.inspect` | Detection state, latency, grace, exemption reason, client information |
| `/vertex diagnose <player>` | `vertex.command.diagnose` | Everything Vertex sees and why each check is or is not evaluating |
| `/vertex testmode <player> on\|off` | `vertex.command.testmode` | Force the checks on for one player for a limited time (console allowed) |
| `/vertex history [player] [n]` | `vertex.command.history` | Recent evidence |
| `/vertex status` | `vertex.command.status` | Health, active checks, platform capabilities, dependency status |
| `/vertex reload` | `vertex.command.reload` | Validate and reload the configuration |

Also: `vertex.alerts` (receive alerts), `vertex.bypass.gameplay` (explicit exemption, default `false`), `vertex.command.*` and `vertex.*`. All default to operators.
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

## ViaVersion and ViaBackwards

Both are optional, never bundled and never required. When ViaVersion is enabled, Vertex uses its official API to learn
the **original client protocol** (it can arrive a moment after the connection starts, so diagnostics may show it as
unknown right after join) and shows it in `/vertex diagnose` and the join report.

- **Where observation happens:** the packet library sees the connection after translation, i.e. in the *server's*
  packet layout. Movement, attack and click packets are therefore analysed with the server's rules — the speed
  attribute, interaction range and hitboxes are the server's, which are what the server actually applies. Attacks are
  observed once, by the packet path; the Bukkit damage event is only a fallback when the packet library is not
  running, so nothing is counted twice.
- Via users are **not** exempt and not flagged for using Via. A translated client can show slightly different packet
  timing (translation batches and reorders some packets); windows contaminated by batching are skipped rather than
  judged.
- Mechanics that differ between client versions (for example attack-cooldown behaviour on old clients) are not
  modelled per client version; the checks use server-known limits plus tolerances.

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
- Exempt players (see *Who is checked*) are not checked at all; Creative flight and Spectator are not accused for
  having those abilities; operators are **not** exempt unless `staff.operators-exempt` is enabled.
- **No universal detection is claimed.** A cheat that stays inside vanilla limits (for example a bunny-hop that is no
  faster than a legitimate sprint-jump chain, or clicking as irregularly as a human) cannot be told apart from
  legitimate input by a server-side observer. Reach hidden inside the latency allowance is not flagged. Features that
  only act on the client (visuals, client-side automation that produces ordinary packets) are invisible.
- Latency, TPS and MSPT: Paper exposes TPS/MSPT; elsewhere Vertex reports a scheduler-derived tick rate (labelled as
  such) and MSPT as unknown. Unavailable values are shown as unknown, never invented.
- Packet batching and jitter can hide or mimic patterns; contaminated windows are skipped.
- Movement packets are sampled from the network thread while world state is sampled on the server thread every two
  ticks, so very short transitions are covered by grace periods rather than exact modelling.
- Live-client verification is limited to what [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md) records: it used a
  protocol library (mineflayer), never the official client or Meteor Client, and says so for every result.

## Building

```bash
./gradlew build      # compiles every module and produces plugin/build/libs/Vertex.jar
```

Requirements: JDK 25 for the toolchain (it compiles with `--release 21`). The Gradle wrapper is included (Gradle 9.8.2, Kotlin DSL). The repository contains no test
sources, fixtures or captured data. The `Build` workflow compiles the project, packages `Vertex.jar`, verifies that the
jar contains no unrelocated third-party classes, that no class exceeds the Java 21 class-file version, and that
LuckPerms/ViaVersion are not bundled, then publishes the jar as the `Vertex` workflow artifact. The manual `Verify`
workflow generates its protocol-client harness at run time, starts real servers and prints every result in its log;
nothing it creates is committed.

## License

GNU General Public License v3.0 or later — see [LICENSE](LICENSE). The embedded PacketEvents library is GPL-3.0, which
determines the license of the combined jar. Dependency notices: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
Vertex is not affiliated with Mojang, Microsoft, PaperMC, SpigotMC or PacketEvents.
