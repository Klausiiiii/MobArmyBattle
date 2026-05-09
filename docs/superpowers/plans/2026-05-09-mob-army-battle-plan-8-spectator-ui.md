# Plan 8: Spectator + Sidebar + Notifications — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** UI-Polish-Layer für laufende Matches: per-Player Sidebar-Scoreboard mit phasenabhängigem Inhalt, `/mab spectate <captain>` für besiegte Tournament-Teams + Battle-Pair-Partner-Spectator, sowie Title+Sound-Notifications bei Phase-Wechsel, Welle-Spawn/Bestanden und Match-Sieger.

**Architecture:** Drei kleine Manager + statische Notifications-Util. `SidebarRenderer` ist Bukkit-frei und unit-getestet (3 Layouts). `SidebarManager` plumbt Renderer an Bukkit-Scoreboard pro Player. `Notifications` ist Title+Sound-Wrapper. `SpectatorManager` hält Map<UUID, SpectateState> und checkt Permission gegen `BattleManager` (Pair-Partner) und `TournamentManager` (eliminated). Notification-Aufrufe werden in Phase-Klassen + BattleManager-Hooks injiziert.

**Tech Stack:** Java 25, Paper API 26.1.2, Adventure Components, Bukkit Scoreboard, JUnit 5.

---

## Architektur-Überblick

| Klasse | Bukkit-frei? | Zweck |
|---|---|---|
| `ui/SidebarRenderer` | ✅ | Pure render-Funktion, drei Layouts (FARM/WAVE_BUILD/BATTLE), max 14 Zeilen, Pool-Truncate auf Top-7 |
| `ui/BattleContext` | ✅ | Record mit Battle-Stats (mobsAlive, kills, etc.) — Input für Renderer |
| `ui/SidebarManager` | ❌ | Tickt parallel zu BossBar, hält pro Player Bukkit-Scoreboard |
| `ui/Notifications` | ❌ | Statische Title+Sound-Helpers |
| `spectator/SpectateState` | ✅ | Record `(matchId, arenaWorldName)` — returnLocation in Manager-Map |
| `spectator/SpectatorManager` | ❌ | Permission + Teleport + GameMode + Cleanup |

## Sidebar-Layouts (referenziert von Tasks 1-3)

**FARM:**
```
§e§lMobArmyBattle
§7Phase: §aFarm
§7Zeit: §f47:23
§7§m─────────
§6Pool:
§7 Zombie x84
§7 Skelett(I) x12
[…bis Top-7]
§7§m─────────
§7Teams aktiv: 5/8
```

**WAVE_BUILD:**
```
§e§lMobArmyBattle
§7Phase: §eWelle bauen
§7Zeit: §f03:41
§7§m─────────
§7Welle 1: §f12 Mobs
§7Welle 2: §c0 Mobs
§7§m─────────
§7Teams aktiv: 5/8
```

**BATTLE:**
```
§e§lMobArmyBattle
§7Phase: §cBattle - W1
§7Zeit: §f02:14
§7§m─────────
§7Mobs übrig: §f8/20
§7Kills: §f12
§7Team: §f3/4 lebt
§7§m─────────
§7Pair gegen §fFooCaptain
§7Teams aktiv: 5/8
```

## Notifications-Inhalte (referenziert von Tasks 7, 12, 13)

| Event | Title | Subtitle | Sound |
|---|---|---|---|
| FARM onEnter | §a§lFarm-Phase | §7Sammelt Mobs | UI_TOAST_CHALLENGE_COMPLETE |
| WAVE_BUILD onEnter | §e§lWellen bauen | §7Captain: /mab build | BLOCK_NOTE_BLOCK_PLING |
| BATTLE onEnter | §c§lBattle! | §7Welle 1 startet gleich | ENTITY_ENDER_DRAGON_GROWL |
| Welle-Spawn | §6§lWelle X | §7N Mobs | ENTITY_ZOMBIE_AMBIENT |
| Welle-Bestanden | §a§lWelle X bestanden | (leer) | UI_TOAST_CHALLENGE_COMPLETE |
| Match-Sieger | §6§lSIEG | §7<TeamName> | UI_TOAST_CHALLENGE_COMPLETE |
| Match-Verlierer | §c§lNiederlage | §7<Sieger-Team> | ENTITY_VILLAGER_NO |

Title fade: 10/40/10 ticks.

## Scope-Cuts

- Keine 1-Min-Warnung vor Phase-Ende.
- Kein Spectator-GUI; `/mab spectate` ohne Args zeigt Text-Liste.
- Pool-Truncate Top-7.
- Tournament-Spectate: kein Auto-Hop bei Match-Wechsel; User ruft `/mab spectate` neu auf.
- Mob-Kills nur Team-Aggregat, nicht per-Player.

---

## Tasks

### Task 1: SidebarRenderer FARM-Layout

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/ui/BattleContext.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/ui/SidebarRenderer.java`
- Create: `src/test/java/de/klausiiiii/mobArmyBattle/ui/SidebarRendererTest.java`

- [ ] **Step 1: Write failing tests for FARM layout**

```java
// src/test/java/de/klausiiiii/mobArmyBattle/ui/SidebarRendererTest.java
package de.klausiiiii.mobArmyBattle.ui;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.match.phase.FarmPhase;
import de.klausiiiii.mobArmyBattle.pool.MobEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SidebarRendererTest {

    private static final String NAKED = "none|none|none|none|none|none";

    @Test
    void rendersFarmLayoutWithEmptyPool() {
        Match match = new Match("m1");
        Team t = new Team(UUID.randomUUID(), 4);
        match.addTeam(t);
        match.addTeam(new Team(UUID.randomUUID(), 4));
        match.transitionTo(new FarmPhase());

        List<String> lines = SidebarRenderer.render(match, t, null, match.getPhaseStartedAt());

        assertEquals("§e§lMobArmyBattle", lines.get(0));
        assertEquals("§7Phase: §aFarm", lines.get(1));
        assertTrue(lines.get(2).startsWith("§7Zeit: §f"));
        assertTrue(lines.contains("§6Pool:"));
        assertEquals("§7Teams aktiv: 2/2", lines.get(lines.size() - 1));
    }

    @Test
    void rendersFarmLayoutWithPopulatedPool() {
        Match match = new Match("m1");
        Team t = new Team(UUID.randomUUID(), 4);
        match.addTeam(t);
        match.addTeam(new Team(UUID.randomUUID(), 4));
        match.transitionTo(new FarmPhase());
        for (int i = 0; i < 5; i++) t.getPool().add(new MobEntry("ZOMBIE", NAKED));
        for (int i = 0; i < 3; i++) t.getPool().add(new MobEntry("SKELETON", NAKED));

        List<String> lines = SidebarRenderer.render(match, t, null, match.getPhaseStartedAt());

        assertTrue(lines.stream().anyMatch(l -> l.contains("Zombie") && l.contains("x5")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("Skeleton") && l.contains("x3")));
    }

    @Test
    void truncatesPoolToTop7Entries() {
        Match match = new Match("m1");
        Team t = new Team(UUID.randomUUID(), 4);
        match.addTeam(t);
        match.addTeam(new Team(UUID.randomUUID(), 4));
        match.transitionTo(new FarmPhase());
        String[] types = {"ZOMBIE", "SKELETON", "SPIDER", "CREEPER", "ENDERMAN", "WITCH", "PIGLIN", "BLAZE", "GHAST"};
        for (int i = 0; i < types.length; i++) {
            for (int j = 0; j <= i; j++) t.getPool().add(new MobEntry(types[i], NAKED));
        }

        List<String> lines = SidebarRenderer.render(match, t, null, match.getPhaseStartedAt());

        long poolLines = lines.stream().filter(l -> l.startsWith("§7 ")).count();
        assertEquals(7, poolLines, "Pool sollte auf Top-7 truncated sein");
    }
}
```

- [ ] **Step 2: Run tests — should fail (compile error)**

```
./gradlew.bat test --tests de.klausiiiii.mobArmyBattle.ui.SidebarRendererTest
```
Expected: compile fail (BattleContext, SidebarRenderer don't exist).

- [ ] **Step 3: Create BattleContext record**

```java
// src/main/java/de/klausiiiii/mobArmyBattle/ui/BattleContext.java
package de.klausiiiii.mobArmyBattle.ui;

