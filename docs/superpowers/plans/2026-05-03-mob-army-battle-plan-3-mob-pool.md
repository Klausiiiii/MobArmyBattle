# MobArmyBattle Plan 3: Mob-Pool + Kill-Listener + Equipment-Tracking

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Wenn ein Spieler in der Farm-Phase einen Mob killt, wird der Mob in den Team-Pool aufgenommen — Equipment-bewusst (Skelett mit Eisen-Rüstung wird separat von vanilla Skelett aggregiert). Bei Spieler-Tod in Farm-Phase verliert der Spieler X% des Pool-Beitrags (Soft-Penalty default).

**Architecture:** Domain-Schicht hält Pool als `List<MobEntry>` mit `(entityTypeName: String, equipmentSignature: String, count: int)` — Bukkit-frei und testbar. `EquipmentSerializer` (Bukkit-Adapter) konvertiert `LivingEntity` → Signatur-String durch Zusammenfassen aller 6 Equipment-Slots zu einem deterministischen Format (`mainhand|offhand|helm|chest|legs|boots`, jedes Item als Material-Name oder "none"). NBT/Verzauberungen kommen in Plan 5 (Battle), wo sie für Re-Spawn gebraucht werden — Plan 3 sammelt nur erste-Klasse-Aggregation.

**Tech Stack:** Java 25, Paper API 26.1.2, JUnit 5.

**Plan-Umfang:** Plan 3 von ~10. Wave-Building (UI) kommt in Plan 4. Mob-Re-Spawn mit Equipment in Plan 5.

**Was nach Plan 3 lauffähig ist:** Spieler in Farm-Welt killt Mobs → Pool wird befüllt. `/mab pool` zeigt eigenen Team-Pool. Bei Spieler-Tod in Farm-Phase: Pool-Penalty wird angewandt (default 10%). Pool zeigt Equipment-Varianten getrennt.

---

## File Structure

| Datei | Zweck | Status |
|---|---|---|
| `src/main/java/de/klausiiiii/mobArmyBattle/pool/MobEntry.java` | Immutable Pool-Entry: type + equipment-signature + count | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/pool/MobPool.java` | Pool-Aggregat: add/remove/total, Equipment-bewusst | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/pool/EquipmentSerializer.java` | Bukkit-Adapter: LivingEntity → Signatur | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/Team.java` | Erweitern: `MobPool pool` field | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/listener/MobKillListener.java` | EntityDeathEvent → Pool.add | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/listener/PlayerDeathFarmListener.java` | PlayerDeathEvent in Farm-Welt → Pool-Penalty | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java` | Add `/mab pool` Subcommand | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java` | Listener registrieren | Modify |
| `src/test/java/de/klausiiiii/mobArmyBattle/pool/MobEntryTest.java` | Equality, Hashing | Create |
| `src/test/java/de/klausiiiii/mobArmyBattle/pool/MobPoolTest.java` | Aggregation, Penalty | Create |
| `src/test/java/de/klausiiiii/mobArmyBattle/match/TeamTest.java` | Add `team.getPool()` test | Modify |

---

## Task 1: MobEntry — immutable Pool-Eintrag (TDD)

**Files:**
- Test: `src/test/java/de/klausiiiii/mobArmyBattle/pool/MobEntryTest.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/pool/MobEntry.java`

- [ ] **Step 1: Failing test**

```java
package de.klausiiiii.mobArmyBattle.pool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobEntryTest {

    @Test
    void entriesWithSameTypeAndEquipmentAreEqual() {
        MobEntry a = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
        MobEntry b = new MobEntry("ZOMBIE", "none|none|none|none|none|none");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void entriesWithDifferentTypeAreNotEqual() {
        MobEntry a = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
        MobEntry b = new MobEntry("SKELETON", "none|none|none|none|none|none");

        assertNotEquals(a, b);
    }

    @Test
    void entriesWithDifferentEquipmentAreNotEqual() {
        MobEntry a = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
        MobEntry b = new MobEntry("ZOMBIE", "iron_sword|none|none|none|none|none");

        assertNotEquals(a, b);
    }

    @Test
    void rejectsNullType() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobEntry(null, "none|none|none|none|none|none"));
    }

    @Test
    void rejectsBlankType() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobEntry("", "none|none|none|none|none|none"));
    }

    @Test
    void rejectsNullEquipment() {
        assertThrows(IllegalArgumentException.class,
                () -> new MobEntry("ZOMBIE", null));
    }

    @Test
    void toStringContainsTypeAndEquipment() {
        MobEntry e = new MobEntry("ZOMBIE", "iron_sword|none|none|none|none|none");
        String s = e.toString();
        assertTrue(s.contains("ZOMBIE"));
        assertTrue(s.contains("iron_sword"));
    }
}
```

- [ ] **Step 2: Run, expect compile fail**: `./gradlew test --tests MobEntryTest`

- [ ] **Step 3: Implementation**

```java
package de.klausiiiii.mobArmyBattle.pool;

