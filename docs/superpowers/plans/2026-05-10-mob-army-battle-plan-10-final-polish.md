# Plan 10: Battle-Timing + Admin-Commands — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Battle-Phase wird zeitlich strukturiert (Vorbereitungsphase 30s vor jeder Welle, Pause 10s zwischen Wellen, Hard-Timeout 10min pro Welle). Plus zwei Admin-Commands `/mab kick` (Captain) und `/mab forcecancel` (op-only).

**Architecture:** BukkitTask-basierte State-Machine pro `BattleSession.TeamState`. Cancellable Tasks für prep + hard-timeout. SidebarRenderer bekommt eine Bauphase-Variante mit Countdown. `MatchManager.forceCancelMatch` orchestriert Notfall-Beendigung.

**Tech Stack:** Java 25, Paper API 26.1.2, Bukkit Scheduler, JUnit 5.

---

## Configs (bereits in Plan 9 reserviert)

```yaml
phases:
  prep-duration-sec: 30
  wave-pause-sec: 10
  wave-hard-timeout-min: 10
```

## Battle-Flow (neu)

```
BattlePhase.onEnter
  → startBattlesFor: createWorlds, teleport
  → für jedes Team: schedulePrep(state, waveNum=1)

schedulePrep(state, waveNum):
  state.currentWaveNumber = waveNum
  Notifications.wavePrep(team, waveNum, prepSec)
  state.prepTask = runTaskLater(prepSec*20, () -> spawnWaveActual(state))

spawnWaveActual(state):
  state.prepTask = null
  wave = opponent.getWave(state.currentWaveNumber)
  if wave leer/forfeit:
    state.stats.recordWaveSurvived()
    checkAdvance(session, state)
    return
  spawn mobs, store in aliveLivingMobs
  state.currentWaveSpawnedTotal = mobs.size()
  state.currentWaveSpawnAt = now()
  state.hardTimeoutTask = runTaskLater(hardTimeoutMin*60*20, () -> onHardTimeout(state))
  Notifications.waveSpawned(team, waveNum, mobs.size())

onMobKilled (last mob, existing flow):
  cancel state.hardTimeoutTask
  state.hardTimeoutTask = null
  state.stats.recordWaveSurvived()
  checkAdvance(session, state)

onHardTimeout(state):
  state.hardTimeoutTask = null
  remove all entities in state.aliveLivingMobs
  state.aliveLivingMobs.clear()
  Notifications.waveTimedOut(team, currentWaveNumber)
  state.stats.markFinished(elapsedMs)
  checkSessionEnd(session)

checkAdvance:
  if currentWaveNumber >= 2:
    if !stats.isFinished:
      Notifications.wavePassed
      stats.markFinished
      broadcastTeam("§aDu hast beide Wellen überlebt!")
    checkSessionEnd
  else:
    Notifications.wavePassed
    scheduleWavePause(state)

scheduleWavePause(state):
  runTaskLater(wavePauseSec*20, () -> schedulePrep(state, currentWaveNumber+1))

cleanup hooks:
  - BattleManager.cleanup: für jede TeamState beider Sessions cancelTasks(state)
  - onPlayerDeath last team death: cancelTasks(state) too (prevents firing after team-out)
```

`cancelTasks(state)`: cancels both prepTask + hardTimeoutTask if non-null, sets to null.

## Notifications Erweiterung

| Event | Title | Subtitle | Sound |
|---|---|---|---|
| wavePrep | §e§lBauphase | §7Welle X in <N>s | BLOCK_NOTE_BLOCK_BELL |
| waveTimedOut | §c§lZeit abgelaufen! | §7Welle X verloren | ENTITY_VILLAGER_NO |

## SidebarRenderer Bauphase-Variante

`BattleContext` neue Felder: `boolean inPrepPhase`, `int prepSecondsLeft`.

In BATTLE-Layout (renderBattle):
- inPrepPhase=true: 
  - "§7Phase: §6Bauphase - W<n>"
  - "§7Spawn in: §f<MM:SS>" (formatted from prepSecondsLeft)
  - keine "Mobs übrig"/"Kills" Zeilen während prep (keine Daten)
  - "§7Team: §f<alive>/<total> lebt" (bleibt)
  - "§7Pair gegen §f<captain>" (bleibt)
- inPrepPhase=false: bisheriges Layout unverändert

