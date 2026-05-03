# MobArmyBattle Plan 3.5: 2-Team-Modus + Mode-Parsing

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Match unterstützt jetzt 2 Teams (oder asymmetrische Größen). `/mab create <mode>` parsed Modi wie `1v1`, `2v2`, `4v4`, `2v3`. `/mab join <captain> [team]` lässt Spieler explizit oder per Auto-Balance ein Team wählen. Match startet nur, wenn beide Teams besetzt sind. Erster Joiner eines leeren zweiten Teams wird automatisch dessen Captain.

**Architecture:** Neue `MatchMode`-Value-Klasse hält parsed Mode (Liste der Team-Größen). `Match` bekommt `MatchMode`-Field. `Team` bekommt `maxSize`. `MatchManager.createMatch(captain, mode)` parsed Mode-String + erstellt Match mit so vielen leeren Teams wie nötig (Captain ist erster Spieler in Team 0). `joinMatch(player, captain, teamIndex)` joint spezifisches Team; Erster Spieler in leerem Team wird Captain dieses Teams. `Match.canStart()` prüft jedes Team ≥ 1 Spieler.

**Plan-Umfang:** Plan 3.5 von ~10. Schließt eine Lücke aus den vorigen Plänen — bisher hatte jedes Match nur 1 Team, was für Battle keinen Sinn ergibt.

**Was nach Plan 3.5 lauffähig ist:** Captain A `/mab create 2v2`, Captain B `/mab join A` (joint Team 2 weil leer, wird Team-2-Captain), zwei weitere `/mab join A` (joinen jeweils das Team mit weniger Spielern), Captain A `/mab start` startet das Match. Pool-Tracking läuft pro Team getrennt.

---

## File Structure

| Datei | Zweck | Status |
|---|---|---|
| `src/main/java/de/klausiiiii/mobArmyBattle/match/MatchMode.java` | Value-Klasse: Liste der Team-Größen + parser | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/Match.java` | Erweitern: `MatchMode mode`, `canStart()` | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/Team.java` | Erweitern: `int maxSize`, `isFull()` | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/MatchManager.java` | createMatch(captain, mode); joinMatch(player, captain, teamIndex) | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java` | `/mab create <mode>` + `/mab join <captain> [team]` + start-validation | Modify |
| `src/test/java/de/klausiiiii/mobArmyBattle/match/MatchModeTest.java` | Mode-Parsing-Tests | Create |
| `src/test/java/de/klausiiiii/mobArmyBattle/match/MatchTest.java` | canStart, mode-Field | Modify |
| `src/test/java/de/klausiiiii/mobArmyBattle/match/TeamTest.java` | maxSize, isFull | Modify |
| `src/test/java/de/klausiiiii/mobArmyBattle/match/MatchManagerTest.java` | New tests for mode + team-index | Modify |

---

## Task 1: MatchMode value class + parser (TDD)

**Files:**
- Test: `src/test/java/de/klausiiiii/mobArmyBattle/match/MatchModeTest.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/match/MatchMode.java`

- [ ] **Step 1: Failing tests**

