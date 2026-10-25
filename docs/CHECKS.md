# Checks: what each one observes, what counts as evidence, and where it stops

Vertex is client-agnostic. It never keys on a client name, brand or module list, and it never claims to detect
"Meteor Client" or any other product. A cheat is seen only when its **packets or the resulting server state**
contradict what the server knows. A cheat that stays inside what a legitimate client can produce is not visible to a
server-side observer, and this page says so for each check.

Every check obeys the same rules:

- It reads **server-known state** (speed attribute, interaction range, jump boost, game mode, flight permission,
  open container, latency, TPS), never client-reported claims.
- It pauses during bounded **grace periods** after join, teleport, respawn, world change, velocity, damage, game-mode
  and flight changes, and while TPS is low.
- A **suspicion** needs a buffer to fill and decays when behaviour is normal. An **established** finding needs a
  direct contradiction.
- A player who is exempt (see README, *Who is checked*) produces no state at all; `/vertex diagnose` shows
  `EXEMPT` and the exact reason. `/vertex testmode` removes the exemption for one player for a limited time.
- `/vertex diagnose <player>` lists one of these states per check: `ACTIVE`, `INSUFFICIENT_DATA` (with the missing
  input), `EXEMPT`, `GRACE_PERIOD`, `UNSUPPORTED`, `ERROR`, `DISABLED`.

## Movement

Input: the position packets of the connection (observed after any protocol translation), plus a snapshot of the
player taken on the server thread every two ticks.

| Id | Evidence | Why it is not a false positive | Limits |
|---|---|---|---|
| `MovementSpeed` | Horizontal displacement of one movement packet above `speed attribute × speed-factor + speed-base`. The factor is calibrated on the fastest vanilla ground movement (a sprint-jump chain, about 2.74 blocks/tick per unit of attribute) plus a 25 % margin. | Uses the live attribute, which already includes sprint and potion modifiers; ice, climbing/liquids/webs and a stale sprint flag widen the limit explicitly. | A single spike never accuses (buffer). |
| `MovementSustainedSpeed` | **The bunny-hop signature.** Average horizontal speed over `sustained-packets` moving packets above what a sprint-jump chain can sustain (`sustained-factor`, margin 6 %). One jump can look fast; a chain that keeps being faster than vanilla cannot. | Repeated jumping alone is legitimate and never flagged; only speed beyond the server-known limit is. | A bunny-hop that stays at or below vanilla sprint-jump speed is **indistinguishable** from legitimate play by speed. Speed potions and attribute changes are read from the attribute. |
| `MovementAscent` | Upward movement beyond jump, step-up and jump-boost allowance without a server-known cause. | Levitation, jump boost, climbables, liquids, vehicles, gliding and riptide are excluded. | |
| `MovementHover` | Standing still in mid-air with no block below and no server-known cause. | Slow falling/levitation/support blocks are excluded. | |

Creative/Spectator flight and elytra gliding are legitimate abilities and are respected; flying in Survival without a
granted ability is not.

## Reach

Input: the **attack packet** (the dedicated attack packet on 26.x, the interact-entity attack on 1.21.x). The
target entity is resolved on the server thread and the distance is measured from the attacker's **eye position to
the nearest point of the target's bounding box**.

Evidence: repeated attempts beyond `interaction-range attribute (+ creative bonus) + tolerance + uncertainty`.
The uncertainty is derived, not arbitrary: both parties may have moved by `speed × (half the round-trip time, capped
+ processing delay)` since the client acted. Because the packet is evaluated whether or not the server applied
damage, an attack that the server silently drops for being out of range is still seen.

Limits: a very small extension (a few tenths of a block) hides inside the latency allowance by design; the check
does not model client-side interpolation of the target. Targets that the server cannot resolve are counted in
`/vertex diagnose` ("unresolved target") and never accused.

## AutoClicker

Input: attack packets (and swing packets when `include-air-swings` is enabled), not only damage events.
Evidence: a completed window of intervals showing at least `required-signals` independent regularity signals
(coefficient of variation, dominant tick interval, entropy, repeating period, sub-tick residual) at a mean rate of
at least `min-mean-cps`. Windows contaminated by packet batching, jitter, low TPS or high latency variance are
**skipped**; attack and swing packets for the same click are merged; swings while breaking blocks are ignored.

Limits: input that is as irregular as a human is, or slower than `min-mean-cps`, is not flagged. A server cannot see
a physical mouse.

## Protocol and state contradictions (established)

`InvalidNumericValue`, `InvalidPacketField`, `SelfAttack`, `ImpossibleFlightToggle`, `InventoryWindowMismatch`:
the packet or the claimed state is impossible for a client that follows the protocol, given what the server knows
(non-finite coordinates, a hotbar slot outside 0–8, a container click with no container open, claiming flight
that was never granted, attacking oneself).

## Timing and packet rate

`PacketRateAnomaly` (movement packets above the expected rate for consecutive seconds, ignoring catch-up bursts after
a client stall) and `PacketFlood` (total inbound packet rate far above normal). Both are suspicions and are paused at
low TPS.

## Not covered

Anything that happens only on the client, injected software, "ghost" clients that stay inside vanilla limits,
and cheats whose packets are indistinguishable from legitimate ones. Vertex reports **no injection status** and does
not flag a player because of a client brand.