`SidebarManager.buildBattleContext`: liest `state.prepTask != null` und berechnet `prepSecondsLeft` aus `state.phaseStartedAt + prepDuration - now`. Hmm — prepStart-Zeit braucht ein neues Feld. Add `state.prepStartedAt: long` set in schedulePrep.

Actually simpler: store `state.prepEndsAt: long` (millis). prepSecondsLeft = max(0, (prepEndsAt - now)/1000).

Update `BattleSession.TeamState`:
- BukkitTask prepTask = null;
- BukkitTask hardTimeoutTask = null;
- long prepEndsAt = 0L;       // 0 means not in prep
- long currentWaveSpawnAt = 0L;

## /mab kick

- Captain-only (caller must be captain of target's team in active match)
- `/mab kick <player>` (target by name)
- Captain cannot kick self → error
- Player not in same team → error
- Effect: `team.removeMember(uuid)` if exists, lobby teleport, optional message
- Tab: target's team members

## /mab forcecancel

- Admin-only (`mobarmybattle.admin`)
- `/mab forcecancel <matchId>` (tab over active match IDs)
- Effect via new `MatchManager.forceCancelMatch(Match)`:
  - For each member: forceRemove + lobby teleport
  - SpectatorManager.evictAll(matchId)
  - BattleManager.cleanup(match) — cancels tasks via integration
  - delete farm + arena worlds (existing WorldManager logic via cleanup)
  - matchByPlayer cleared via forceRemove
  - matchesById.remove(matchId)

## Files

| Datei | Status |
|---|---|
| `battle/BattleSession.java` | Modify (4 neue Felder pro TeamState) |
| `battle/BattleManager.java` | Modify (schedulePrep, spawnWaveActual, scheduleWavePause, onHardTimeout, cancelTasks, integration) |
| `ui/Notifications.java` | Modify (+wavePrep, +waveTimedOut) |
| `ui/BattleContext.java` | Modify (+inPrepPhase, +prepSecondsLeft) |
| `ui/SidebarRenderer.java` | Modify (renderBattle Bauphase-Variante) |
| `ui/SidebarManager.java` | Modify (buildBattleContext liest prep state) |
| `match/Team.java` | Modify (verify removeMember exists; add if missing) |
| `match/MatchManager.java` | Modify (forceCancelMatch method) |
| `command/MabCommand.java` | Modify (kick, forcecancel + tab + sendUsage) |
| `test/ui/SidebarRendererTest.java` | Modify (~3 neue Tests) |

## Tests (~3 neue, total 216)

- `rendersBattlePrepPhase` — inPrepPhase=true, prepSecondsLeft=25, asserts "Bauphase" header + "Spawn in: 00:25" line, no "Mobs übrig"
- `rendersBattlePrepWithZeroSeconds` — prepSecondsLeft=0 shows "00:00"
- `rendersBattleAfterPrep` — inPrepPhase=false (existing layout)

Bukkit-aware (BattleManager timing, MatchManager.forceCancelMatch, MabCommand) → manueller Smoke-Test.

## Scope-Cuts

- `/mab kick` mit `mobarmybattle.kick` permission (Captain-Check reicht)
- Async-Welt-Generierung
- Forfeit-Wave-Skip-Prep (Prep läuft auch bei forfeit-Wave 30s)
- Animated countdown (statisches MM:SS, kein wechselnder Title pro Sekunde)

---

## Tasks

### Task 1: BattleSession TeamState neue Felder

**File:** `src/main/java/de/klausiiiii/mobArmyBattle/battle/BattleSession.java`

- [ ] Add 4 fields to inner `TeamState` class after `currentWaveSpawnedTotal`:

```java
        public org.bukkit.scheduler.BukkitTask prepTask = null;
        public org.bukkit.scheduler.BukkitTask hardTimeoutTask = null;
        public long prepEndsAt = 0L;          // 0 means not in prep
        public long currentWaveSpawnAt = 0L;  // when current wave spawned (for hard-timeout)
```

- [ ] Build green (213 tests).
- [ ] Commit: `feat(battle): TeamState fields for prep + hard-timeout scheduling`

---

### Task 2: BattleContext + SidebarRenderer Bauphase

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/ui/BattleContext.java`
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/ui/SidebarRenderer.java`
- Modify: `src/test/java/de/klausiiiii/mobArmyBattle/ui/SidebarRendererTest.java`

- [ ] Extend BattleContext with two new fields:

```java
public record BattleContext(
        int mobsAlive,
        int mobsTotalThisWave,
        int mobKills,
        int teamMembersAlive,
        int teamMembersTotal,
        int currentWaveNumber,
        String pairCaptainName,
        boolean inPrepPhase,
        int prepSecondsLeft
) {}
```

- [ ] Update existing tests in SidebarRendererTest.java to use 9-arg constructor (existing usage was 7-arg — add `false, 0` at end for prep fields).

- [ ] Add 3 new failing tests:

```java
    @Test
    void rendersBattlePrepLayout() {
        Match match = battleMatch();
        Team t = match.getTeams().get(0);
        BattleContext ctx = new BattleContext(0, 0, 0, 4, 4, 1, "FooCaptain", true, 25);

        List<String> lines = SidebarRenderer.render(match, t, ctx, match.getPhaseStartedAt());

        assertTrue(lines.contains("§7Phase: §6Bauphase - W1"));
        assertTrue(lines.contains("§7Spawn in: §f00:25"));
        assertFalse(lines.stream().anyMatch(l -> l.contains("Mobs übrig")));
        assertTrue(lines.contains("§7Pair gegen §fFooCaptain"));
    }

    @Test
    void rendersBattlePrepWithZeroSeconds() {
        Match match = battleMatch();
        Team t = match.getTeams().get(0);
        BattleContext ctx = new BattleContext(0, 0, 0, 4, 4, 1, "FooCaptain", true, 0);

        List<String> lines = SidebarRenderer.render(match, t, ctx, match.getPhaseStartedAt());

        assertTrue(lines.contains("§7Spawn in: §f00:00"));
    }

    @Test
    void rendersBattleAfterPrepWithLiveMobs() {
        Match match = battleMatch();
        Team t = match.getTeams().get(0);
        BattleContext ctx = new BattleContext(8, 20, 12, 3, 4, 1, "FooCaptain", false, 0);

        List<String> lines = SidebarRenderer.render(match, t, ctx, match.getPhaseStartedAt());

        assertTrue(lines.contains("§7Phase: §cBattle - W1"));
        assertTrue(lines.contains("§7Mobs übrig: §f8/20"));
        assertFalse(lines.stream().anyMatch(l -> l.contains("Spawn in")));
    }
```

- [ ] Run tests — fail.

- [ ] Modify `SidebarRenderer.renderBattle` to branch on `ctx.inPrepPhase()`:

```java
    private static List<String> renderBattle(Match match, Team viewer, BattleContext ctx, long currentTimeMs) {
        List<String> lines = new ArrayList<>();
        boolean prep = ctx != null && ctx.inPrepPhase();
        String phaseLabel = prep
                ? "§7Phase: §6Bauphase - W" + (ctx == null ? 0 : ctx.currentWaveNumber())
                : "§7Phase: §cBattle - W" + (ctx == null ? 0 : ctx.currentWaveNumber());
        addHeader(lines, match, phaseLabel, currentTimeMs);
        lines.add(SEP);
        if (ctx == null) {
            lines.add("§7(Daten lädt …)");
        } else if (prep) {
            int s = Math.max(0, ctx.prepSecondsLeft());
            lines.add("§7Spawn in: §f" + String.format("%02d:%02d", s / 60, s % 60));
            String teamColor = ctx.teamMembersAlive() == 0 ? "§c" : "§f";
            lines.add("§7Team: " + teamColor + ctx.teamMembersAlive() + "/" + ctx.teamMembersTotal() + " lebt");
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
        addFooter(lines, match);
        return lines;
    }
```

- [ ] Run tests — pass.
- [ ] Build green.
- [ ] Commit: `feat(ui): SidebarRenderer Bauphase-Variante + BattleContext extension`

---

### Task 3: Notifications +wavePrep +waveTimedOut

**File:** `src/main/java/de/klausiiiii/mobArmyBattle/ui/Notifications.java`

- [ ] Add 2 new methods:

```java
    public static void wavePrep(Team team, int waveNumber, int prepSec) {
        sendTeam(team, "§e§lBauphase", "§7Welle " + waveNumber + " in " + prepSec + "s",
                Sound.BLOCK_NOTE_BLOCK_BELL);
    }

    public static void waveTimedOut(Team team, int waveNumber) {
        sendTeam(team, "§c§lZeit abgelaufen!", "§7Welle " + waveNumber + " verloren",
                Sound.ENTITY_VILLAGER_NO);
    }
```

- [ ] Verify Sound enum constants exist (`BLOCK_NOTE_BLOCK_BELL`, `ENTITY_VILLAGER_NO` — second already used).
- [ ] Build green.
- [ ] Commit: `feat(ui): Notifications wavePrep + waveTimedOut`

---

### Task 4: BattleManager timing integration

**File:** `src/main/java/de/klausiiiii/mobArmyBattle/battle/BattleManager.java`

This is the meat. Read current BattleManager carefully.

Current `startNextWave` increments waveNumber AND spawns mobs immediately. Split into:
- `schedulePrep(session, state, waveNum)` — increments waveNum, schedules spawn
- `spawnWaveActual(session, state)` — actual spawn, schedules hardTimeout

- [ ] Add helper methods:

```java
    private void schedulePrep(BattleSession session, BattleSession.TeamState state, int waveNum) {
        state.currentWaveNumber = waveNum;
        int prepSec = plugin != null ? plugin.getMabConfig().phaseDurations().prepDurationSec() : 30;
        state.prepEndsAt = System.currentTimeMillis() + prepSec * 1000L;
        Notifications.wavePrep(state.team, waveNum, prepSec);
        state.prepTask = Bukkit.getScheduler().runTaskLater(plugin,
                () -> spawnWaveActual(session, state),
                prepSec * 20L);
    }

    private void spawnWaveActual(BattleSession session, BattleSession.TeamState state) {
        state.prepTask = null;
        state.prepEndsAt = 0L;
        Wave wave = state.currentWaveNumber == 1 ? state.opponent.getWave1() : state.opponent.getWave2();
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
        state.currentWaveSpawnedTotal = mobs.size();
        state.currentWaveSpawnAt = System.currentTimeMillis();
        broadcastTeam(state.team, "§6Welle " + state.currentWaveNumber + " gestartet — " + mobs.size() + " Mobs.");
        Notifications.waveSpawned(state.team, state.currentWaveNumber, mobs.size());
        int hardTimeoutMin = plugin != null ? plugin.getMabConfig().phaseDurations().waveHardTimeoutMin() : 10;
        state.hardTimeoutTask = Bukkit.getScheduler().runTaskLater(plugin,
                () -> onHardTimeout(session, state),
                hardTimeoutMin * 60L * 20L);
    }

    private void onHardTimeout(BattleSession session, BattleSession.TeamState state) {
        state.hardTimeoutTask = null;
        // remove living mob entities + clear tracking
        for (UUID mobUUID : new java.util.HashSet<>(state.aliveLivingMobs)) {
            sessionByMobUUID.remove(mobUUID);
            org.bukkit.entity.Entity e = Bukkit.getEntity(mobUUID);
            if (e != null) e.remove();
        }
        state.aliveLivingMobs.clear();
        Notifications.waveTimedOut(state.team, state.currentWaveNumber);
        broadcastTeam(state.team, "§cZeit abgelaufen — Welle " + state.currentWaveNumber + " verloren.");
        if (!state.stats.isFinished()) {
            state.stats.markFinished(session.elapsedMs());
        }
        checkSessionEnd(session);
    }

    private void scheduleWavePause(BattleSession session, BattleSession.TeamState state) {
        int pauseSec = plugin != null ? plugin.getMabConfig().phaseDurations().wavePauseSec() : 10;
        if (pauseSec <= 0) {
            schedulePrep(session, state, state.currentWaveNumber + 1);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> schedulePrep(session, state, state.currentWaveNumber + 1),
                pauseSec * 20L);
    }

    private void cancelTasks(BattleSession.TeamState state) {
        if (state.prepTask != null) {
            state.prepTask.cancel();
            state.prepTask = null;
        }
        if (state.hardTimeoutTask != null) {
            state.hardTimeoutTask.cancel();
            state.hardTimeoutTask = null;
        }
        state.prepEndsAt = 0L;
    }
```

- [ ] Replace existing `startNextWave` calls:
  - In `startBattlesFor` (after teleport): replace `startNextWave(session, session.getStateA()); startNextWave(session, session.getStateB());` with `schedulePrep(session, session.getStateA(), 1); schedulePrep(session, session.getStateB(), 1);`
  - In `checkAdvance` else-branch (after wavePassed): replace `startNextWave(session, state);` with `scheduleWavePause(session, state);`

- [ ] Replace `onMobKilled` last-mob branch: before `state.stats.recordWaveSurvived()`, add `if (state.hardTimeoutTask != null) { state.hardTimeoutTask.cancel(); state.hardTimeoutTask = null; }`

- [ ] In `onPlayerDeath` last-team-out branch (where `markFinished` is called): add `cancelTasks(state)` before checkSessionEnd.

- [ ] In `cleanup(Match)`: for each session in matchSessions, call `cancelTasks(s.getStateA())` and `cancelTasks(s.getStateB())`.

- [ ] Delete the old `startNextWave` method (or keep for back-compat; prefer delete).

- [ ] Build green (213 tests). Tests use plugin=null where applicable but BattleManager itself is Bukkit-aware so no test break.

- [ ] Commit: `feat(battle): prep + wave-pause + hard-timeout scheduling`

---

### Task 5: SidebarManager.buildBattleContext liest prep state

**File:** `src/main/java/de/klausiiiii/mobArmyBattle/ui/SidebarManager.java`

- [ ] Update buildBattleContext:

```java
    private BattleContext buildBattleContext(UUID viewer, Team team) {
        BattleSession session = battleManager.getSessionByPlayer(viewer);
        if (session == null) return null;
        BattleSession.TeamState own = session.getStateByPlayerUUID(viewer);
        if (own == null) return null;
        Team opponent = own.opponent;
        UUID oppCaptain = opponent != null ? opponent.getCaptainId() : null;
        Player oppCaptainPlayer = oppCaptain != null ? Bukkit.getPlayer(oppCaptain) : null;
        String pairName = oppCaptainPlayer != null ? oppCaptainPlayer.getName()
                : (oppCaptain != null ? oppCaptain.toString().substring(0, 8) : "?");
        int alive = team.getMemberIds().size() - own.downedPlayers.size();
        boolean inPrep = own.prepTask != null && own.prepEndsAt > 0L;
        int prepLeft = inPrep ? (int) Math.max(0L, (own.prepEndsAt - System.currentTimeMillis()) / 1000L) : 0;
        return new BattleContext(
                own.aliveLivingMobs.size(),
                own.currentWaveSpawnedTotal,
                own.stats.getMobKills(),
                Math.max(0, alive),
                team.getMemberIds().size(),
                own.currentWaveNumber,
                pairName,
                inPrep,
                prepLeft);
    }
```

- [ ] Build green.
- [ ] Commit: `feat(ui): SidebarManager exposes prep-phase state to renderer`

---

### Task 6: Team.removeMember + MatchManager.forceCancelMatch

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/Team.java` (verify removeMember exists)
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/MatchManager.java`

- [ ] Read Team.java to check if `removeMember(UUID)` exists. If not, add:

```java
    public boolean removeMember(UUID playerId) {
        return memberIds.remove(playerId);
    }
```

(And expose to test access — adapt visibility per existing codebase.)

- [ ] Add forceCancelMatch to MatchManager:

```java
    public void forceCancelMatch(Match match,
                                 de.klausiiiii.mobArmyBattle.battle.BattleManager battleManager,
                                 de.klausiiiii.mobArmyBattle.spectator.SpectatorManager spectatorManager,
                                 de.klausiiiii.mobArmyBattle.world.WorldManager worldManager) {
        if (match == null) return;
        // Notify+evict spectators
        if (spectatorManager != null) spectatorManager.evictAll(match.getId());
        // Cancel battle tasks + clean battle state
        if (battleManager != null) battleManager.cleanup(match);
        // Force-remove all members (clears matchByPlayer)
        java.util.List<java.util.UUID> allMembers = new java.util.ArrayList<>();
        for (Team t : match.getTeams()) allMembers.addAll(t.getMemberIds());
        for (java.util.UUID id : allMembers) {
            forceRemove(id);
        }
        // Match-level cleanup
        matchesById.remove(match.getId());
    }
```

(Adjust visibility/imports per codebase. If `forceRemove` doesn't exist, use the existing match-removal pattern from leaveMatch.)

Note: world deletion happens via existing FinishedPhase or BattleManager.cleanup logic — verify whether the worlds get unloaded by these. If not, manually unload arena/farm worlds via WorldManager.

- [ ] Build green.
- [ ] Commit: `feat(match): MatchManager.forceCancelMatch + Team.removeMember`

---

### Task 7: MabCommand /mab kick + /mab forcecancel

**File:** `src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java`

- [ ] Add `"kick"` and `"forcecancel"` to SUBCOMMANDS list.

- [ ] In onCommand switch:

```java
        case "kick" -> handleKick(player, args);
        case "forcecancel" -> handleForceCancel(player, args);
```

- [ ] Implement:

```java
    private void handleKick(Player player, String[] args) {
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) {
            player.sendMessage("§cDu bist in keinem Match.");
            return;
        }
        Team team = match.findTeamOf(player.getUniqueId());
        if (team == null || !player.getUniqueId().equals(team.getCaptainId())) {
            player.sendMessage("§cNur der Captain kann kicken.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§7Usage: /mab kick <player>");
            return;
        }
        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage("§cSpieler '" + targetName + "' nicht online.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cDu kannst dich nicht selbst kicken — nutze /mab leave.");
            return;
        }
        if (!team.hasMember(target.getUniqueId())) {
            player.sendMessage("§cSpieler ist nicht in deinem Team.");
            return;
        }
        matchManager.leaveMatch(target.getUniqueId());
        target.sendMessage("§eDu wurdest aus dem Team gekickt.");
        player.sendMessage("§a" + target.getName() + " wurde gekickt.");
    }

    private void handleForceCancel(Player player, String[] args) {
        if (!player.hasPermission("mobarmybattle.admin")) {
            player.sendMessage("§cKeine Berechtigung.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§7Usage: /mab forcecancel <matchId>");
            return;
        }
        String matchId = args[1];
        Match match = matchManager.getMatchById(matchId);
        if (match == null) {
            player.sendMessage("§cKein Match mit ID '" + matchId + "'.");
            return;
        }
        matchManager.forceCancelMatch(match,
                plugin.getBattleManager(),
                plugin.getSpectatorManager(),
                plugin.getWorldManager());
        player.sendMessage("§aMatch " + matchId + " wurde abgebrochen.");
    }
```

- [ ] Tab completion:

```java
        if (args.length == 2 && args[0].equalsIgnoreCase("kick") && sender instanceof Player p) {
            Match match = matchManager.getMatchOf(p.getUniqueId());
            if (match == null) return java.util.List.of();
            Team team = match.findTeamOf(p.getUniqueId());
            if (team == null || !p.getUniqueId().equals(team.getCaptainId())) return java.util.List.of();
            java.util.List<String> names = new java.util.ArrayList<>();
            for (java.util.UUID id : team.getMemberIds()) {
                if (id.equals(p.getUniqueId())) continue;
                Player m = Bukkit.getPlayer(id);
                if (m != null) names.add(m.getName());
            }
            return names;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("forcecancel") && sender.hasPermission("mobarmybattle.admin")) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (Match m : matchManager.getActiveMatches()) ids.add(m.getId());
            return ids;
        }
```

- [ ] Update sendUsage to include both new commands (with permission gating for forcecancel).

- [ ] Filter `forcecancel` out of first-arg tab list when sender lacks `mobarmybattle.admin`.

- [ ] Build green.
- [ ] Commit: `feat(command): /mab kick (Captain) + /mab forcecancel (admin)`

---

### Task 8: Final review + verify

- [ ] Full build green (216 tests target).
- [ ] Manual smoke test (deferred to user):
  - Battle with prep visible on Sidebar
  - Wave 1 → wave-pause 10s → prep 30s → wave 2
  - Hard-timeout: don't kill mobs in wave, force-end after configured time
  - `/mab kick` from captain
  - `/mab forcecancel` from op

## Acceptance Criteria

- [ ] BattleSession TeamState has prepTask, hardTimeoutTask, prepEndsAt, currentWaveSpawnAt
- [ ] BattlePhase entry → 30s prep → wave 1 spawn
- [ ] Wave 1 dead → 10s pause → 30s prep → wave 2 spawn
- [ ] Wave hard-timeout (10min default): force-ends wave, marks not-finished
- [ ] Sidebar shows "Bauphase - W<n>" with countdown during prep
- [ ] Notifications fire: wavePrep, waveTimedOut, plus existing
- [ ] `/mab kick <player>` works for captain only, not for self, only for own team members
- [ ] `/mab forcecancel <matchId>` works for op only, force-ends match
- [ ] Tab-completion for both
- [ ] All 213 + 3 = 216 tests pass