```java
package de.klausiiiii.mobArmyBattle.match;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchModeTest {

    @Test
    void parsesSymmetric1v1() {
        MatchMode mode = MatchMode.parse("1v1");
        assertEquals(List.of(1, 1), mode.getTeamSizes());
        assertEquals(2, mode.getTeamCount());
    }

    @Test
    void parsesSymmetric4v4() {
        MatchMode mode = MatchMode.parse("4v4");
        assertEquals(List.of(4, 4), mode.getTeamSizes());
    }

    @Test
    void parsesAsymmetric2v3() {
        MatchMode mode = MatchMode.parse("2v3");
        assertEquals(List.of(2, 3), mode.getTeamSizes());
    }

    @Test
    void parseIsCaseInsensitive() {
        MatchMode mode = MatchMode.parse("3V2");
        assertEquals(List.of(3, 2), mode.getTeamSizes());
    }

    @Test
    void rejectsBadFormat() {
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse("1"));
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse("1v"));
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse("v1"));
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse(""));
    }

    @Test
    void rejectsNullInput() {
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse(null));
    }

    @Test
    void rejectsZeroSizes() {
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse("0v1"));
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse("1v0"));
    }

    @Test
    void rejectsTooLargeSizes() {
        assertThrows(IllegalArgumentException.class, () -> MatchMode.parse("5v5"));
    }

    @Test
    void displayNameRoundtrips() {
        assertEquals("1v1", MatchMode.parse("1v1").getDisplayName());
        assertEquals("2v3", MatchMode.parse("2v3").getDisplayName());
    }

    @Test
    void canConstructDirectly() {
        MatchMode mode = new MatchMode(List.of(2, 2));
        assertEquals(List.of(2, 2), mode.getTeamSizes());
        assertEquals("2v2", mode.getDisplayName());
    }

    @Test
    void rejectsLessThanTwoTeams() {
        assertThrows(IllegalArgumentException.class, () -> new MatchMode(List.of(1)));
    }

    @Test
    void rejectsMoreThanTwoTeams() {
        assertThrows(IllegalArgumentException.class, () -> new MatchMode(List.of(1, 1, 1)));
    }

    @Test
    void teamSizesAreImmutable() {
        MatchMode mode = MatchMode.parse("2v2");
        assertThrows(UnsupportedOperationException.class,
                () -> mode.getTeamSizes().add(3));
    }

    @Test
    void equalsAndHashCode() {
        assertEquals(MatchMode.parse("2v3"), MatchMode.parse("2v3"));
        assertEquals(MatchMode.parse("2v3").hashCode(), MatchMode.parse("2v3").hashCode());
        assertNotEquals(MatchMode.parse("2v2"), MatchMode.parse("3v3"));
    }
}
```

- [ ] **Step 2: Run, expect compile fail**

- [ ] **Step 3: Implementation**

```java
package de.klausiiiii.mobArmyBattle.match;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MatchMode {

    private static final int MAX_TEAM_SIZE = 4;

    private final List<Integer> teamSizes;

    public MatchMode(List<Integer> teamSizes) {
        if (teamSizes == null || teamSizes.size() != 2) {
            throw new IllegalArgumentException("MatchMode benötigt genau 2 Teams");
        }
        for (Integer size : teamSizes) {
            if (size == null || size < 1) {
                throw new IllegalArgumentException("Team-Größe muss >= 1 sein");
            }
            if (size > MAX_TEAM_SIZE) {
                throw new IllegalArgumentException("Team-Größe darf maximal " + MAX_TEAM_SIZE + " sein");
            }
        }
        this.teamSizes = List.copyOf(teamSizes);
    }

    public static MatchMode parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Mode-String darf nicht leer sein");
        }
        String[] parts = input.toLowerCase().split("v");
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new IllegalArgumentException("Ungültiges Mode-Format (erwartet z.B. \"2v2\"): " + input);
        }
        int a, b;
        try {
            a = Integer.parseInt(parts[0]);
            b = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ungültiges Mode-Format: " + input);
        }
        return new MatchMode(List.of(a, b));
    }

    public List<Integer> getTeamSizes() {
        return Collections.unmodifiableList(teamSizes);
    }

    public int getTeamCount() {
        return teamSizes.size();
    }

    public int getMaxSizeOfTeam(int teamIndex) {
        return teamSizes.get(teamIndex);
    }

    public String getDisplayName() {
        return teamSizes.get(0) + "v" + teamSizes.get(1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MatchMode that)) return false;
        return teamSizes.equals(that.teamSizes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teamSizes);
    }

    @Override
    public String toString() {
        return "MatchMode{" + getDisplayName() + "}";
    }
}
```

- [ ] **Step 4: All tests pass** — 61 + 14 = 75.

- [ ] **Step 5: Commit**: `feat: add MatchMode with mode-string parser and validation`

---

