# MobArmyBattle Plan 1: Skeleton + Domain-Modell + Match-Erstellung

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Plugin-Skeleton mit Domain-Modell (Match, Team, MatchPhase State-Machine) und vier `/mab`-Befehlen (`create`, `join`, `leave`, `start`). Match kann erstellt, beigetreten, verlassen und vom Captain gestartet werden. State-Machine wechselt von Lobby → Farm korrekt. Domain ist Bukkit-frei und unit-getestet.

**Architecture:** State-Pattern für Match-Phasen (Phase-Interface + 5 Phase-Klassen, in diesem Plan noch leer/stub). Domain-Klassen (Match, Team) verwenden `UUID` statt Bukkit-`Player`-Referenzen → testbar ohne MockBukkit. `MatchManager` als zentraler Service hält alle aktiven Matches. Bukkit-Adapter (CommandExecutor) übersetzt zwischen `Player` und `UUID`.

**Tech Stack:** Java 25, Paper API 26.1.2, Gradle Kotlin DSL, JUnit 5 (für Domain-Tests), Adventure-API (Paper-Standard für Komponenten-Nachrichten).

**Plan-Umfang:** Plan 1 von ~10. Welt-Erstellung, Pool, GUI, Battle, Tournament, SQLite, Spectator, Config kommen in späteren Plänen. **Nach Plan 1 spielbar:** Plugin lädt, Captain kann Match erstellen, Spieler joinen, Captain startet — Match wechselt zu FarmPhase (die noch nichts tut, aber existiert).

---

## File Structure

| Datei | Zweck | Status |
|---|---|---|
| `build.gradle.kts` | JUnit 5 hinzufügen | Modify |
| `src/main/resources/plugin.yml` | Befehle deklarieren | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java` | Plugin-Lifecycle | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/MatchPhaseType.java` | Enum für die 5 Phase-Typen | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/MatchPhase.java` | Interface für Phase-Implementierungen | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/LobbyPhase.java` | Lobby-Phase-Stub | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FarmPhase.java` | Farm-Phase-Stub | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/WaveBuildPhase.java` | Wave-Build-Phase-Stub | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/BattlePhase.java` | Battle-Phase-Stub | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FinishedPhase.java` | Finished-Phase-Stub | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/Team.java` | Team mit Captain + Members (UUIDs) | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/Match.java` | Aggregat-Root mit State-Machine | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/MatchManager.java` | Service, hält alle aktiven Matches | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java` | Haupt-CommandExecutor mit Subbefehlen | Create |
| `src/test/java/de/klausiiiii/mobArmyBattle/match/TeamTest.java` | Tests für Team | Create |
| `src/test/java/de/klausiiiii/mobArmyBattle/match/MatchTest.java` | Tests für Match-State-Machine | Create |
| `src/test/java/de/klausiiiii/mobArmyBattle/match/MatchManagerTest.java` | Tests für MatchManager | Create |

---

## Task 1: Build-Setup für JUnit 5

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: build.gradle.kts erweitern**

Komplette neue Datei (überschreibt das Vorhandene):

```kotlin
plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    test {
        useJUnitPlatform()
    }

    runServer {
        minecraftVersion("26.1.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
```

- [ ] **Step 2: Verify Gradle accepts the build**

Run: `./gradlew help`
Expected: Build erfolgreich, keine Fehler.

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "build: add JUnit 5 for domain tests"
```

---

## Task 2: plugin.yml mit Befehl-Deklarationen

**Files:**
- Modify: `src/main/resources/plugin.yml`

- [ ] **Step 1: plugin.yml erweitern**

Komplette neue Datei:

```yaml
name: MobArmyBattle
description: $description
version: '${version}'

main: de.klausiiiii.mobArmyBattle.MobArmyBattle
api-version: '26.1.2'
load: POSTWORLD

authors: [ Klausiiiii ]

commands:
  mab:
    description: MobArmyBattle main command
    usage: /mab <create|join|leave|start> [args]
    aliases: [ mobarmybattle ]

permissions:
  mobarmybattle.create:
    default: true
    description: Allow creating matches
  mobarmybattle.join:
    default: true
    description: Allow joining matches
  mobarmybattle.admin:
    default: op
    description: Admin commands
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/plugin.yml
git commit -m "feat: declare /mab command and basic permissions"
```

---

## Task 3: MatchPhaseType Enum

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/match/MatchPhaseType.java`