public record BattleContext(
        int mobsAlive,
        int mobsTotalThisWave,
        int mobKills,
        int teamMembersAlive,
        int teamMembersTotal,
        int currentWaveNumber,
        String pairCaptainName
) {}
```

- [ ] **Step 4: Implement SidebarRenderer with FARM-only logic**

```java
// src/main/java/de/klausiiiii/mobArmyBattle/ui/SidebarRenderer.java
package de.klausiiiii.mobArmyBattle.ui;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.pool.MobEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class SidebarRenderer {

    private static final int MAX_POOL_LINES = 7;
    private static final String SEP = "§7§m─────────";

    private SidebarRenderer() {}

    public static List<String> render(Match match, Team viewer, BattleContext battleCtx, long currentTimeMs) {
        MatchPhaseType phase = match.getCurrentPhase().getType();
        return switch (phase) {
            case FARM -> renderFarm(match, viewer, currentTimeMs);
            case WAVE_BUILD -> List.of();  // Task 2
            case BATTLE -> List.of();       // Task 3
            default -> List.of();           // LOBBY/FINISHED → empty
        };
    }

    private static List<String> renderFarm(Match match, Team viewer, long currentTimeMs) {
        List<String> lines = new ArrayList<>();
        lines.add("§e§lMobArmyBattle");
        lines.add("§7Phase: §aFarm");
        lines.add("§7Zeit: §f" + formatElapsed(match.getPhaseStartedAt(), currentTimeMs));
        lines.add(SEP);
        lines.add("§6Pool:");
        appendPoolLines(lines, viewer);
        lines.add(SEP);
        lines.add("§7Teams aktiv: " + activeTeamCount(match) + "/" + match.getTeams().size());
        return lines;
    }

    private static void appendPoolLines(List<String> out, Team viewer) {
        List<Map.Entry<MobEntry, Integer>> sorted = new ArrayList<>(viewer.getPool().getEntries().entrySet());
        sorted.sort(Comparator.<Map.Entry<MobEntry, Integer>>comparingInt(Map.Entry::getValue).reversed());
        int n = Math.min(sorted.size(), MAX_POOL_LINES);
        for (int i = 0; i < n; i++) {
            Map.Entry<MobEntry, Integer> e = sorted.get(i);
            out.add("§7 " + prettyName(e.getKey()) + " x" + e.getValue());
        }
    }

    private static String prettyName(MobEntry entry) {
        String type = entry.getEntityType();
        String pretty = type.charAt(0) + type.substring(1).toLowerCase().replace('_', ' ');
        if (!"none|none|none|none|none|none".equals(entry.getEquipmentSignature())) {
            pretty = pretty + " (geared)";
        }
        return pretty;
    }

    private static int activeTeamCount(Match match) {
        int n = 0;
        for (Team t : match.getTeams()) if (!t.isDisbanded() && t.size() > 0) n++;
        return n;
    }

    static String formatElapsed(long startMs, long nowMs) {
        long sec = Math.max(0, (nowMs - startMs) / 1000);
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }
}
```

- [ ] **Step 5: Run tests — should pass**

```
./gradlew.bat test --tests de.klausiiiii.mobArmyBattle.ui.SidebarRendererTest
```
Expected: 3 tests pass.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/ui/ src/test/java/de/klausiiiii/mobArmyBattle/ui/
git commit -m "feat(ui): SidebarRenderer FARM layout + BattleContext record"
```

---

### Task 2: SidebarRenderer WAVE_BUILD layout

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/ui/SidebarRenderer.java` (renderWaveBuild)
- Modify: `src/test/java/de/klausiiiii/mobArmyBattle/ui/SidebarRendererTest.java` (3 tests)

- [ ] **Step 1: Add failing tests for WAVE_BUILD layout**

Append to `SidebarRendererTest.java`:

```java
    @Test
    void rendersWaveBuildLayoutWithBothWavesEmpty() {
        Match match = waveBuildMatch();
        Team t = match.getTeams().get(0);

        List<String> lines = SidebarRenderer.render(match, t, null, match.getPhaseStartedAt());

        assertEquals("§7Phase: §eWelle bauen", lines.get(1));
        assertTrue(lines.stream().anyMatch(l -> l.equals("§7Welle 1: §c0 Mobs")));
        assertTrue(lines.stream().anyMatch(l -> l.equals("§7Welle 2: §c0 Mobs")));
    }

    @Test
    void rendersWaveBuildWithFilledWave() {
        Match match = waveBuildMatch();
        Team t = match.getTeams().get(0);
        t.getWave1().add(new MobEntry("ZOMBIE", NAKED), 8);
        t.getWave1().add(new MobEntry("SKELETON", NAKED), 4);

        List<String> lines = SidebarRenderer.render(match, t, null, match.getPhaseStartedAt());

        assertTrue(lines.contains("§7Welle 1: §f12 Mobs"));
    }

    @Test
    void rendersForfeitedWaveAsZero() {
        Match match = waveBuildMatch();
        Team t = match.getTeams().get(0);
        t.getWave2().forfeit();

        List<String> lines = SidebarRenderer.render(match, t, null, match.getPhaseStartedAt());

        assertTrue(lines.contains("§7Welle 2: §c0 Mobs"));
    }

    private static Match waveBuildMatch() {
        Match m = new Match("m1");
        m.addTeam(new Team(UUID.randomUUID(), 4));
        m.addTeam(new Team(UUID.randomUUID(), 4));
        m.transitionTo(new de.klausiiiii.mobArmyBattle.match.phase.FarmPhase());
        m.transitionTo(new de.klausiiiii.mobArmyBattle.match.phase.WaveBuildPhase());
        return m;
    }