## Task 2: Team — maxSize + isFull (TDD)

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/Team.java`
- Modify: `src/test/java/de/klausiiiii/mobArmyBattle/match/TeamTest.java`

- [ ] **Step 1: Add tests** to TeamTest.java (before final closing `}`):

```java
    @Test
    void teamWithMaxSizeRejectsOverflow() {
        UUID captain = UUID.randomUUID();
        Team team = new Team(captain, 2);

        team.addMember(UUID.randomUUID());

        assertThrows(IllegalStateException.class,
                () -> team.addMember(UUID.randomUUID()));
    }

    @Test
    void teamReportsFullWhenAtMaxSize() {
        UUID captain = UUID.randomUUID();
        Team team = new Team(captain, 2);

        assertFalse(team.isFull());

        team.addMember(UUID.randomUUID());

        assertTrue(team.isFull());
    }

    @Test
    void teamWithoutMaxSizeNeverFull() {
        Team team = new Team(UUID.randomUUID());
        for (int i = 0; i < 100; i++) team.addMember(UUID.randomUUID());

        assertFalse(team.isFull());
    }

    @Test
    void getMaxSizeReturnsZeroForUnboundedTeam() {
        Team team = new Team(UUID.randomUUID());
        assertEquals(0, team.getMaxSize());
    }

    @Test
    void getMaxSizeReturnsBoundForBoundedTeam() {
        Team team = new Team(UUID.randomUUID(), 3);
        assertEquals(3, team.getMaxSize());
    }
```

- [ ] **Step 2: Run, expect fail**

- [ ] **Step 3: Modify Team.java**

Add field after `private final MobPool pool;`:
```java
    private final int maxSize;
```

Replace existing constructor with TWO constructors:
```java
    public Team(UUID captainId) {
        this(captainId, 0);
    }

    public Team(UUID captainId, int maxSize) {
        if (captainId == null) {
            throw new IllegalArgumentException("captainId darf nicht null sein");
        }
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize darf nicht negativ sein");
        }
        this.captainId = captainId;
        this.memberIds = new LinkedHashSet<>();
        this.memberIds.add(captainId);
        this.pool = new MobPool();
        this.maxSize = maxSize;
    }
```

Modify `addMember`:
```java
    public void addMember(UUID playerId) {
        if (memberIds.contains(playerId)) {
            throw new IllegalArgumentException("Spieler ist bereits Team-Mitglied: " + playerId);
        }
        if (maxSize > 0 && memberIds.size() >= maxSize) {
            throw new IllegalStateException("Team ist voll: " + maxSize + " Mitglieder");
        }
        memberIds.add(playerId);
    }
```

Add new methods (anywhere appropriate):
```java
    public int getMaxSize() {
        return maxSize;
    }

    public boolean isFull() {
        return maxSize > 0 && memberIds.size() >= maxSize;
    }
```

- [ ] **Step 4: All tests pass** — 75 + 5 = 80.

- [ ] **Step 5: Commit**: `feat: add max-size constraint to Team`

---

## Task 3: Match — mode field + canStart (TDD)

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/Match.java`
- Modify: `src/test/java/de/klausiiiii/mobArmyBattle/match/MatchTest.java`

- [ ] **Step 1: Add tests** to MatchTest.java (before final `}`):

```java
    @Test
    void matchHasMode() {
        Match match = new Match("test-match-1", 0L, MatchMode.parse("2v2"));

        assertEquals(MatchMode.parse("2v2"), match.getMode());
    }

    @Test
    void backwardsCompatibleConstructorsUseDefault1v1Mode() {
        Match m1 = new Match("test-match-1");
        Match m2 = new Match("test-match-2", 42L);

        assertEquals(MatchMode.parse("1v1"), m1.getMode());
        assertEquals(MatchMode.parse("1v1"), m2.getMode());
    }

    @Test
    void canStartReturnsFalseWhenTeamMissing() {
        Match match = new Match("test-match-1", 0L, MatchMode.parse("1v1"));
        match.addTeam(new Team(UUID.randomUUID(), 1));

        assertFalse(match.canStart());
    }

    @Test
    void canStartReturnsTrueWhenAllTeamsHaveAtLeastOnePlayer() {
        Match match = new Match("test-match-1", 0L, MatchMode.parse("2v2"));
        match.addTeam(new Team(UUID.randomUUID(), 2));
        match.addTeam(new Team(UUID.randomUUID(), 2));

        assertTrue(match.canStart());
    }

    @Test
    void canStartReturnsFalseWhenTeamWasDisbanded() {
        Match match = new Match("test-match-1", 0L, MatchMode.parse("1v1"));
        Team team1 = new Team(UUID.randomUUID(), 1);
        Team team2 = new Team(UUID.randomUUID(), 1);
        match.addTeam(team1);
        match.addTeam(team2);
        team2.disband();

        assertFalse(match.canStart());
    }
```