import java.util.Objects;

public final class MobEntry {

    private final String entityTypeName;
    private final String equipmentSignature;

    public MobEntry(String entityTypeName, String equipmentSignature) {
        if (entityTypeName == null || entityTypeName.isBlank()) {
            throw new IllegalArgumentException("entityTypeName darf nicht leer sein");
        }
        if (equipmentSignature == null) {
            throw new IllegalArgumentException("equipmentSignature darf nicht null sein");
        }
        this.entityTypeName = entityTypeName;
        this.equipmentSignature = equipmentSignature;
    }

    public String getEntityTypeName() {
        return entityTypeName;
    }

    public String getEquipmentSignature() {
        return equipmentSignature;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MobEntry that)) return false;
        return entityTypeName.equals(that.entityTypeName)
                && equipmentSignature.equals(that.equipmentSignature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityTypeName, equipmentSignature);
    }

    @Override
    public String toString() {
        return "MobEntry{" + entityTypeName + ", eq=" + equipmentSignature + "}";
    }
}
```

- [ ] **Step 4: Run all tests**: pass (40 + 7 = 47).

- [ ] **Step 5: Commit**: `feat: add immutable MobEntry with equipment-aware identity`

---

## Task 2: MobPool — Aggregation (TDD)

**Files:**
- Test: `src/test/java/de/klausiiiii/mobArmyBattle/pool/MobPoolTest.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/pool/MobPool.java`

- [ ] **Step 1: Failing test**

```java
package de.klausiiiii.mobArmyBattle.pool;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MobPoolTest {

    @Test
    void newPoolIsEmpty() {
        MobPool pool = new MobPool();
        assertEquals(0, pool.totalCount());
        assertTrue(pool.getEntries().isEmpty());
    }

    @Test
    void addingFirstMobCreatesEntry() {
        MobPool pool = new MobPool();
        pool.add(new MobEntry("ZOMBIE", "none|none|none|none|none|none"));

        assertEquals(1, pool.totalCount());
        assertEquals(1, pool.countOf(new MobEntry("ZOMBIE", "none|none|none|none|none|none")));
    }

    @Test
    void addingSameMobIncrementsCount() {
        MobPool pool = new MobPool();
        MobEntry zombie = new MobEntry("ZOMBIE", "none|none|none|none|none|none");

        pool.add(zombie);
        pool.add(zombie);
        pool.add(zombie);

        assertEquals(3, pool.totalCount());
        assertEquals(3, pool.countOf(zombie));
    }

    @Test
    void differentEquipmentTrackedSeparately() {
        MobPool pool = new MobPool();
        MobEntry vanilla = new MobEntry("SKELETON", "none|none|none|none|none|none");
        MobEntry iron = new MobEntry("SKELETON", "bow|none|iron_helmet|iron_chestplate|none|none");

        pool.add(vanilla);
        pool.add(iron);
        pool.add(iron);

        assertEquals(3, pool.totalCount());
        assertEquals(1, pool.countOf(vanilla));
        assertEquals(2, pool.countOf(iron));
    }

    @Test
    void countOfReturnsZeroForUnknownEntry() {
        MobPool pool = new MobPool();
        pool.add(new MobEntry("ZOMBIE", "none|none|none|none|none|none"));

        assertEquals(0, pool.countOf(new MobEntry("CREEPER", "none|none|none|none|none|none")));
    }

    @Test
    void canRemoveEntries() {
        MobPool pool = new MobPool();
        MobEntry e = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
        pool.add(e);
        pool.add(e);
        pool.add(e);

        int removed = pool.remove(e, 2);

        assertEquals(2, removed);
        assertEquals(1, pool.countOf(e));
    }

    @Test
    void removingMoreThanAvailableRemovesAll() {
        MobPool pool = new MobPool();
        MobEntry e = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
        pool.add(e);
        pool.add(e);

        int removed = pool.remove(e, 10);

        assertEquals(2, removed);
        assertEquals(0, pool.countOf(e));
    }

    @Test
    void removingDownToZeroDropsEntry() {
        MobPool pool = new MobPool();
        MobEntry e = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
        pool.add(e);

        pool.remove(e, 1);

        assertTrue(pool.getEntries().isEmpty());
    }

    @Test
    void applyPenaltyReducesEachEntryByPercent() {
        MobPool pool = new MobPool();
        MobEntry zombie = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
        MobEntry skeleton = new MobEntry("SKELETON", "none|none|none|none|none|none");
        for (int i = 0; i < 100; i++) pool.add(zombie);
        for (int i = 0; i < 50; i++) pool.add(skeleton);

        int lost = pool.applyPenalty(10);  // 10%

        assertEquals(15, lost);  // 10 from zombies + 5 from skeletons
        assertEquals(90, pool.countOf(zombie));
        assertEquals(45, pool.countOf(skeleton));
    }

    @Test
    void penaltyZeroDoesNothing() {
        MobPool pool = new MobPool();
        pool.add(new MobEntry("ZOMBIE", "none|none|none|none|none|none"));

        int lost = pool.applyPenalty(0);

        assertEquals(0, lost);
        assertEquals(1, pool.totalCount());
    }

    @Test
    void penaltyHundredEmptiesPool() {
        MobPool pool = new MobPool();
        for (int i = 0; i < 5; i++) pool.add(new MobEntry("ZOMBIE", "none|none|none|none|none|none"));

        int lost = pool.applyPenalty(100);

        assertEquals(5, lost);
        assertEquals(0, pool.totalCount());
    }

    @Test
    void penaltyRoundsDown() {
        MobPool pool = new MobPool();
        for (int i = 0; i < 7; i++) pool.add(new MobEntry("ZOMBIE", "none|none|none|none|none|none"));

        int lost = pool.applyPenalty(10);  // 0.7, rounds down to 0

        assertEquals(0, lost);
        assertEquals(7, pool.totalCount());
    }

    @Test
    void getEntriesReturnsImmutableSnapshot() {
        MobPool pool = new MobPool();
        pool.add(new MobEntry("ZOMBIE", "none|none|none|none|none|none"));

        Map<MobEntry, Integer> snapshot = pool.getEntries();

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.put(new MobEntry("CREEPER", "none|none|none|none|none|none"), 1));
    }
}
```

- [ ] **Step 2: Run, expect fail**: `./gradlew test --tests MobPoolTest`

- [ ] **Step 3: Implementation**

```java
package de.klausiiiii.mobArmyBattle.pool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MobPool {

    private final Map<MobEntry, Integer> entries = new LinkedHashMap<>();

    public void add(MobEntry entry) {
        entries.merge(entry, 1, Integer::sum);
    }

    public int remove(MobEntry entry, int amount) {
        if (amount <= 0) return 0;
        Integer current = entries.get(entry);
        if (current == null) return 0;
        int toRemove = Math.min(current, amount);
        int remaining = current - toRemove;
        if (remaining == 0) {
            entries.remove(entry);
        } else {
            entries.put(entry, remaining);
        }
        return toRemove;
    }

    public int countOf(MobEntry entry) {
        return entries.getOrDefault(entry, 0);
    }

    public int totalCount() {
        return entries.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Map<MobEntry, Integer> getEntries() {
        return Collections.unmodifiableMap(entries);
    }

    public int applyPenalty(int percent) {
        if (percent <= 0) return 0;
        if (percent >= 100) {
            int total = totalCount();
            entries.clear();
            return total;
        }
        int totalLost = 0;
        for (Map.Entry<MobEntry, Integer> e : new LinkedHashMap<>(entries).entrySet()) {
            int loss = e.getValue() * percent / 100;
            if (loss > 0) {
                totalLost += remove(e.getKey(), loss);
            }
        }
        return totalLost;
    }
}
```

- [ ] **Step 4: Tests pass**: 47 + 12 = 59 total.

- [ ] **Step 5: Commit**: `feat: add MobPool with equipment-aware aggregation and penalty`

---

## Task 3: Team — bekommt MobPool

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/Team.java`
- Modify: `src/test/java/de/klausiiiii/mobArmyBattle/match/TeamTest.java`

- [ ] **Step 1: Test ergänzen** (vor closing `}`):

```java
    @Test
    void newTeamHasEmptyPool() {
        Team team = new Team(UUID.randomUUID());

        assertNotNull(team.getPool());
        assertEquals(0, team.getPool().totalCount());
    }
```

Add import at top of TeamTest:
```java
import de.klausiiiii.mobArmyBattle.pool.MobPool;
```

(Actually, you don't need to import MobPool in the test — `team.getPool()` returns it transitively. The `assertEquals(0, team.getPool().totalCount())` only needs `MobPool` if we declared a variable. Skip the import.)

- [ ] **Step 2: Modify Team.java** — add field + getter

After `private final Set<UUID> memberIds;` add:
```java
    private final MobPool pool;
```

In constructor (after `this.memberIds.add(captainId);`) add:
```java
        this.pool = new MobPool();
```

After `public int size() {...}` (but before `isDisbanded()`) add:
```java
    public MobPool getPool() {
        return pool;
    }
```

Add import at top:
```java
import de.klausiiiii.mobArmyBattle.pool.MobPool;
```

- [ ] **Step 3: Tests pass**: 60 (+1 new).

- [ ] **Step 4: Commit**: `feat: add MobPool to Team`

---

## Task 4: EquipmentSerializer (Bukkit-Adapter)

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/pool/EquipmentSerializer.java`

Bukkit-bound — kein Unit-Test, manuell verifiziert in Task 8.

- [ ] **Step 1: Implementation**

```java
package de.klausiiiii.mobArmyBattle.pool;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

public final class EquipmentSerializer {

    private static final String NONE = "none";

    private EquipmentSerializer() {
    }

    public static String serialize(LivingEntity entity) {
        EntityEquipment eq = entity.getEquipment();
        if (eq == null) {
            return joinSlots(NONE, NONE, NONE, NONE, NONE, NONE);
        }
        return joinSlots(
                slotOf(eq.getItemInMainHand()),
                slotOf(eq.getItemInOffHand()),
                slotOf(eq.getHelmet()),
                slotOf(eq.getChestplate()),
                slotOf(eq.getLeggings()),
                slotOf(eq.getBoots())
        );
    }

    private static String slotOf(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return NONE;
        }
        return item.getType().name().toLowerCase();
    }

    private static String joinSlots(String... slots) {
        return String.join("|", slots);
    }
}
```

- [ ] **Step 2: Build OK**: `./gradlew build`

- [ ] **Step 3: Commit**: `feat: add EquipmentSerializer for mob equipment signatures`

---

## Task 5: MobKillListener

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/listener/MobKillListener.java`

- [ ] **Step 1: Implementation**

```java
package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.pool.EquipmentSerializer;
import de.klausiiiii.mobArmyBattle.pool.MobEntry;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class MobKillListener implements Listener {

    private final MatchManager matchManager;

    public MobKillListener(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;

        Player killer = entity.getKiller();
        if (killer == null) return;

        Match match = matchManager.getMatchOf(killer.getUniqueId());
        if (match == null) return;
        if (match.getCurrentPhase().getType() != MatchPhaseType.FARM) return;

        Team team = match.findTeamOf(killer.getUniqueId());
        if (team == null) return;

        // Verify entity died in the team's farm world
        String farmWorldName = match.getFarmWorldName(team);
        if (farmWorldName == null) return;
        if (!entity.getWorld().getName().equals(farmWorldName)) return;

        String typeName = entity.getType().name();
        String signature = EquipmentSerializer.serialize(entity);
        team.getPool().add(new MobEntry(typeName, signature));
    }
}
```

- [ ] **Step 2: Build OK**

- [ ] **Step 3: Commit**: `feat: track player mob kills into team pool during farm phase`

---

## Task 6: PlayerDeathFarmListener (Pool-Penalty)

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/listener/PlayerDeathFarmListener.java`

- [ ] **Step 1: Implementation**

```java
package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeathFarmListener implements Listener {

    private static final int DEFAULT_PENALTY_PERCENT = 10;

    private final MatchManager matchManager;

    public PlayerDeathFarmListener(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) return;
        if (match.getCurrentPhase().getType() != MatchPhaseType.FARM) return;

        Team team = match.findTeamOf(player.getUniqueId());
        if (team == null) return;

        int lost = team.getPool().applyPenalty(DEFAULT_PENALTY_PERCENT);
        if (lost > 0) {
            player.sendMessage(Component.text(
                    "Tod-Strafe: " + lost + " Mobs aus dem Team-Pool verloren.",
                    NamedTextColor.RED));
        }
    }
}
```

- [ ] **Step 2: Build OK**

- [ ] **Step 3: Commit**: `feat: apply 10% pool penalty on player death in farm phase`

---

## Task 7: /mab pool — Subcommand

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java`