```

- [ ] **Step 2: Run tests — should fail**

```
./gradlew.bat test --tests de.klausiiiii.mobArmyBattle.ui.SidebarRendererTest
```
Expected: 3 new tests fail (WAVE_BUILD branch returns empty list).

- [ ] **Step 3: Implement renderWaveBuild**

In `SidebarRenderer.java`, replace `case WAVE_BUILD -> List.of();` with `case WAVE_BUILD -> renderWaveBuild(match, viewer, currentTimeMs);` and add:

```java
    private static List<String> renderWaveBuild(Match match, Team viewer, long currentTimeMs) {
        List<String> lines = new ArrayList<>();
        lines.add("§e§lMobArmyBattle");
        lines.add("§7Phase: §eWelle bauen");
        lines.add("§7Zeit: §f" + formatElapsed(match.getPhaseStartedAt(), currentTimeMs));
        lines.add(SEP);
        lines.add(waveLine(1, viewer.getWave1().totalMobCount()));
        lines.add(waveLine(2, viewer.getWave2().totalMobCount()));
        lines.add(SEP);
        lines.add("§7Teams aktiv: " + activeTeamCount(match) + "/" + match.getTeams().size());
        return lines;
    }

    private static String waveLine(int n, int count) {
        String color = count == 0 ? "§c" : "§f";
        return "§7Welle " + n + ": " + color + count + " Mobs";
    }
```

- [ ] **Step 4: Run tests — pass**

```
./gradlew.bat test --tests de.klausiiiii.mobArmyBattle.ui.SidebarRendererTest
```
Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/ui/SidebarRenderer.java src/test/java/de/klausiiiii/mobArmyBattle/ui/SidebarRendererTest.java
git commit -m "feat(ui): SidebarRenderer WAVE_BUILD layout"
```

---

### Task 3: SidebarRenderer BATTLE layout

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/ui/SidebarRenderer.java`
- Modify: `src/test/java/de/klausiiiii/mobArmyBattle/ui/SidebarRendererTest.java`

- [ ] **Step 1: Add failing tests for BATTLE layout**

```java
    @Test
    void rendersBattleLayoutWithLiveData() {
        Match match = battleMatch();
        Team t = match.getTeams().get(0);
        BattleContext ctx = new BattleContext(8, 20, 12, 3, 4, 1, "FooCaptain");

        List<String> lines = SidebarRenderer.render(match, t, ctx, match.getPhaseStartedAt());

        assertTrue(lines.contains("§7Phase: §cBattle - W1"));
        assertTrue(lines.contains("§7Mobs übrig: §f8/20"));
        assertTrue(lines.contains("§7Kills: §f12"));
        assertTrue(lines.contains("§7Team: §f3/4 lebt"));
        assertTrue(lines.contains("§7Pair gegen §fFooCaptain"));
    }

    @Test
    void rendersBattleAllMembersDownInRed() {
        Match match = battleMatch();
        Team t = match.getTeams().get(0);
        BattleContext ctx = new BattleContext(0, 20, 5, 0, 4, 2, "FooCaptain");

        List<String> lines = SidebarRenderer.render(match, t, ctx, match.getPhaseStartedAt());

        assertTrue(lines.contains("§7Team: §c0/4 lebt"));
        assertTrue(lines.contains("§7Phase: §cBattle - W2"));
    }

    private static Match battleMatch() {
        Match m = new Match("m1");
        m.addTeam(new Team(UUID.randomUUID(), 4));
        m.addTeam(new Team(UUID.randomUUID(), 4));
        m.transitionTo(new de.klausiiiii.mobArmyBattle.match.phase.FarmPhase());
        m.transitionTo(new de.klausiiiii.mobArmyBattle.match.phase.WaveBuildPhase());
        m.transitionTo(new de.klausiiiii.mobArmyBattle.match.phase.BattlePhase());
        return m;
    }
```

- [ ] **Step 2: Run tests — fail**

Expected: 2 new tests fail.

- [ ] **Step 3: Implement renderBattle**

Replace `case BATTLE -> List.of();` with `case BATTLE -> renderBattle(match, viewer, battleCtx, currentTimeMs);` and add:

```java
    private static List<String> renderBattle(Match match, Team viewer, BattleContext ctx, long currentTimeMs) {
        List<String> lines = new ArrayList<>();
        lines.add("§e§lMobArmyBattle");
        lines.add("§7Phase: §cBattle - W" + (ctx == null ? 0 : ctx.currentWaveNumber()));
        lines.add("§7Zeit: §f" + formatElapsed(match.getPhaseStartedAt(), currentTimeMs));
        lines.add(SEP);
        if (ctx == null) {
            lines.add("§7(Daten lädt …)");
        } else {
            lines.add("§7Mobs übrig: §f" + ctx.mobsAlive() + "/" + ctx.mobsTotalThisWave());
            lines.add("§7Kills: §f" + ctx.mobKills());
            String teamColor = ctx.teamMembersAlive() == 0 ? "§c" : "§f";
            lines.add("§7Team: " + teamColor + ctx.teamMembersAlive() + "/" + ctx.teamMembersTotal() + " lebt");
        }
        lines.add(SEP);
        if (ctx != null && ctx.pairCaptainName() != null) {
            lines.add("§7Pair gegen §f" + ctx.pairCaptainName());
        }
        lines.add("§7Teams aktiv: " + activeTeamCount(match) + "/" + match.getTeams().size());
        return lines;
    }
```

- [ ] **Step 4: Run tests — pass**

```
./gradlew.bat test --tests de.klausiiiii.mobArmyBattle.ui.SidebarRendererTest
```
Expected: 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/ui/SidebarRenderer.java src/test/java/de/klausiiiii/mobArmyBattle/ui/SidebarRendererTest.java
git commit -m "feat(ui): SidebarRenderer BATTLE layout"
```

---

### Task 4: SpectateState record

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/spectator/SpectateState.java`
- Create: `src/test/java/de/klausiiiii/mobArmyBattle/spectator/SpectateStateTest.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/de/klausiiiii/mobArmyBattle/spectator/SpectateStateTest.java
package de.klausiiiii.mobArmyBattle.spectator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpectateStateTest {

    @Test
    void recordHoldsMatchAndArenaIds() {
        SpectateState s = new SpectateState("match-1", "mab_arena_match-1_team-0-arena");
        assertEquals("match-1", s.matchId());
        assertEquals("mab_arena_match-1_team-0-arena", s.arenaWorldName());
    }

    @Test
    void recordEqualsByValue() {
        SpectateState a = new SpectateState("m", "w");
        SpectateState b = new SpectateState("m", "w");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
```

- [ ] **Step 2: Run — fail (class missing)**

```
./gradlew.bat test --tests de.klausiiiii.mobArmyBattle.spectator.SpectateStateTest
```

- [ ] **Step 3: Create record**

```java
// src/main/java/de/klausiiiii/mobArmyBattle/spectator/SpectateState.java
package de.klausiiiii.mobArmyBattle.spectator;

public record SpectateState(String matchId, String arenaWorldName) {
    public SpectateState {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId darf nicht leer sein");
        }
        if (arenaWorldName == null || arenaWorldName.isBlank()) {
            throw new IllegalArgumentException("arenaWorldName darf nicht leer sein");
        }
    }
}
```

- [ ] **Step 4: Run — pass**

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/spectator/ src/test/java/de/klausiiiii/mobArmyBattle/spectator/
git commit -m "feat(spectator): SpectateState domain record"
```

---

### Task 5: TournamentRound.activeCaptains()

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/tournament/TournamentRound.java`
- Modify: `src/test/java/de/klausiiiii/mobArmyBattle/tournament/TournamentRoundTest.java`

- [ ] **Step 1: Add failing tests**

Append to `TournamentRoundTest.java`:

```java
    @Test
    void activeCaptainsIncludesUnfinishedPairings() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        TournamentPairing p = new TournamentPairing(a, b);
        p.setMatchId("match-1");
        TournamentRound r = new TournamentRound(1, java.util.List.of(p), null);

        assertEquals(java.util.Set.of(a, b), r.activeCaptains());
    }

    @Test
    void activeCaptainsExcludesLoser() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        TournamentPairing p = new TournamentPairing(a, b);
        p.setMatchId("match-1");
        p.setWinner(a);
        TournamentRound r = new TournamentRound(1, java.util.List.of(p), null);

        assertEquals(java.util.Set.of(a), r.activeCaptains());
    }

    @Test
    void activeCaptainsIncludesByeCaptain() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), bye = UUID.randomUUID();
        TournamentPairing p = new TournamentPairing(a, b);
        p.setMatchId("match-1");
        TournamentRound r = new TournamentRound(1, java.util.List.of(p), bye);

        assertTrue(r.activeCaptains().contains(bye));
        assertEquals(3, r.activeCaptains().size());
    }
