# MobArmyBattle Plan 2: Welt-Management

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Welten verwalten — eine zentrale Lobby-Welt + pro Team eine Farm-Welt mit identischem Seed pro Match. Spieler werden bei Server-Join in die Lobby teleportiert, bei Match-Start in ihre Farm-Welt, bei Match-Ende zurück in die Lobby. Welten werden nach Match-Ende gelöscht.

**Architecture:** `WorldManager`-Service kapselt alle Welt-Ops (Bukkit `WorldCreator` für Generierung, manuelles Verzeichnis-Löschen für Cleanup). `FarmPhase` bekommt im Constructor eine optionale `MobArmyBattle`-Plugin-Referenz; ohne Plugin (Tests) ist sie No-Op, mit Plugin teleportiert sie und erstellt Welten. Lobby ist eine VOID-Welt mit Bedrock-Plattform 5x5 + Welcome-Sign. PlayerJoin/Quit-Listener für Lobby-Teleport und Auto-Leave-on-Disconnect.

**Tech Stack:** Paper API 26.1.2, Bukkit `World`/`WorldCreator`/`Listener`, Apache Commons IO für rekursives Verzeichnis-Löschen (oder JDK `Files.walkFileTree`).

**Plan-Umfang:** Plan 2 von ~10. Arena-Welten kommen erst in Plan 5 (Battle), Multiverse-Integration optional später.

**Was nach Plan 2 spielbar ist:** Spieler joinen Server → spawnen in Lobby. Captain `/mab create` → /mab join, /mab start. Bei start: pro Team wird eine Farm-Welt mit gleichem Seed erstellt, Teammates werden teleportiert. Match-Verlassen via `/mab leave` teleportiert zurück zur Lobby; bei Solo-Captain-Leave während Farm wird Welt gelöscht. Server-Restart mit aktiven Match-Welten: Cleanup beim nächsten Start.

---

## File Structure

| Datei | Zweck | Status |
|---|---|---|
| `src/main/java/de/klausiiiii/mobArmyBattle/world/WorldManager.java` | Service: Welt-Erstellung/-Löschung, Lobby + Farm | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/world/LobbyChunkGenerator.java` | Custom Generator: leere VOID-Welt | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/world/WorldCleanup.java` | Helper: rekursives Verzeichnis-Löschen | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/Match.java` | Erweitern um `farmWorlds` Map + `seed` Field | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FarmPhase.java` | onEnter: Welt-Erstellung + Teleport | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FinishedPhase.java` | onEnter: Welten cleanup | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/MatchManager.java` | createMatch akzeptiert seed; cleanup-Hook | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/listener/PlayerConnectionListener.java` | PlayerJoin/Quit-Listener | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java` | WorldManager init + Listener registrieren + cleanupOrphans on enable | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java` | FarmPhase mit Plugin-Ref erstellen; teleport bei leave | Modify |
| `src/test/java/de/klausiiiii/mobArmyBattle/match/MatchTest.java` | Anpassungen falls nötig (seed, farmWorlds) | Modify |
| `src/test/java/de/klausiiiii/mobArmyBattle/world/WorldCleanupTest.java` | Test für Verzeichnis-Löschen | Create |

---

## Task 1: WorldCleanup Helper + Test

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/world/WorldCleanup.java`
- Test: `src/test/java/de/klausiiiii/mobArmyBattle/world/WorldCleanupTest.java`

- [ ] **Step 1: Failing test schreiben**

```java
package de.klausiiiii.mobArmyBattle.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorldCleanupTest {

    @Test
    void deletesEmptyDirectory(@TempDir Path tmp) throws IOException {
        Path dir = Files.createDirectory(tmp.resolve("empty"));
        WorldCleanup.deleteRecursively(dir.toFile());
        assertFalse(Files.exists(dir));
    }

    @Test
    void deletesDirectoryWithFiles(@TempDir Path tmp) throws IOException {
        Path dir = Files.createDirectory(tmp.resolve("worlddata"));
        Files.writeString(dir.resolve("level.dat"), "binary content");
        Files.createDirectory(dir.resolve("region"));
        Files.writeString(dir.resolve("region").resolve("r.0.0.mca"), "chunkdata");

        WorldCleanup.deleteRecursively(dir.toFile());

        assertFalse(Files.exists(dir));
    }

    @Test
    void doesNothingIfDirectoryDoesNotExist(@TempDir Path tmp) {
        Path missing = tmp.resolve("doesnotexist");
        // should NOT throw
        WorldCleanup.deleteRecursively(missing.toFile());
        assertFalse(Files.exists(missing));
    }
}
```