- [ ] **Step 1: Modifications**

1. Update `SUBCOMMANDS` constant:
```java
    private static final List<String> SUBCOMMANDS = List.of("create", "join", "leave", "start", "pool");
```

2. Add new case in switch:
```java
                case "pool" -> handlePool(player);
```

3. Add new method `handlePool` (after `handleStart`):
```java
    private void handlePool(Player player) {
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) {
            player.sendMessage(Component.text("Du bist in keinem Match.", NamedTextColor.RED));
            return;
        }
        Team team = match.findTeamOf(player.getUniqueId());
        if (team == null || team.getPool().getEntries().isEmpty()) {
            player.sendMessage(Component.text("Pool ist leer.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("Team-Pool (" + team.getPool().totalCount() + " Mobs):",
                NamedTextColor.GOLD));
        team.getPool().getEntries().forEach((entry, count) -> {
            String label = entry.getEntityTypeName().toLowerCase();
            String eq = entry.getEquipmentSignature();
            boolean hasEq = !eq.equals("none|none|none|none|none|none");
            String suffix = hasEq ? " (" + summarize(eq) + ")" : "";
            player.sendMessage(Component.text(
                    "  " + label + suffix + " x " + count,
                    NamedTextColor.GRAY));
        });
    }

    private String summarize(String eq) {
        // First non-none slot for compact display
        for (String slot : eq.split("\\|")) {
            if (!slot.equals("none")) {
                return slot;
            }
        }
        return "geared";
    }
```