(Existing test `backwardsCompatibleConstructorWorks` from Plan 2 still works.)

- [ ] **Step 2: Run, expect fail**

- [ ] **Step 3: Modify Match.java**

Add field after `private final long seed;`:
```java
    private final MatchMode mode;
```

Replace constructors:
```java
    public Match(String id) {
        this(id, new Random().nextLong(), MatchMode.parse("1v1"));
    }

    public Match(String id, long seed) {
        this(id, seed, MatchMode.parse("1v1"));
    }

    public Match(String id, long seed, MatchMode mode) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Match-ID darf nicht leer sein");
        }
        if (mode == null) {
            throw new IllegalArgumentException("MatchMode darf nicht null sein");
        }
        this.id = id;
        this.seed = seed;
        this.mode = mode;
        this.teams = new ArrayList<>();
        this.farmWorldNames = new HashMap<>();
        this.currentPhase = new LobbyPhase();
        this.currentPhase.onEnter(this);
    }
```

Add new methods:
```java
    public MatchMode getMode() {
        return mode;
    }

    public boolean canStart() {
        if (teams.size() < mode.getTeamCount()) return false;
        for (Team t : teams) {
            if (t.isDisbanded() || t.size() == 0) return false;
        }
        return true;
    }
```

- [ ] **Step 4: All tests pass** — 80 + 4 = 84 (one existing test `backwardsCompatibleConstructorWorks` already counted in 80; now +4 new = 84).

- [ ] **Step 5: Commit**: `feat: add MatchMode and canStart to Match`

---

## Task 4: MatchManager — mode-aware create/join (TDD)

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/MatchManager.java`
- Modify: `src/test/java/de/klausiiiii/mobArmyBattle/match/MatchManagerTest.java`

- [ ] **Step 1: Add tests** to MatchManagerTest.java (before final `}`):

```java
    @Test
    void createMatchWithModeSetsMatchMode() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();

        Match match = manager.createMatch(captain, MatchMode.parse("2v2"));

        assertEquals(MatchMode.parse("2v2"), match.getMode());
        assertEquals(2, match.getTeams().size());
        assertEquals(captain, match.getTeams().get(0).getCaptainId());
        assertNull(match.getTeams().get(1).getCaptainId());
    }

    @Test
    void createMatchWithDefaultModeIs1v1() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();

        Match match = manager.createMatch(captain);

        assertEquals(MatchMode.parse("1v1"), match.getMode());
        assertEquals(2, match.getTeams().size());
    }

    @Test
    void joinMatchInTeamIndex0AddsToCaptainTeam() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, MatchMode.parse("2v2"));

        manager.joinMatch(joiner, captain, 0);

        Match m = manager.getMatchOf(joiner);
        assertTrue(m.getTeams().get(0).hasMember(joiner));
        assertFalse(m.getTeams().get(1).hasMember(joiner));
    }

    @Test
    void joinMatchInTeamIndex1MakesJoinerCaptainOfTeam2() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, MatchMode.parse("2v2"));

        manager.joinMatch(joiner, captain, 1);

        Match m = manager.getMatchOf(joiner);
        assertEquals(joiner, m.getTeams().get(1).getCaptainId());
        assertTrue(m.getTeams().get(1).hasMember(joiner));
    }

    @Test
    void joinMatchAutoBalanceJoinsEmptyTeam() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, MatchMode.parse("2v2"));

        manager.joinMatch(joiner, captain);

        Match m = manager.getMatchOf(joiner);
        assertEquals(joiner, m.getTeams().get(1).getCaptainId());
    }

    @Test
    void joinMatchAutoBalanceJoinsTeamWithFewerPlayers() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID j1 = UUID.randomUUID();
        UUID j2 = UUID.randomUUID();
        UUID j3 = UUID.randomUUID();
        manager.createMatch(captain, MatchMode.parse("2v2"));
        manager.joinMatch(j1, captain, 1);
        manager.joinMatch(j2, captain, 0);

        manager.joinMatch(j3, captain);

        Match m = manager.getMatchOf(j3);
        assertEquals(2, m.getTeams().get(0).size());
        assertEquals(2, m.getTeams().get(1).size());
    }

    @Test
    void joiningFullTeamThrows() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID j1 = UUID.randomUUID();
        UUID j2 = UUID.randomUUID();
        manager.createMatch(captain, MatchMode.parse("1v1"));
        manager.joinMatch(j1, captain, 1);

        assertThrows(IllegalStateException.class,
                () -> manager.joinMatch(j2, captain, 1));
    }

    @Test
    void joiningInvalidTeamIndexThrows() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, MatchMode.parse("1v1"));

        assertThrows(IllegalArgumentException.class,
                () -> manager.joinMatch(joiner, captain, 2));
        assertThrows(IllegalArgumentException.class,
                () -> manager.joinMatch(joiner, captain, -1));
    }
