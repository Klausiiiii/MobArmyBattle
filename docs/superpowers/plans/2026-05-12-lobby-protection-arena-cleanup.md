# Lobby Build-Protection + Arena Cleanup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the `mab_lobby` world read-only (except right-clicking the sign, which opens the `/mab` menu) and delete per-match arena worlds when a match finishes.

**Architecture:** A new `LobbyProtectionListener` cancels block break/place/bucket events and right-click-block interactions in the lobby world, with a `mobarmybattle.lobby.bypass` permission (default `op`) as an escape hatch; a right-click on a sign instead opens `MabMenuGui`. A new `WorldManager.deleteArenaWorldsOf(matchId)` is called from `FinishedPhase.onEnter` to unload + delete the throwaway `mab_arena_*` worlds for that match (which also closes the existing orphan-world leak).

**Tech Stack:** Paper API 26.1.2, Java 25, Gradle (Kotlin DSL). Bukkit event listeners + `WorldManager`.

**Testing note — read before you look for "write the failing test" steps:** Per this project's conventions (see `CLAUDE.md`), Bukkit-aware adapter code — `listener/*`, `world/WorldManager` — is **not** unit-tested; it's verified manually via `./gradlew.bat runServer`. All code in this plan is Bukkit-aware. So the per-task verification step is `./gradlew.bat compileJava`, the final task runs the full `./gradlew.bat build` (~172 existing tests must still pass), and Task 3 is a manual `runServer` checklist. Do **not** add JUnit tests for the listener or `WorldManager` — that's not how this codebase works. `FinishedPhase` keeps its `if (plugin == null) return;` guard, so `MatchTest` is unaffected.

**Spec:** `docs/superpowers/specs/2026-05-12-lobby-protection-arena-cleanup-design.md`

---

## File Structure

- **new** `src/main/java/de/klausiiiii/mobArmyBattle/listener/LobbyProtectionListener.java` — single responsibility: enforce "lobby is read-only except the menu sign". Stateless apart from the injected plugin.
- `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java` — register the new listener in `onEnable()`.
- `src/main/java/de/klausiiiii/mobArmyBattle/world/WorldManager.java` — add `deleteArenaWorldsOf(String)`; update the lobby sign text.
- `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FinishedPhase.java` — call `deleteArenaWorldsOf` after the existing farm-world deletion.
- `src/main/resources/plugin.yml` — declare `mobarmybattle.lobby.bypass`.

---

## Task 1: Lobby build-protection + working menu sign

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/listener/LobbyProtectionListener.java`
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java` (onEnable, "Other listeners" block — currently around lines 109–117)
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/world/WorldManager.java` (`buildLobbyPlatform`, currently lines 82–83)
- Modify: `src/main/resources/plugin.yml` (permissions block)

- [ ] **Step 1: Add the bypass permission to `plugin.yml`**

In `src/main/resources/plugin.yml`, add `mobarmybattle.lobby.bypass: true` to the `mobarmybattle.*` children list, and add a top-level entry for it. After editing, the relevant parts look like:

```yaml
permissions:
  mobarmybattle.*:
    description: Alle MobArmyBattle-Permissions
    default: op
    children:
      mobarmybattle.create.match: true
      mobarmybattle.create.tournament: true
      mobarmybattle.join: true
      mobarmybattle.spectate: true
      mobarmybattle.stats: true
      mobarmybattle.kick: true
      mobarmybattle.reload: true
      mobarmybattle.admin: true
      mobarmybattle.lobby.bypass: true
```

…and append, after the existing `mobarmybattle.admin:` block:

```yaml
  mobarmybattle.lobby.bypass:
    default: op
    description: Umgeht den Bauschutz in der Lobby
