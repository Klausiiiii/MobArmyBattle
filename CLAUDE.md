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
./gradlew.bat build         # compile + test (~172 tests)
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

Bukkit-aware adapters live in `command/`, `listener/`, `world/`, `bossbar/`, plus the manager classes in `battle/`, `tournament/`, `stats/`. **Do not import `org.bukkit.*` into the domain packages** — push Bukkit calls out to the adapters.

### Cross-cutting events: BattleManager listeners

`BattleManager` is the integration hub for what happens during/after a battle. Two listener channels:

- `addBattleEndListener(BiConsumer<Match, UUID winnerCaptain>)` — fires once **per pair** when both teams of that pair have concluded. Used by `TournamentManager` to track per-round winners.
- `addMatchCompletedListener(BiConsumer<Match, List<TeamOutcome>>)` — fires once **per match** when *all* pairs have concluded, just before transitioning to `FinishedPhase`. Used by `StatsRecorder` to persist W/L. `TeamOutcome` is a record with captainId, memberIds, winner-flag, mobKills.

Both channels fire from `BattleManager.checkSessionEnd`. New cross-cutting features (e.g. broadcasting, achievements) should subscribe instead of patching `BattleManager` internals.

### MatchManager as the source of truth

`MatchManager` indexes matches both by id and by player UUID (`matchByPlayer`). Use `getMatchOf(uuid)` to look up — never iterate. `tickAll()` runs every 20 server ticks (1s) from a scheduler in `MobArmyBattle.onEnable`; phases drive their own progression in `tick(Match)`. The same scheduler also runs `bossBarManager.tickAll(...)` for per-match BossBars (phase + countdown). `forceRemove(uuid)` is the back-door used by `FinishedPhase` / `WaveBuildPhase` to evict players without going through team-cleanup; prefer `leaveMatch` for normal flow.

### Listener phase-gating pattern

Event listeners (`MobKillListener`, `PlayerDeathFarmListener`, `BattleEventListener`) follow a strict guard order: look up match via `MatchManager`, check `getCurrentPhase().getType()` matches the relevant phase, find the team, then verify the entity is in the right world. If you add a phase-specific listener, copy this pattern — events fire globally and must self-gate. `BattleEventListener` additionally short-circuits via world-name prefix (`WorldManager.ARENA_WORLD_PREFIX`) since battles span multiple matches and gating purely on phase isn't enough.

### Respawn routing

`PlayerRespawnListener` is the single point for `PlayerRespawnEvent`:
- Death in arena world (`mab_arena_*`) → leave alone (BattleEventListener has set spectator)
- In match + FARM phase → respawn at `WorldManager.safeSpawnAt(farmWorld, x, z)` (highest block)
- Else (lobby, default `world`, etc.) → lobby spawn

If you add new "kinds" of game world, extend this listener — don't add a parallel respawn handler.

### World naming + orphan cleanup

`WorldManager` owns three name spaces:
- `mab_lobby` — singleton, persistent, flat generator + quartz spawn platform, always day (`applyAlwaysDay` sets `time=6000` and `GameRules.ADVANCE_TIME=false`)
- `mab_farm_<matchId>_<teamId>` — per-team farm world, **same seed across all teams of a match** (vanilla NORMAL gen)
- `mab_arena_<matchId>_<teamId>-arena` — per-team arena (flat/void via `LobbyChunkGenerator`), also always day; ArenaLoader either loads `.nbt` from `plugins/MobArmyBattle/arenas/` or generates a 30×30 bedrock platform with `[MAB_SPAWN]` banner markers

On plugin enable, `cleanupOrphanWorlds()` deletes any `mab_farm_*`/`mab_arena_*` directories not currently loaded — if you add a new dynamic world prefix, register it there. World deletion is `unloadWorld(world, false)` then `WorldCleanup.deleteRecursively(folder)` — both required.

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

## Conventions

- **Player-facing messages and exception messages are in German.** Code, identifiers, JavaDoc, and commit messages are English. Match the existing style of the file you're editing.
- **Conventional Commits** (`feat:`, `fix:`, `refactor:`, etc.). Branches were named `feat/plan-<N>-<feature>` early on; recent work has gone direct-to-master.
- **Plan-driven development:** specs in `docs/superpowers/specs/`, multi-step plans with checkbox tasks in `docs/superpowers/plans/`. New phases get a plan file before implementation; mark scope cuts in the plan-doc.
- **Domain-first when extending:** add domain class + tests first (Bukkit-free, JUnit), then the Bukkit-aware adapter.

## Local dev server

`./gradlew.bat runServer` writes server data to `run/` (gitignored, includes `data.db` and any generated `mab_*` worlds). EULA is auto-accepted by the run-paper plugin. JVM is set to 2G/2G. Server port is read from `run/server.properties`.