- [ ] **Step 2: Run test (fail)**: `./gradlew test --tests WorldCleanupTest` → COMPILE FAIL.

- [ ] **Step 3: Implementation**

```java
package de.klausiiiii.mobArmyBattle.world;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public final class WorldCleanup {

    private WorldCleanup() {
    }

    public static void deleteRecursively(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        Path path = dir.toPath();
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete " + dir, e);
        }
    }
}
```

- [ ] **Step 4: Run tests** — all pass (3 new + 33 existing = 36).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/world/WorldCleanup.java src/test/java/de/klausiiiii/mobArmyBattle/world/WorldCleanupTest.java
git commit -m "feat: add WorldCleanup helper for recursive directory deletion"
```

---

## Task 2: LobbyChunkGenerator (VOID-Welt)

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/world/LobbyChunkGenerator.java`

Custom ChunkGenerator gibt leere Chunks zurück (VOID-Welt). Plattform wird später vom WorldManager gebaut.

- [ ] **Step 1: Implementation**

```java
package de.klausiiiii.mobArmyBattle.world;

import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

public class LobbyChunkGenerator extends ChunkGenerator {

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random,
                              int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        // empty - no terrain
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
```

- [ ] **Step 2: Build** — `./gradlew build` succeeds.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/world/LobbyChunkGenerator.java
git commit -m "feat: add LobbyChunkGenerator for empty VOID world"
```

---

## Task 3: WorldManager Service

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/world/WorldManager.java`

Service ist Bukkit-bound, daher kein Unit-Test (manueller Test in Task 11).

- [ ] **Step 1: Implementation**

