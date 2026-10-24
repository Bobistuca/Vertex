# Confidence labels and detection kinds

Vertex separates **what it can establish** from **what it merely suspects**. Alerts always show which one applies.

## Detection kinds

| Kind | Label (en_US) | Symbol | Meaning |
|---|---|---|---|
| `ESTABLISHED` | Established contradiction | `✖` | The packet or server-known state contradicts what an unmodified client can produce: a non-finite coordinate, a hotbar slot outside 0–8, a self-attack, flying without server-granted flight, a container click while the server has no container open. These are protocol facts, so a single occurrence is reported. They do **not** identify a specific tool. |
| `SUSPICION` | Suspicion | `⚠` | A statistical or heuristic result (auto-click regularity, packet rate, movement beyond server-known limits, reach). Several independent samples are required, and an explainable reason and the measurements are attached. A suspicion is evidence to review, never proof of a specific cheat. |
| `ADMIN` | Administrative change | `⚙` | An audit entry about OP status, game mode, groups, tags or teams. Kept separate from gameplay suspicions. |

## Confidence labels (suspicions only)

The label says **how many independent signals agreed and by what margin**. It is **not** a calibrated probability.

| Label | Meaning |
|---|---|
| low | The minimum criteria were met with little margin (for example one signal category when the configuration allows one). |
| medium | At least two independent signal categories agreed, or a buffered measurement crossed its threshold. |
| high | Three signal categories agreed, or a measurement exceeded its limit by a large factor (for example more than twice the allowed movement per packet, or more than 1.5 blocks beyond the allowed reach). |

Established contradictions show `protocol` instead of a level, because the question is not statistical.

## What a server cannot see

The server only observes network packets and server-side state. It cannot see physical mouse clicks, hotkeys,
the player's computer, JVM memory, loaded libraries or injected software. A client brand is a string chosen by the
client: a Fabric/Forge/NeoForge/Lunar brand is not evidence of cheating and a "vanilla" brand is not evidence of a
clean client. Vertex therefore reports **injection status: not verifiable from server-side data**, always.
