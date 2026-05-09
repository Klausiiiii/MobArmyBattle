# MobArmyBattle Plan 5: Battle-Engine

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Match wechselt von WaveBuild zu Battle. Plugin pairt Teams in 2er-Gruppen (1+2, 3+4, etc.). Pro Team wird eine eigene Arena-Welt geladen (gleiche Struktur, isoliert). Beide Teams im Pair kämpfen parallel: jedes Team gegen die Wellen des Pair-Partners. Welle 1 spawnt sofort, Welle 2 unmittelbar nach allen Mobs aus Welle 1 tot. Sieger wird hierarchisch ermittelt (Wellen > Kills > Zeit). Solo-Tod = permanent down. Team-Tod = Spectator bis Welle 2 (kein Drop). Wer fertig ist, kann den Pair-Partner zuschauen.

**Architecture:** `TeamPair` value class. `BattleSession` pro Pair: hält beide Teams, Arena-Welten, aktuelle Welle pro Team, Statistik (Kills/Zeit/aktive-Welle). `ArenaLoader` lädt aus `.nbt`-Strukturdatei via Paper `StructureManager` ODER prozedural als Fallback. `ArenaSpawnScanner` extrahiert Banner-Marker (Lore-Tag `[MAB_SPAWN]`). `WaveSpawner` spawnt Mobs aus Wave-Slots an Spawn-Punkten, Equipment via Material-Lookup aus Signature-String. `BattlePhase.onEnter` pairt Teams, erstellt Arenas, teleportiert, startet Welle 1. `BattlePhase.tick` watcht Mob-Counts, spawnt Welle 2, ermittelt Sieger pro Pair. `BattleStats` für Sieger-Berechnung.

**Tech Stack:** Java 25, Paper API 26.1.2, JUnit 5. **Keine externen Plugins nötig** — Paper's `StructureManager` bietet `.nbt`-Loading vanilla.

**Plan-Umfang:** Plan 5 von ~10. Nach Plan 5: spielbares Match Ende-zu-Ende. Tournament-Bracket kommt in Plan 6, SQLite-Stats in Plan 7.

**Was nach Plan 5 spielbar ist:** Vollständiger Match-Lifecycle:
1. `/mab create N`, mehrere joinen, `/mab start` → Farm-Phase
2. Mobs killen → `/mab endfarm` → Wave-Build-GUI
3. Wellen bestätigen → Battle startet
4. Pair von Teams kämpft parallel in eigenen Arena-Welten
5. Sieger ermittelt → FinishedPhase → Lobby

**Annahmen:**
- Strukturdatei wird vom Admin in `plugins/MobArmyBattle/arenas/` als `.nbt` abgelegt (User wird das machen)
- Falls keine Datei vorhanden: prozeduraler Fallback (30×30 Bedrock-Plattform mit 4 Marker-Bannern an den Ecken)
- Battle ohne Drops; Farm-Welt-Drop-Verhalten unverändert (default soft penalty)
- Equipment beim Wave-Spawn nur Material (keine NBT/Verzauberungen) — Signature-Format reicht für Plan 5

---

## File Structure

| Datei | Zweck | Status |
|---|---|---|
| `src/main/java/de/klausiiiii/mobArmyBattle/battle/TeamPair.java` | Value class: zwei Teams die gegeneinander spielen | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/battle/TeamPairing.java` | Utility: pairt Liste von Teams in TeamPairs (Bye bei ungerade) | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/battle/BattleStats.java` | Per-Team-Stats: wavesSurvived, kills, finishTimeMs | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/battle/BattleResult.java` | Sieger-Berechnung: hierarchisch Wellen > Kills > Zeit | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/battle/BattleSession.java` | Match-Pair-Session: Welten, aktive Welle, Stats, Listen-Hooks | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/battle/WaveSpawner.java` | Bukkit: Spawnt Mobs aus Wave + SpawnPoints, setzt Equipment | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/battle/ArenaLoader.java` | Bukkit: Lädt Arena aus .nbt oder Fallback-prozedural | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/battle/ArenaSpawnScanner.java` | Bukkit: Findet Banner mit [MAB_SPAWN] Lore | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/battle/BattleManager.java` | Hält alle aktiven BattleSessions, Match → Sessions | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/listener/BattleEventListener.java` | EntityDeath in Battle (Mob-Kill-Count), PlayerDeath (no drop, perm down) | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/BattlePhase.java` | onEnter pairt + setup, tick checks | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/world/WorldManager.java` | createArenaWorld(matchId, teamId) | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java` | BattleManager + BattleEventListener wiring | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java` | `/mab spectate <teamN>` (Pair-Partner zuschauen) | Modify |
| Test files for: TeamPair, TeamPairing, BattleStats, BattleResult | Domain tests | Create |

---

## Task 1: TeamPair value class + TeamPairing utility (TDD)

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/battle/TeamPair.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/battle/TeamPairing.java`
- Test: `src/test/java/de/klausiiiii/mobArmyBattle/battle/TeamPairTest.java`
- Test: `src/test/java/de/klausiiiii/mobArmyBattle/battle/TeamPairingTest.java`

- [ ] **Step 1: Failing tests for TeamPair**