```

- [ ] **Step 2: Run — fail**

```
./gradlew.bat test --tests de.klausiiiii.mobArmyBattle.tournament.TournamentRoundTest
```

- [ ] **Step 3: Implement activeCaptains in TournamentRound.java**

Add method:

```java
    public java.util.Set<UUID> activeCaptains() {
        java.util.Set<UUID> active = new java.util.HashSet<>();
        for (TournamentPairing p : pairings) {
            if (p.isFinished()) {
                if (p.getWinner() != null) active.add(p.getWinner());
            } else {
                active.add(p.getCaptainA());
                active.add(p.getCaptainB());
            }
        }
        if (byeCaptain != null) active.add(byeCaptain);
        return active;
    }
```

> Note: `TournamentPairing.getCaptainA()/getCaptainB()` may not exist yet — check; if missing add them in this same task.

- [ ] **Step 4: Run — pass**

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/tournament/TournamentRound.java src/main/java/de/klausiiiii/mobArmyBattle/tournament/TournamentPairing.java src/test/java/de/klausiiiii/mobArmyBattle/tournament/TournamentRoundTest.java
git commit -m "feat(tournament): TournamentRound.activeCaptains()"
```

---

### Task 6: Tournament.isEliminated()

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/tournament/Tournament.java`
- Modify: `src/test/java/de/klausiiiii/mobArmyBattle/tournament/TournamentTest.java`

- [ ] **Step 1: Add failing tests**

Append to `TournamentTest.java`:

```java
    @Test
    void isEliminatedFalseForRegisteringTournament() {
        Tournament t = new Tournament("t1", "T1", UUID.randomUUID());
        UUID c = UUID.randomUUID();
        t.register(c);
        assertFalse(t.isEliminated(c));
    }

    @Test
    void isEliminatedFalseForUnregisteredCaptain() {
        Tournament t = freshRunning(2);
        assertFalse(t.isEliminated(UUID.randomUUID()));
    }

    @Test
    void isEliminatedTrueAfterLosingPairing() {
        Tournament t = new Tournament("t1", "T1", UUID.randomUUID());
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        t.register(a);
        t.register(b);
        t.start(new java.util.Random(0));
        TournamentPairing p = t.getCurrentRound().getPairings().get(0);
        p.setMatchId("m1");
        UUID winner = p.getCaptainA();
        UUID loser = p.getCaptainB();
        t.recordPairingWinner("m1", winner);

        assertFalse(t.isEliminated(winner));
        assertTrue(t.isEliminated(loser));
    }

    private static Tournament freshRunning(int n) {
        Tournament t = new Tournament("t" + n, "T" + n, UUID.randomUUID());
        for (int i = 0; i < n; i++) t.register(UUID.randomUUID());
        t.start(new java.util.Random(0));
        return t;
    }
```

- [ ] **Step 2: Run — fail**

- [ ] **Step 3: Implement isEliminated in Tournament.java**

Add method:

```java
    public boolean isEliminated(UUID captainId) {
        if (status != Status.RUNNING) return false;
        if (!registered.contains(captainId)) return false;
        TournamentRound round = getCurrentRound();
        if (round == null) return false;
        return !round.activeCaptains().contains(captainId);
    }
```

- [ ] **Step 4: Run — pass**

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/tournament/Tournament.java src/test/java/de/klausiiiii/mobArmyBattle/tournament/TournamentTest.java
git commit -m "feat(tournament): Tournament.isEliminated()"
```

---

### Task 7: Notifications utility

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/ui/Notifications.java`

(No unit tests — Bukkit-aware. Manual smoke-test later.)

- [ ] **Step 1: Create Notifications.java**

```java
package de.klausiiiii.mobArmyBattle.ui;

import de.klausiiiii.mobArmyBattle.match.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.UUID;

public final class Notifications {

    private static final Title.Times TIMES =
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500));

    private Notifications() {}

    public static void farmStart(Team team) {
        sendTeam(team, "§a§lFarm-Phase", "§7Sammelt Mobs", Sound.UI_TOAST_CHALLENGE_COMPLETE);
    }

    public static void waveBuildStart(Team team) {
        sendTeam(team, "§e§lWellen bauen", "§7Captain: /mab build", Sound.BLOCK_NOTE_BLOCK_PLING);
    }

    public static void battleStart(Team team) {
        sendTeam(team, "§c§lBattle!", "§7Welle 1 startet gleich", Sound.ENTITY_ENDER_DRAGON_GROWL);
    }

    public static void waveSpawned(Team team, int waveNumber, int mobCount) {
        sendTeam(team, "§6§lWelle " + waveNumber, "§7" + mobCount + " Mobs", Sound.ENTITY_ZOMBIE_AMBIENT);
    }

    public static void wavePassed(Team team, int waveNumber) {
        sendTeam(team, "§a§lWelle " + waveNumber + " bestanden", "", Sound.UI_TOAST_CHALLENGE_COMPLETE);
    }

    public static void victory(Team team, String winnerTeamName) {
        sendTeam(team, "§6§lSIEG", "§7" + winnerTeamName, Sound.UI_TOAST_CHALLENGE_COMPLETE);
    }

    public static void defeat(Team team, String winnerTeamName) {
        sendTeam(team, "§c§lNiederlage", "§7" + winnerTeamName, Sound.ENTITY_VILLAGER_NO);
    }

    private static void sendTeam(Team team, String title, String subtitle, Sound sound) {
        Title t = Title.title(Component.text(title), Component.text(subtitle), TIMES);
        for (UUID id : team.getMemberIds()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.showTitle(t);
                p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
            }
        }
    }
}
```

- [ ] **Step 2: Verify compile**

```
./gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/ui/Notifications.java
git commit -m "feat(ui): Notifications static title+sound helpers"
```

---

### Task 8: BattleManager helper hooks (getSessionByPlayer, currentWaveSpawnedTotal)

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/battle/BattleSession.java`
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/battle/BattleManager.java`

- [ ] **Step 1: Add field to BattleSession.TeamState**

In `BattleSession.java` inside `TeamState`, add after `currentWaveNumber`:

```java
        public int currentWaveSpawnedTotal = 0;
