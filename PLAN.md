# MobArmyBattle — Roadmap

Multi-Phase-Spielmodus für Paper-Plugin: Spieler farmen Mobs → bauen Wellen → kämpfen mit den Wellen des Gegners. Vollständige Spec: [`docs/superpowers/specs/2026-05-03-mob-army-battle-design.md`](docs/superpowers/specs/2026-05-03-mob-army-battle-design.md).

---

## Status-Übersicht

| Plan | Thema | Branch | Status |
|---|---|---|---|
| 1 | Skeleton + Domain-Modell + `/mab create/join/leave/start` | `feat/plan-1-skeleton` | ✅ Merged |
| 2 | Welt-Management (Lobby + Farm-Welt mit Seed) | `feat/plan-2-world-management` | ✅ Merged |
| 3 | Mob-Pool + Equipment-Tracking + Kill-Listener | `feat/plan-3-mob-pool` | ✅ Merged |
| 3.5 | Dynamic Teams + maxTeamSize (FFA-Modus) | `feat/plan-3.5-two-teams` | ✅ Merged |
| 4 | Wave-Building (Domain + Inventar-GUI + Forfeit) | `feat/plan-4-wave-build` | ✅ PR #6 offen |
| 5 | Battle-Engine (Pairing + Arena + WaveSpawner + Sieger) | `feat/plan-5-battle` | ✅ Merged |
| 6 | Tournament + Bracket (Solo-Only v1) | — | ✅ implementiert |
| 7 | SQLite + Stats + Leaderboard (v1) | — | ✅ implementiert |
| 8 | Spectator + Sidebar-Scoreboard + UI-Polish | — | ✅ implementiert (Smoke-Test offen) |
| 9 | Config + Permissions + Konfigurierbare Werte | — | ✅ implementiert (Smoke-Test offen) |
| 10 | Battle-Timing + Admin-Commands | — | ✅ implementiert (Smoke-Test offen) |

---

## Plan-Details

### ✅ Plan 1 — Skeleton + Domain
**Ziel:** Plugin-Skeleton + Match/Team/MatchPhase State-Machine + 4 Befehle.
**Geliefert:** `Match`, `Team`, `MatchPhase`-Interface, 5 Phase-Stubs, `MatchManager`, `MabCommand`, Tab-Completion.
**Plan-Doc:** `docs/superpowers/plans/2026-05-03-mob-army-battle-plan-1-skeleton.md`

### ✅ Plan 2 — Welt-Management
**Ziel:** Zentrale Lobby-Welt + Farm-Welten mit gleichem Seed pro Team.
**Geliefert:** `WorldManager`, `LobbyChunkGenerator`, `WorldCleanup`, FarmPhase-Aktivierung, Lobby-Teleport bei Join, Auto-Leave bei Quit, Orphan-Welt-Cleanup.
**Plan-Doc:** `docs/superpowers/plans/2026-05-03-mob-army-battle-plan-2-world-management.md`

### ✅ Plan 3 — Mob-Pool
**Ziel:** Equipment-bewusstes Mob-Tracking pro Team.
**Geliefert:** `MobEntry` (mit Equipment-Signature), `MobPool` (LinkedHashMap-Aggregation, applyPenalty), `EquipmentSerializer` (LivingEntity → "main\|off\|helm\|chest\|legs\|boots"-String), `MobKillListener` (nur Farm-Phase + nur Team-Welt), `PlayerDeathFarmListener` (10% Pool-Penalty), `/mab pool`.
**Plan-Doc:** `docs/superpowers/plans/2026-05-03-mob-army-battle-plan-3-mob-pool.md`

### ✅ Plan 3.5 — Dynamic Teams
**Ziel:** Match unterstützt N Teams mit `maxTeamSize` statt fixer Modi.
**Geliefert:** `Match.maxTeamSize` (default 1 = Solo-FFA), `Team` mit max-size, dynamic team creation in `joinMatch`, auto-balance, `Match.canStart()` verlangt ≥2 aktive Teams. `/mab create N`, `/mab join <captain> [team]`. Beispiele: `/mab create 1` mit 8 Spielern → 8 Solo-Teams.
**Plan-Doc:** `docs/superpowers/plans/2026-05-03-mob-army-battle-plan-3-5-two-teams.md`