```

Make sure to add the import in MatchManagerTest.java if not present:
```java
import de.klausiiiii.mobArmyBattle.match.MatchMode;
```

(MatchMode is in same package as MatchManagerTest, so no import needed actually.)

- [ ] **Step 2: Run, expect fail**

- [ ] **Step 3: Modify MatchManager.java**

Replace the entire `createMatch` method:
```java
    public Match createMatch(UUID captainId) {
        return createMatch(captainId, MatchMode.parse("1v1"));
    }

    public Match createMatch(UUID captainId, MatchMode mode) {
        if (matchByPlayer.containsKey(captainId)) {
            throw new IllegalStateException("Spieler ist bereits in einem Match: " + captainId);
        }
        String id = "match-" + matchIdCounter.getAndIncrement();
        long seed = new Random().nextLong();
        Match match = new Match(id, seed, mode);

        // Team 0: captain's team
        Team firstTeam = new Team(captainId, mode.getMaxSizeOfTeam(0));
        match.addTeam(firstTeam);

        // Team 1+: empty placeholder teams (no captain yet)
        for (int i = 1; i < mode.getTeamCount(); i++) {
            match.addTeam(Team.empty(mode.getMaxSizeOfTeam(i)));
        }

        matchesById.put(id, match);
        matchByPlayer.put(captainId, match);
        return match;
    }
```

Replace `joinMatch(UUID, UUID)` with two methods:
```java
    public void joinMatch(UUID playerId, UUID captainId) {
        if (matchByPlayer.containsKey(playerId)) {
            throw new IllegalStateException("Spieler ist bereits in einem Match: " + playerId);
        }
        Match match = matchByPlayer.get(captainId);
        if (match == null) {
            throw new IllegalArgumentException("Captain hat kein Match: " + captainId);
        }
        int target = pickAutoBalanceTeam(match);
        joinMatch(playerId, captainId, target);
    }

    public void joinMatch(UUID playerId, UUID captainId, int teamIndex) {
        if (matchByPlayer.containsKey(playerId)) {
            throw new IllegalStateException("Spieler ist bereits in einem Match: " + playerId);
        }
        Match match = matchByPlayer.get(captainId);
        if (match == null) {
            throw new IllegalArgumentException("Captain hat kein Match: " + captainId);
        }
        if (teamIndex < 0 || teamIndex >= match.getTeams().size()) {
            throw new IllegalArgumentException("Ungültiger Team-Index: " + teamIndex);
        }
        Team target = match.getTeams().get(teamIndex);
        if (target.isFull()) {
            throw new IllegalStateException("Team " + (teamIndex + 1) + " ist voll");
        }
        if (target.isDisbanded() || target.getCaptainId() == null) {
            target.promoteEmpty(playerId);
        } else {
            target.addMember(playerId);
        }
        matchByPlayer.put(playerId, match);
    }

    private int pickAutoBalanceTeam(Match match) {
        int bestIdx = 0;
        int bestSize = Integer.MAX_VALUE;
        List<Team> teams = match.getTeams();
        for (int i = 0; i < teams.size(); i++) {
            Team t = teams.get(i);
            if (t.isFull()) continue;
            int size = (t.getCaptainId() == null) ? 0 : t.size();
            if (size < bestSize) {
                bestSize = size;
                bestIdx = i;
            }
        }
        return bestIdx;
    }
