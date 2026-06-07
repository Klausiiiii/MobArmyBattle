# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Paper plugin (Minecraft mod) for Paper API 26.1.2 on Java 25, built with Gradle (Kotlin DSL). Implements a multi-phase competitive game mode: teams farm mobs, build two waves from their pool, then waves fight each other in arenas. Matches can be standalone or part of single-elimination tournaments.

- **Roadmap:** [`PLAN.md`](PLAN.md) — phase-by-phase status overview.
- **Spec:** [`docs/superpowers/specs/2026-05-03-mob-army-battle-design.md`](docs/superpowers/specs/2026-05-03-mob-army-battle-design.md) — full design.
- **Plan-Docs:** `docs/superpowers/plans/2026-05-*-plan-N-*.md` — one per shipped phase, includes scope cuts.

## Build & Test

Windows shell — use `./gradlew.bat`. On Linux/macOS use `./gradlew`.

```
./gradlew.bat build         # compile + test (~216 tests)
./gradlew.bat test          # tests only
./gradlew.bat runServer     # Paper 26.1.2 dev server in ./run/ with the plugin loaded
```

Single test class or method:

```
./gradlew.bat test --tests de.klausiiiii.mobArmyBattle.match.MatchTest
./gradlew.bat test --tests de.klausiiiii.mobArmyBattle.match.MatchTest.transitionThroughAllPhases
```

`processResources` expands `${version}` and `$description` in `plugin.yml` from `gradle.properties` — bump those, not the yaml.

## Architecture

### State-pattern match lifecycle

`Match` is the aggregate root and holds a single `MatchPhase` (interface). Phases live in `match/phase/`: `LobbyPhase → FarmPhase → WaveBuildPhase → BattlePhase → FinishedPhase`. Transitions go through `Match.transitionTo(newPhase)` which calls `onExit` then `onEnter` and timestamps `phaseStartedAt`. Once a match reaches `FINISHED`, further transitions throw — design this as terminal.

