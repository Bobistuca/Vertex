| Implementation | Minecraft | Server build | Passed | Failed | Skipped |
|---|---|---|---|---|---|
| craftbukkit | 26.1.2 | BuildTools 201; Spigot build 4632 | 42 | 0 | 0 |
| craftbukkit | 26.2 | BuildTools 201; Spigot build 4648 | 32 | 0 | 1 |
| craftbukkit | 26.3 | BuildTools 201; Spigot build 4663 | 32 | 0 | 1 |
| paper | 26.1.2 | Paper 74 (STABLE) sha256=1d70b1dab9cf4a6de615209a536f3a45a2186240253c428213ce2188ab95e5f7 | 42 | 0 | 0 |
| paper | 26.2 | Paper 134 (STABLE) sha256=3cce0a4922baf79bd8e1275ee5ff9f073f24675770e09b0d4be9442d513e7a6b | 32 | 0 | 1 |
| paper | 26.3 | Paper 222 (STABLE) sha256=f649771a2190214306fb0cfba22c7b2cbac5df6308b1158309593603ed07d488 | 32 | 0 | 1 |
| spigot | 26.1.2 | BuildTools 201; Spigot build 4632 | 42 | 0 | 0 |
| spigot | 26.2 | BuildTools 201; Spigot build 4648 | 32 | 0 | 1 |
| spigot | 26.3 | BuildTools 201; Spigot build 4663 | 32 | 0 | 1 |

### craftbukkit 26.1.2

- **pass** `server-start` 32s to reach Done
- **pass** `rcon-connect` 
- **pass** `server-identifies-expected-version` This server is running CraftBukkit version 4632-Bukkit-690a402 (MC: 26.1.2) (Implementing API version 26.1.2-R0.1-SNAPSHOT)
- **pass** `plugin-load` Enabling Vertex found in the log
- **pass** `plugin-enable` startup banner printed
- **pass** `no-load-errors` []
- **pass** `packet-listener-running` running | listener errors=0
- **info** `messaging-capability` bukkit-legacy-text | hover=false | actionbar=false
- **info** `metrics-capability` scheduler-derived | tps=unknown | mspt=unknown
- **pass** `fallback-messenger-selected` expected bukkit-legacy-text; got bukkit-legacy-text | hover=false | actionbar=false
- **pass** `checks-listed` 12 lines mention active
- **pass** `command-help` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-alias-vx` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-inspect-unknown-player` [Vertex] Jogador não encontrado: Nobody

- **pass** `command-history` [Vertex] Histórico • * (0)
Nenhuma evidência registrada.

- **pass** `command-alerts-console-refused` [Vertex] Este comando só pode ser usado por um jogador.

- **pass** `command-unknown-sub` [Vertex] Subcomando desconhecido: nonsense. Use /vertex ajuda.