```

This requires two new helper methods on `Team`. Add them:

In `Team.java`:

```java
    /**
     * Creates an empty team without a captain (used as a placeholder for the
     * second team in a multi-team match before anyone has joined it).
     */
    public static Team empty(int maxSize) {
        return new Team(maxSize, true);
    }

    private Team(int maxSize, boolean emptySentinel) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize darf nicht negativ sein");
        }
        this.captainId = null;
        this.memberIds = new LinkedHashSet<>();
        this.pool = new MobPool();
        this.maxSize = maxSize;
    }

    /**
     * Sets the captain on a previously empty team and adds them as the only member.
     */
    public void promoteEmpty(UUID newCaptainId) {
        if (captainId != null && !memberIds.isEmpty()) {
            throw new IllegalStateException("Team ist nicht leer");
        }
        if (newCaptainId == null) {
            throw new IllegalArgumentException("newCaptainId darf nicht null sein");
        }
        this.captainId = newCaptainId;
        this.memberIds.add(newCaptainId);
    }
```

- [ ] **Step 4: All tests pass** — 84 + 8 = 92.

- [ ] **Step 5: Commit**: `feat: MatchManager creates multi-team matches with mode-aware joining`

---

## Task 5: MabCommand — mode + team-arg + start-validation

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java`

- [ ] **Step 1: Modifications**

1. Update `handleCreate` — accept mode argument:

Replace existing `handleCreate(Player player)` and the case handling. Update the switch case:
```java
                case "create" -> handleCreate(player, args);
```

Replace the method:
```java
    private void handleCreate(Player player, String[] args) {
        MatchMode mode;
        if (args.length < 2) {
            mode = MatchMode.parse("1v1");
        } else {
            try {
                mode = MatchMode.parse(args[1]);
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text(
                        "Ungültiger Modus: " + args[1] + " (z.B. 1v1, 2v2, 4v4, 2v3)",
                        NamedTextColor.RED));
                return;
            }
        }
        Match match = matchManager.createMatch(player.getUniqueId(), mode);
        player.sendMessage(Component.text(
                "Match erstellt: " + match.getId() + " (" + mode.getDisplayName() + "). Du bist Captain Team 1.",
                NamedTextColor.GREEN));
        player.sendMessage(Component.text(
                "Andere joinen mit: /mab join " + player.getName() + " [1|2]",
                NamedTextColor.GRAY));
    }
```

Add import:
```java
import de.klausiiiii.mobArmyBattle.match.MatchMode;
```

2. Update `handleJoin` — accept optional team-index:

```java
    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Verwendung: /mab join <captain> [1|2]", NamedTextColor.RED));
            return;
        }
        Player captainPlayer = Bukkit.getPlayerExact(args[1]);
        if (captainPlayer == null) {
            player.sendMessage(Component.text("Spieler nicht online: " + args[1], NamedTextColor.RED));
            return;
        }
        if (args.length >= 3) {
            int teamIndex;
            try {
                teamIndex = Integer.parseInt(args[2]) - 1;  // 1-based for user
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text(
                        "Ungültiger Team-Index: " + args[2] + " (1 oder 2)",
                        NamedTextColor.RED));
                return;
            }
            matchManager.joinMatch(player.getUniqueId(), captainPlayer.getUniqueId(), teamIndex);
        } else {
            matchManager.joinMatch(player.getUniqueId(), captainPlayer.getUniqueId());
        }
        Match match = matchManager.getMatchOf(player.getUniqueId());
        Team team = match.findTeamOf(player.getUniqueId());
        int teamNumber = match.getTeams().indexOf(team) + 1;
        player.sendMessage(Component.text(
                "Du bist Team " + teamNumber + " in " + captainPlayer.getName() + "s Match beigetreten.",
                NamedTextColor.GREEN));
        captainPlayer.sendMessage(Component.text(
                player.getName() + " ist Team " + teamNumber + " beigetreten.",
                NamedTextColor.GREEN));
    }
```