```java
package de.klausiiiii.mobArmyBattle.battle;

import de.klausiiiii.mobArmyBattle.match.Team;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamPairTest {

    @Test
    void pairHoldsTwoTeams() {
        Team a = new Team(UUID.randomUUID(), 1);
        Team b = new Team(UUID.randomUUID(), 1);
        TeamPair pair = new TeamPair(a, b);

        assertSame(a, pair.getTeamA());
        assertSame(b, pair.getTeamB());
    }

    @Test
    void rejectsSameTeamTwice() {
        Team a = new Team(UUID.randomUUID(), 1);
        assertThrows(IllegalArgumentException.class, () -> new TeamPair(a, a));
    }

    @Test
    void rejectsNullTeam() {
        Team a = new Team(UUID.randomUUID(), 1);
        assertThrows(IllegalArgumentException.class, () -> new TeamPair(null, a));
        assertThrows(IllegalArgumentException.class, () -> new TeamPair(a, null));
    }

    @Test
    void otherReturnsTheOtherTeam() {
        Team a = new Team(UUID.randomUUID(), 1);
        Team b = new Team(UUID.randomUUID(), 1);
        TeamPair pair = new TeamPair(a, b);

        assertSame(b, pair.other(a));
        assertSame(a, pair.other(b));
    }

    @Test
    void otherThrowsForUnknownTeam() {
        Team a = new Team(UUID.randomUUID(), 1);
        Team b = new Team(UUID.randomUUID(), 1);
        Team c = new Team(UUID.randomUUID(), 1);
        TeamPair pair = new TeamPair(a, b);

        assertThrows(IllegalArgumentException.class, () -> pair.other(c));
    }

    @Test
    void containsReturnsTrueForBothTeams() {
        Team a = new Team(UUID.randomUUID(), 1);
        Team b = new Team(UUID.randomUUID(), 1);
        Team c = new Team(UUID.randomUUID(), 1);
        TeamPair pair = new TeamPair(a, b);

        assertTrue(pair.contains(a));
        assertTrue(pair.contains(b));
        assertFalse(pair.contains(c));
    }
}
```

- [ ] **Step 2: Implement TeamPair.java**

```java
package de.klausiiiii.mobArmyBattle.battle;

import de.klausiiiii.mobArmyBattle.match.Team;

public final class TeamPair {

    private final Team teamA;
    private final Team teamB;

    public TeamPair(Team teamA, Team teamB) {
        if (teamA == null || teamB == null) {
            throw new IllegalArgumentException("Beide Teams müssen non-null sein");
        }
        if (teamA == teamB) {
            throw new IllegalArgumentException("Ein Team kann nicht gegen sich selbst kämpfen");
        }
        this.teamA = teamA;
        this.teamB = teamB;
    }

    public Team getTeamA() {
        return teamA;
    }

    public Team getTeamB() {
        return teamB;
    }

    public boolean contains(Team team) {
        return team == teamA || team == teamB;
    }

    public Team other(Team team) {
        if (team == teamA) return teamB;
        if (team == teamB) return teamA;
        throw new IllegalArgumentException("Team ist nicht in diesem Pair");
    }
}
```

- [ ] **Step 3: Failing tests for TeamPairing**

```java
package de.klausiiiii.mobArmyBattle.battle;

import de.klausiiiii.mobArmyBattle.match.Team;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamPairingTest {

    @Test
    void pairsTwoTeamsIntoOnePair() {
        Team a = new Team(UUID.randomUUID(), 1);
        Team b = new Team(UUID.randomUUID(), 1);

        TeamPairing.Result result = TeamPairing.pair(List.of(a, b));

        assertEquals(1, result.getPairs().size());
        assertTrue(result.getPairs().get(0).contains(a));
        assertTrue(result.getPairs().get(0).contains(b));
        assertTrue(result.getByeTeams().isEmpty());
    }

    @Test
    void pairsFourTeamsIntoTwoPairs() {
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < 4; i++) teams.add(new Team(UUID.randomUUID(), 1));

        TeamPairing.Result result = TeamPairing.pair(teams);

        assertEquals(2, result.getPairs().size());
        assertTrue(result.getByeTeams().isEmpty());
    }

    @Test
    void pairsThreeTeamsLeavesOneBye() {
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < 3; i++) teams.add(new Team(UUID.randomUUID(), 1));

        TeamPairing.Result result = TeamPairing.pair(teams);

        assertEquals(1, result.getPairs().size());
        assertEquals(1, result.getByeTeams().size());
    }

    @Test
    void pairsEightTeamsIntoFourPairs() {
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < 8; i++) teams.add(new Team(UUID.randomUUID(), 1));

        TeamPairing.Result result = TeamPairing.pair(teams);

        assertEquals(4, result.getPairs().size());
        assertTrue(result.getByeTeams().isEmpty());
    }

    @Test
    void rejectsLessThanTwoTeams() {
        Team a = new Team(UUID.randomUUID(), 1);
        assertThrows(IllegalArgumentException.class, () -> TeamPairing.pair(List.of(a)));
        assertThrows(IllegalArgumentException.class, () -> TeamPairing.pair(List.of()));
    }

    @Test
    void everyTeamAppearsExactlyOnce() {
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < 6; i++) teams.add(new Team(UUID.randomUUID(), 1));

        TeamPairing.Result result = TeamPairing.pair(teams);

        java.util.Set<Team> seen = new java.util.HashSet<>();
        for (TeamPair pair : result.getPairs()) {
            assertTrue(seen.add(pair.getTeamA()), "Team A in pair was already seen");
            assertTrue(seen.add(pair.getTeamB()), "Team B in pair was already seen");
        }
        seen.addAll(result.getByeTeams());
        assertEquals(teams.size(), seen.size());
    }
}
```

- [ ] **Step 4: Implement TeamPairing.java**

