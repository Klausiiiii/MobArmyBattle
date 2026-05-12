# Design: Lobby build-protection + arena cleanup on match end

**Date:** 2026-05-12
**Status:** Approved (pending spec review)

## Problem

1. The lobby world (`mab_lobby`) has no build protection — players can break and place
   blocks freely, and the existing menu sign at `(0, 64, 2)` does nothing when clicked.
2. Per-match arena worlds (`mab_arena_<matchId>_<teamId>-arena`) are never deleted when a
   match finishes. Players are in `SURVIVAL` during the battle and can place/break blocks in
   the arena; those changes (and the world folder itself) persist on disk until the next
   plugin restart, when `WorldManager.cleanupOrphanWorlds()` finally removes them.

## Goals

- Lobby is effectively read-only: no block breaking, no block placing, no block interaction —
  **except** a right-click on the lobby sign, which opens the `/mab` menu.
- A configurable permission lets staff bypass the lobby protection; ops have it by default.
- When a match reaches `FINISHED`, its arena world(s) are deleted, so the next match always
  starts from a pristine arena (re-pasted `.nbt` or freshly generated platform). This also
  removes the existing orphan-world leak for arenas.

## Non-goals

- No protection against PvP, item drops, entity interaction, or anything other than blocks in
  the lobby.
- No persistent / restored arena. Arenas remain throwaway per-match worlds; "reset" = delete
  and let the next match regenerate.
- No block-level undo/snapshot system.
- No new unit tests — all new code is Bukkit-aware adapter code, verified manually via
  `runServer`, consistent with the project's existing convention for `listener/` and
  `world/` classes.

## Design

### Part A — `LobbyProtectionListener`

New class `de.klausiiiii.mobArmyBattle.listener.LobbyProtectionListener`, constructed with the
`MobArmyBattle` plugin instance (same pattern as the other listeners), registered in
`MobArmyBattle.onEnable()` alongside the existing listeners.

Every handler first checks the event's block/player world:
`world.getName().equals(WorldManager.LOBBY_WORLD_NAME)`. If not the lobby, return immediately.
A player with permission `mobarmybattle.lobby.bypass` is exempt from all of the below.

| Event | Behaviour in lobby |
|---|---|
| `BlockBreakEvent` | cancel |
| `BlockPlaceEvent` | cancel |
| `PlayerBucketEmptyEvent` | cancel |
| `PlayerBucketFillEvent` | cancel |
| `PlayerInteractEvent`, `action == RIGHT_CLICK_BLOCK`, `hand == EquipmentSlot.HAND` | cancel; if the clicked block's state is a `Sign`, also `plugin.getMabMenuGui().open(player)` |

Notes:
- The `hand == HAND` guard avoids the menu opening twice (the event fires for main + off hand).
- Cancelling every `RIGHT_CLICK_BLOCK` (not just the sign) keeps the lobby fully inert
  defensively, even though the current lobby has no doors/buttons/chests.
- `BlockBreakEvent`/`BlockPlaceEvent` use `ignoreCancelled = true`.

### Part B — Arena cleanup on match end

`WorldManager.deleteArenaWorldsOf(String matchId)` (matches the type of `Match.getId()`):
- Iterates `Bukkit.getWorlds()`.
- For each world whose name starts with `WorldManager.ARENA_WORLD_PREFIX + matchId + "_"`,
  calls the existing `deleteWorld(world)` (which already teleports any remaining players to the
  lobby, then `unloadWorld(..., false)` + recursive folder delete).

`FinishedPhase.onEnter(match)`: after the existing farm-world deletion loop (by which point all
players have been teleported to the lobby and `forceRemove`d), add
`wm.deleteArenaWorldsOf(match.getId())`. The existing `if (plugin == null) return;` guard keeps
`MatchTest` unaffected.

Out of scope but noted: force-cancelling a match mid-battle (`MatchManager.forceCancelMatch`)
still leaks arena worlds until restart — `cleanupOrphanWorlds()` covers that case as today.
Wiring `deleteArenaWorldsOf` into the force-cancel path is a possible follow-up, not part of
this change.

### Part C — `plugin.yml`

Add permission:

```yaml
  mobarmybattle.lobby.bypass:
    default: op
    description: Umgeht den Bauschutz in der Lobby
```

…and add `mobarmybattle.lobby.bypass: true` to the `mobarmybattle.*` children list.

### Part D — Lobby sign text (minor)

In `WorldManager.buildLobbyPlatform`, change the sign's front face lines 2–3 from
`/mab create` / `zum Starten` to something that advertises the click, e.g.
`» Rechtsklick «` / `öffnet das Menü`. (Lines 0–1 "MobArmy" / "Battle" stay.)
Cosmetic only; existing lobby worlds keep the old text until regenerated — acceptable.

## Files touched

- **new** `src/main/java/de/klausiiiii/mobArmyBattle/listener/LobbyProtectionListener.java`
- `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java` — register the listener
- `src/main/java/de/klausiiiii/mobArmyBattle/world/WorldManager.java` — `deleteArenaWorldsOf`, sign text
- `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FinishedPhase.java` — call `deleteArenaWorldsOf`
- `src/main/resources/plugin.yml` — `mobarmybattle.lobby.bypass`

## Verification

- `./gradlew.bat build` compiles and the existing ~172 tests still pass.
- Manual via `./gradlew.bat runServer`:
  - In `mab_lobby` as a non-op: cannot break/place blocks or empty a bucket; right-clicking
    the sign opens the `/mab` menu; right-clicking nothing else does anything.
  - As an op (or with `mobarmybattle.lobby.bypass`): can build normally.
  - Run a full match to `FINISHED`; confirm the `mab_arena_*` world folder(s) for that match
    are gone afterwards and no orphan remains.