4. Add to `sendUsage`:
```java
        player.sendMessage(Component.text("/mab pool — Team-Pool anzeigen", NamedTextColor.GRAY));
```

5. Add imports if not present (Match, Team are already imported).

Add to `Pool` import group at top:
```java
import de.klausiiiii.mobArmyBattle.pool.MobEntry;
```

(Actually `MobEntry` not directly used in the method — the `entries.forEach` lambda handles it via Map.Entry. So you can skip the import.)

- [ ] **Step 2: Build OK**

- [ ] **Step 3: Commit**: `feat: add /mab pool command to display team mob inventory`

---

## Task 8: MainPlugin — Listener registrieren

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java`

- [ ] **Step 1: Add listener registration in onEnable** (after the existing `PlayerConnectionListener` registration):

```java
        getServer().getPluginManager().registerEvents(
                new MobKillListener(matchManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerDeathFarmListener(matchManager), this);
```

Add imports:
```java
import de.klausiiiii.mobArmyBattle.listener.MobKillListener;
import de.klausiiiii.mobArmyBattle.listener.PlayerDeathFarmListener;
```

- [ ] **Step 2: Build + tests**: 60 tests pass, build OK.

- [ ] **Step 3: Commit**: `feat: register mob kill and player death listeners`

---

## Task 9: Manueller Test

- [ ] **Step 1**: `./gradlew runServer`

- [ ] **Step 2**: Im Spiel
1. `/mab create`, `/mab start` → Farm-Welt erstellt + teleportiert
2. Mit `/give @s diamond_sword` und `/effect give @s strength 9999 100` ausstatten
3. Mob spawnen lassen (in Höhle gehen oder warten bis Nacht) ODER `/summon zombie ~ ~ ~`
4. Zombie killen → keine sofortige Anzeige (Stille im Hintergrund)
5. `/mab pool` → "Team-Pool (1 Mobs): zombie x 1"
6. Mehr Zombies killen → Counter steigt
7. `/summon skeleton ~ ~ ~ {ArmorItems:[{},{},{},{Count:1,id:"iron_helmet"}]}` (oder einfach ein Skelett mit zufälligem Equipment killen)
8. `/mab pool` → zwei Einträge bei unterschiedlichem Equipment, ein Eintrag bei vanilla

- [ ] **Step 3**: Pool-Penalty testen
1. Pool von ≥10 Mobs aufbauen
2. `/effect give @s instant_damage 1 200` (Tod)
3. Beim Respawn: Roter Chat-Text "Tod-Strafe: X Mobs aus dem Team-Pool verloren."
4. `/mab pool` → 10% weniger Mobs

---

## Acceptance Criteria

- [x] 60 Domain-Tests grün
- [x] `./gradlew build` BUILD SUCCESSFUL
- [x] Mob-Kill in Farm-Phase landet im Team-Pool
- [x] Mob mit Equipment wird separat von vanilla aggregiert
- [x] `/mab pool` zeigt aktuellen Stand
- [x] Tod in Farm-Phase löst 10%-Penalty aus

## Bekannte Loose Ends (für spätere Pläne)

- **Equipment-Snapshot ist String, nicht NBT** — für Plan 5 (Re-Spawn) braucht's vollen ItemStack-Snapshot mit Verzauberungen. MobEntry erweitern um `byte[] equipmentBytes` zusätzlich zur signature.
- **Penalty-Prozent hardcoded** — Config-Layer kommt in Plan 9.
- **Statistik-Anzeige im Scoreboard** — Plan 8 (UI-Polish).
- **Teammate-Kill zählt nur wenn Killer in der Farm-Welt des Teams ist** — derzeit könnte ein Spieler theoretisch in der falschen Welt killen (sollte nicht passieren wegen Welt-Trennung, aber Defensiv-Check ist drin).