Each phase class has **two constructors**: a no-arg one (used by unit tests, where Bukkit isn't available) and a `MobArmyBattle plugin`-arg one (used at runtime to access `WorldManager`, `MatchManager`, `BattleManager`, `WaveBuildGui`). Phases that need Bukkit guard with `if (plugin == null) return;`. Preserve this pattern when adding/editing phases — tests construct phases without a plugin.

### Bukkit-free domain boundary

The following are deliberately Bukkit-free and unit-tested with plain JUnit:
- `match/`: `Match`, `Team`, `MatchManager`, `MatchPhase`, `MatchPhaseType`
- `pool/`: `MobEntry`, `MobPool`
- `wave/`: `Wave`, `WaveSlot` (NOT `WaveBuildGui`)
- `battle/`: `TeamPair`, `TeamPairing`, `BattleStats`, `BattleResult`, `TeamOutcome` record (NOT `BattleManager`/`ArenaLoader`/etc.)
- `tournament/`: `Tournament`, `TournamentRound`, `TournamentPairing` (NOT `TournamentManager`)
- `stats/`: `PlayerStats` (NOT `StatsDatabase`/`StatsRepository`)
- `ui/`: `SidebarRenderer`, `BattleContext` (NOT `SidebarManager`)
- `spectator/`: `SpectateState` (NOT `SpectatorManager`/`DeathSpectateGui`)
- `config/`: all records — `MabConfig`, `PhaseDurations`, `StarterKitConfig`, `DeathPenaltyConfig`, `WorldBorderConfig`, `ReconnectConfig` (NOT `ConfigLoader`/`ReconnectGraceManager`)

Bukkit-aware adapters live in `command/`, `listener/`, `world/`, `bossbar/`, plus the manager classes in `battle/`, `tournament/`, `stats/`, `ui/`, `spectator/`, `config/`. **Do not import `org.bukkit.*` into the domain packages** — push Bukkit calls out to the adapters.

### Config flow: global vs per-match

`MabConfig` is an immutable record (with nested records for each section). It comes from two places:

- **Global**: `MobArmyBattle.mabConfig`, loaded by `ConfigLoader.load(this)` from `config.yml`. Reloaded by `/mab reload`.
- **Per-match snapshot**: `Match.mabConfig`, captured at create time and editable from the lobby config screen (`MabMenuGui`). May be `null` in unit tests.

**Read sites must use `plugin.effectiveConfig(match)`** — returns the per-match snapshot if present, else falls back to the global. Direct reads of `plugin.getMabConfig()` bypass per-match overrides; only do that when no match is in scope (e.g. reload, lobby world creation). When adding a new config field, also add it to the `MabMenuGui` config screen (`CFG_*` slots in `command/MabMenuGui.java`) and to `ConfigLoader` with a default fallback.

### Cross-cutting events: BattleManager listeners

`BattleManager` is the integration hub for what happens during/after a battle. Two listener channels:

- `addBattleEndListener(BiConsumer<Match, UUID winnerCaptain>)` — fires once **per pair** when both teams of that pair have concluded. Used by `TournamentManager` to track per-round winners.
- `addMatchCompletedListener(BiConsumer<Match, List<TeamOutcome>>)` — fires once **per match** when *all* pairs have concluded, just before transitioning to `FinishedPhase`. Used by `StatsRecorder` to persist W/L. `TeamOutcome` is a record with captainId, memberIds, winner-flag, mobKills.

Both channels fire from `BattleManager.checkSessionEnd`. New cross-cutting features (e.g. broadcasting, achievements) should subscribe instead of patching `BattleManager` internals.

`BattleManager` also holds back-references injected during enable (`setSpectatorManager`, `setDeathSpectateGui`) so battle code can route dead players into spectate. Bye teams are teleported into a random concurrent arena as spectators via `sendByeToRandomArena`.

### MatchManager as the source of truth

`MatchManager` indexes matches both by id and by player UUID (`matchByPlayer`). Use `getMatchOf(uuid)` to look up — never iterate. `tickAll()` runs every 20 server ticks (1s) from a scheduler in `MobArmyBattle.onEnable`; phases drive their own progression in `tick(Match)`. The same scheduler also runs `bossBarManager.tickAll(...)` and `sidebarManager.tickAll(...)` for per-match BossBars + per-player sidebars. `forceRemove(uuid)` is the back-door used by `FinishedPhase` / `WaveBuildPhase` to evict players without going through team-cleanup; prefer `leaveMatch` for normal flow.

### Host vs Captain

Two related-but-distinct identities:
- **Captain** = `Team.captainId`, owner/leader of one team. One per team.
- **Host** = `Match.getHostId()` = captain of `teams.get(0)`, i.e. the player who *created* the match. One per match.

`/mab forcecancel`, `/mab kick`, "start match", "end farm phase" and the per-match config screen are host-only. Wave-building is captain-only. When the host leaves voluntarily, `MobArmyBattle.cascadeIfHostLeaving(player)` evicts every other member to the lobby — call it **before** `matchManager.leaveMatch(host)` in any code path where a player might be leaving as host (e.g. `/mab leave`, `MabMenuGui` leave button).

### Player-facing UI surfaces

Players never need to type a `/mab` command — the menu villager in the lobby is the primary surface:

- `LobbyProtectionListener` — makes lobby read-only (cancels block break/place, bucket use, interact, mob spawn, PvP), teleports players who fall below `y=0` back to spawn. Right-clicking the tagged menu villager opens `MabMenuGui`. Bypass via `mobarmybattle.lobby.bypass` (op default).
- `WorldManager.ensureMenuVillager(lobby)` — spawns one invulnerable, AI-less Librarian villager tagged with `NamespacedKey("mab_menu_villager")` in the lobby on enable. `wipeAllMobs(lobby)` runs first to clear stale entities.
- `MabMenuGui` (in `command/`) — chest-inventory state machine: `MAIN → CREATE → CONFIG`, or `MAIN → JOIN`, or `MAIN → CONFIG (edit)`. The config screen edits a `pendingConfig` and either calls `matchManager.createMatch(host, size, config)` (create mode) or `match.setMabConfig(config)` (edit mode). Going through this UI must always end via `MobArmyBattle.broadcastNewMatch(captain, size)` on create — both command and GUI paths call this so the lobby-wide click-to-join broadcast is consistent.

`/mab` commands still exist for ops and scripting (and the GUI delegates to them conceptually), but new player-facing features should be reachable from the menu.

### Sidebar + BossBar + Notifications (UI)

Per-player UI is layered:
- `ui/SidebarManager` — owns per-player Bukkit `Scoreboard`s. Ticks every second, calls `SidebarRenderer.render(BattleContext)` to produce the text lines, applies them. Bukkit-free `SidebarRenderer` is unit-tested with 11 tests covering FARM / WAVE_BUILD / BATTLE layouts (including prep-phase variant).
- `bossbar/MatchBossBarManager` — per-match `BossBar` showing phase + countdown. Reads phase durations from `plugin.effectiveConfig(match)`.
- `ui/Notifications` — static helpers for title + sound: phase-onEnter, wave-spawn, wave-cleared, victory/defeat, prep-phase, wave-timeout. Uses `LegacyComponentSerializer` for `§`-codes.

`BattleContext` is a record passed into `SidebarRenderer` carrying battle-time state (`inPrepPhase`, `prepSecondsLeft`, pair captain, current wave, etc.). Extend the record + `SidebarManager.buildBattleContext` together when adding new sidebar information.

### Spectator routing

Two paths into spectator mode:
1. **Death during BATTLE** — `BattleEventListener` sets the player to spectator-Gamemode and `DeathSpectateGui` opens a head-picker for surviving teammates / opposing teams in the same pair.
2. **Tournament-eliminated** — `/mab spectate <captain>` (also wired into `BattleEventListener` and the GUI). `SpectatorManager` enforces two permission paths: pair-partner-finished and tournament-eliminated, with cross-match/cross-tournament protection.

`PlayerConnectionListener.onQuit` cleans up spectator state — a pair-partner who disconnects must release their match-membership correctly.

### Listener phase-gating pattern

Event listeners (`MobKillListener`, `PlayerDeathFarmListener`, `BattleEventListener`) follow a strict guard order: look up match via `MatchManager`, check `getCurrentPhase().getType()` matches the relevant phase, find the team, then verify the entity is in the right world. If you add a phase-specific listener, copy this pattern — events fire globally and must self-gate. `BattleEventListener` additionally short-circuits via world-name prefix (`WorldManager.ARENA_WORLD_PREFIX`) since battles span multiple matches and gating purely on phase isn't enough. `LobbyProtectionListener` short-circuits on world name (`WorldManager.LOBBY_WORLD_NAME`).

### Respawn routing

`PlayerRespawnListener` is the single point for `PlayerRespawnEvent`:
- Death in arena world (`mab_arena_*`) → leave alone (BattleEventListener has set spectator)
- In match + FARM phase → respawn at `WorldManager.safeSpawnAt(farmWorld, x, z)` (highest block)
- Else (lobby, default `world`, etc.) → lobby spawn

If you add new "kinds" of game world, extend this listener — don't add a parallel respawn handler.

### World naming + cleanup

`WorldManager` owns three name spaces:
- `mab_lobby` — singleton, persistent, flat generator + quartz spawn platform, always day, mob-spawning disabled, menu villager at fixed coords, PEACEFUL difficulty
- `mab_farm_<matchId>_<teamId>` — per-team farm world, **same seed across all teams of a match** (vanilla NORMAL gen). Border + monster-spawn-limit applied from `MabConfig.farmBorder` and `farmMobSpawnMultiplier`.
- `mab_arena_<matchId>_<teamId>-arena` — per-team arena (flat/void via `LobbyChunkGenerator`), also always day, natural spawns disabled (only `SpawnReason.CUSTOM` from `WaveSpawner`). ArenaLoader either loads `.nbt` from `plugins/MobArmyBattle/arenas/` or generates a 30×30 bedrock platform with `[MAB_SPAWN]` banner markers.

Cleanup is two-stage:
- On plugin enable, `cleanupOrphanWorlds()` deletes any `mab_farm_*` / `mab_arena_*` directories not currently loaded.
- On match finish, `FinishedPhase` calls `WorldManager.deleteArenaWorldsOf(matchId)` to actively delete that match's arena worlds (farm worlds are reused by `BattlePhase` so are deleted via the orphan path at next enable, or by `/mab forcecancel`-style flows).

If you add a new dynamic world prefix, register it in both `cleanupOrphanWorlds()` and (if applicable) the per-match cleanup hook. World deletion is `unloadWorld(world, false)` then `WorldCleanup.deleteRecursively(folder)` — both required.

### Equipment-aware mob pool

`MobEntry` is keyed by `(EntityType.name(), equipmentSignature)` where the signature is a 6-slot pipe-joined string from `EquipmentSerializer` (`mainhand|offhand|helmet|chest|legs|boots`, `none` for empty). Identical entries collapse into a counter in `MobPool`. The `none|none|none|none|none|none` sentinel is checked in a few places to detect "naked" mobs — if you change the signature format, update those checks. `WaveSpawner` reverses the signature on spawn (`Material.matchMaterial(slot)`).

### Tournament flow (Solo-Only v1)

`TournamentManager` orchestrates single-elimination brackets:
- `create(name, masterId)` → REGISTERING state
- captains `join(name, captainId)` (must not be in any match/tournament)
- master `start(name, requesterId)` → builds Round 1 (`Tournament.start(rng)` shuffles + pairs, with bye for odd count)
- per pairing: creates a fresh `Match` with `maxTeamSize=1`, both captains as separate teams, auto-transitions to `FarmPhase`
- subscribes to `BattleManager.addBattleEndListener` to record per-pair winners
- on round complete: 15-sec delay (Bukkit scheduler), then advance — bye captains roll forward unpaired
- forfeit on disconnect via `PlayerConnectionListener.onQuit → tournamentManager.onCaptainQuit`

Solo-Only means each captain plays as a 1-person team; multi-player teams in tournaments aren't supported in v1.

### Stats persistence

SQLite via Paper's library loader (`libraries:` in `plugin.yml`, no shading). `StatsDatabase` opens `plugins/MobArmyBattle/data.db` on enable, runs DDL idempotently. `StatsRepository` does CRUD on the single `player_stats` table (UPSERT via `ON CONFLICT`). `StatsRecorder` subscribes to `BattleManager.addMatchCompletedListener` and increments per-member stats. The whole stack is failure-tolerant: if SQLite can't open, the plugin logs a warning and runs without stats — don't break this property when extending.

`mob_kills` is tracked per **team** in BattleStats, not per-player. `StatsRecorder` divides team kills evenly across members (`kills / teamSize`) — fine for solo, approximation for team play.

### Reconnect grace

`ReconnectGraceManager` (BukkitTask-based) defers `leaveMatch(player)` by `MabConfig.reconnect().graceSec()` when a player quits while in a match. If they rejoin within the window, the task is cancelled and they're teleported back to their farm world. `PlayerConnectionListener.onQuit` is the entry point. On `onDisable` the manager cancels every pending task so plugin reload doesn't leak players in zombie state.

## Conventions

- **Player-facing messages and exception messages are in German.** Code, identifiers, JavaDoc, and commit messages are English. Match the existing style of the file you're editing.
- **Conventional Commits** (`feat:`, `fix:`, `refactor:`, etc.). Branches were named `feat/plan-<N>-<feature>` early on; recent work has gone direct-to-master.
- **Plan-driven development:** specs in `docs/superpowers/specs/`, multi-step plans with checkbox tasks in `docs/superpowers/plans/`. New phases get a plan file before implementation; mark scope cuts in the plan-doc.
- **Domain-first when extending:** add domain class + tests first (Bukkit-free, JUnit), then the Bukkit-aware adapter.
- **Config reads via `plugin.effectiveConfig(match)`**, never `plugin.getMabConfig()` when a match is in scope.

## Local dev server

`./gradlew.bat runServer` writes server data to `run/` (gitignored, includes `data.db` and any generated `mab_*` worlds). EULA is auto-accepted by the run-paper plugin. JVM is set to 2G/2G. Server port is read from `run/server.properties`.