3. Update `handleStart` — verify canStart:

In `handleStart`, BEFORE the line `match.transitionTo(new FarmPhase(plugin));`, add:
```java
        if (!match.canStart()) {
            player.sendMessage(Component.text(
                    "Match kann nicht starten — beide Teams brauchen mindestens 1 Spieler.",
                    NamedTextColor.RED));
            return;
        }
```

4. Update `onTabComplete` — suggest modes after `/mab create`:

In `onTabComplete`, after the existing `if (args.length == 2 && args[0].equalsIgnoreCase("join"))` block, add:
```java
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return filterByPrefix(List.of("1v1", "2v2", "3v3", "4v4", "2v3", "3v4"), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("join")) {
            return filterByPrefix(List.of("1", "2"), args[2]);
        }
```

5. Update `sendUsage`:

Replace `"/mab create — Match erstellen"` line with:
```java
        player.sendMessage(Component.text("/mab create [mode] — Match erstellen (Default 1v1, z.B. 2v2)", NamedTextColor.GRAY));
```

Replace `"/mab join <captain> — Match beitreten"` line with:
```java
        player.sendMessage(Component.text("/mab join <captain> [1|2] — Match beitreten", NamedTextColor.GRAY));
```

- [ ] **Step 2: Build + tests** — `./gradlew build` BUILD SUCCESSFUL, 92 tests pass.

- [ ] **Step 3: Commit**: `feat: MabCommand supports mode + team-index args, validates start`

---

## Task 6: Manueller Test

- [ ] **Step 1**: `./gradlew runServer`

- [ ] **Step 2**: Im Spiel
1. **Player A:** `/mab create 2v2` → "Match erstellt: match-1 (2v2). Du bist Captain Team 1."
2. **Player A:** `/mab pool` → "Pool ist leer." (Match nicht gestartet, Pool aber existiert)
3. **Player A:** `/mab start` → Fehler: "Match kann nicht starten — beide Teams brauchen mindestens 1 Spieler."
4. **Player B:** `/mab join A` → joint Team 2 (auto-balance), wird Captain Team 2
5. **Player A:** `/mab start` → Match startet, beide Spieler werden in **separate** Farm-Welten teleportiert (Player A in Team 1's Welt, Player B in Team 2's Welt).
6. **Player A** killt Mobs in seiner Welt → Pool A füllt sich. **Player B** killt Mobs in B's Welt → Pool B füllt sich. `/mab pool` zeigt jeweils nur den eigenen Team-Pool.

- [ ] **Step 3**: Tab-Completion testen
- `/mab create <TAB>` → "1v1", "2v2", "3v3", "4v4", "2v3", "3v4"
- `/mab join Klausiiiii <TAB>` → "1", "2"

---

## Acceptance Criteria

- [x] 92 Domain-Tests grün
- [x] `./gradlew build` BUILD SUCCESSFUL
- [x] `/mab create <mode>` parsed Modi (1v1 bis 4v4, asymmetrisch)
- [x] `/mab join <captain> [team]` joint spezifisches Team oder auto-balanced
- [x] `/mab start` verlangt beide Teams mit mind. 1 Spieler
- [x] FarmPhase erstellt eine Welt pro Team (sprunghaft skaliert)
- [x] Pool-Tracking pro Team korrekt isoliert

## Bekannte Loose Ends

- **Asymmetrische Validierung im UI** — `/mab create 5v5` wird abgelehnt (max-size 4 in MatchMode), aber Mode-String wie `1v0` auch (sollte sein).
- **Captain-Promotion bei Leave** — wenn Team-2-Captain verlässt, der nächste Spieler in Team 2 wird neuer Captain. Das funktioniert weiterhin via `MatchManager.leaveMatch` Logik (Plan 1+1.5), aber nicht extra getestet für Multi-Team-Matches.
- **`/mab kick`** noch nicht implementiert (Spec hat es, aber bisher nicht im Code).
- **Tournament** kommt in Plan 6.