- [ ] **Step 1: Enum erstellen**

```java
package de.klausiiiii.mobArmyBattle.match;

public enum MatchPhaseType {
    LOBBY,
    FARM,
    WAVE_BUILD,
    BATTLE,
    FINISHED
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/match/MatchPhaseType.java
git commit -m "feat: add MatchPhaseType enum"
```

---

## Task 4: MatchPhase Interface + 5 Stub-Phase-Klassen

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/match/MatchPhase.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/LobbyPhase.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FarmPhase.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/WaveBuildPhase.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/BattlePhase.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FinishedPhase.java`

- [ ] **Step 1: MatchPhase Interface**

```java
package de.klausiiiii.mobArmyBattle.match;

public interface MatchPhase {
    MatchPhaseType getType();
    void onEnter(Match match);
    void onExit(Match match);
    void tick(Match match);
}
```

- [ ] **Step 2: LobbyPhase Stub**

```java
package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;

public class LobbyPhase implements MatchPhase {
    @Override
    public MatchPhaseType getType() {
        return MatchPhaseType.LOBBY;
    }

    @Override
    public void onEnter(Match match) {
    }

    @Override
    public void onExit(Match match) {
    }

    @Override
    public void tick(Match match) {
    }
}
```

- [ ] **Step 3: FarmPhase Stub**

```java
package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;

public class FarmPhase implements MatchPhase {
    @Override
    public MatchPhaseType getType() {
        return MatchPhaseType.FARM;
    }

    @Override
    public void onEnter(Match match) {
    }

    @Override
    public void onExit(Match match) {
    }

    @Override
    public void tick(Match match) {
    }
}
```

- [ ] **Step 4: WaveBuildPhase Stub**

```java
package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;

public class WaveBuildPhase implements MatchPhase {
    @Override
    public MatchPhaseType getType() {
        return MatchPhaseType.WAVE_BUILD;
    }

    @Override
    public void onEnter(Match match) {
    }

    @Override
    public void onExit(Match match) {
    }

    @Override
    public void tick(Match match) {
    }
}
```

- [ ] **Step 5: BattlePhase Stub**

```java
package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;

public class BattlePhase implements MatchPhase {
    @Override
    public MatchPhaseType getType() {
        return MatchPhaseType.BATTLE;
    }

    @Override
    public void onEnter(Match match) {
    }

    @Override
    public void onExit(Match match) {
    }

    @Override
    public void tick(Match match) {
    }
}
```

- [ ] **Step 6: FinishedPhase Stub**

```java
package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;

public class FinishedPhase implements MatchPhase {
    @Override
    public MatchPhaseType getType() {
        return MatchPhaseType.FINISHED;
    }

    @Override
    public void onEnter(Match match) {
    }

    @Override
    public void onExit(Match match) {
    }