```java
package de.klausiiiii.mobArmyBattle.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.io.File;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WorldManager {

    public static final String LOBBY_WORLD_NAME = "mab_lobby";
    public static final String FARM_WORLD_PREFIX = "mab_farm_";
    public static final String ARENA_WORLD_PREFIX = "mab_arena_";

    private static final int LOBBY_SPAWN_Y = 64;
    private static final int LOBBY_PLATFORM_RADIUS = 2; // 5x5 platform

    private final JavaPlugin plugin;
    private final Logger log;
    private World lobbyWorld;

    public WorldManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public World getOrCreateLobbyWorld() {
        if (lobbyWorld != null) {
            return lobbyWorld;
        }
        World existing = Bukkit.getWorld(LOBBY_WORLD_NAME);
        if (existing != null) {
            lobbyWorld = existing;
            return existing;
        }

        WorldCreator creator = new WorldCreator(LOBBY_WORLD_NAME)
                .generator(new LobbyChunkGenerator())
                .type(WorldType.FLAT);
        lobbyWorld = Bukkit.createWorld(creator);
        if (lobbyWorld == null) {
            throw new IllegalStateException("Konnte Lobby-Welt nicht erstellen");
        }
        lobbyWorld.setSpawnLocation(0, LOBBY_SPAWN_Y, 0);
        buildLobbyPlatform(lobbyWorld);
        log.info("Lobby-Welt initialisiert: " + LOBBY_WORLD_NAME);
        return lobbyWorld;
    }

    private void buildLobbyPlatform(World world) {
        for (int x = -LOBBY_PLATFORM_RADIUS; x <= LOBBY_PLATFORM_RADIUS; x++) {
            for (int z = -LOBBY_PLATFORM_RADIUS; z <= LOBBY_PLATFORM_RADIUS; z++) {
                world.getBlockAt(x, LOBBY_SPAWN_Y - 1, z).setType(Material.QUARTZ_BLOCK);
            }
        }
        Block signBlock = world.getBlockAt(0, LOBBY_SPAWN_Y, LOBBY_PLATFORM_RADIUS);
        signBlock.setType(Material.OAK_SIGN);
        if (signBlock.getState() instanceof Sign sign) {
            sign.getSide(Side.FRONT).line(0, Component.text("MobArmy", NamedTextColor.GOLD));
            sign.getSide(Side.FRONT).line(1, Component.text("Battle", NamedTextColor.GOLD));
            sign.getSide(Side.FRONT).line(2, Component.text("/mab create", NamedTextColor.GREEN));
            sign.getSide(Side.FRONT).line(3, Component.text("zum Starten", NamedTextColor.GRAY));
            sign.update();
        }
    }

    public World createFarmWorld(String matchId, String teamId, long seed) {
        String name = FARM_WORLD_PREFIX + matchId + "_" + teamId;
        WorldCreator creator = new WorldCreator(name)
                .seed(seed)
                .type(WorldType.NORMAL);
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            throw new IllegalStateException("Konnte Farm-Welt nicht erstellen: " + name);
        }
        world.setKeepSpawnInMemory(false);
        log.info("Farm-Welt erstellt: " + name + " (seed=" + seed + ")");
        return world;
    }

    public void deleteWorld(World world) {
        if (world == null) {
            return;
        }
        String name = world.getName();
        File folder = world.getWorldFolder();
        for (Player p : world.getPlayers()) {
            teleportToLobby(p);
        }
        boolean unloaded = Bukkit.unloadWorld(world, false);
        if (!unloaded) {
            log.warning("Konnte Welt nicht entladen: " + name);
            return;
        }
        WorldCleanup.deleteRecursively(folder);
        log.info("Welt gelöscht: " + name);
    }

    public void cleanupOrphanWorlds() {
        File container = Bukkit.getWorldContainer();
        File[] all = container.listFiles();
        if (all == null) {
            return;
        }
        for (File entry : all) {
            if (!entry.isDirectory()) continue;
            String n = entry.getName();
            if (n.startsWith(FARM_WORLD_PREFIX) || n.startsWith(ARENA_WORLD_PREFIX)) {
                if (Bukkit.getWorld(n) != null) continue;  // active, leave alone
                log.info("Lösche Orphan-Welt: " + n);
                WorldCleanup.deleteRecursively(entry);
            }
        }
    }

    public void teleportToLobby(Player player) {
        World lobby = getOrCreateLobbyWorld();
        Location spawn = lobby.getSpawnLocation();
        player.teleport(spawn.clone().add(0.5, 0, 0.5));
    }

    public World getLobbyWorld() {
        return lobbyWorld;
    }

    public static long generateSharedSeed() {
        return new Random().nextLong();
    }
}
```

- [ ] **Step 2: Build** — `./gradlew build` succeeds.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/world/WorldManager.java
git commit -m "feat: add WorldManager for lobby/farm world lifecycle"
```

---

## Task 4: Match — seed + farmWorlds-Map

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/Match.java`
- Modify: `src/test/java/de/klausiiiii/mobArmyBattle/match/MatchTest.java` (add tests)

- [ ] **Step 1: Failing tests schreiben**

Add to bottom of `MatchTest.java` (before closing `}`):

```java
    @Test
    void matchHasSeed() {
        Match match = new Match("test-match-1", 12345L);
        assertEquals(12345L, match.getSeed());
    }

    @Test
    void canAssociateFarmWorld() {
        Match match = new Match("test-match-1", 0L);
        UUID captain = UUID.randomUUID();
        Team team = new Team(captain);
        match.addTeam(team);

        match.setFarmWorldName(team, "mab_farm_test-match-1_team-1");

        assertEquals("mab_farm_test-match-1_team-1", match.getFarmWorldName(team));
    }

    @Test
    void getFarmWorldNameReturnsNullIfNotSet() {
        Match match = new Match("test-match-1", 0L);
        Team team = new Team(UUID.randomUUID());
        match.addTeam(team);

        assertNull(match.getFarmWorldName(team));
    }

    @Test
    void backwardsCompatibleConstructorUsesRandomSeed() {
        Match match = new Match("test-match-1");
        // seed should be set (any value), not throw
        long seed = match.getSeed();
        // No assertion on value, just that it doesn't throw
        assertNotNull(match.getId());
    }
```

- [ ] **Step 2: Run tests (fail)**: `./gradlew test --tests MatchTest` → fails on missing constructor + methods.

- [ ] **Step 3: Match.java erweitern**