### ✅ Plan 4 — Wave-Building
**Ziel:** Inventar-GUI für Captain zum Bauen von 2 Wellen aus Pool.
**Geliefert:** `Wave` (finalisierbar, forfeit-fähig), `WaveSlot`, `WaveBuildGui` (54-Slot-Inventar mit Pool-Übersicht / Tabs / Reset / Forfeit / Confirm / Cancel), `/mab endfarm` für Phase-Übergang. Pool-0-Teams werden auto-disbanded (Lobby-Teleport + Match-Remove). Pool-Sharing über beide Wellen korrekt (3 Mobs total → max 3 verteilt). FinishedPhase räumt Spieler komplett auf. `/mab join` erlaubt Match-Wechsel.
**Plan-Doc:** `docs/superpowers/plans/2026-05-03-mob-army-battle-plan-4-wave-build.md`

### ✅ Plan 5 — Battle-Engine
**Ziel:** Vollständiger Battle-Lifecycle vom Pairing bis zum Sieger.
**Geliefert:**
- `TeamPair` + `TeamPairing` (paart N Teams in 2er-Gruppen, Bye bei ungerade)
- `BattleStats` (waves/kills/time pro Team) + `BattleResult` (hierarchische Sieger-Berechnung)
- `WorldManager.createArenaWorld` (VOID-Welt pro Team)
- `ArenaSpawnScanner` (findet `[MAB_SPAWN]`-Banner)
- `ArenaLoader` (`.nbt` via Paper StructureManager + 30×30 Bedrock-Fallback mit Marker-Bannern)
- `WaveSpawner` (Mobs aus Wave + Equipment-Materialien aus Signature)
- `BattleSession` (Pair-State: Welten, aktuelle Welle, Stats, alive-Mobs)
- `BattleManager` (Orchestriert: pairt, erstellt Welten, teleportiert, spawnt Wellen, watcht Mob-Deaths, ermittelt Sieger)
- `BattleEventListener` (kein Item-/XP-Drop in Arena, KeepInventory, Spectator-Mode bei Tod)
- `BattlePhase` aktiviert
**Plan-Doc:** `docs/superpowers/plans/2026-05-03-mob-army-battle-plan-5-battle-engine.md`

### ✅ Plan 6 — Tournament + Bracket (Solo-Only v1)
**Ziel:** Single-Elimination-Tournaments mit `/mab tournament create/join/start/leave/list`.
**Geliefert:**
- `Tournament` (Aggregate-Root mit Status REGISTERING/RUNNING/FINISHED)
- `TournamentRound` + `TournamentPairing` (Bye bei ungerade)
- `TournamentManager` (Bukkit-aware: Match-Erstellung pro Pairing, Auto-FARM-Transition, Round-Advance mit 15-Sek-Pause)
- `BattleManager.addBattleEndListener` (Hook für Battle-Completion)
- `MabCommand` Tournament-Subcommands + Tab-Completion
- Forfeit-Handling bei Captain-Disconnect
**Scope-Cut:** Nur Solo-Tournaments (1v1-Pairings). Multi-Player-Teams in Tournament-Matches kommt in v2.
**Plan-Doc:** `docs/superpowers/plans/2026-05-09-mob-army-battle-plan-6-tournament.md`

### ✅ Plan 7 — SQLite + Stats + Leaderboard (v1)
**Ziel:** Lifetime-Stats pro Spieler in SQLite, abrufbar via Commands.
**Geliefert:**
- SQLite via Paper Library Loader (`org.xerial:sqlite-jdbc:3.46.1.3` in plugin.yml)
- `StatsDatabase` (Connection-Mgmt + DDL), `StatsRepository` (CRUD), `StatsRecorder` (Match-Listener)
- `PlayerStats` Domain-Value-Object (Bukkit-frei, 8 Tests)
- `BattleManager.addMatchCompletedListener` mit `TeamOutcome`-Aggregat
- Schema: `player_stats(player_uuid, matches_total, matches_won, mob_kills_total)` + Win-Index
- `/mab stats [player]` + `/mab leaderboard` (Top 10 by Wins)
- DB-Fehler → Log-Warning, Plugin läuft weiter (kein Crash, kein JSON-Backup in v1)
**Scope-Cut:** Nur `player_stats`-Tabelle. matches/teams/wave_data Schema, avg_battle_time, favorite_mob, JSON-Backup → v2.
**Plan-Doc:** `docs/superpowers/plans/2026-05-09-mob-army-battle-plan-7-stats.md`