```java
package de.klausiiiii.mobArmyBattle.battle;

import de.klausiiiii.mobArmyBattle.match.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TeamPairing {

    private TeamPairing() {
    }

    public static Result pair(List<Team> teams) {
        if (teams == null || teams.size() < 2) {
            throw new IllegalArgumentException("Pairing braucht mindestens 2 Teams");
        }
        List<TeamPair> pairs = new ArrayList<>();
        List<Team> byes = new ArrayList<>();
        int i = 0;
        while (i + 1 < teams.size()) {
            pairs.add(new TeamPair(teams.get(i), teams.get(i + 1)));
            i += 2;
        }
        if (i < teams.size()) {
            byes.add(teams.get(i));
        }
        return new Result(pairs, byes);
    }

    public static final class Result {
        private final List<TeamPair> pairs;
        private final List<Team> byeTeams;

        Result(List<TeamPair> pairs, List<Team> byeTeams) {
            this.pairs = Collections.unmodifiableList(pairs);
            this.byeTeams = Collections.unmodifiableList(byeTeams);
        }

        public List<TeamPair> getPairs() {
            return pairs;
        }

        public List<Team> getByeTeams() {
            return byeTeams;
        }
    }
}
```

- [ ] **Step 5: Run tests, all pass (114 + 11 = 125)**

- [ ] **Step 6: Commit**: `feat: add TeamPair + TeamPairing for battle pairing`

---

## Task 2: BattleStats + BattleResult (TDD)

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/battle/BattleStats.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/battle/BattleResult.java`
- Tests: `src/test/java/de/klausiiiii/mobArmyBattle/battle/BattleStatsTest.java`
- Tests: `src/test/java/de/klausiiiii/mobArmyBattle/battle/BattleResultTest.java`

- [ ] **Step 1: Failing tests for BattleStats**

```java
package de.klausiiiii.mobArmyBattle.battle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BattleStatsTest {

    @Test
    void newStatsHasZeroEverything() {
        BattleStats stats = new BattleStats();
        assertEquals(0, stats.getWavesSurvived());
        assertEquals(0, stats.getMobKills());
        assertEquals(0L, stats.getFinishTimeMs());
        assertFalse(stats.isFinished());
    }

    @Test
    void canIncrementKills() {
        BattleStats stats = new BattleStats();
        stats.recordMobKill();
        stats.recordMobKill();
        assertEquals(2, stats.getMobKills());
    }

    @Test
    void canMarkWaveSurvived() {
        BattleStats stats = new BattleStats();
        stats.recordWaveSurvived();
        assertEquals(1, stats.getWavesSurvived());
    }

    @Test
    void canMarkFinished() {
        BattleStats stats = new BattleStats();
        stats.markFinished(1234L);
        assertTrue(stats.isFinished());
        assertEquals(1234L, stats.getFinishTimeMs());
    }

    @Test
    void cannotMarkFinishedTwice() {
        BattleStats stats = new BattleStats();
        stats.markFinished(100L);
        assertThrows(IllegalStateException.class, () -> stats.markFinished(200L));
    }
}
```

- [ ] **Step 2: Implement BattleStats**

```java
package de.klausiiiii.mobArmyBattle.battle;

public class BattleStats {

    private int wavesSurvived = 0;
    private int mobKills = 0;
    private long finishTimeMs = 0L;
    private boolean finished = false;

    public int getWavesSurvived() {
        return wavesSurvived;
    }

    public int getMobKills() {
        return mobKills;
    }

    public long getFinishTimeMs() {
        return finishTimeMs;
    }

    public boolean isFinished() {
        return finished;
    }

    public void recordMobKill() {
        mobKills++;
    }

    public void recordWaveSurvived() {
        wavesSurvived++;
    }

    public void markFinished(long elapsedMs) {
        if (finished) {
            throw new IllegalStateException("Stats sind bereits finalisiert");
        }
        this.finishTimeMs = elapsedMs;
        this.finished = true;
    }
}
```

- [ ] **Step 3: Failing tests for BattleResult**

```java
package de.klausiiiii.mobArmyBattle.battle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BattleResultTest {

    private BattleStats stats(int waves, int kills, long timeMs, boolean finished) {
        BattleStats s = new BattleStats();
        for (int i = 0; i < waves; i++) s.recordWaveSurvived();
        for (int i = 0; i < kills; i++) s.recordMobKill();
        if (finished) s.markFinished(timeMs);
        return s;
    }

    @Test
    void winnerByMoreWaves() {
        BattleStats a = stats(2, 5, 1000L, true);
        BattleStats b = stats(1, 50, 100L, true);
        assertEquals(BattleResult.Winner.A, BattleResult.compare(a, b));
    }

    @Test
    void tiedWavesWinnerByMoreKills() {
        BattleStats a = stats(2, 10, 5000L, true);
        BattleStats b = stats(2, 20, 5000L, true);
        assertEquals(BattleResult.Winner.B, BattleResult.compare(a, b));
    }

    @Test
    void tiedWavesAndKillsWinnerByFasterTime() {
        BattleStats a = stats(2, 10, 1000L, true);
        BattleStats b = stats(2, 10, 5000L, true);
        assertEquals(BattleResult.Winner.A, BattleResult.compare(a, b));
    }

    @Test
    void allEqualReturnsDraw() {
        BattleStats a = stats(2, 10, 1000L, true);
        BattleStats b = stats(2, 10, 1000L, true);
        assertEquals(BattleResult.Winner.DRAW, BattleResult.compare(a, b));
    }

    @Test
    void notFinishedTreatedAsTimeMaxValue() {
        BattleStats a = stats(2, 10, 0L, false);
        BattleStats b = stats(2, 10, 5000L, true);
        assertEquals(BattleResult.Winner.B, BattleResult.compare(a, b));
    }
}
```

- [ ] **Step 4: Implement BattleResult**

```java
package de.klausiiiii.mobArmyBattle.battle;