Replace `src/main/java/de/klausiiiii/mobArmyBattle/match/Match.java` with:

```java
package de.klausiiiii.mobArmyBattle.match;

import de.klausiiiii.mobArmyBattle.match.phase.LobbyPhase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class Match {
    private final String id;
    private final long seed;
    private final List<Team> teams;
    private final Map<Team, String> farmWorldNames;
    private MatchPhase currentPhase;

    public Match(String id) {
        this(id, new Random().nextLong());
    }

    public Match(String id, long seed) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Match-ID darf nicht leer sein");
        }
        this.id = id;
        this.seed = seed;
        this.teams = new ArrayList<>();
        this.farmWorldNames = new HashMap<>();
        this.currentPhase = new LobbyPhase();
        this.currentPhase.onEnter(this);
    }

    public String getId() {
        return id;
    }

    public long getSeed() {
        return seed;
    }

    public List<Team> getTeams() {
        return Collections.unmodifiableList(teams);
    }

    public MatchPhase getCurrentPhase() {
        return currentPhase;
    }

    public void addTeam(Team team) {
        teams.add(team);
    }

    public Team findTeamOf(UUID playerId) {
        for (Team team : teams) {
            if (team.hasMember(playerId)) {
                return team;
            }
        }
        return null;
    }

    public void setFarmWorldName(Team team, String worldName) {
        farmWorldNames.put(team, worldName);
    }

    public String getFarmWorldName(Team team) {
        return farmWorldNames.get(team);
    }

    public Map<Team, String> getAllFarmWorldNames() {
        return Collections.unmodifiableMap(farmWorldNames);
    }

    public void transitionTo(MatchPhase newPhase) {
        if (currentPhase.getType() == MatchPhaseType.FINISHED) {
            throw new IllegalStateException("Match ist bereits beendet, kein Phase-Wechsel mehr möglich");
        }
        currentPhase.onExit(this);
        currentPhase = newPhase;
        currentPhase.onEnter(this);
    }

    public void tick() {
        currentPhase.tick(this);
    }
}
```

- [ ] **Step 4: Run tests** — all 36 + 4 new = 40 should pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/match/Match.java src/test/java/de/klausiiiii/mobArmyBattle/match/MatchTest.java
git commit -m "feat: add seed and farm-world-name tracking to Match"
```

---

## Task 5: MatchManager — generateSeed bei createMatch

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/MatchManager.java`

Currently `MatchManager.createMatch` calls `new Match(id)` (random seed). Now make seed explicit.

- [ ] **Step 1: Modify**

In `MatchManager.createMatch`, replace the line `Match match = new Match(id);` with:

```java
        long seed = WorldManager.generateSharedSeed();
        Match match = new Match(id, seed);
```

Add import at top:
```java
import de.klausiiiii.mobArmyBattle.world.WorldManager;
```

- [ ] **Step 2: Run tests** — all pass (40 tests).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/match/MatchManager.java
git commit -m "refactor: MatchManager uses explicit shared seed for match creation"
```

---

## Task 6: FarmPhase — onEnter implementieren

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FarmPhase.java`

FarmPhase wird zu einem echten Phase-Handler. Plugin-Reference im Constructor (optional für Tests).

- [ ] **Step 1: Replace FarmPhase.java**

```java
package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

public class FarmPhase implements MatchPhase {

    private final MobArmyBattle plugin;

    public FarmPhase() {
        this(null);
    }

    public FarmPhase(MobArmyBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public MatchPhaseType getType() {
        return MatchPhaseType.FARM;
    }

    @Override
    public void onEnter(Match match) {
        if (plugin == null) {
            return; // test mode - no Bukkit ops
        }
        WorldManager wm = plugin.getWorldManager();
        int teamIdx = 0;
        for (Team team : match.getTeams()) {
            String teamId = "team-" + (teamIdx++);
            World farmWorld = wm.createFarmWorld(match.getId(), teamId, match.getSeed());
            match.setFarmWorldName(team, farmWorld.getName());
            Location spawn = farmWorld.getSpawnLocation();
            for (UUID memberId : team.getMemberIds()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.teleport(spawn);
                }
            }
        }
    }

    @Override
    public void onExit(Match match) {
    }

    @Override
    public void tick(Match match) {
    }
}
```