```

- [ ] **Step 2: Set field on spawn in BattleManager.startNextWave**

In `BattleManager.java` `startNextWave`, after the `for (LivingEntity m : mobs) {…}` loop, add:

```java
        state.currentWaveSpawnedTotal = mobs.size();
```

(Move it BEFORE the existing `broadcastTeam(state.team, ...)` line; keep the broadcast.)

- [ ] **Step 3: Add getSessionByPlayer to BattleManager**

```java
    public BattleSession getSessionByPlayer(UUID playerUUID) {
        for (java.util.List<BattleSession> sessions : matchSessions.values()) {
            for (BattleSession session : sessions) {
                if (session.getStateByPlayerUUID(playerUUID) != null) return session;
            }
        }
        return null;
    }
```

- [ ] **Step 4: Verify compile + existing tests pass**

```
./gradlew.bat build
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/battle/BattleSession.java src/main/java/de/klausiiiii/mobArmyBattle/battle/BattleManager.java
git commit -m "feat(battle): track wave spawned total + expose getSessionByPlayer"
```

---

### Task 9: TournamentManager.findEliminatedTournamentOf

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/tournament/TournamentManager.java`

- [ ] **Step 1: Add method**

```java
    public Tournament findEliminatedTournamentOf(UUID playerId) {
        for (Tournament t : tournaments.values()) {
            if (t.isEliminated(playerId)) return t;
        }
        return null;
    }
```

(Variable `tournaments` is whatever existing internal Map is named — check the file. If iteration field is named differently, adapt. Use `getAll()` if exposed.)

- [ ] **Step 2: Verify compile**

```
./gradlew.bat compileJava
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/tournament/TournamentManager.java
git commit -m "feat(tournament): findEliminatedTournamentOf for spectate permission"
```

---

