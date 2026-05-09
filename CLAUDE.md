# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Paper plugin (Minecraft mod) for Paper API 26.1.2 on Java 25, built with Gradle (Kotlin DSL). Implements a multi-phase competitive game mode: teams farm mobs, build two waves from their pool, then waves fight each other in an arena. See `docs/superpowers/specs/2026-05-03-mob-army-battle-design.md` for the full design spec.

## Build & Test

Windows shell — use `./gradlew.bat` (or `gradlew.bat`). On Linux/macOS use `./gradlew`.

```
./gradlew.bat build         # compile + test
./gradlew.bat test          # run unit tests only
./gradlew.bat runServer     # launch a Paper 26.1.2 dev server in ./run/ with the plugin loaded
```

Run a single test class or method:

```
./gradlew.bat test --tests de.klausiiiii.mobArmyBattle.match.MatchTest
./gradlew.bat test --tests de.klausiiiii.mobArmyBattle.match.MatchTest.transitionThroughAllPhases
```

`processResources` expands `${version}` and `$description` in `plugin.yml` from `gradle.properties` — bump those, not the yaml.

## Architecture

### State-pattern match lifecycle

`Match` is the aggregate root and holds a single `MatchPhase` (interface). Phases live in `match/phase/`: `LobbyPhase → FarmPhase → WaveBuildPhase → BattlePhase → FinishedPhase`. Transitions go through `Match.transitionTo(newPhase)` which calls `onExit` then `onEnter`. Once a match reaches `FINISHED`, further transitions throw — design this as terminal.

Each phase class has **two constructors**: a no-arg one (used by unit tests, where Bukkit isn't available) and a `MobArmyBattle plugin`-arg one (used at runtime to access `WorldManager`, `MatchManager`, `WaveBuildGui`). Phases that need Bukkit guard with `if (plugin == null) return;`. Preserve this pattern when adding/editing phases — tests construct phases without a plugin.

### Bukkit-free domain boundary

`Match`, `Team`, `MobPool`, `MobEntry`, `Wave`, `WaveSlot`, `MatchManager` are deliberately Bukkit-free: they use `UUID`, not `Player`. This is what lets the unit tests run with plain JUnit (no MockBukkit). Bukkit types only appear in `listener/`, `command/`, `world/`, `wave/WaveBuildGui`, and the phase classes' Bukkit-aware branches. **Do not import `org.bukkit.*` into the domain packages** — push Bukkit calls out to the adapters.

### MatchManager as the source of truth

`MatchManager` indexes matches both by id and by player UUID (`matchByPlayer`). Use `getMatchOf(uuid)` to look up — never iterate. `tickAll()` is called every 20 server ticks (1s) by a scheduler started in `MobArmyBattle.onEnable`; phases drive their own progression in `tick(Match)`. `forceRemove(uuid)` is the back-door used by `FinishedPhase` / `WaveBuildPhase` to evict players without going through team-cleanup; prefer `leaveMatch` for normal flow.

### Listener phase-gating pattern

Event listeners (`MobKillListener`, `PlayerDeathFarmListener`) follow a strict guard order: look up match via `MatchManager`, check `getCurrentPhase().getType() == FARM`, find the team, then verify the entity is in that team's farm world (`match.getFarmWorldName(team)`). If you add a phase-specific listener, copy this pattern — events fire globally and must self-gate.

### World naming + orphan cleanup

`WorldManager` owns three name spaces: `mab_lobby` (singleton, persistent, custom flat generator with a quartz spawn platform), `mab_farm_<matchId>_<teamId>` (per-team farm world, **same seed for all teams in a match**), `mab_arena_<matchId>_<teamId>` (planned). On plugin enable, `cleanupOrphanWorlds()` deletes any `mab_farm_*`/`mab_arena_*` directories not currently loaded — if you add a new dynamic world prefix, register it there. World deletion is `unloadWorld(world, false)` then `WorldCleanup.deleteRecursively(folder)` — both required.

### Equipment-aware mob pool

`MobEntry` is keyed by `(EntityType.name(), equipmentSignature)` where the signature is a 6-slot pipe-joined string from `EquipmentSerializer` (`mainhand|offhand|helmet|chest|legs|boots`, `none` for empty). Identical entries collapse into a counter in `MobPool` rather than storing a list. The `none|none|none|none|none|none` sentinel is checked in a few places to detect "naked" mobs — if you change the signature format, update those checks.

## Conventions

- **Player-facing messages and exception messages are in German.** Code, identifiers, JavaDoc, and commit messages are English. Match the existing style of the file you're editing.
- **Conventional Commits** (`feat:`, `fix:`, `refactor:`, etc.). Recent branches are named `feat/plan-<N>-<feature>`.
- **Plan-driven development:** specs live in `docs/superpowers/specs/`, multi-step plans with checkbox tasks live in `docs/superpowers/plans/`. New features get a plan file before implementation.

## Local dev server

`./gradlew.bat runServer` writes server data to `run/` (gitignored). EULA is auto-accepted by the run-paper plugin. JVM is set to 2G/2G.