public final class BattleResult {

    public enum Winner { A, B, DRAW }

    private BattleResult() {
    }

    public static Winner compare(BattleStats a, BattleStats b) {
        if (a.getWavesSurvived() != b.getWavesSurvived()) {
            return a.getWavesSurvived() > b.getWavesSurvived() ? Winner.A : Winner.B;
        }
        if (a.getMobKills() != b.getMobKills()) {
            return a.getMobKills() > b.getMobKills() ? Winner.A : Winner.B;
        }
        long aTime = a.isFinished() ? a.getFinishTimeMs() : Long.MAX_VALUE;
        long bTime = b.isFinished() ? b.getFinishTimeMs() : Long.MAX_VALUE;
        if (aTime != bTime) {
            return aTime < bTime ? Winner.A : Winner.B;
        }
        return Winner.DRAW;
    }
}
```

- [ ] **Step 5: Tests pass — 130 total**

- [ ] **Step 6: Commit**: `feat: add BattleStats + BattleResult with hierarchical winner logic`

---

## Task 3: WorldManager — createArenaWorld

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/world/WorldManager.java`

Add method `createArenaWorld(matchId, teamId)` that creates a flat VOID world for arena (Bukkit-only). Building the actual arena content (loading structure or fallback platform) happens in ArenaLoader (Task 5).

- [ ] **Step 1: Modify**

Add after `createFarmWorld`:

```java
    public World createArenaWorld(String matchId, String teamId) {
        String name = ARENA_WORLD_PREFIX + matchId + "_" + teamId;
        WorldCreator creator = new WorldCreator(name)
                .generator(new LobbyChunkGenerator())
                .type(WorldType.FLAT);
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            throw new IllegalStateException("Konnte Arena-Welt nicht erstellen: " + name);
        }
        world.setKeepSpawnInMemory(false);
        log.info("Arena-Welt erstellt: " + name);
        return world;
    }
```

- [ ] **Step 2: Build OK**

- [ ] **Step 3: Commit**: `feat: WorldManager.createArenaWorld for empty VOID arena worlds`

---

## Task 4: ArenaSpawnScanner

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/battle/ArenaSpawnScanner.java`

Bukkit-bound. Iteriert geladene Chunks, sucht nach Bannern mit Lore-Tag `[MAB_SPAWN]` (lowercased plain text), nimmt deren Locations, entfernt die Banner.

- [ ] **Step 1: Implementation**

```java
package de.klausiiiii.mobArmyBattle.battle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class ArenaSpawnScanner {

    public static final String SPAWN_TAG = "[MAB_SPAWN]";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ArenaSpawnScanner() {
    }

    public static List<Location> scanAndConsume(World world, int radius) {
        List<Location> spawns = new ArrayList<>();
        for (Chunk chunk : world.getLoadedChunks()) {
            for (BlockState state : chunk.getTileEntities()) {
                if (!(state instanceof Banner banner)) continue;
                if (!hasSpawnTag(banner)) continue;
                Location loc = banner.getLocation().add(0.5, 0, 0.5);
                spawns.add(loc);
                banner.getBlock().setType(Material.AIR);
            }
        }
        if (spawns.isEmpty()) {
            // Fallback: use world spawn location
            spawns.add(world.getSpawnLocation().add(0.5, 1, 0.5));
        }
        return spawns;
    }

    private static boolean hasSpawnTag(Banner banner) {
        // The plain-text representation of the banner's customName + lore
        Component name = banner.customName();
        if (name != null) {
            String text = PLAIN.serialize(name);
            if (text.contains(SPAWN_TAG)) return true;
        }
        // Banners don't have lore via API; rely on customName.
        // Admins set banners with customName containing [MAB_SPAWN].
        return false;
    }
}
```

- [ ] **Step 2: Build OK**

- [ ] **Step 3: Commit**: `feat: ArenaSpawnScanner finds [MAB_SPAWN] banners`

---

## Task 5: ArenaLoader

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/battle/ArenaLoader.java`

Lädt Arena entweder aus `.nbt`-Strukturdatei oder generiert prozedural Fallback (30×30 Bedrock-Plattform mit 4 Marker-Bannern an den Ecken).

- [ ] **Step 1: Implementation**