### Task 10: SpectatorManager

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/spectator/SpectatorManager.java`

- [ ] **Step 1: Create SpectatorManager.java**

```java
package de.klausiiiii.mobArmyBattle.spectator;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.battle.BattleManager;
import de.klausiiiii.mobArmyBattle.battle.BattleSession;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.tournament.Tournament;
import de.klausiiiii.mobArmyBattle.tournament.TournamentManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SpectatorManager {

    public sealed interface PermissionResult {
        record Allowed(String matchId, String arenaWorldName) implements PermissionResult {}
        record Denied(String reason) implements PermissionResult {}
    }

    private final MobArmyBattle plugin;
    private final MatchManager matchManager;
    private final BattleManager battleManager;
    private final TournamentManager tournamentManager;
    private final Map<UUID, SpectateState> states = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();

    public SpectatorManager(MobArmyBattle plugin,
                            MatchManager matchManager,
                            BattleManager battleManager,
                            TournamentManager tournamentManager) {
        this.plugin = plugin;
        this.matchManager = matchManager;
        this.battleManager = battleManager;
        this.tournamentManager = tournamentManager;
    }

    public PermissionResult checkPermission(UUID viewerId, UUID targetCaptainId) {
        Match targetMatch = findMatchByCaptain(targetCaptainId);
        if (targetMatch == null) {
            return new PermissionResult.Denied("Captain ist nicht in einem laufenden Match.");
        }
        if (targetMatch.getCurrentPhase().getType() != MatchPhaseType.BATTLE) {
            return new PermissionResult.Denied("Match ist nicht in der Battle-Phase.");
        }
        Team targetTeam = targetMatch.findTeamOf(targetCaptainId);
        if (targetTeam == null) {
            return new PermissionResult.Denied("Target-Team nicht gefunden.");
        }
        String arenaWorld = arenaWorldNameFor(targetMatch, targetTeam);
        if (Bukkit.getWorld(arenaWorld) == null) {
            return new PermissionResult.Denied("Arena-Welt existiert nicht.");
        }

        Match viewerMatch = matchManager.getMatchOf(viewerId);
        if (viewerMatch != null && viewerMatch.getId().equals(targetMatch.getId())) {
            BattleSession session = battleManager.getSessionByPlayer(viewerId);
            if (session == null) return new PermissionResult.Denied("Du bist nicht in einer Battle-Session.");
            BattleSession.TeamState own = session.getStateByPlayerUUID(viewerId);
            if (own == null || !own.stats.isFinished()) {
                return new PermissionResult.Denied("Du musst erst beide Wellen abschließen.");
            }
            if (own.opponent != targetTeam) {
                return new PermissionResult.Denied("Nur dein Pair-Partner kann zugeschaut werden.");
            }
            return new PermissionResult.Allowed(targetMatch.getId(), arenaWorld);
        }

        Tournament tournament = tournamentManager.findEliminatedTournamentOf(viewerId);
        if (tournament != null) {
            return new PermissionResult.Allowed(targetMatch.getId(), arenaWorld);
        }

        return new PermissionResult.Denied("Du darfst nicht zuschauen.");
    }

    public boolean startSpectate(Player viewer, UUID targetCaptainId) {
        PermissionResult res = checkPermission(viewer.getUniqueId(), targetCaptainId);
        if (res instanceof PermissionResult.Denied d) {
            viewer.sendMessage("§c" + d.reason());
            return false;
        }
        PermissionResult.Allowed allowed = (PermissionResult.Allowed) res;
        World arena = Bukkit.getWorld(allowed.arenaWorldName());
        if (arena == null) {
            viewer.sendMessage("§cArena-Welt nicht gefunden.");
            return false;
        }

        if (!states.containsKey(viewer.getUniqueId())) {
            returnLocations.put(viewer.getUniqueId(), viewer.getLocation());
        }
        Location spawn = arena.getSpawnLocation();
        viewer.teleport(spawn);
        viewer.setGameMode(GameMode.SPECTATOR);
        states.put(viewer.getUniqueId(), new SpectateState(allowed.matchId(), allowed.arenaWorldName()));
        viewer.sendMessage("§7Spectator-Mode aktiv. /mab leave zum Beenden.");
        return true;
    }

    public void endSpectate(UUID viewerId) {
        SpectateState state = states.remove(viewerId);
        Location ret = returnLocations.remove(viewerId);
        Player p = Bukkit.getPlayer(viewerId);
        if (p == null) return;
        if (state != null) {
            World lobby = plugin.getWorldManager().getOrCreateLobbyWorld();
            Location target = ret != null ? ret : lobby.getSpawnLocation();
            p.teleport(target);
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    public void evictAll(String matchId) {
        List<UUID> toEvict = new ArrayList<>();
        for (Map.Entry<UUID, SpectateState> e : states.entrySet()) {
            if (e.getValue().matchId().equals(matchId)) toEvict.add(e.getKey());
        }
        for (UUID id : toEvict) endSpectate(id);
    }

    public boolean isSpectating(UUID viewerId) {
        return states.containsKey(viewerId);
    }

    public List<String> listAvailableTargets(UUID viewerId) {
        List<String> out = new ArrayList<>();
        for (Match m : matchManager.getActiveMatches()) {
            if (m.getCurrentPhase().getType() != MatchPhaseType.BATTLE) continue;
            for (Team t : m.getTeams()) {
                UUID cap = t.getCaptainId();
                if (cap == null) continue;
                PermissionResult res = checkPermission(viewerId, cap);
                if (res instanceof PermissionResult.Allowed) {
                    Player p = Bukkit.getPlayer(cap);
                    if (p != null) out.add(p.getName());
                }
            }
        }
        return out;
    }

    private Match findMatchByCaptain(UUID captainId) {
        for (Match m : matchManager.getActiveMatches()) {
            Team t = m.findTeamOf(captainId);
            if (t != null && captainId.equals(t.getCaptainId())) return m;
        }
        return null;
    }

    private static String arenaWorldNameFor(Match match, Team team) {
        int idx = match.getTeams().indexOf(team);
        return de.klausiiiii.mobArmyBattle.world.WorldManager.ARENA_WORLD_PREFIX + match.getId() + "_team-" + idx + "-arena";
    }
}
```

> Note: `WorldManager.ARENA_WORLD_PREFIX` exists per CLAUDE.md. Verify naming pattern matches `BattleManager.idOf` (`team-<idx>-arena`).

- [ ] **Step 2: Verify compile**

```
./gradlew.bat compileJava
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/spectator/SpectatorManager.java
git commit -m "feat(spectator): SpectatorManager with permission + teleport + evictAll"
```

---

### Task 11: SidebarManager

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/ui/SidebarManager.java`

- [ ] **Step 1: Create SidebarManager.java**

```java
package de.klausiiiii.mobArmyBattle.ui;

import de.klausiiiii.mobArmyBattle.battle.BattleManager;
import de.klausiiiii.mobArmyBattle.battle.BattleSession;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SidebarManager {

    private static final String OBJECTIVE = "mab_sidebar";

    private final BattleManager battleManager;
    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();

    public SidebarManager(BattleManager battleManager) {
        this.battleManager = battleManager;
    }

    public void tickAll(Iterable<Match> matches) {
        long now = System.currentTimeMillis();
        java.util.Set<UUID> seenPlayers = new java.util.HashSet<>();
        for (Match match : matches) {
            MatchPhaseType phase = match.getCurrentPhase().getType();
            if (phase == MatchPhaseType.LOBBY || phase == MatchPhaseType.FINISHED) continue;
            for (Team team : match.getTeams()) {
                for (UUID id : team.getMemberIds()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) continue;
                    BattleContext ctx = phase == MatchPhaseType.BATTLE ? buildBattleContext(p.getUniqueId(), team) : null;
                    List<String> lines = SidebarRenderer.render(match, team, ctx, now);
                    apply(p, lines);
                    seenPlayers.add(id);
                }
            }
        }
        clearStale(seenPlayers);
    }

    private BattleContext buildBattleContext(UUID viewer, Team team) {
        BattleSession session = battleManager.getSessionByPlayer(viewer);
        if (session == null) return null;
        BattleSession.TeamState own = session.getStateByPlayerUUID(viewer);
        if (own == null) return null;
        Team opponent = own.opponent;
        UUID oppCaptain = opponent != null ? opponent.getCaptainId() : null;
        Player oppCaptainPlayer = oppCaptain != null ? Bukkit.getPlayer(oppCaptain) : null;
        String pairName = oppCaptainPlayer != null ? oppCaptainPlayer.getName() : (oppCaptain != null ? oppCaptain.toString().substring(0, 8) : "?");
        int alive = team.getMemberIds().size() - own.downedPlayers.size();
        return new BattleContext(
                own.aliveLivingMobs.size(),
                own.currentWaveSpawnedTotal,
                own.stats.getMobKills(),
                Math.max(0, alive),
                team.getMemberIds().size(),
                own.currentWaveNumber,
                pairName);
    }

    private void apply(Player player, List<String> lines) {
        Scoreboard board = scoreboards.computeIfAbsent(player.getUniqueId(), id -> {
            Scoreboard b = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = b.registerNewObjective(OBJECTIVE, Criteria.DUMMY, "§e§lMobArmyBattle");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            return b;
        });
        Objective obj = board.getObjective(OBJECTIVE);
        if (obj == null) return;
        for (String entry : board.getEntries()) board.resetScores(entry);
        int score = lines.size();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (String raw : lines) {
            String unique = raw;
            int suffix = 0;
            while (used.contains(unique)) {
                suffix++;
                unique = raw + " ".repeat(suffix);
            }
            used.add(unique);
            obj.getScore(unique).setScore(score);
            score--;
        }
        if (player.getScoreboard() != board) player.setScoreboard(board);
    }

    private void clearStale(java.util.Set<UUID> active) {
        java.util.Iterator<UUID> it = scoreboards.keySet().iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (active.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            it.remove();
        }
    }

    public void clear() {
        for (UUID id : scoreboards.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        scoreboards.clear();
    }
}
```

- [ ] **Step 2: Verify compile**

```
./gradlew.bat compileJava
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/ui/SidebarManager.java
git commit -m "feat(ui): SidebarManager — per-player scoreboard tick"
```

---

### Task 12: Phase-Notifications

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FarmPhase.java`
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/WaveBuildPhase.java`
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/BattlePhase.java`

- [ ] **Step 1: Add Notifications.farmStart in FarmPhase.onEnter**

In `FarmPhase.onEnter(Match match)`, after the existing logic (and only when `plugin != null`), add:

```java
        if (plugin == null) return;
        for (de.klausiiiii.mobArmyBattle.match.Team t : match.getTeams()) {
            de.klausiiiii.mobArmyBattle.ui.Notifications.farmStart(t);
        }
```

(Place this AFTER any existing world-creation logic — Notifications fire for already-teleported players.)

- [ ] **Step 2: Add Notifications.waveBuildStart in WaveBuildPhase.onEnter**

```java
        if (plugin == null) return;
        for (de.klausiiiii.mobArmyBattle.match.Team t : match.getTeams()) {
            de.klausiiiii.mobArmyBattle.ui.Notifications.waveBuildStart(t);
        }
```

(Append to existing onEnter, after current code.)

- [ ] **Step 3: Add Notifications.battleStart in BattlePhase.onEnter**

```java
        if (plugin == null) return;
        for (de.klausiiiii.mobArmyBattle.match.Team t : match.getTeams()) {
            de.klausiiiii.mobArmyBattle.ui.Notifications.battleStart(t);
        }
```

- [ ] **Step 4: Verify compile + tests**

```
./gradlew.bat build
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FarmPhase.java src/main/java/de/klausiiiii/mobArmyBattle/match/phase/WaveBuildPhase.java src/main/java/de/klausiiiii/mobArmyBattle/match/phase/BattlePhase.java
git commit -m "feat(phase): notifications on FARM/WAVE_BUILD/BATTLE onEnter"
```

---

### Task 13: BattleManager Notifications + evictAll hook

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/battle/BattleManager.java`
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java` (only the SpectatorManager wiring will come in Task 16; for now BattleManager calls a registered callback OR receives an injected SpectatorManager via setter)

Approach: add a setter `BattleManager.setSpectatorManager(SpectatorManager)` so wiring stays clean. BattleManager calls evictAll inside notifyMatchCompleted.

- [ ] **Step 1: Add Notifications imports + setter to BattleManager**

In `BattleManager.java`:

```java
import de.klausiiiii.mobArmyBattle.spectator.SpectatorManager;
import de.klausiiiii.mobArmyBattle.ui.Notifications;
```

Add field + setter:

```java
    private SpectatorManager spectatorManager;

    public void setSpectatorManager(SpectatorManager mgr) {
        this.spectatorManager = mgr;
    }
```

- [ ] **Step 2: Wave-spawn + wave-passed notifications**

In `BattleManager.startNextWave`, replace:

```java
        broadcastTeam(state.team, "§6Welle " + state.currentWaveNumber + " gestartet — " + mobs.size() + " Mobs.");
```

with:

```java
        broadcastTeam(state.team, "§6Welle " + state.currentWaveNumber + " gestartet — " + mobs.size() + " Mobs.");
        Notifications.waveSpawned(state.team, state.currentWaveNumber, mobs.size());
```

In `BattleManager.checkAdvance`, when `state.currentWaveNumber < 2` and we just survived (i.e., before recursing into startNextWave), add wavePassed call. The current code:

```java
        } else {
            startNextWave(session, state);
        }
```

Replace with:

```java
        } else {
            Notifications.wavePassed(state.team, state.currentWaveNumber);
            startNextWave(session, state);
        }