### ✅ Plan 8 — Spectator + Sidebar + Notifications
**Ziel:** UI-Polish: Sidebar-Scoreboard, `/mab spectate <captain>`, Title+Sound-Notifications.
**Geliefert:**
- `ui/SidebarRenderer` (Bukkit-frei, 8 Tests, FARM/WAVE_BUILD/BATTLE-Layouts mit Pool-Top-7-Truncate, FFA-Footer "Teams aktiv: X/Y", Pair-Captain in Battle)
- `ui/BattleContext` Record + `ui/SidebarManager` (per-Player Bukkit-Scoreboard, 1-Sek-Tick parallel zu BossBar)
- `ui/Notifications` (Static Title+Sound für FARM/WAVE_BUILD/BATTLE-onEnter, Welle-Spawn, Welle-Bestanden, Sieg/Niederlage; nutzt `LegacyComponentSerializer` für §-Codes)
- `spectator/SpectateState` Record + `spectator/SpectatorManager` (zwei Permission-Pfade: Pair-Partner finished + Tournament-Eliminated, mit Cross-Match/Cross-Tournament-Schutz)
- `Tournament.isEliminated()` + `TournamentRound.activeCaptains()` + `TournamentManager.findEliminatedTournamentOf/getTournamentByMatchId`
- `BattleManager` Hooks (`currentWaveSpawnedTotal`, `getSessionByPlayer`, `setSpectatorManager`, evictAll on cleanup/checkSessionEnd)
- `MabCommand` `/mab spectate [captain]` + Tab-Completion + Spectator-Handling in `/mab leave`
- `PlayerConnectionListener.onQuit` cleanup (Pair-Partner-Spectator räumt Match-Membership korrekt auf)
- `MobArmyBattle.onEnable` Wiring + `plugin.yml` `mobarmybattle.spectate` Permission
**Scope-Cut:** Keine 1-Min-Warnung vor Phase-Ende, kein Spectator-GUI (Text-Liste reicht), Pool-Truncate Top-7, Tournament-Spectate kein Auto-Hop bei Match-Wechsel.
**Plan-Doc:** `docs/superpowers/plans/2026-05-09-mob-army-battle-plan-8-spectator-ui.md`

### ✅ Plan 9 — Config + Permissions
**Ziel:** Alles konfigurierbar über `config.yml`. Bestehende hardcoded Werte ersetzt + neue Behaviors (Starter-Kit, Death-Penalty-Modi, Auto-Farm-Timer, Reconnect-Grace).
**Geliefert:**
- 6 immutable records (`PhaseDurations`, `StarterKitConfig`, `DeathPenaltyConfig`, `WorldBorderConfig`, `ReconnectConfig`, `MabConfig` Wurzel) mit Constructor-Validation
- `config/ConfigLoader` (Bukkit-aware): liest `FileConfiguration` mit per-Sektion + per-Feld-Fallback auf Defaults
- `config/ReconnectGraceManager` (BukkitTask-basierte deferred `leaveMatch`)
- `world/StarterKitApplier` (NONE/LEATHER_FULL/IRON_FULL/CUSTOM)
- `config.yml` Default-Resource auto-kopiert in `plugins/MobArmyBattle/`
- `MatchBossBarManager` liest Phase-Dauern aus Config (live-reload-fähig)
- `FarmPhase.tick` Auto-Transition zu WaveBuild nach `farm-duration-min`
- `PlayerDeathFarmListener` config-driven (4 Modi: NONE 0%/no-drop, SOFT 5%/no-drop, DROP_ITEMS 0%/drop, HARD 25%/drop)
- `WorldManager` setzt Farm/Arena-Border + Mob-Spawn-Multiplier aus Config
- `PlayerConnectionListener` deferred `leaveMatch` via Grace-Manager + Restore on Rejoin (Farm-Welt-Teleport)
- `MabCommand` `/mab reload` (op-only) + granulare Permission-Checks pro Subcommand
- Granulare Permissions in `plugin.yml`: `mobarmybattle.create.match/.tournament/.join/.spectate/.stats/.kick/.reload/.admin/*` mit Backward-Compat-Alias `mobarmybattle.create`
**Scope-Cut:** Punkte-System (eigener Plan), per-Mode-Penalty-Override, Enchantment-Kits, Snapshot-Reconnect, Silent-Reload (kein Broadcast).
**Reserviert für Plan 10:** `prep-duration-sec`, `wave-pause-sec`, `wave-hard-timeout-min` Config-Felder existieren, werden aber noch nicht von BattlePhase konsumiert.
**Plan-Doc:** `docs/superpowers/plans/2026-05-09-mob-army-battle-plan-9-config-permissions.md`