```

- [ ] **Step 2: Create `LobbyProtectionListener`**

Create `src/main/java/de/klausiiiii/mobArmyBattle/listener/LobbyProtectionListener.java`:

```java
package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Makes the lobby world ({@link WorldManager#LOBBY_WORLD_NAME}) effectively read-only:
 * block breaking, placing and bucket use are cancelled, and right-clicking any block does
 * nothing — except a right-click on a sign, which opens the {@code /mab} menu (same as
 * running {@code /mab} with no arguments). Players holding {@link #BYPASS_PERMISSION}
 * (ops by default) are exempt.
 */
public class LobbyProtectionListener implements Listener {

    public static final String BYPASS_PERMISSION = "mobarmybattle.lobby.bypass";

    private final MobArmyBattle plugin;

    public LobbyProtectionListener(MobArmyBattle plugin) {
        this.plugin = plugin;
    }

    private static boolean protectedFor(Player player) {
        return WorldManager.LOBBY_WORLD_NAME.equals(player.getWorld().getName())
                && !player.hasPermission(BYPASS_PERMISSION);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (protectedFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (protectedFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (protectedFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (protectedFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!protectedFor(player)) return;
        Block clicked = event.getClickedBlock();
        event.setCancelled(true);
        if (clicked != null && clicked.getState() instanceof Sign) {
            plugin.getMabMenuGui().open(player);
        }
    }
}
```

Notes for the implementer:
- `protectedFor` keys off `player.getWorld()` (a player can only break/place/interact with blocks in their own world), so we never need to disambiguate `getBlock().getWorld()` vs `getBlockClicked().getWorld()`.
- The `event.getHand() != EquipmentSlot.HAND` guard prevents the menu opening twice (the interact event fires once per hand).
- `event.setCancelled(true)` on the interact event also suppresses the vanilla sign-edit screen, which is what we want.

- [ ] **Step 3: Register the listener in `MobArmyBattle.onEnable()`**

In `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java`, in the `// 11. Other listeners` block, add a registration after the `WorldGroupInventoryListener` one. The block becomes:

```java
        // 11. Other listeners
        getServer().getPluginManager().registerEvents(
                new MobKillListener(matchManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerDeathFarmListener(matchManager, this), this);
        getServer().getPluginManager().registerEvents(
                new PlayerRespawnListener(this, matchManager), this);
        getServer().getPluginManager().registerEvents(
                new WorldGroupInventoryListener(lobbyInventoryManager), this);
        getServer().getPluginManager().registerEvents(
                new LobbyProtectionListener(this), this);
```

Add the import near the other `listener` imports at the top of the file:

```java
import de.klausiiiii.mobArmyBattle.listener.LobbyProtectionListener;
```

(`mabMenuGui` is assigned later in `onEnable` at step `// 12`, but that's fine — `LobbyProtectionListener` only calls `plugin.getMabMenuGui()` when a player clicks a sign at runtime, long after `onEnable` finishes.)

- [ ] **Step 4: Update the lobby sign text in `WorldManager.buildLobbyPlatform`**

In `src/main/java/de/klausiiiii/mobArmyBattle/world/WorldManager.java`, replace these two lines:

```java
            sign.getSide(Side.FRONT).line(2, Component.text("/mab create", NamedTextColor.GREEN));
            sign.getSide(Side.FRONT).line(3, Component.text("zum Starten", NamedTextColor.GRAY));
```

with:

```java
            sign.getSide(Side.FRONT).line(2, Component.text("» Rechtsklick «", NamedTextColor.GREEN));
            sign.getSide(Side.FRONT).line(3, Component.text("öffnet das Menü", NamedTextColor.GRAY));
```

(Lines 0–1 — "MobArmy" / "Battle" — stay unchanged. Existing `mab_lobby` worlds keep the old text until regenerated; that's acceptable.)

- [ ] **Step 5: Compile**

Run: `./gradlew.bat compileJava`
Expected: `BUILD SUCCESSFUL`, no errors.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/listener/LobbyProtectionListener.java src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java src/main/java/de/klausiiiii/mobArmyBattle/world/WorldManager.java src/main/resources/plugin.yml
git commit -m "feat(lobby): build-protection + right-click sign opens /mab menu"
```

---

## Task 2: Delete per-match arena worlds when the match finishes

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/world/WorldManager.java` (add `deleteArenaWorldsOf`, e.g. right after `deleteWorld`)
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FinishedPhase.java` (`onEnter`, after the existing farm-world loop at lines ~62–67)

- [ ] **Step 1: Add `deleteArenaWorldsOf` to `WorldManager`**

In `src/main/java/de/klausiiiii/mobArmyBattle/world/WorldManager.java`, add this method (place it just after the existing `deleteWorld(World)` method). Add `import java.util.ArrayList;` to the imports.

```java
    /**
     * Unloads and deletes every loaded arena world belonging to the given match
     * ({@code mab_arena_<matchId>_*}). Safe to call even if no such worlds exist.
     */
    public void deleteArenaWorldsOf(String matchId) {
        String prefix = ARENA_WORLD_PREFIX + matchId + "_";
        // Copy the list: deleteWorld -> Bukkit.unloadWorld mutates Bukkit.getWorlds().
        for (World w : new ArrayList<>(Bukkit.getWorlds())) {
            if (w.getName().startsWith(prefix)) {
                deleteWorld(w);
            }
        }
    }
```

- [ ] **Step 2: Call it from `FinishedPhase.onEnter`**

In `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FinishedPhase.java`, in `onEnter`, after the existing farm-world deletion loop, add the arena cleanup. The end of `onEnter` becomes:

```java
        for (String worldName : match.getAllFarmWorldNames().values()) {
            World w = Bukkit.getWorld(worldName);
            if (w != null) {
                wm.deleteWorld(w);
            }
        }
        wm.deleteArenaWorldsOf(match.getId());
    }
```

(No new imports needed in `FinishedPhase` — `wm` is the existing `WorldManager` local, `match.getId()` is already available. This line is only reached when `plugin != null`, so `MatchTest` is unaffected.)

- [ ] **Step 3: Compile**

Run: `./gradlew.bat compileJava`
Expected: `BUILD SUCCESSFUL`, no errors.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/world/WorldManager.java src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FinishedPhase.java
git commit -m "feat(world): delete per-match arena worlds when match finishes"
```

---

## Task 3: Full build + manual verification

**Files:** none (verification only — no commit).

- [ ] **Step 1: Run the full build**

Run: `./gradlew.bat build`
Expected: `BUILD SUCCESSFUL`; the existing ~172 tests all pass (this change adds no tests and must not break any).

- [ ] **Step 2: Manual verification on a dev server**

Run: `./gradlew.bat runServer`

Then check, in-game:
1. **Lobby protection (non-op):** join as a non-op player (or `/deop` yourself) and teleport to `mab_lobby`. Confirm you cannot break a quartz block, cannot place a block, and cannot empty a water bucket onto the platform.
2. **Menu sign:** right-click the sign on the lobby platform — the `/mab` menu GUI opens (same as typing `/mab`). The sign-edit screen does **not** open. Right-clicking it again doesn't open two GUIs.
3. **Other right-clicks inert:** right-clicking elsewhere in the lobby does nothing.
4. **Bypass:** `/op` yourself (or grant `mobarmybattle.lobby.bypass`) — now you can break/place blocks in the lobby normally.
5. **Arena cleanup:** start and complete a full match (lobby → farm → wave-build → battle → finished). After the match ends, confirm the `run/mab_arena_<matchId>_*` world folder(s) for that match are gone (not just unloaded — deleted from disk), and `run/` contains no leftover `mab_arena_*` directory.
6. New sign text reads "MobArmy / Battle / » Rechtsklick « / öffnet das Menü" in a freshly generated lobby (delete `run/mab_lobby` first if testing this).

If anything fails, treat it as a bug to fix before considering the plan done — don't tick this step off on a partial result.