- [ ] **Step 2: Run tests** — all 40 still pass (Match-Tests use 0-arg `new FarmPhase()`).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FarmPhase.java
git commit -m "feat: FarmPhase creates farm worlds and teleports teams on enter"
```

---

## Task 7: FinishedPhase — onEnter cleanup

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FinishedPhase.java`

- [ ] **Step 1: Replace FinishedPhase.java**

```java
package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class FinishedPhase implements MatchPhase {

    private final MobArmyBattle plugin;

    public FinishedPhase() {
        this(null);
    }

    public FinishedPhase(MobArmyBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public MatchPhaseType getType() {
        return MatchPhaseType.FINISHED;
    }

    @Override
    public void onEnter(Match match) {
        if (plugin == null) {
            return;
        }
        WorldManager wm = plugin.getWorldManager();
        for (String worldName : match.getAllFarmWorldNames().values()) {
            World w = Bukkit.getWorld(worldName);
            if (w != null) {
                wm.deleteWorld(w);
            }
        }
    }

    @Override
    public void onExit(Match match) {
    }

    @Override
    public void tick(Match match) {
    }
}
```

- [ ] **Step 2: Run tests** — all 40 pass.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FinishedPhase.java
git commit -m "feat: FinishedPhase cleans up farm worlds on enter"
```

---

## Task 8: PlayerConnectionListener

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/listener/PlayerConnectionListener.java`

- [ ] **Step 1: Implementation**

```java
package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final MatchManager matchManager;
    private final WorldManager worldManager;

    public PlayerConnectionListener(MatchManager matchManager, WorldManager worldManager) {
        this.matchManager = matchManager;
        this.worldManager = worldManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        if (matchManager.getMatchOf(event.getPlayer().getUniqueId()) != null) {
            return; // already in match (reconnect grace - left for later plan)
        }
        worldManager.teleportToLobby(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        // Plan 2: Disconnect leaves match immediately. Reconnect-grace comes in Plan 9.
        matchManager.leaveMatch(event.getPlayer().getUniqueId());
    }
}
```

- [ ] **Step 2: Build** — `./gradlew build` succeeds.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/listener/PlayerConnectionListener.java
git commit -m "feat: lobby-teleport on join, leave-match on quit"
```

---

## Task 9: MabCommand — FarmPhase mit Plugin-Ref + leave teleportiert

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java`

- [ ] **Step 1: Modifications**

Add field to MabCommand class:
```java
    private final MobArmyBattle plugin;
```

Add import:
```java
import de.klausiiiii.mobArmyBattle.MobArmyBattle;
```

Change constructor signature to:
```java
    public MabCommand(MobArmyBattle plugin, MatchManager matchManager) {
        this.plugin = plugin;
        this.matchManager = matchManager;
    }
```

In `handleStart`, change `match.transitionTo(new FarmPhase());` to:
```java
        match.transitionTo(new FarmPhase(plugin));
```

In `handleLeave`, before calling `matchManager.leaveMatch`, save the player ref. After the leave call, teleport to lobby:
```java
    private void handleLeave(Player player) {
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) {
            player.sendMessage(Component.text("Du bist in keinem Match.", NamedTextColor.RED));
            return;
        }
        matchManager.leaveMatch(player.getUniqueId());
        plugin.getWorldManager().teleportToLobby(player);
        player.sendMessage(Component.text("Match verlassen.", NamedTextColor.YELLOW));
    }
```

- [ ] **Step 2: Build** — succeeds.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java
git commit -m "refactor: MabCommand takes plugin ref, teleports on leave"
```

---

## Task 10: MainPlugin — alles verdrahten

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java`

- [ ] **Step 1: Replace MobArmyBattle.java**

```java
package de.klausiiiii.mobArmyBattle;

import de.klausiiiii.mobArmyBattle.command.MabCommand;
import de.klausiiiii.mobArmyBattle.listener.PlayerConnectionListener;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobArmyBattle extends JavaPlugin {

    private MatchManager matchManager;
    private WorldManager worldManager;

    @Override
    public void onEnable() {
        worldManager = new WorldManager(this);
        worldManager.cleanupOrphanWorlds();
        worldManager.getOrCreateLobbyWorld();

        matchManager = new MatchManager();

        PluginCommand mabCmd = getCommand("mab");
        if (mabCmd == null) {
            getLogger().severe("Befehl /mab nicht in plugin.yml deklariert!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        MabCommand mabHandler = new MabCommand(this, matchManager);
        mabCmd.setExecutor(mabHandler);
        mabCmd.setTabCompleter(mabHandler);

        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(matchManager, worldManager), this);

        getLogger().info("MobArmyBattle aktiviert.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MobArmyBattle deaktiviert.");
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }
}
```