### ✅ Plan 10 — Battle-Timing + Admin-Commands
**Ziel:** Battle-Phase zeitlich strukturieren (Prep + Pause + Hard-Timeout) + Admin-Commands.
**Geliefert:**
- `BattleSession.TeamState` neue Felder: `prepTask`, `hardTimeoutTask`, `wavePauseTask`, `prepEndsAt`, `currentWaveSpawnAt`
- `BattleManager` State-Machine: `schedulePrep` → `spawnWaveActual` → `onMobKilled` (cancelt hard-timeout) → `scheduleWavePause` → `schedulePrep` (Welle 2). Alle Tasks werden bei `cleanup` und `onPlayerDeath` (all-down) gecancelt.
- `onHardTimeout` (10min default): cleanup spawned mobs, `markFinished` (nicht überlebt), checkSessionEnd
- `BattleContext` erweitert (+`inPrepPhase`, +`prepSecondsLeft`); `SidebarRenderer` zeigt "§6Bauphase - W<n>" mit "§7Spawn in: §f<MM:SS>"-Countdown während Prep, sonst bisheriges Layout
- `Notifications.wavePrep` (Title "Bauphase" + Bell) + `waveTimedOut` (Title "Zeit abgelaufen" + Villager-No)
- `SidebarManager.buildBattleContext` liest `prepTask != null && prepEndsAt > 0`, berechnet `prepSecondsLeft`
- `/mab kick <player>` (Captain-only): leaveMatch + Lobby-Teleport. Self-kick abgelehnt, fremdes Team abgelehnt
- `/mab forcecancel <matchId>` (admin-only): warnt bei Tournament-Match, evictAll spectators → BattleManager.cleanup → forceRemove + Lobby-Teleport pro Member, matchById remove
- `MatchManager.forceCancelMatch(Match, BattleManager, SpectatorManager, WorldManager)` orchestrator
- Tab-Completion für beide Commands
**Scope-Cut:** Punkte-System (eigener Plan), Async-Welt-Generierung, World-Cleanup-on-forcecancel (Worlds werden Orphan, beim nächsten Plugin-Start gelöscht), Tournament-Auto-Forfeit-on-forcecancel (nur Warnung an Admin), animated wavePrep-Subtitle (statisches "Welle X in Xs").
**Plan-Doc:** `docs/superpowers/plans/2026-05-10-mob-army-battle-plan-10-final-polish.md`

---

## Code-Konventionen

- **Domain-Klassen Bukkit-frei** (`Match`, `Team`, `Pool`, `Wave`, `Battle*`) → unit-testbar mit JUnit 5 ohne MockBukkit
- **Bukkit-Adapter** in `command/`, `listener/`, `world/`, `wave/WaveBuildGui`, `battle/Arena*` + `WaveSpawner`
- **Phase-Klassen** haben 0-arg + `MobArmyBattle plugin`-arg Constructor (Tests vs. Production)
- **Player-facing-Texte und Exception-Messages auf Deutsch**
- **Conventional Commits** (`feat:`, `fix:`, `refactor:`)
- **Branch-Namen**: `feat/plan-<N>-<topic>`

## Build & Test

```bash
./gradlew.bat build         # compile + test
./gradlew.bat test          # alle Tests (216 grün)
./gradlew.bat runServer     # lokaler Paper-Server mit Plugin
```

## Aktuelle Test-Coverage

| Bereich | Tests |
|---|---|
| `Team` | 15 |
| `Match` | 13 |
| `MatchManager` | 19 |
| `MobEntry` | 7 |
| `MobPool` | 13 |
| `Wave` | 17 |
| `WaveSlot` | 5 |
| `MatchMode` (entfernt in 3.5) | — |
| `TeamPair` | 6 |
| `TeamPairing` | 6 |
| `BattleStats` | 5 |
| `BattleResult` | 5 |
| `WorldCleanup` | 3 |
| `TournamentPairing` | 10 |
| `TournamentRound` | 10 |
| `Tournament` | 18 |
| `PlayerStats` | 8 |
| `SidebarRenderer` | 11 |
| `SpectateState` | 2 |
| `PhaseDurations` | 5 |
| `StarterKitConfig` | 5 |
| `DeathPenaltyConfig` | 6 |
| `WorldBorderConfig` | 4 |
| `ReconnectConfig` | 3 |
| `MabConfig` | 2 |
| **Gesamt** | **216** |

Bukkit-Code (GUI, Welten, Listener, BattleManager, SidebarManager, SpectatorManager, Notifications, ConfigLoader, ReconnectGraceManager, StarterKitApplier) wird manuell via `runServer` verifiziert.