```java
package de.klausiiiii.mobArmyBattle.battle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.StructureManager;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ArenaLoader {

    private static final int FALLBACK_RADIUS = 15;
    private static final int FALLBACK_Y = 64;

    private final JavaPlugin plugin;
    private final Logger log;
    private final File arenasFolder;

    public ArenaLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.arenasFolder = new File(plugin.getDataFolder(), "arenas");
        if (!arenasFolder.exists()) {
            arenasFolder.mkdirs();
        }
    }

    /**
     * Populates the given world with arena content. Tries .nbt files first;
     * if none found, generates a fallback bedrock platform.
     */
    public void loadInto(World world) {
        File[] files = arenasFolder.listFiles((dir, name) -> name.endsWith(".nbt"));
        if (files != null && files.length > 0) {
            File chosen = files[(int) (Math.random() * files.length)];
            try {
                StructureManager mgr = Bukkit.getStructureManager();
                org.bukkit.structure.Structure structure = mgr.loadStructure(chosen);
                if (structure != null) {
                    org.bukkit.Location origin = world.getSpawnLocation().clone();
                    structure.place(origin, true, org.bukkit.block.structure.StructureRotation.NONE,
                            org.bukkit.block.structure.Mirror.NONE,
                            -1, 1.0f, java.util.Random.from(java.util.random.RandomGenerator.getDefault()));
                    log.info("Arena geladen aus " + chosen.getName());
                    return;
                }
            } catch (IOException e) {
                log.log(Level.WARNING, "Arena-Datei " + chosen.getName() + " nicht ladbar, fallback aktiv", e);
            }
        }
        buildFallbackArena(world);
    }

    private void buildFallbackArena(World world) {
        log.info("Fallback-Arena wird gebaut in " + world.getName());
        // 30x30 bedrock platform at Y=63, players spawn at Y=65, marker banners at corners
        for (int x = -FALLBACK_RADIUS; x <= FALLBACK_RADIUS; x++) {
            for (int z = -FALLBACK_RADIUS; z <= FALLBACK_RADIUS; z++) {
                world.getBlockAt(x, FALLBACK_Y - 1, z).setType(Material.BEDROCK);
            }
        }
        // Place 4 marker banners at corners
        placeMarker(world, FALLBACK_RADIUS - 1, FALLBACK_Y, FALLBACK_RADIUS - 1);
        placeMarker(world, FALLBACK_RADIUS - 1, FALLBACK_Y, -FALLBACK_RADIUS + 1);
        placeMarker(world, -FALLBACK_RADIUS + 1, FALLBACK_Y, FALLBACK_RADIUS - 1);
        placeMarker(world, -FALLBACK_RADIUS + 1, FALLBACK_Y, -FALLBACK_RADIUS + 1);
        // World spawn at center
        world.setSpawnLocation(0, FALLBACK_Y, 0);
    }

    private void placeMarker(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z).setType(Material.RED_BANNER);
        BlockState state = world.getBlockAt(x, y, z).getState();
        if (state instanceof Banner banner) {
            banner.customName(Component.text(ArenaSpawnScanner.SPAWN_TAG, NamedTextColor.RED));
            banner.update();
        }
    }

    public org.bukkit.Location getPlayerSpawn(World world) {
        return world.getSpawnLocation().clone().add(0.5, 1, 0.5);
    }
}
```

- [ ] **Step 2: Build OK**