- [ ] **Step 2: Build + tests** — all 40 tests pass, build succeeds.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java
git commit -m "feat: wire WorldManager, lobby-init, and connection listener"
```

---

## Task 11: Manueller End-to-End-Test

- [ ] **Step 1: Run server**: `./gradlew runServer`

- [ ] **Step 2: Im Spiel**

1. **Server-Join:** Spieler spawnt in Lobby (5x5 Quartz-Plattform mit Sign auf Y=64). Sign zeigt "MobArmyBattle / /mab create / zum Starten".
2. **`/mab create`:** Captain wird Captain, bleibt in Lobby.
3. **`/mab start`:** Welt `mab_farm_match-1_team-0` wird erstellt (Konsole zeigt "Farm-Welt erstellt: ..."), Captain wird teleportiert zum Welt-Spawn. Welt ist normal random Vanilla mit gleichem Seed wie für nicht-existierende anderen Teams.
4. **`/mab leave`:** Solo-Captain wird zur Lobby teleportiert. Da er Solo war: Match wird disbanded. Welt sollte gelöscht werden? **Hinweis:** Plan 2 löscht Welten erst bei FinishedPhase. Solo-leave geht aktuell nicht durch FinishedPhase. Das ist ein bekanntes Loose-End — Welten werden bei nächstem Server-Start als Orphan geräumt.
5. **`/stop` und re-`runServer`:** Beim Start wird die Orphan-Welt gelöscht (Konsole: "Lösche Orphan-Welt: ..."). Lobby existiert weiterhin.

- [ ] **Step 3: Multiplayer-Test (mit zweitem Account)**

1. A: `/mab create`, B: `/mab join A`. Beide in Lobby.
2. A: `/mab start`. Beide werden in eine gemeinsame Welt `mab_farm_match-1_team-0` teleportiert (gleicher Team).
3. B verlässt mit `/mab leave` → zurück zur Lobby. A bleibt in Farm-Welt.

---

## Acceptance Criteria

- [x] `./gradlew test` 40+ Tests grün
- [x] `./gradlew build` BUILD SUCCESSFUL
- [x] Beim Server-Start: Lobby-Welt existiert oder wird generiert, Orphan-Welten gelöscht
- [x] Spieler bei Server-Join in Lobby
- [x] Bei Match-Start: Farm-Welt(en) erstellt, Spieler teleportiert
- [x] Bei `/mab leave`: zurück in Lobby
- [x] Bei Match-Ende (über Phase-Übergang zu FINISHED): Farm-Welten gelöscht
- [x] Bei Server-Restart: alle aktiven Match-Welten als Orphans entfernt

## Bekannte Loose Ends (für spätere Pläne)

- **Solo-Leave löscht Welt nicht direkt** — Welt wird erst bei nächstem Server-Start aufgeräumt. Ein expliziter Cleanup bei MatchManager.leaveMatch wenn Match dadurch leer wird kommt später (sauberer wäre: bei Match-Disband auch Welten löschen).
- **Reconnect-Grace** — aktuell führt Disconnect zu sofortigem Match-Leave. Plan 9 implementiert die 5-Min-Grace-Periode.
- **WorldBorder** — Plan-2-Welten haben keinen Border. Spec sagt Default 1000. Kommt mit Config in Plan 9 oder bei Mob-Cap-Multiplier in Plan 3.
- **FarmPhase-Timer** — kein 60-min-Timer aktuell. Phase wechselt nicht automatisch. Kommt in Plan 3 oder Plan 4.
- **Welt-Generierung blockiert Server** — `Bukkit.createWorld` ist sync und kann sekundenlang dauern. Bei größerer Spielerzahl problematisch. Async-World-Erstellung mit Chunky-API oder ähnlich kommt in Plan 9 als Polish.