    @Override
    public void tick(Match match) {
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/match/MatchPhase.java src/main/java/de/klausiiiii/mobArmyBattle/match/phase/
git commit -m "feat: add MatchPhase interface and stub phase classes"
```

---

## Task 5: Team-Klasse + Tests (TDD)

**Files:**
- Test: `src/test/java/de/klausiiiii/mobArmyBattle/match/TeamTest.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/match/Team.java`

- [ ] **Step 1: Failing test schreiben**

```java
package de.klausiiiii.mobArmyBattle.match;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamTest {

    @Test
    void newTeamHasCaptainAsOnlyMember() {
        UUID captain = UUID.randomUUID();
        Team team = new Team(captain);

        assertEquals(captain, team.getCaptainId());
        assertEquals(1, team.getMemberIds().size());
        assertTrue(team.getMemberIds().contains(captain));
    }

    @Test
    void canAddMember() {
        UUID captain = UUID.randomUUID();
        UUID newMember = UUID.randomUUID();
        Team team = new Team(captain);

        team.addMember(newMember);

        assertTrue(team.getMemberIds().contains(newMember));
        assertEquals(2, team.getMemberIds().size());
    }

    @Test
    void cannotAddSameMemberTwice() {
        UUID captain = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Team team = new Team(captain);
        team.addMember(member);

        assertThrows(IllegalArgumentException.class, () -> team.addMember(member));
    }

    @Test
    void canRemoveMember() {
        UUID captain = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Team team = new Team(captain);
        team.addMember(member);

        team.removeMember(member);

        assertFalse(team.getMemberIds().contains(member));
        assertEquals(1, team.getMemberIds().size());
    }

    @Test
    void cannotRemoveCaptainDirectly() {
        UUID captain = UUID.randomUUID();
        Team team = new Team(captain);

        assertThrows(IllegalStateException.class, () -> team.removeMember(captain));
    }

    @Test
    void canPromoteNewCaptain() {
        UUID oldCaptain = UUID.randomUUID();
        UUID newCaptain = UUID.randomUUID();
        Team team = new Team(oldCaptain);
        team.addMember(newCaptain);

        team.promoteToCaptain(newCaptain);

        assertEquals(newCaptain, team.getCaptainId());
        assertTrue(team.getMemberIds().contains(oldCaptain));
        assertTrue(team.getMemberIds().contains(newCaptain));
    }

    @Test
    void cannotPromoteNonMember() {
        UUID captain = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        Team team = new Team(captain);

        assertThrows(IllegalArgumentException.class, () -> team.promoteToCaptain(outsider));
    }

    @Test
    void hasMemberReturnsTrueForCaptain() {
        UUID captain = UUID.randomUUID();
        Team team = new Team(captain);

        assertTrue(team.hasMember(captain));
    }
}
```

- [ ] **Step 2: Run failing tests**

Run: `./gradlew test`
Expected: FAIL — `Team`-Klasse existiert nicht.

- [ ] **Step 3: Implementierung**

```java
package de.klausiiiii.mobArmyBattle.match;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Collections;

public class Team {
    private UUID captainId;
    private final Set<UUID> memberIds;

    public Team(UUID captainId) {
        if (captainId == null) {
            throw new IllegalArgumentException("captainId darf nicht null sein");
        }
        this.captainId = captainId;
        this.memberIds = new LinkedHashSet<>();
        this.memberIds.add(captainId);
    }

    public UUID getCaptainId() {
        return captainId;
    }

    public Set<UUID> getMemberIds() {
        return Collections.unmodifiableSet(memberIds);
    }

    public boolean hasMember(UUID playerId) {
        return memberIds.contains(playerId);
    }

    public void addMember(UUID playerId) {
        if (memberIds.contains(playerId)) {
            throw new IllegalArgumentException("Spieler ist bereits Team-Mitglied: " + playerId);
        }
        memberIds.add(playerId);
    }

    public void removeMember(UUID playerId) {
        if (playerId.equals(captainId)) {
            throw new IllegalStateException("Captain kann nicht direkt entfernt werden, erst promoteToCaptain");
        }
        memberIds.remove(playerId);
    }

    public void promoteToCaptain(UUID newCaptainId) {
        if (!memberIds.contains(newCaptainId)) {
            throw new IllegalArgumentException("Neuer Captain muss bereits Team-Mitglied sein: " + newCaptainId);
        }
        this.captainId = newCaptainId;
    }

    public int size() {
        return memberIds.size();
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test`
Expected: PASS, alle 8 TeamTests grün.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/match/Team.java src/test/java/de/klausiiiii/mobArmyBattle/match/TeamTest.java
git commit -m "feat: add Team domain class with captain promotion"
```

---

## Task 6: Match-Klasse mit State-Machine + Tests (TDD)

**Files:**
- Test: `src/test/java/de/klausiiiii/mobArmyBattle/match/MatchTest.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/match/Match.java`

- [ ] **Step 1: Failing test schreiben**

```java
package de.klausiiiii.mobArmyBattle.match;

import de.klausiiiii.mobArmyBattle.match.phase.LobbyPhase;
import de.klausiiiii.mobArmyBattle.match.phase.FarmPhase;
import de.klausiiiii.mobArmyBattle.match.phase.WaveBuildPhase;
import de.klausiiiii.mobArmyBattle.match.phase.BattlePhase;
import de.klausiiiii.mobArmyBattle.match.phase.FinishedPhase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MatchTest {

    @Test
    void newMatchStartsInLobbyPhase() {
        Match match = new Match("test-match-1");

        assertEquals(MatchPhaseType.LOBBY, match.getCurrentPhase().getType());
    }

    @Test
    void newMatchHasNoTeams() {
        Match match = new Match("test-match-1");

        assertTrue(match.getTeams().isEmpty());
    }

    @Test
    void canAddTeam() {
        Match match = new Match("test-match-1");
        UUID captain = UUID.randomUUID();
        Team team = new Team(captain);

        match.addTeam(team);

        assertEquals(1, match.getTeams().size());
        assertTrue(match.getTeams().contains(team));
    }

    @Test
    void canFindTeamByPlayerId() {
        Match match = new Match("test-match-1");
        UUID captain = UUID.randomUUID();
        Team team = new Team(captain);
        match.addTeam(team);

        Team found = match.findTeamOf(captain);

        assertSame(team, found);
    }

    @Test
    void findTeamReturnsNullForUnknownPlayer() {
        Match match = new Match("test-match-1");
        match.addTeam(new Team(UUID.randomUUID()));

        Team found = match.findTeamOf(UUID.randomUUID());

        assertNull(found);
    }

    @Test
    void transitionFromLobbyToFarm() {
        Match match = new Match("test-match-1");

        match.transitionTo(new FarmPhase());

        assertEquals(MatchPhaseType.FARM, match.getCurrentPhase().getType());
    }

    @Test
    void transitionThroughAllPhases() {
        Match match = new Match("test-match-1");

        match.transitionTo(new FarmPhase());
        assertEquals(MatchPhaseType.FARM, match.getCurrentPhase().getType());

        match.transitionTo(new WaveBuildPhase());
        assertEquals(MatchPhaseType.WAVE_BUILD, match.getCurrentPhase().getType());

        match.transitionTo(new BattlePhase());
        assertEquals(MatchPhaseType.BATTLE, match.getCurrentPhase().getType());

        match.transitionTo(new FinishedPhase());
        assertEquals(MatchPhaseType.FINISHED, match.getCurrentPhase().getType());
    }

    @Test
    void cannotTransitionFromFinished() {
        Match match = new Match("test-match-1");
        match.transitionTo(new FinishedPhase());

        assertThrows(IllegalStateException.class, () -> match.transitionTo(new LobbyPhase()));
    }

    @Test
    void matchHasUniqueId() {
        Match match = new Match("foo-id");

        assertEquals("foo-id", match.getId());
    }
}
```

- [ ] **Step 2: Run failing tests**

Run: `./gradlew test`
Expected: FAIL — `Match`-Klasse existiert nicht.

- [ ] **Step 3: Match-Implementierung**

```java
package de.klausiiiii.mobArmyBattle.match;

import de.klausiiiii.mobArmyBattle.match.phase.LobbyPhase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Match {
    private final String id;
    private final List<Team> teams;
    private MatchPhase currentPhase;

    public Match(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Match-ID darf nicht leer sein");
        }
        this.id = id;
        this.teams = new ArrayList<>();
        this.currentPhase = new LobbyPhase();
        this.currentPhase.onEnter(this);
    }

    public String getId() {
        return id;
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

- [ ] **Step 4: Run tests**

Run: `./gradlew test`
Expected: PASS, alle Match-Tests grün.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/match/Match.java src/test/java/de/klausiiiii/mobArmyBattle/match/MatchTest.java
git commit -m "feat: add Match aggregate with state-machine transitions"
```

---

## Task 7: MatchManager-Service + Tests

**Files:**
- Test: `src/test/java/de/klausiiiii/mobArmyBattle/match/MatchManagerTest.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/match/MatchManager.java`

- [ ] **Step 1: Failing test schreiben**

```java
package de.klausiiiii.mobArmyBattle.match;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MatchManagerTest {

    @Test
    void canCreateMatchWithCaptain() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();

        Match match = manager.createMatch(captain);

        assertNotNull(match);
        assertEquals(MatchPhaseType.LOBBY, match.getCurrentPhase().getType());
        assertEquals(1, match.getTeams().size());
        assertEquals(captain, match.getTeams().get(0).getCaptainId());
    }

    @Test
    void matchIdsAreUnique() {
        MatchManager manager = new MatchManager();

        Match m1 = manager.createMatch(UUID.randomUUID());
        Match m2 = manager.createMatch(UUID.randomUUID());

        assertNotEquals(m1.getId(), m2.getId());
    }

    @Test
    void canFindMatchOfPlayer() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        Match match = manager.createMatch(captain);

        Match found = manager.getMatchOf(captain);

        assertSame(match, found);
    }

    @Test
    void getMatchOfReturnsNullWhenNotInMatch() {
        MatchManager manager = new MatchManager();

        assertNull(manager.getMatchOf(UUID.randomUUID()));
    }

    @Test
    void cannotCreateSecondMatchForSamePlayer() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        manager.createMatch(captain);

        assertThrows(IllegalStateException.class, () -> manager.createMatch(captain));
    }

    @Test
    void canJoinExistingMatch() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        Match match = manager.createMatch(captain);

        manager.joinMatch(joiner, captain);

        assertSame(match, manager.getMatchOf(joiner));
        assertTrue(match.getTeams().get(0).hasMember(joiner));
    }

    @Test
    void joiningUnknownCaptainThrows() {
        MatchManager manager = new MatchManager();

        assertThrows(IllegalArgumentException.class,
                () -> manager.joinMatch(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void joiningWhileAlreadyInMatchThrows() {
        MatchManager manager = new MatchManager();
        UUID captain1 = UUID.randomUUID();
        UUID captain2 = UUID.randomUUID();
        manager.createMatch(captain1);
        manager.createMatch(captain2);

        assertThrows(IllegalStateException.class,
                () -> manager.joinMatch(captain1, captain2));
    }

    @Test
    void canLeaveMatch() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain);
        manager.joinMatch(joiner, captain);

        manager.leaveMatch(joiner);

        assertNull(manager.getMatchOf(joiner));
    }

    @Test
    void leavingAsCaptainPromotesNextMember() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        Match match = manager.createMatch(captain);
        manager.joinMatch(joiner, captain);

        manager.leaveMatch(captain);

        assertNull(manager.getMatchOf(captain));
        assertEquals(joiner, match.getTeams().get(0).getCaptainId());
    }

    @Test
    void leavingAsLastMemberClosesMatch() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        Match match = manager.createMatch(captain);

        manager.leaveMatch(captain);

        assertNull(manager.getMatchOf(captain));
        assertFalse(manager.getActiveMatches().contains(match));
    }
}
```

- [ ] **Step 2: Run failing tests**

Run: `./gradlew test`
Expected: FAIL — `MatchManager` existiert nicht.

- [ ] **Step 3: MatchManager-Implementierung**

```java
package de.klausiiiii.mobArmyBattle.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class MatchManager {
    private final Map<String, Match> matchesById = new HashMap<>();
    private final Map<UUID, Match> matchByPlayer = new HashMap<>();
    private final AtomicLong matchIdCounter = new AtomicLong(1);

    public Match createMatch(UUID captainId) {
        if (matchByPlayer.containsKey(captainId)) {
            throw new IllegalStateException("Spieler ist bereits in einem Match: " + captainId);
        }
        String id = "match-" + matchIdCounter.getAndIncrement();
        Match match = new Match(id);
        match.addTeam(new Team(captainId));
        matchesById.put(id, match);
        matchByPlayer.put(captainId, match);
        return match;
    }

    public void joinMatch(UUID playerId, UUID captainId) {
        if (matchByPlayer.containsKey(playerId)) {
            throw new IllegalStateException("Spieler ist bereits in einem Match: " + playerId);
        }
        Match match = matchByPlayer.get(captainId);
        if (match == null) {
            throw new IllegalArgumentException("Captain hat kein Match: " + captainId);
        }
        Team captainsTeam = match.findTeamOf(captainId);
        if (captainsTeam == null || !captainsTeam.getCaptainId().equals(captainId)) {
            throw new IllegalArgumentException("Spieler " + captainId + " ist nicht Captain");
        }
        captainsTeam.addMember(playerId);
        matchByPlayer.put(playerId, match);
    }

    public void leaveMatch(UUID playerId) {
        Match match = matchByPlayer.get(playerId);
        if (match == null) {
            return;
        }
        Team team = match.findTeamOf(playerId);
        boolean wasCaptain = team.getCaptainId().equals(playerId);

        if (wasCaptain) {
            UUID nextCaptain = team.getMemberIds().stream()
                    .filter(id -> !id.equals(playerId))
                    .findFirst()
                    .orElse(null);
            if (nextCaptain != null) {
                team.promoteToCaptain(nextCaptain);
                team.removeMember(playerId);
            }
            // If nextCaptain is null, captain was the last member of this team.
            // We can't remove him via Team.removeMember (it would throw); the
            // match-empty check below handles cleanup based on matchByPlayer.
        } else {
            team.removeMember(playerId);
        }
        matchByPlayer.remove(playerId);

        boolean stillHasPlayers = matchByPlayer.values().stream()
                .anyMatch(m -> m == match);
        if (!stillHasPlayers) {
            matchesById.remove(match.getId());
        }
    }

    public Match getMatchOf(UUID playerId) {
        return matchByPlayer.get(playerId);
    }

    public Match getMatchById(String matchId) {
        return matchesById.get(matchId);
    }

    public List<Match> getActiveMatches() {
        return Collections.unmodifiableList(new ArrayList<>(matchesById.values()));
    }

    public void tickAll() {
        for (Match match : matchesById.values()) {
            match.tick();
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `./gradlew test`
Expected: PASS, alle MatchManagerTests grün.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/match/MatchManager.java src/test/java/de/klausiiiii/mobArmyBattle/match/MatchManagerTest.java
git commit -m "feat: add MatchManager service with create/join/leave/captain-promotion"
```

---

## Task 8: MabCommand Executor (Bukkit-Adapter)

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java`

- [ ] **Step 1: MabCommand erstellen**

```java
package de.klausiiiii.mobArmyBattle.command;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.match.phase.FarmPhase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class MabCommand implements CommandExecutor {

    private final MatchManager matchManager;

    public MabCommand(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Nur Spieler dürfen /mab ausführen.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        try {
            switch (sub) {
                case "create" -> handleCreate(player);
                case "join" -> handleJoin(player, args);
                case "leave" -> handleLeave(player);
                case "start" -> handleStart(player);
                default -> sendUsage(player);
            }
        } catch (IllegalStateException | IllegalArgumentException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    private void handleCreate(Player player) {
        Match match = matchManager.createMatch(player.getUniqueId());
        player.sendMessage(Component.text("Match erstellt: " + match.getId() + ". Du bist Captain.",
                NamedTextColor.GREEN));
        player.sendMessage(Component.text("Andere joinen mit: /mab join " + player.getName(),
                NamedTextColor.GRAY));
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Verwendung: /mab join <captain-name>", NamedTextColor.RED));
            return;
        }
        Player captainPlayer = Bukkit.getPlayerExact(args[1]);
        if (captainPlayer == null) {
            player.sendMessage(Component.text("Spieler nicht online: " + args[1], NamedTextColor.RED));
            return;
        }
        matchManager.joinMatch(player.getUniqueId(), captainPlayer.getUniqueId());
        player.sendMessage(Component.text("Match beigetreten von " + captainPlayer.getName(),
                NamedTextColor.GREEN));
        captainPlayer.sendMessage(Component.text(player.getName() + " ist deinem Match beigetreten.",
                NamedTextColor.GREEN));
    }

    private void handleLeave(Player player) {
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) {
            player.sendMessage(Component.text("Du bist in keinem Match.", NamedTextColor.RED));
            return;
        }
        matchManager.leaveMatch(player.getUniqueId());
        player.sendMessage(Component.text("Match verlassen.", NamedTextColor.YELLOW));
    }

    private void handleStart(Player player) {
        UUID playerId = player.getUniqueId();
        Match match = matchManager.getMatchOf(playerId);
        if (match == null) {
            player.sendMessage(Component.text("Du bist in keinem Match.", NamedTextColor.RED));
            return;
        }
        if (match.getCurrentPhase().getType() != MatchPhaseType.LOBBY) {
            player.sendMessage(Component.text("Match ist bereits gestartet.", NamedTextColor.RED));
            return;
        }
        Team team = match.findTeamOf(playerId);
        if (!team.getCaptainId().equals(playerId)) {
            player.sendMessage(Component.text("Nur der Captain darf starten.", NamedTextColor.RED));
            return;
        }
        match.transitionTo(new FarmPhase());

        // Notify all members
        for (Team t : match.getTeams()) {
            for (UUID memberId : t.getMemberIds()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.sendMessage(Component.text("Match gestartet — Phase: FARM (Stub).",
                            NamedTextColor.GOLD));
                }
            }
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(Component.text("MobArmyBattle-Befehle:", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/mab create — Match erstellen", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab join <captain> — Match beitreten", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab leave — Match verlassen", NamedTextColor.GRAY));
        player.sendMessage(Component.text("/mab start — Match starten (nur Captain)", NamedTextColor.GRAY));
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java
git commit -m "feat: add /mab command executor with create/join/leave/start"
```

---

## Task 9: MainPlugin-Klasse erweitern

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java`

- [ ] **Step 1: Plugin-Hauptklasse erweitern**

```java
package de.klausiiiii.mobArmyBattle;

import de.klausiiiii.mobArmyBattle.command.MabCommand;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobArmyBattle extends JavaPlugin {

    private MatchManager matchManager;

    @Override
    public void onEnable() {
        matchManager = new MatchManager();

        PluginCommand mabCmd = getCommand("mab");
        if (mabCmd == null) {
            getLogger().severe("Befehl /mab nicht in plugin.yml deklariert!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        mabCmd.setExecutor(new MabCommand(matchManager));

        getLogger().info("MobArmyBattle aktiviert.");
    }

    @Override
    public void onDisable() {
        getLogger().info("MobArmyBattle deaktiviert.");
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }
}
```

- [ ] **Step 2: Build prüfen**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java
git commit -m "feat: wire MatchManager and /mab command in plugin lifecycle"
```

---

## Task 10: Manueller End-to-End-Test

**Files:** keine

- [ ] **Step 1: Server starten**

Run: `./gradlew runServer`
Expected: Paper-Server startet, MobArmyBattle erscheint im Plugin-Log mit "MobArmyBattle aktiviert."

- [ ] **Step 2: Im Spiel testen — als Captain**

Im Spiel:
1. `/mab` → Hilfetext erscheint
2. `/mab create` → "Match erstellt: match-1. Du bist Captain."
3. `/mab create` nochmal → Fehler "Spieler ist bereits in einem Match"
4. `/mab leave` → "Match verlassen."
5. `/mab start` → Fehler "Du bist in keinem Match."

- [ ] **Step 3: Im Spiel testen — als Joiner (zweiter Spieler)**

Voraussetzung: zweiter Spieler joint Server (oder Test mit Multi-Account).
1. Spieler A: `/mab create`
2. Spieler B: `/mab join <PlayerA>` → Beide bekommen Bestätigung
3. Spieler B: `/mab start` → Fehler "Nur der Captain darf starten"
4. Spieler A: `/mab start` → Beide bekommen "Match gestartet — Phase: FARM (Stub)."

- [ ] **Step 4: Test-Erfolg dokumentieren**

Wenn alle Steps grün: Plan 1 abgeschlossen. Keine Welt-Erstellung erfolgte (kommt in Plan 2), aber State-Machine-Übergang Lobby → Farm funktioniert.

- [ ] **Step 5: Server stoppen**

Im Server-Log: `stop` eintippen oder Strg+C.

---

## Acceptance Criteria

- [x] `./gradlew test` läuft erfolgreich, alle Domain-Tests grün
- [x] `./gradlew build` produziert eine Plugin-JAR
- [x] `./gradlew runServer` startet Paper-Server mit geladenem Plugin
- [x] `/mab create`, `/mab join`, `/mab leave`, `/mab start` funktionieren manuell wie spezifiziert
- [x] Match-State wechselt Lobby → Farm bei Captain-Start
- [x] Captain-Promotion bei Leave funktioniert
- [x] Plugin disabled sauber bei Server-Shutdown

## Was fehlt (kommt in späteren Plänen)

- Welt-Erstellung (Lobby, Farm, Arena) — **Plan 2**
- Mob-Pool + Equipment-Tracking + Kill-Listener — **Plan 3**
- Wave-Build-GUI — **Plan 4**
- Battle-Engine (Spawning, Sieger-Berechnung) — **Plan 5**
- Tournament + Bracket — **Plan 6**
- SQLite + Stats + Leaderboard — **Plan 7**
- Spectator + Sidebar-Scoreboard — **Plan 8**
- Modus-Auswahl (1v1/2v2/etc.), Asymmetrische Teams — **Plan 9**
- Config-File + alle Permissions — **Plan 9**
- Final Polish + Testing — **Plan 10**