(Note: Paper's StructureManager API may have slight differences in 26.1.2 — if compile fails, simplify to fallback-only for now and note that Plan 5+ will revisit StructureManager once a working .nbt is available.)

- [ ] **Step 3: Commit**: `feat: ArenaLoader with .nbt support and procedural fallback`

---

## Task 6: WaveSpawner

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/battle/WaveSpawner.java`

- [ ] **Step 1: Implementation**

```java
package de.klausiiiii.mobArmyBattle.battle;

import de.klausiiiii.mobArmyBattle.pool.MobEntry;
import de.klausiiiii.mobArmyBattle.wave.Wave;
import de.klausiiiii.mobArmyBattle.wave.WaveSlot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WaveSpawner {

    private static final Random RNG = new Random();

    public List<LivingEntity> spawnWave(World world, List<Location> spawnPoints, Wave wave) {
        List<LivingEntity> spawned = new ArrayList<>();
        if (spawnPoints.isEmpty() || wave.isForfeited()) return spawned;
        int spawnIdx = 0;
        for (WaveSlot slot : wave.getSlots()) {
            EntityType type = parseType(slot.getEntry().getEntityTypeName());
            if (type == null) continue;
            for (int i = 0; i < slot.getCount(); i++) {
                Location loc = spawnPoints.get(spawnIdx % spawnPoints.size());
                spawnIdx++;
                org.bukkit.entity.Entity ent = world.spawnEntity(loc, type);
                if (ent instanceof LivingEntity living) {
                    applyEquipment(living, slot.getEntry().getEquipmentSignature());
                    spawned.add(living);
                }
            }
        }
        return spawned;
    }

    private EntityType parseType(String name) {
        try {
            return EntityType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void applyEquipment(LivingEntity entity, String signature) {
        if (signature == null || signature.equals("none|none|none|none|none|none")) return;
        EntityEquipment eq = entity.getEquipment();
        if (eq == null) return;
        String[] parts = signature.split("\\|");
        if (parts.length != 6) return;
        eq.setItemInMainHand(itemFor(parts[0]));
        eq.setItemInOffHand(itemFor(parts[1]));
        eq.setHelmet(itemFor(parts[2]));
        eq.setChestplate(itemFor(parts[3]));
        eq.setLeggings(itemFor(parts[4]));
        eq.setBoots(itemFor(parts[5]));
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
    }

    private ItemStack itemFor(String slot) {
        if (slot.equals("none")) return null;
        Material mat = Material.matchMaterial(slot);
        if (mat == null) return null;
        return new ItemStack(mat);
    }
}
```

- [ ] **Step 2: Build OK**

- [ ] **Step 3: Commit**: `feat: WaveSpawner spawns mobs from Wave + spawn points`

---

## Task 7: BattleSession

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/battle/BattleSession.java`

Hält Pair, Welten, Spawn-Points, Stats, aktuelle Welle pro Team. Dieser Code orchestriert pro Pair den Lifecycle.

- [ ] **Step 1: Implementation**

```java
package de.klausiiiii.mobArmyBattle.battle;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.wave.Wave;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BattleSession {

    public static class TeamState {
        public final Team team;
        public final Team opponent;
        public final World arena;
        public final List<Location> spawnPoints;
        public final BattleStats stats;
        public int currentWaveNumber = 0;  // 0 = pre, 1/2 = active, 3 = done
        public final Set<UUID> aliveLivingMobs = new HashSet<>();
        public final Set<UUID> downedPlayers = new HashSet<>();

        public TeamState(Team team, Team opponent, World arena, List<Location> spawnPoints) {
            this.team = team;
            this.opponent = opponent;
            this.arena = arena;
            this.spawnPoints = spawnPoints;
            this.stats = new BattleStats();
        }
    }

    private final Match match;
    private final TeamPair pair;
    private final TeamState stateA;
    private final TeamState stateB;
    private final long startTimeMs;
    private boolean concluded = false;

    public BattleSession(Match match, TeamPair pair,
                         World arenaA, List<Location> spawnsA,
                         World arenaB, List<Location> spawnsB) {
        this.match = match;
        this.pair = pair;
        this.stateA = new TeamState(pair.getTeamA(), pair.getTeamB(), arenaA, spawnsA);
        this.stateB = new TeamState(pair.getTeamB(), pair.getTeamA(), arenaB, spawnsB);
        this.startTimeMs = System.currentTimeMillis();
    }

    public Match getMatch() {
        return match;
    }

    public TeamPair getPair() {
        return pair;
    }

    public TeamState getStateA() {
        return stateA;
    }

    public TeamState getStateB() {
        return stateB;
    }

    public TeamState getStateOf(Team team) {
        if (team == stateA.team) return stateA;
        if (team == stateB.team) return stateB;
        return null;
    }

    public TeamState getStateByMobUUID(UUID mobUUID) {
        if (stateA.aliveLivingMobs.contains(mobUUID)) return stateA;
        if (stateB.aliveLivingMobs.contains(mobUUID)) return stateB;
        return null;
    }

    public TeamState getStateByPlayerUUID(UUID playerUUID) {
        if (stateA.team.hasMember(playerUUID)) return stateA;
        if (stateB.team.hasMember(playerUUID)) return stateB;
        return null;
    }

    public boolean isConcluded() {
        return concluded;
    }

    public void markConcluded() {
        concluded = true;
    }

    public long elapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }

    /**
     * The opponent's wave to spawn for the given team's arena.
     * Wave 1 of opponent = first wave attacking team.
     */
    public Wave nextWaveForTeam(TeamState state) {
        Wave w1 = state.opponent.getWave1();
        Wave w2 = state.opponent.getWave2();
        if (state.currentWaveNumber == 0) return w1;
        if (state.currentWaveNumber == 1) return w2;
        return null;
    }
}
```

- [ ] **Step 2: Build OK**

- [ ] **Step 3: Commit**: `feat: BattleSession holds per-pair battle state`

---

## Task 8: BattleManager + BattleEventListener + BattlePhase

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/battle/BattleManager.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/listener/BattleEventListener.java`
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/BattlePhase.java`

These three pieces work together. BattleManager holds `Map<Match, List<BattleSession>>`. Phase calls `setup(match)` which:
1. Pairs teams via TeamPairing (bye → auto-winner FinishedPhase logic later, in Plan 5 just give bye-team a free win on stats)
2. Per pair: create 2 arena worlds, load via ArenaLoader, scan spawn points, teleport teams, spawn first opponent's wave for each side, store BattleSession
3. Listener tracks mob deaths to update stats, player deaths for "downed" handling

- [ ] **Step 1: BattleManager.java**

```java
package de.klausiiiii.mobArmyBattle.battle;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.wave.Wave;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BattleManager {

    private final MobArmyBattle plugin;
    private final ArenaLoader arenaLoader;
    private final WaveSpawner waveSpawner = new WaveSpawner();
    private final Map<String, List<BattleSession>> matchSessions = new HashMap<>();
    private final Map<UUID, BattleSession> sessionByMobUUID = new HashMap<>();

    public BattleManager(MobArmyBattle plugin) {
        this.plugin = plugin;
        this.arenaLoader = new ArenaLoader(plugin);
    }

    public void startBattlesFor(Match match) {
        List<Team> activeTeams = new ArrayList<>();
        for (Team t : match.getTeams()) {
            if (!t.isDisbanded() && t.size() > 0) activeTeams.add(t);
        }
        TeamPairing.Result pairing = TeamPairing.pair(activeTeams);
        List<BattleSession> sessions = new ArrayList<>();
        WorldManager wm = plugin.getWorldManager();

        for (TeamPair pair : pairing.getPairs()) {
            World arenaA = wm.createArenaWorld(match.getId(), idOf(match, pair.getTeamA()));
            arenaLoader.loadInto(arenaA);
            List<Location> spawnsA = ArenaSpawnScanner.scanAndConsume(arenaA, 50);

            World arenaB = wm.createArenaWorld(match.getId(), idOf(match, pair.getTeamB()));
            arenaLoader.loadInto(arenaB);
            List<Location> spawnsB = ArenaSpawnScanner.scanAndConsume(arenaB, 50);

            BattleSession session = new BattleSession(match, pair, arenaA, spawnsA, arenaB, spawnsB);
            teleportTeam(pair.getTeamA(), arenaA);
            teleportTeam(pair.getTeamB(), arenaB);

            startNextWave(session, session.getStateA());
            startNextWave(session, session.getStateB());

            sessions.add(session);
        }
        matchSessions.put(match.getId(), sessions);

        for (Team byeTeam : pairing.getByeTeams()) {
            for (UUID id : byeTeam.getMemberIds()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.sendMessage("§eDu hast einen Bye — automatischer Sieg.");
                }
            }
        }
    }

    private String idOf(Match match, Team team) {
        int idx = match.getTeams().indexOf(team);
        return "team-" + idx;
    }

    private void teleportTeam(Team team, World arena) {
        Location spawn = arenaLoader.getPlayerSpawn(arena);
        for (UUID id : team.getMemberIds()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.teleport(spawn);
                p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
                p.setFoodLevel(20);
            }
        }
    }

    private void startNextWave(BattleSession session, BattleSession.TeamState state) {
        state.currentWaveNumber++;
        Wave wave = state.opponent.getWave1();
        if (state.currentWaveNumber == 2) {
            wave = state.opponent.getWave2();
        }
        if (wave == null || wave.isForfeited() || wave.totalMobCount() == 0) {
            broadcastTeam(state.team, "§eGegner-Welle " + state.currentWaveNumber + " ist leer/forfeit — übersprungen.");
            state.stats.recordWaveSurvived();
            checkAdvance(session, state);
            return;
        }
        List<LivingEntity> mobs = waveSpawner.spawnWave(state.arena, state.spawnPoints, wave);
        for (LivingEntity m : mobs) {
            state.aliveLivingMobs.add(m.getUniqueId());
            sessionByMobUUID.put(m.getUniqueId(), session);
        }
        broadcastTeam(state.team, "§6Welle " + state.currentWaveNumber + " gestartet — " + mobs.size() + " Mobs.");
    }

    public void onMobKilled(UUID mobUUID, UUID killerUUID) {
        BattleSession session = sessionByMobUUID.remove(mobUUID);
        if (session == null) return;
        BattleSession.TeamState state = session.getStateByMobUUID(mobUUID);
        // After remove() above we lost the marker, recompute
        if (state == null) {
            // Try both states
            if (session.getStateA().aliveLivingMobs.contains(mobUUID)) state = session.getStateA();
            else if (session.getStateB().aliveLivingMobs.contains(mobUUID)) state = session.getStateB();
        }
        if (state == null) return;
        state.aliveLivingMobs.remove(mobUUID);
        if (killerUUID != null && state.team.hasMember(killerUUID)) {
            state.stats.recordMobKill();
        }
        if (state.aliveLivingMobs.isEmpty()) {
            state.stats.recordWaveSurvived();
            checkAdvance(session, state);
        }
    }

    private void checkAdvance(BattleSession session, BattleSession.TeamState state) {
        if (state.currentWaveNumber >= 2) {
            // team finished both opponent waves alive
            if (!state.stats.isFinished()) {
                state.stats.markFinished(session.elapsedMs());
                broadcastTeam(state.team, "§aDu hast beide Wellen überlebt!");
            }
            checkSessionEnd(session);
        } else {
            startNextWave(session, state);
        }
    }

    public void onPlayerDeath(UUID playerUUID) {
        for (List<BattleSession> sessions : matchSessions.values()) {
            for (BattleSession session : sessions) {
                BattleSession.TeamState state = session.getStateByPlayerUUID(playerUUID);
                if (state == null) continue;
                state.downedPlayers.add(playerUUID);
                Player p = Bukkit.getPlayer(playerUUID);
                if (p != null) {
                    p.setGameMode(org.bukkit.GameMode.SPECTATOR);
                }
                // Check if all team members are down
                boolean allDown = true;
                for (UUID id : state.team.getMemberIds()) {
                    if (!state.downedPlayers.contains(id)) {
                        allDown = false;
                        break;
                    }
                }
                if (allDown) {
                    if (!state.stats.isFinished()) {
                        state.stats.markFinished(session.elapsedMs());
                    }
                    broadcastTeam(state.team, "§cAlle Spieler tot — Battle für euer Team beendet.");
                    checkSessionEnd(session);
                }
                return;
            }
        }
    }

    private void checkSessionEnd(BattleSession session) {
        BattleSession.TeamState a = session.getStateA();
        BattleSession.TeamState b = session.getStateB();
        if (!a.stats.isFinished() || !b.stats.isFinished()) return;
        if (session.isConcluded()) return;
        session.markConcluded();
        BattleResult.Winner winner = BattleResult.compare(a.stats, b.stats);
        announceResult(session, winner);
        // Check if all sessions in match are done
        Match match = session.getMatch();
        List<BattleSession> all = matchSessions.get(match.getId());
        if (all != null && all.stream().allMatch(BattleSession::isConcluded)) {
            match.transitionTo(new de.klausiiiii.mobArmyBattle.match.phase.FinishedPhase(plugin));
        }
    }

    private void announceResult(BattleSession session, BattleResult.Winner winner) {
        BattleSession.TeamState a = session.getStateA();
        BattleSession.TeamState b = session.getStateB();
        String msg;
        if (winner == BattleResult.Winner.A) {
            msg = "§a" + teamName(a.team) + " gewinnt gegen " + teamName(b.team);
        } else if (winner == BattleResult.Winner.B) {
            msg = "§a" + teamName(b.team) + " gewinnt gegen " + teamName(a.team);
        } else {
            msg = "§e" + teamName(a.team) + " vs " + teamName(b.team) + ": Unentschieden";
        }
        broadcastTeam(a.team, msg);
        broadcastTeam(b.team, msg);
    }

    private String teamName(Team team) {
        Player cap = Bukkit.getPlayer(team.getCaptainId());
        return cap != null ? "Team " + cap.getName() : "Team";
    }

    private void broadcastTeam(Team team, String message) {
        for (UUID id : team.getMemberIds()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(message);
        }
    }

    public void cleanup(Match match) {
        List<BattleSession> sessions = matchSessions.remove(match.getId());
        if (sessions == null) return;
        for (BattleSession s : sessions) {
            for (UUID mob : s.getStateA().aliveLivingMobs) sessionByMobUUID.remove(mob);
            for (UUID mob : s.getStateB().aliveLivingMobs) sessionByMobUUID.remove(mob);
            World a = s.getStateA().arena;
            World b = s.getStateB().arena;
            plugin.getWorldManager().deleteWorld(a);
            plugin.getWorldManager().deleteWorld(b);
        }
    }
}
```

- [ ] **Step 2: BattleEventListener.java**

```java
package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.battle.BattleManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class BattleEventListener implements Listener {

    private final BattleManager battleManager;

    public BattleEventListener(BattleManager battleManager) {
        this.battleManager = battleManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;
        Player killer = event.getEntity().getKiller();
        battleManager.onMobKilled(event.getEntity().getUniqueId(),
                killer != null ? killer.getUniqueId() : null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Drop suppression for battle worlds: handled by world-name check
        Player p = event.getEntity();
        String worldName = p.getWorld().getName();
        if (worldName.startsWith(de.klausiiiii.mobArmyBattle.world.WorldManager.ARENA_WORLD_PREFIX)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            battleManager.onPlayerDeath(p.getUniqueId());
        }
    }
}
```

- [ ] **Step 3: BattlePhase.java rewrite**

```java
package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;

public class BattlePhase implements MatchPhase {

    private final MobArmyBattle plugin;

    public BattlePhase() {
        this(null);
    }

    public BattlePhase(MobArmyBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public MatchPhaseType getType() {
        return MatchPhaseType.BATTLE;
    }

    @Override
    public void onEnter(Match match) {
        if (plugin == null) return;
        plugin.getBattleManager().startBattlesFor(match);
    }

    @Override
    public void onExit(Match match) {
        if (plugin == null) return;
        plugin.getBattleManager().cleanup(match);
    }

    @Override
    public void tick(Match match) {
    }
}
```

- [ ] **Step 4: Modify MobArmyBattle.java** to wire BattleManager + Listener

Add field:
```java
    private BattleManager battleManager;
```

Add import:
```java
import de.klausiiiii.mobArmyBattle.battle.BattleManager;
import de.klausiiiii.mobArmyBattle.listener.BattleEventListener;
```

In `onEnable`, after `waveBuildGui` registration:
```java
        battleManager = new BattleManager(this);
        getServer().getPluginManager().registerEvents(
                new BattleEventListener(battleManager), this);
```

Add getter:
```java
    public BattleManager getBattleManager() {
        return battleManager;
    }
```

- [ ] **Step 5: Build OK + tests pass**

- [ ] **Step 6: Commit**: `feat: BattleManager orchestrates pair-based battles with mob spawning and winner detection`

---

## Task 9: Manueller End-to-End-Test

- [ ] **Step 1**: `./gradlew runServer`

- [ ] **Step 2**:
1. `/op <name>`, `/gamemode creative`
2. Player A: `/mab create 1`
3. Player B: `/mab join A`
4. Player A: `/mab start` → Farm-Welt erstellt
5. Beide killen Mobs (z.B. `/summon zombie ~ ~ ~` + `/effect give @s strength 9999 100`, attack)
6. A: `/mab endfarm` → GUI öffnet sich
7. Beide bauen Welle 1 + Welle 2 (kann auch forfeit) und bestätigen
8. Battle startet → beide werden in eigene Arena-Welt teleportiert (Bedrock-Plattform 30×30)
9. Welle 1 spawnt: Pool-Mobs aus Gegner-Welle 1 erscheinen
10. Killen → Welle 2 spawnt → killen
11. Beide fertig → Sieger wird verkündet → FinishedPhase → zurück zur Lobby

---

## Acceptance Criteria

- [x] 130+ Tests grün
- [x] Build OK
- [x] Battle: 2 Teams werden gepaired, jedes in eigener Arena
- [x] Welle 1 spawnt aus Gegner-Welle, Welle 2 nahtlos danach
- [x] Sieger nach Kriterien: Wellen > Kills > Zeit
- [x] Solo-Tod = Spectator (Plan 5: keine Respawn-Logik, "spectator" via Bukkit GameMode)
- [x] Battle-Welt: kein Item-Drop, kein XP-Drop, KeepInventory
- [x] Match endet nach allen Pairs → FinishedPhase → Lobby

## Bekannte Loose Ends

- **Spectator-Modus:** Plan 5 nutzt einfach Bukkit Spectator-GameMode bei Tod. Volle "kann zu anderem Pair zusehen"-Logik kommt in Plan 8 (UI-Polish).
- **Vorbereitungsphase:** 30s Block-Platzieren vor Welle 1 fehlt — kommt mit Config in Plan 9.
- **Live-Bauen während Battle:** Aktuell volles Bauen erlaubt (Vanilla). Restrictions kommen mit Config später.
- **Welle 2 Pause:** 10s Pause zwischen Welle 1 und 2 fehlt (Spec sagt 10s default) — direkt anschließend.
- **Hard-Timeout pro Welle:** 10min default fehlt — Battle endet nicht wenn Spieler unfähig ist Mobs zu killen.
- **Tournament-Bracket:** Kommt in Plan 6.
- **NBT-Strukturdaten:** Aktuell mit prozeduralem Fallback. Wenn der Admin eine `.nbt` in `plugins/MobArmyBattle/arenas/` ablegt, wird sie geladen.