- **pass** `helper-ran` ['[22:32:21] [Server thread/INFO]: [VertexSmokeHelper] SMOKE_HELPER_SUBMITTED', '[22:32:25] [Server thread/INFO]: [VertexSmokeHelper] SMOKE_HELPER_DONE']
- **pass** `aggregation-2x-single-message` 1 message(s): ['[22:32:23] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `duplicate-observation-counted-once` 1 message(s): ['[22:32:23] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • ReachDistance • 1x • ⚠ Suspeita: média']
- **pass** `alert-uses-identity-format` ['[22:32:23] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `default-config-written` /home/runner/work/_temp/server/plugins/Vertex/config.yml
- **pass** `reload-invalid-refused` [Vertex] Recarga recusada; a última configuração válida continua ativa.
▸ alerts.aggregation-window-ms: must be between 200 and 60000

- **pass** `reload-keeps-working-config` 
- **pass** `reload-valid-applied` [Vertex] Configuration reloaded successfully.

- **pass** `language-switch-en_US` [Vertex] Commands
▸ /vertex alerts - toggle your personal alerts
▸ /vertex inspect [player] - detection and client state
- **pass** `tick-rate-measured` scheduler-derived | tps=20.0 | mspt=50.0
- **pass** `real-client-join` bot joined with client version 26.1.2
- **pass** `permission-ordinary-player-denied-status` CHAT [Vertex] You do not have permission to use this command.
- **pass** `permission-survival-player-denied-alerts-toggle` CHAT [Vertex] You do not have permission to use this command.
- **pass** `creative-does-not-grant-commands` CHAT [Vertex] You do not have permission to use this command.
- **pass** `creative-can-toggle-own-alerts` CHAT [Vertex] Your personal alerts are now disabled.
- **pass** `creative-recipient-receives-alert` CHAT [Vertex] SmokeSubject1 • AutoClickerPattern • 2x • ⚠ Suspicion: high
- **pass** `survival-non-operator-receives-nothing` no alert received
- **pass** `operator-receives-alert-in-survival` CHAT [Vertex] SmokeSubject3 • AutoClickerPattern • 2x • ⚠ Suspicion: high
- **pass** `operator-can-use-status` CHAT [Vertex] Vertex status
- **pass** `real-packet-observed-by-listener` [22:33:25] [Server thread/INFO]: [Vertex] [Vertex] VertexBot • InvalidPacketField • 1x • ✖ Established contradiction: protocol
- **pass** `clean-shutdown` exit code 0
- **pass** `shutdown-message` 
- **pass** `no-vertex-stack-traces` []
- **pass** `evidence-persisted-with-accurate-total` 25 record(s); counts=[2, 2, 2, 2]
- **pass** `audit-op-attempts-recorded` 2 attempt(s)
- **pass** `audit-op-single-change-with-console-actor` [{'severity': 'INFO', 'after': 'true', 'command': 'op AuditTarget', 'actorKind': 'CONSOLE', 'actor': 'CONSOLE', 'actorEstablished': 'true', 'outcome': 'CHANGED'
- **pass** `audit-repeated-op-is-no-change` 1 no-change record(s)
- **info** `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

### craftbukkit 26.2

- **pass** `server-start` 19s to reach Done
- **pass** `rcon-connect` 
- **pass** `server-identifies-expected-version` This server is running CraftBukkit version 4648-Bukkit-96ae8cd (MC: 26.2) (Implementing API version 26.2-R0.1-SNAPSHOT)
- **pass** `plugin-load` Enabling Vertex found in the log
- **pass** `plugin-enable` startup banner printed
- **pass** `no-load-errors` []
- **pass** `packet-listener-running` running | listener errors=0
- **info** `messaging-capability` bukkit-legacy-text | hover=false | actionbar=false
- **info** `metrics-capability` scheduler-derived | tps=unknown | mspt=unknown
- **pass** `fallback-messenger-selected` expected bukkit-legacy-text; got bukkit-legacy-text | hover=false | actionbar=false
- **pass** `checks-listed` 12 lines mention active
- **pass** `command-help` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-alias-vx` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-inspect-unknown-player` [Vertex] Jogador não encontrado: Nobody

- **pass** `command-history` [Vertex] Histórico • * (0)
Nenhuma evidência registrada.

- **pass** `command-alerts-console-refused` [Vertex] Este comando só pode ser usado por um jogador.

- **pass** `command-unknown-sub` [Vertex] Subcomando desconhecido: nonsense. Use /vertex ajuda.

- **pass** `helper-ran` ['[22:31:44] [Server thread/INFO]: [VertexSmokeHelper] SMOKE_HELPER_SUBMITTED', '[22:31:48] [Server thread/INFO]: [VertexSmokeHelper] SMOKE_HELPER_DONE']
- **pass** `aggregation-2x-single-message` 1 message(s): ['[22:31:46] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `duplicate-observation-counted-once` 1 message(s): ['[22:31:46] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • ReachDistance • 1x • ⚠ Suspeita: média']
- **pass** `alert-uses-identity-format` ['[22:31:46] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `default-config-written` /home/runner/work/_temp/server/plugins/Vertex/config.yml
- **pass** `reload-invalid-refused` [Vertex] Recarga recusada; a última configuração válida continua ativa.
▸ alerts.aggregation-window-ms: must be between 200 and 60000

- **pass** `reload-keeps-working-config` 
- **pass** `reload-valid-applied` [Vertex] Configuration reloaded successfully.

- **pass** `language-switch-en_US` [Vertex] Commands
▸ /vertex alerts - toggle your personal alerts
▸ /vertex inspect [player] - detection and client state
- **pass** `tick-rate-measured` scheduler-derived | tps=20.0 | mspt=50.0
- **skipped** `real-client-packets` no protocol client available for this Minecraft version
- **pass** `clean-shutdown` exit code 0
- **pass** `shutdown-message` 
- **pass** `no-vertex-stack-traces` []
- **pass** `evidence-persisted-with-accurate-total` 8 record(s); counts=[2]
- **pass** `audit-op-attempts-recorded` 2 attempt(s)
- **pass** `audit-op-single-change-with-console-actor` [{'actorEstablished': 'true', 'outcome': 'CHANGED', 'before': 'false', 'severity': 'INFO', 'after': 'true', 'command': 'op AuditTarget', 'actorKind': 'CONSOLE',
- **pass** `audit-repeated-op-is-no-change` 1 no-change record(s)
- **info** `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

### craftbukkit 26.3

- **pass** `server-start` 21s to reach Done
- **pass** `rcon-connect` 
- **pass** `server-identifies-expected-version` This server is running CraftBukkit version 4663-Bukkit-c4db4c3 (MC: 26.3) (Implementing API version 26.3-R0.1-SNAPSHOT)
- **pass** `plugin-load` Enabling Vertex found in the log
- **pass** `plugin-enable` startup banner printed
- **pass** `no-load-errors` []
- **pass** `packet-listener-running` running | listener errors=0
- **info** `messaging-capability` bukkit-legacy-text | hover=false | actionbar=false
- **info** `metrics-capability` scheduler-derived | tps=unknown | mspt=unknown
- **pass** `fallback-messenger-selected` expected bukkit-legacy-text; got bukkit-legacy-text | hover=false | actionbar=false
- **pass** `checks-listed` 12 lines mention active
- **pass** `command-help` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-alias-vx` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-inspect-unknown-player` [Vertex] Jogador não encontrado: Nobody

- **pass** `command-history` [Vertex] Histórico • * (0)
Nenhuma evidência registrada.

- **pass** `command-alerts-console-refused` [Vertex] Este comando só pode ser usado por um jogador.

- **pass** `command-unknown-sub` [Vertex] Subcomando desconhecido: nonsense. Use /vertex ajuda.

- **pass** `helper-ran` ['[22:31:40] [Server thread/INFO]: [VertexSmokeHelper] SMOKE_HELPER_SUBMITTED', '[22:31:44] [Server thread/INFO]: [VertexSmokeHelper] SMOKE_HELPER_DONE']
- **pass** `aggregation-2x-single-message` 1 message(s): ['[22:31:42] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `duplicate-observation-counted-once` 1 message(s): ['[22:31:42] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • ReachDistance • 1x • ⚠ Suspeita: média']
- **pass** `alert-uses-identity-format` ['[22:31:42] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `default-config-written` /home/runner/work/_temp/server/plugins/Vertex/config.yml
- **pass** `reload-invalid-refused` [Vertex] Recarga recusada; a última configuração válida continua ativa.
▸ alerts.aggregation-window-ms: must be between 200 and 60000

- **pass** `reload-keeps-working-config` 
- **pass** `reload-valid-applied` [Vertex] Configuration reloaded successfully.

- **pass** `language-switch-en_US` [Vertex] Commands
▸ /vertex alerts - toggle your personal alerts
▸ /vertex inspect [player] - detection and client state
- **pass** `tick-rate-measured` scheduler-derived | tps=20.0 | mspt=50.0
- **skipped** `real-client-packets` no protocol client available for this Minecraft version
- **pass** `clean-shutdown` exit code 0
- **pass** `shutdown-message` 
- **pass** `no-vertex-stack-traces` []
- **pass** `evidence-persisted-with-accurate-total` 8 record(s); counts=[2]
- **pass** `audit-op-attempts-recorded` 2 attempt(s)
- **pass** `audit-op-single-change-with-console-actor` [{'outcome': 'CHANGED', 'actorEstablished': 'true', 'actor': 'CONSOLE', 'actorKind': 'CONSOLE', 'command': 'op AuditTarget', 'after': 'true', 'severity': 'INFO'
- **pass** `audit-repeated-op-is-no-change` 1 no-change record(s)
- **info** `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

### paper 26.1.2

- **pass** `server-start` 46s to reach Done
- **pass** `rcon-connect` 
- **pass** `server-identifies-expected-version` [22:31:48 INFO]: This server is running Paper version 26.1.2-74-ver/26.1.2@e4e17fc (2026-07-06T16:51:09Z) (Implementing API version 26.1.2.build.74-stable)
- **pass** `plugin-load` Enabling Vertex found in the log
- **pass** `plugin-enable` startup banner printed
- **pass** `no-load-errors` []
- **pass** `packet-listener-running` running | listener errors=0
- **info** `messaging-capability` paper-adventure | hover=true | actionbar=true
- **info** `metrics-capability` server-api | tps=20.0 | mspt=0.4
- **pass** `paper-native-adventure-selected` paper-adventure | hover=true | actionbar=true
- **pass** `checks-listed` 12 lines mention active
- **pass** `command-help` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-alias-vx` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-inspect-unknown-player` [Vertex] Jogador não encontrado: Nobody

- **pass** `command-history` [Vertex] Histórico • * (2)
▸ 2026-10-24 22:32:09 • SmokeSubject • ReachDistance • 1x · SUSPICION
▸ 2026-10-24 22:32:09 •
- **pass** `command-alerts-console-refused` [Vertex] Este comando só pode ser usado por um jogador.

- **pass** `command-unknown-sub` [Vertex] Subcomando desconhecido: nonsense. Use /vertex ajuda.

- **pass** `helper-ran` ['[22:32:09 INFO]: [VertexSmokeHelper] SMOKE_HELPER_SUBMITTED', '[22:32:13 INFO]: [VertexSmokeHelper] SMOKE_HELPER_DONE']
- **pass** `aggregation-2x-single-message` 1 message(s): ['[22:32:11 INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `duplicate-observation-counted-once` 1 message(s): ['[22:32:11 INFO]: [Vertex] [Vertex] SmokeSubject • ReachDistance • 1x • ⚠ Suspeita: média']
- **pass** `alert-uses-identity-format` ['[22:32:11 INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `default-config-written` /home/runner/work/_temp/server/plugins/Vertex/config.yml
- **pass** `reload-invalid-refused` [Vertex] Recarga recusada; a última configuração válida continua ativa.
▸ alerts.aggregation-window-ms: must be between 200 and 60000

- **pass** `reload-keeps-working-config` 
- **pass** `reload-valid-applied` [Vertex] Configuration reloaded successfully.

- **pass** `language-switch-en_US` [Vertex] Commands
▸ /vertex alerts - toggle your personal alerts
▸ /vertex inspect [player] - detection and client state
- **pass** `tick-rate-measured` server-api | tps=20.0 | mspt=0.2
- **pass** `real-client-join` bot joined with client version 26.1.2
- **pass** `permission-ordinary-player-denied-status` CHAT [Vertex] You do not have permission to use this command.
- **pass** `permission-survival-player-denied-alerts-toggle` CHAT [Vertex] You do not have permission to use this command.
- **pass** `creative-does-not-grant-commands` CHAT [Vertex] You do not have permission to use this command.
- **pass** `creative-can-toggle-own-alerts` CHAT [Vertex] Your personal alerts are now disabled.
- **pass** `creative-recipient-receives-alert` CHAT [Vertex] SmokeSubject1 • AutoClickerPattern • 2x • ⚠ Suspicion: high
- **pass** `survival-non-operator-receives-nothing` no alert received
- **pass** `operator-receives-alert-in-survival` CHAT [Vertex] SmokeSubject3 • AutoClickerPattern • 2x • ⚠ Suspicion: high
- **pass** `operator-can-use-status` CHAT [Vertex] Vertex status
- **pass** `real-packet-observed-by-listener` [22:33:17 INFO]: [Vertex] [Vertex] VertexBot • InvalidPacketField • 1x • ✖ Established contradiction: protocol
- **pass** `clean-shutdown` exit code 0
- **pass** `shutdown-message` 
- **pass** `no-vertex-stack-traces` []
- **pass** `evidence-persisted-with-accurate-total` 26 record(s); counts=[2, 2, 2, 2]
- **pass** `audit-op-attempts-recorded` 2 attempt(s)
- **pass** `audit-op-single-change-with-console-actor` [{'actor': 'CONSOLE', 'actorKind': 'CONSOLE', 'command': 'op AuditTarget', 'after': 'true', 'severity': 'INFO', 'before': 'false', 'outcome': 'CHANGED', 'actorE
- **pass** `audit-repeated-op-is-no-change` 1 no-change record(s)
- **info** `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

### paper 26.2

- **pass** `server-start` 20s to reach Done
- **pass** `rcon-connect` 
- **pass** `server-identifies-expected-version` [22:31:20 INFO]: This server is running Paper version 26.2-134-ver/26.2@3e24be0 (2026-10-14T18:33:55Z) (Implementing API version 26.2.build.134-stable)
- **pass** `plugin-load` Enabling Vertex found in the log
- **pass** `plugin-enable` startup banner printed
- **pass** `no-load-errors` []
- **pass** `packet-listener-running` running | listener errors=0
- **info** `messaging-capability` paper-adventure | hover=true | actionbar=true
- **info** `metrics-capability` server-api | tps=20.0 | mspt=0.4
- **pass** `paper-native-adventure-selected` paper-adventure | hover=true | actionbar=true
- **pass** `checks-listed` 12 lines mention active
- **pass** `command-help` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-alias-vx` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-inspect-unknown-player` [Vertex] Jogador não encontrado: Nobody

- **pass** `command-history` [Vertex] Histórico • * (2)
▸ 2026-10-24 22:31:37 • SmokeSubject • ReachDistance • 1x · SUSPICION
▸ 2026-10-24 22:31:37 •
- **pass** `command-alerts-console-refused` [Vertex] Este comando só pode ser usado por um jogador.

- **pass** `command-unknown-sub` [Vertex] Subcomando desconhecido: nonsense. Use /vertex ajuda.

- **pass** `helper-ran` ['[22:31:37 INFO]: [VertexSmokeHelper] SMOKE_HELPER_SUBMITTED', '[22:31:41 INFO]: [VertexSmokeHelper] SMOKE_HELPER_DONE']
- **pass** `aggregation-2x-single-message` 1 message(s): ['[22:31:39 INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `duplicate-observation-counted-once` 1 message(s): ['[22:31:39 INFO]: [Vertex] [Vertex] SmokeSubject • ReachDistance • 1x • ⚠ Suspeita: média']
- **pass** `alert-uses-identity-format` ['[22:31:39 INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `default-config-written` /home/runner/work/_temp/server/plugins/Vertex/config.yml
- **pass** `reload-invalid-refused` [Vertex] Recarga recusada; a última configuração válida continua ativa.
▸ alerts.aggregation-window-ms: must be between 200 and 60000

- **pass** `reload-keeps-working-config` 
- **pass** `reload-valid-applied` [Vertex] Configuration reloaded successfully.

- **pass** `language-switch-en_US` [Vertex] Commands
▸ /vertex alerts - toggle your personal alerts
▸ /vertex inspect [player] - detection and client state
- **pass** `tick-rate-measured` server-api | tps=20.0 | mspt=0.2
- **skipped** `real-client-packets` no protocol client available for this Minecraft version
- **pass** `clean-shutdown` exit code 0
- **pass** `shutdown-message` 
- **pass** `no-vertex-stack-traces` []
- **pass** `evidence-persisted-with-accurate-total` 8 record(s); counts=[2]
- **pass** `audit-op-attempts-recorded` 2 attempt(s)
- **pass** `audit-op-single-change-with-console-actor` [{'after': 'true', 'command': 'op AuditTarget', 'actorKind': 'CONSOLE', 'actor': 'CONSOLE', 'actorEstablished': 'true', 'outcome': 'CHANGED', 'before': 'false',
- **pass** `audit-repeated-op-is-no-change` 1 no-change record(s)
- **info** `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

### paper 26.3

- **pass** `server-start` 21s to reach Done
- **pass** `rcon-connect` 
- **pass** `server-identifies-expected-version` [22:31:18 INFO]: This server is running Paper version 26.3-222-main@e3ba7b1 (2026-10-24T13:18:21Z) (Implementing API version 26.3.build.222-stable)
- **pass** `plugin-load` Enabling Vertex found in the log
- **pass** `plugin-enable` startup banner printed
- **pass** `no-load-errors` []
- **pass** `packet-listener-running` running | listener errors=0
- **info** `messaging-capability` paper-adventure | hover=true | actionbar=true
- **info** `metrics-capability` server-api | tps=20.0 | mspt=0.4
- **pass** `paper-native-adventure-selected` paper-adventure | hover=true | actionbar=true
- **pass** `checks-listed` 12 lines mention active
- **pass** `command-help` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-alias-vx` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-inspect-unknown-player` [Vertex] Jogador não encontrado: Nobody

- **pass** `command-history` [Vertex] Histórico • * (2)
▸ 2026-10-24 22:31:34 • SmokeSubject • ReachDistance • 1x · SUSPICION
▸ 2026-10-24 22:31:34 •
- **pass** `command-alerts-console-refused` [Vertex] Este comando só pode ser usado por um jogador.

- **pass** `command-unknown-sub` [Vertex] Subcomando desconhecido: nonsense. Use /vertex ajuda.

- **pass** `helper-ran` ['[22:31:34 INFO]: [VertexSmokeHelper] SMOKE_HELPER_SUBMITTED', '[22:31:38 INFO]: [VertexSmokeHelper] SMOKE_HELPER_DONE']
- **pass** `aggregation-2x-single-message` 1 message(s): ['[22:31:37 INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `duplicate-observation-counted-once` 1 message(s): ['[22:31:37 INFO]: [Vertex] [Vertex] SmokeSubject • ReachDistance • 1x • ⚠ Suspeita: média']
- **pass** `alert-uses-identity-format` ['[22:31:37 INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `default-config-written` /home/runner/work/_temp/server/plugins/Vertex/config.yml
- **pass** `reload-invalid-refused` [Vertex] Recarga recusada; a última configuração válida continua ativa.
▸ alerts.aggregation-window-ms: must be between 200 and 60000

- **pass** `reload-keeps-working-config` 
- **pass** `reload-valid-applied` [Vertex] Configuration reloaded successfully.

- **pass** `language-switch-en_US` [Vertex] Commands
▸ /vertex alerts - toggle your personal alerts
▸ /vertex inspect [player] - detection and client state
- **pass** `tick-rate-measured` server-api | tps=20.0 | mspt=0.2
- **skipped** `real-client-packets` no protocol client available for this Minecraft version
- **pass** `clean-shutdown` exit code 0
- **pass** `shutdown-message` 
- **pass** `no-vertex-stack-traces` []
- **pass** `evidence-persisted-with-accurate-total` 8 record(s); counts=[2]
- **pass** `audit-op-attempts-recorded` 2 attempt(s)
- **pass** `audit-op-single-change-with-console-actor` [{'before': 'false', 'severity': 'INFO', 'after': 'true', 'command': 'op AuditTarget', 'actorKind': 'CONSOLE', 'actor': 'CONSOLE', 'actorEstablished': 'true', '
- **pass** `audit-repeated-op-is-no-change` 1 no-change record(s)
- **info** `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

### spigot 26.1.2

- **pass** `server-start` 25s to reach Done
- **pass** `rcon-connect` 
- **pass** `server-identifies-expected-version` This server is running Spigot version 4632-Spigot-566f972-690a402 (MC: 26.1.2) (Implementing API version 26.1.2-R0.1-SNAPSHOT)
- **pass** `plugin-load` Enabling Vertex found in the log
- **pass** `plugin-enable` startup banner printed
- **pass** `no-load-errors` []
- **pass** `packet-listener-running` running | listener errors=0
- **info** `messaging-capability` spigot-bungee-chat | hover=true | actionbar=true
- **info** `metrics-capability` scheduler-derived | tps=unknown | mspt=unknown
- **pass** `fallback-messenger-selected` expected spigot-bungee-chat; got spigot-bungee-chat | hover=true | actionbar=true
- **pass** `checks-listed` 12 lines mention active
- **pass** `command-help` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-alias-vx` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-inspect-unknown-player` [Vertex] Jogador não encontrado: Nobody

- **pass** `command-history` [Vertex] Histórico • * (0)
Nenhuma evidência registrada.

- **pass** `command-alerts-console-refused` [Vertex] Este comando só pode ser usado por um jogador.

- **pass** `command-unknown-sub` [Vertex] Subcomando desconhecido: nonsense. Use /vertex ajuda.

- **pass** `helper-ran` ['[22:31:49] [Server thread/INFO]: [VertexSmokeHelper] SMOKE_HELPER_SUBMITTED', '[22:31:53] [Server thread/INFO]: [VertexSmokeHelper] SMOKE_HELPER_DONE']
- **pass** `aggregation-2x-single-message` 1 message(s): ['[22:31:51] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `duplicate-observation-counted-once` 1 message(s): ['[22:31:51] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • ReachDistance • 1x • ⚠ Suspeita: média']
- **pass** `alert-uses-identity-format` ['[22:31:51] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `default-config-written` /home/runner/work/_temp/server/plugins/Vertex/config.yml
- **pass** `reload-invalid-refused` [Vertex] Recarga recusada; a última configuração válida continua ativa.
▸ alerts.aggregation-window-ms: must be between 200 and 60000

- **pass** `reload-keeps-working-config` 
- **pass** `reload-valid-applied` [Vertex] Configuration reloaded successfully.

- **pass** `language-switch-en_US` [Vertex] Commands
▸ /vertex alerts - toggle your personal alerts
▸ /vertex inspect [player] - detection and client state
- **pass** `tick-rate-measured` scheduler-derived | tps=20.0 | mspt=50.0
- **pass** `real-client-join` bot joined with client version 26.1.2
- **pass** `permission-ordinary-player-denied-status` CHAT [Vertex] You do not have permission to use this command.
- **pass** `permission-survival-player-denied-alerts-toggle` CHAT [Vertex] You do not have permission to use this command.
- **pass** `creative-does-not-grant-commands` CHAT [Vertex] You do not have permission to use this command.
- **pass** `creative-can-toggle-own-alerts` CHAT [Vertex] Your personal alerts are now disabled.
- **pass** `creative-recipient-receives-alert` CHAT [Vertex] SmokeSubject1 • AutoClickerPattern • 2x • ⚠ Suspicion: high
- **pass** `survival-non-operator-receives-nothing` no alert received
- **pass** `operator-receives-alert-in-survival` CHAT [Vertex] SmokeSubject3 • AutoClickerPattern • 2x • ⚠ Suspicion: high
- **pass** `operator-can-use-status` CHAT [Vertex] Vertex status
- **pass** `real-packet-observed-by-listener` [22:32:54] [Server thread/INFO]: [Vertex] [Vertex] VertexBot • InvalidPacketField • 1x • ✖ Established contradiction: protocol
- **pass** `clean-shutdown` exit code 0
- **pass** `shutdown-message` 
- **pass** `no-vertex-stack-traces` []
- **pass** `evidence-persisted-with-accurate-total` 25 record(s); counts=[2, 2, 2, 2]
- **pass** `audit-op-attempts-recorded` 2 attempt(s)
- **pass** `audit-op-single-change-with-console-actor` [{'actor': 'CONSOLE', 'actorEstablished': 'true', 'outcome': 'CHANGED', 'before': 'false', 'severity': 'INFO', 'after': 'true', 'command': 'op AuditTarget', 'ac
- **pass** `audit-repeated-op-is-no-change` 1 no-change record(s)
- **info** `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

### spigot 26.2

- **pass** `server-start` 24s to reach Done
- **pass** `rcon-connect` 
- **pass** `server-identifies-expected-version` This server is running Spigot version 4648-Spigot-8db49a2-96ae8cd (MC: 26.2) (Implementing API version 26.2-R0.1-SNAPSHOT)
- **pass** `plugin-load` Enabling Vertex found in the log
- **pass** `plugin-enable` startup banner printed
- **pass** `no-load-errors` []
- **pass** `packet-listener-running` running | listener errors=0
- **info** `messaging-capability` spigot-bungee-chat | hover=true | actionbar=true
- **info** `metrics-capability` scheduler-derived | tps=unknown | mspt=unknown
- **pass** `fallback-messenger-selected` expected spigot-bungee-chat; got spigot-bungee-chat | hover=true | actionbar=true
- **pass** `checks-listed` 12 lines mention active
- **pass** `command-help` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-alias-vx` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-inspect-unknown-player` [Vertex] Jogador não encontrado: Nobody

- **pass** `command-history` [Vertex] Histórico • * (0)
Nenhuma evidência registrada.

- **pass** `command-alerts-console-refused` [Vertex] Este comando só pode ser usado por um jogador.

- **pass** `command-unknown-sub` [Vertex] Subcomando desconhecido: nonsense. Use /vertex ajuda.

- **pass** `helper-ran` ['[22:31:44] [Server thread/INFO]: [VertexSmokeHelper] SMOKE_HELPER_SUBMITTED', '[22:31:48] [Server thread/INFO]: [VertexSmokeHelper] SMOKE_HELPER_DONE']
- **pass** `aggregation-2x-single-message` 1 message(s): ['[22:31:46] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `duplicate-observation-counted-once` 1 message(s): ['[22:31:46] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • ReachDistance • 1x • ⚠ Suspeita: média']
- **pass** `alert-uses-identity-format` ['[22:31:46] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `default-config-written` /home/runner/work/_temp/server/plugins/Vertex/config.yml
- **pass** `reload-invalid-refused` [Vertex] Recarga recusada; a última configuração válida continua ativa.
▸ alerts.aggregation-window-ms: must be between 200 and 60000

- **pass** `reload-keeps-working-config` 
- **pass** `reload-valid-applied` [Vertex] Configuration reloaded successfully.

- **pass** `language-switch-en_US` [Vertex] Commands
▸ /vertex alerts - toggle your personal alerts
▸ /vertex inspect [player] - detection and client state
- **pass** `tick-rate-measured` scheduler-derived | tps=20.0 | mspt=50.0
- **skipped** `real-client-packets` no protocol client available for this Minecraft version
- **pass** `clean-shutdown` exit code 0
- **pass** `shutdown-message` 
- **pass** `no-vertex-stack-traces` []
- **pass** `evidence-persisted-with-accurate-total` 8 record(s); counts=[2]
- **pass** `audit-op-attempts-recorded` 2 attempt(s)
- **pass** `audit-op-single-change-with-console-actor` [{'command': 'op AuditTarget', 'after': 'true', 'severity': 'INFO', 'before': 'false', 'outcome': 'CHANGED', 'actorEstablished': 'true', 'actor': 'CONSOLE', 'ac
- **pass** `audit-repeated-op-is-no-change` 1 no-change record(s)
- **info** `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true

### spigot 26.3

- **pass** `server-start` 23s to reach Done
- **pass** `rcon-connect` 
- **pass** `server-identifies-expected-version` This server is running Spigot version 4663-Spigot-bed90fc-c4db4c3 (MC: 26.3) (Implementing API version 26.3-R0.1-SNAPSHOT)
- **pass** `plugin-load` Enabling Vertex found in the log
- **pass** `plugin-enable` startup banner printed
- **pass** `no-load-errors` []
- **pass** `packet-listener-running` running | listener errors=0
- **info** `messaging-capability` spigot-bungee-chat | hover=true | actionbar=true
- **info** `metrics-capability` scheduler-derived | tps=unknown | mspt=unknown
- **pass** `fallback-messenger-selected` expected spigot-bungee-chat; got spigot-bungee-chat | hover=true | actionbar=true
- **pass** `checks-listed` 12 lines mention active
- **pass** `command-help` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-alias-vx` [Vertex] Comandos
▸ /vertex alertas - liga/desliga seus alertas pessoais
▸ /vertex inspecionar [jogador] - estado de det
- **pass** `command-inspect-unknown-player` [Vertex] Jogador não encontrado: Nobody

- **pass** `command-history` [Vertex] Histórico • * (0)
Nenhuma evidência registrada.

- **pass** `command-alerts-console-refused` [Vertex] Este comando só pode ser usado por um jogador.

- **pass** `command-unknown-sub` [Vertex] Subcomando desconhecido: nonsense. Use /vertex ajuda.

- **pass** `helper-ran` ['[22:31:55] [Server thread/INFO]: [VertexSmokeHelper] SMOKE_HELPER_SUBMITTED', '[22:31:59] [Server thread/INFO]: [VertexSmokeHelper] SMOKE_HELPER_DONE']
- **pass** `aggregation-2x-single-message` 1 message(s): ['[22:31:57] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `duplicate-observation-counted-once` 1 message(s): ['[22:31:57] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • ReachDistance • 1x • ⚠ Suspeita: média']
- **pass** `alert-uses-identity-format` ['[22:31:57] [Server thread/INFO]: [Vertex] [Vertex] SmokeSubject • AutoClickerPattern • 2x • ⚠ Suspeita: alta']
- **pass** `default-config-written` /home/runner/work/_temp/server/plugins/Vertex/config.yml
- **pass** `reload-invalid-refused` [Vertex] Recarga recusada; a última configuração válida continua ativa.
▸ alerts.aggregation-window-ms: must be between 200 and 60000

- **pass** `reload-keeps-working-config` 
- **pass** `reload-valid-applied` [Vertex] Configuration reloaded successfully.

- **pass** `language-switch-en_US` [Vertex] Commands
▸ /vertex alerts - toggle your personal alerts
▸ /vertex inspect [player] - detection and client state
- **pass** `tick-rate-measured` scheduler-derived | tps=20.0 | mspt=50.0
- **skipped** `real-client-packets` no protocol client available for this Minecraft version
- **pass** `clean-shutdown` exit code 0
- **pass** `shutdown-message` 
- **pass** `no-vertex-stack-traces` []
- **pass** `evidence-persisted-with-accurate-total` 8 record(s); counts=[2]
- **pass** `audit-op-attempts-recorded` 2 attempt(s)
- **pass** `audit-op-single-change-with-console-actor` [{'before': 'false', 'severity': 'INFO', 'after': 'true', 'command': 'op AuditTarget', 'actorKind': 'CONSOLE', 'actor': 'CONSOLE', 'actorEstablished': 'true', '
- **pass** `audit-repeated-op-is-no-change` 1 no-change record(s)
- **info** `audit-rcon-command-visibility` RCON op: 1 attempt(s) observed; change actor CONSOLE/established=true