```

Also at the top of `checkAdvance` (when `state.currentWaveNumber >= 2 && !isFinished`), add wavePassed before markFinished:

```java
        if (state.currentWaveNumber >= 2) {
            if (!state.stats.isFinished()) {
                Notifications.wavePassed(state.team, state.currentWaveNumber);
                state.stats.markFinished(session.elapsedMs());
                broadcastTeam(state.team, "§aDu hast beide Wellen überlebt!");
            }
            checkSessionEnd(session);
        }
```

- [ ] **Step 3: Match-Sieger Notifications in announceResult**

In `BattleManager.announceResult`, after the existing message broadcast, add:

```java
        String aName = teamName(a.team);
        String bName = teamName(b.team);
        if (winner == BattleResult.Winner.A) {
            Notifications.victory(a.team, aName);
            Notifications.defeat(b.team, aName);
        } else if (winner == BattleResult.Winner.B) {
            Notifications.victory(b.team, bName);
            Notifications.defeat(a.team, bName);
        }
```

- [ ] **Step 4: evictAll spectators on cleanup**

In `BattleManager.cleanup(Match match)`, after the `matchSessions.remove(...)` and existing for-loops, add:

```java
        if (spectatorManager != null) {
            spectatorManager.evictAll(match.getId());
        }
```

Also: in `BattleManager.checkSessionEnd`, in the branch `if (all != null && all.stream().allMatch(BattleSession::isConcluded))` — call evictAll BEFORE the FinishedPhase transition (since FinishedPhase may unload arenas):

```java
        if (all != null && all.stream().allMatch(BattleSession::isConcluded)) {
            notifyMatchCompleted(match, all);
            if (spectatorManager != null) spectatorManager.evictAll(match.getId());
            match.transitionTo(new de.klausiiiii.mobArmyBattle.match.phase.FinishedPhase(plugin));
        }
```

- [ ] **Step 5: Verify build green (existing tests don't break)**

```
./gradlew.bat build
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/battle/BattleManager.java
git commit -m "feat(battle): notifications for wave-spawn/passed/winner + spectator evict"
```

---

### Task 14: MabCommand /mab spectate

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java`

- [ ] **Step 1: Add SpectatorManager constructor injection**

In MabCommand, add field + update constructor:

```java
    private final SpectatorManager spectatorManager;

    public MabCommand(MobArmyBattle plugin, MatchManager matchManager, SpectatorManager spectatorManager) {
        this.plugin = plugin;
        this.matchManager = matchManager;
        this.spectatorManager = spectatorManager;
    }
```

(If old constructor still in use elsewhere, update all call sites — `MobArmyBattle.onEnable` will be modified in Task 16.)

- [ ] **Step 2: Add spectate subcommand handler**

In the main `onCommand` switch (after existing cases like `stats`/`leaderboard`):

```java
        case "spectate":
            handleSpectate(player, args);
            return true;
```

Add method:

```java
    private void handleSpectate(Player player, String[] args) {
        if (args.length < 2) {
            java.util.List<String> targets = spectatorManager.listAvailableTargets(player.getUniqueId());
            if (targets.isEmpty()) {
                player.sendMessage("§cKeine zuschaubaren Targets verfügbar.");
            } else {
                player.sendMessage("§7Verfügbare Targets: §f" + String.join(", ", targets));
                player.sendMessage("§7Nutze §f/mab spectate <captain>");
            }
            return;
        }
        String captainName = args[1];
        Player target = Bukkit.getPlayerExact(captainName);
        if (target == null) {
            player.sendMessage("§cSpieler '" + captainName + "' ist nicht online.");
            return;
        }
        spectatorManager.startSpectate(player, target.getUniqueId());
    }
```

- [ ] **Step 3: Tab-Completion for "spectate"**

In `onTabComplete`, in the first-arg list, add `"spectate"`. For `args.length == 2 && args[0].equals("spectate")`:

```java
        if (args.length == 2 && args[0].equalsIgnoreCase("spectate") && sender instanceof Player p) {
            return spectatorManager.listAvailableTargets(p.getUniqueId());
        }
```

- [ ] **Step 4: Handle /mab leave for spectators**

In existing `handleLeave` method (or wherever leave is handled), at the top:

```java
        if (spectatorManager.isSpectating(player.getUniqueId())) {
            spectatorManager.endSpectate(player.getUniqueId());
            player.sendMessage("§7Spectator-Mode beendet.");
            return;
        }
```

- [ ] **Step 5: Verify compile**

```
./gradlew.bat compileJava
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java
git commit -m "feat(command): /mab spectate <captain> + tab + leave-handles-spectator"
```

---

### Task 15: PlayerConnectionListener.onQuit endSpectate

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/listener/PlayerConnectionListener.java`

- [ ] **Step 1: Inject SpectatorManager into listener**

Update constructor signature:

```java
public PlayerConnectionListener(MatchManager matchManager,
                                WorldManager worldManager,
                                TournamentManager tournamentManager,
                                SpectatorManager spectatorManager) {
    this.matchManager = matchManager;
    this.worldManager = worldManager;
    this.tournamentManager = tournamentManager;
    this.spectatorManager = spectatorManager;
}
```

Add field. Imports as needed.

- [ ] **Step 2: Call endSpectate in onQuit BEFORE existing match-handling**

In `onPlayerQuit(PlayerQuitEvent event)`, add at the top:

```java
        UUID id = event.getPlayer().getUniqueId();
        if (spectatorManager != null && spectatorManager.isSpectating(id)) {
            spectatorManager.endSpectate(id);
            return;
        }
```

(Spectators are not match members, so existing match-leave logic shouldn't apply.)

- [ ] **Step 3: Verify compile**

```
./gradlew.bat compileJava
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/listener/PlayerConnectionListener.java
git commit -m "feat(listener): clean up spectator state on player quit"
```

---

### Task 16: Wiring in MobArmyBattle.onEnable + plugin.yml

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java`
- Modify: `src/main/resources/plugin.yml`

- [ ] **Step 1: Add SpectatorManager + SidebarManager fields and getters**

In `MobArmyBattle`:

```java
    private SpectatorManager spectatorManager;
    private SidebarManager sidebarManager;

    public SpectatorManager getSpectatorManager() { return spectatorManager; }
    public SidebarManager getSidebarManager() { return sidebarManager; }
```

- [ ] **Step 2: Update onEnable wiring**

After `battleManager` is created and before the listener registration, change order so SpectatorManager exists before MabCommand:

```java
        battleManager = new BattleManager(this);
        getServer().getPluginManager().registerEvents(
                new BattleEventListener(battleManager), this);
        battleManager.addBattleEndListener(tournamentManager::onBattleFinished);

        spectatorManager = new SpectatorManager(this, matchManager, battleManager, tournamentManager);
        battleManager.setSpectatorManager(spectatorManager);

        sidebarManager = new SidebarManager(battleManager);
```

Update MabCommand construction (now needs spectatorManager):

```java
        MabCommand mabHandler = new MabCommand(this, matchManager, spectatorManager);
        mabCmd.setExecutor(mabHandler);
        mabCmd.setTabCompleter(mabHandler);
```

Update PlayerConnectionListener construction:

```java
        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(matchManager, worldManager, tournamentManager, spectatorManager), this);
```

> Note: re-order so `spectatorManager` is created before `MabCommand` AND `PlayerConnectionListener` are constructed. The current code constructs MabCommand and ConnectionListener BEFORE BattleManager. You'll need to reshuffle: build battleManager + spectatorManager first, then commands and listeners.

- [ ] **Step 3: Wire sidebarManager.tickAll in scheduler**

In the existing scheduler block:

```java
        getServer().getScheduler().runTaskTimer(this, () -> {
            matchManager.tickAll();
            bossBarManager.tickAll(matchManager.getActiveMatches());
            sidebarManager.tickAll(matchManager.getActiveMatches());
        }, 20L, 20L);
```

- [ ] **Step 4: onDisable cleanup**

Add `sidebarManager.clear()` next to `bossBarManager.clear()`:

```java
        if (sidebarManager != null) {
            sidebarManager.clear();
        }
```

- [ ] **Step 5: Add permission to plugin.yml**

In `src/main/resources/plugin.yml`, under `permissions:`, add:

```yaml
  mobarmybattle.spectate:
    description: Erlaubt /mab spectate
    default: true
```

- [ ] **Step 6: Verify build green**

```
./gradlew.bat build
```
Expected: BUILD SUCCESSFUL, alle Tests grün (≥180 inkl. neue Tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java src/main/resources/plugin.yml
git commit -m "feat: wire SidebarManager + SpectatorManager into plugin lifecycle"
```

---

### Task 17: Manual smoke-test via runServer

This task has no commits — it's a verification gate before declaring Plan 8 done.

- [ ] **Step 1: Start server**

```
./gradlew.bat runServer
```

- [ ] **Step 2: Sidebar smoke test**

1. Join server, run `/mab create 1`, then with second account `/mab join <captain>` (use offline test or two clients).
2. Confirm: no Sidebar in lobby.
3. Run `/mab start` (Captain). Expect: FARM phase Title + Sound. Sidebar appears with Pool: section.
4. Kill some mobs in farm world. Confirm: Pool entries grow live in Sidebar.
5. Run `/mab endfarm`. Expect: WAVE_BUILD Title + Sound. Sidebar updates to Wave-Counts.
6. Build wave via GUI, confirm. Expect: BATTLE Title + Sound when transition fires. Sidebar shows Mobs/Kills/Team/Pair-Captain.
7. Survive battle. Expect: Welle 1 spawn Title, Welle 1 bestanden Title, Welle 2 Title, SIEG/Niederlage Title at end.
8. After match end: Sidebar disappears (FINISHED phase).

- [ ] **Step 3: Spectator smoke test**

Requires 4 players to test pair-partner. With 2 players (1v1):
1. Setup 1v1 match. Player A wins both waves quickly. Run `/mab spectate <PlayerB>` from A — should fail ("Du musst erst beide Wellen abschließen") if A's session not yet finished, or succeed once A's stats.isFinished is true.
2. Tournament test: Create tournament with 4 captains (or test with 2 and accept that "eliminated" only reachable after first-round loss). Loser tries `/mab spectate <activeCaptain>` → expect teleport to that arena, GAMEMODE.SPECTATOR.
3. Quit while spectating → confirm no error in console.
4. Match ends while spectating → confirm auto-teleport to lobby.

- [ ] **Step 4: Verify final test count**

```
./gradlew.bat test
```
Expected: ≥180 tests pass (~172 baseline + 11+ new: 8 Sidebar + 2 SpectateState + 3 TournamentRound + 3 Tournament).

---

## Self-Review Output

**Spec coverage:**
- Sidebar 3 layouts: Tasks 1-3 ✓
- `/mab spectate <captain>`: Task 14 ✓
- Pair-Partner-Spectate: SpectatorManager.checkPermission Task 10 ✓
- Tournament-Eliminated-Spectate: Task 9 + Task 10 ✓
- Phase notifications: Task 12 ✓
- Wave/winner notifications: Task 13 ✓
- Quit-cleanup: Task 15 ✓
- Match-end-cleanup: Task 13 (Step 4) ✓

**Type consistency:**
- BattleContext fields: mobsAlive, mobsTotalThisWave, mobKills, teamMembersAlive, teamMembersTotal, currentWaveNumber, pairCaptainName — same in Tasks 1, 3, 11.
- SpectateState: (matchId, arenaWorldName) — same in Tasks 4, 10.
- SpectatorManager method names: `checkPermission`, `startSpectate`, `endSpectate`, `evictAll`, `isSpectating`, `listAvailableTargets` — used consistently in Tasks 10, 14, 15.

**Placeholder scan:** None.

**Edge case coverage:**
- BATTLE rendering with `null` ctx: handled in Task 3 ("Daten lädt …").
- LOBBY/FINISHED: skip in SidebarManager Task 11.
- Spectator quit: Task 15.
- Player without team in match: SidebarManager iterates teams of match, skip if no team match.

## Akzeptanzkriterien

- [ ] Spieler im Match sieht ab FARM-Phase Sidebar
- [ ] Sidebar updated jede Sekunde
- [ ] LOBBY und FINISHED zeigen keinen Sidebar
- [ ] FARM-Sidebar zeigt Pool truncated auf Top-7
- [ ] BATTLE-Sidebar zeigt eigene Pair-Captain im Footer
- [ ] FFA-Match zählt "Teams aktiv: N/X" korrekt
- [ ] Title+Sound bei FARM/WAVE_BUILD/BATTLE-Onset
- [ ] Title bei Welle 1 + Welle 2 Spawn (Mob-Count)
- [ ] "Welle X bestanden" Title nach allen Mobs tot
- [ ] SIEG-Title / Niederlage-Title nach announceResult
- [ ] `/mab spectate` ohne Args zeigt Liste
- [ ] `/mab spectate <captain>` als finished Pair-Partner: Teleport
- [ ] `/mab spectate <captain>` als eliminierter Tournament-Captain: Teleport
- [ ] Ohne Berechtigung: Fehlermeldung
- [ ] Spectator-Quit: kein Crash
- [ ] Match-Ende während Spectate: Auto-Teleport zur Lobby
- [ ] `./gradlew.bat build` grün, alle Tests grün, Total ≥180
