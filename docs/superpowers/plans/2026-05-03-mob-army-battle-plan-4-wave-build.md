# MobArmyBattle Plan 4: Wave-Building (Domain + GUI)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task.

**Goal:** Captain baut nach der Farm-Phase in einem Inventar-GUI zwei Wellen aus Team-Pool. Pool-Mobs werden konsumiert, jede Welle braucht mind. 1 Mob, Wellen sind geheim für Gegner. Per `/mab endfarm` (Captain-Befehl) wird die Farm-Phase manuell beendet → Wave-Build-Phase startet → GUI öffnet sich automatisch beim Captain.

**Architecture:** Domain hält `WaveSlot` (`MobEntry` + count) und `Wave` (Liste von Slots). `Team` bekommt 2 Wellen. `WaveBuildPhase.onEnter` öffnet GUI bei jedem Captain. `WaveBuildGui` ist Bukkit-Adapter: 6-Reihen-Inventar, oben Pool-Übersicht (Mob-Spawn-Eier), unten aktuelle Welle. Klick auf Pool-Item bewegt Mob in aktuelle Welle (verbraucht Pool); Klick auf Wave-Slot entfernt (zurück in Pool). Tab-Switcher für Welle 1/2. Bestätigen-Button checkt min-1-Mob, dann setzt Wave als finalisiert. Wenn beide Wellen aller Teams finalisiert sind → Phase wechselt zu `BATTLE` (Battle ist noch Stub).

**Tech Stack:** Java 25, Paper API 26.1.2, Bukkit `InventoryView`/`InventoryClickEvent`/`InventoryCloseEvent`, JUnit 5.

**Plan-Umfang:** Plan 4 von ~10. Vorschlag-Mechanik (Teammates schlagen vor, Captain-Veto) wird vereinfacht: **nur Captain darf bauen**, Teammates haben read-only-GUI. Volle Vorschlag-Mechanik kommt später als Polish.

**Was nach Plan 4 spielbar ist:** `/mab create N`, alle joinen, `/mab start` → Farm-Phase. Mobs killen → `/mab endfarm` (Captain) → Wave-Build-Phase startet, GUI öffnet sich bei Captain, Captain klickt 2 Wellen zusammen, bestätigt beide. Sobald alle Captains beider Teams bestätigt haben → Phase wechselt zu BATTLE (Stub).

---

## File Structure

| Datei | Zweck | Status |
|---|---|---|
| `src/main/java/de/klausiiiii/mobArmyBattle/wave/WaveSlot.java` | Immutable: MobEntry + count | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/wave/Wave.java` | Liste von WaveSlots, finalisierbar, Constraints | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/Team.java` | + `Wave wave1, wave2`, `boolean wavesFinalised()` | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/wave/WaveBuildGui.java` | Bukkit-Adapter: 6-Reihen-Inventar | Create |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/WaveBuildPhase.java` | onEnter: GUI öffnen bei Captains | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FarmPhase.java` | (optional) Phase-End-Hook prüfen | Keep |
| `src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java` | `/mab endfarm` Subcommand | Modify |
| `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java` | Listener registrieren (InventoryClickEvent) | Modify |
| `src/test/java/de/klausiiiii/mobArmyBattle/wave/WaveSlotTest.java` | Equality, validation | Create |
| `src/test/java/de/klausiiiii/mobArmyBattle/wave/WaveTest.java` | Add/remove slots, finalize, min-1 constraint | Create |
| `src/test/java/de/klausiiiii/mobArmyBattle/match/TeamTest.java` | + `wavesFinalised()` test | Modify |

---

## Task 1: WaveSlot — immutable value (TDD)

**Files:**
- Test: `src/test/java/de/klausiiiii/mobArmyBattle/wave/WaveSlotTest.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/wave/WaveSlot.java`

- [ ] **Step 1: Failing tests**

```java
package de.klausiiiii.mobArmyBattle.wave;

import de.klausiiiii.mobArmyBattle.pool.MobEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaveSlotTest {

    private static final MobEntry ZOMBIE = new MobEntry("ZOMBIE", "none|none|none|none|none|none");

    @Test
    void slotHoldsEntryAndCount() {
        WaveSlot slot = new WaveSlot(ZOMBIE, 5);
        assertEquals(ZOMBIE, slot.getEntry());
        assertEquals(5, slot.getCount());
    }

    @Test
    void rejectsNullEntry() {
        assertThrows(IllegalArgumentException.class, () -> new WaveSlot(null, 1));
    }

    @Test
    void rejectsZeroOrNegativeCount() {
        assertThrows(IllegalArgumentException.class, () -> new WaveSlot(ZOMBIE, 0));
        assertThrows(IllegalArgumentException.class, () -> new WaveSlot(ZOMBIE, -1));
    }

    @Test
    void slotsWithSameEntryAndCountAreEqual() {
        WaveSlot a = new WaveSlot(ZOMBIE, 3);
        WaveSlot b = new WaveSlot(ZOMBIE, 3);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void slotsWithDifferentCountAreNotEqual() {
        WaveSlot a = new WaveSlot(ZOMBIE, 3);
        WaveSlot b = new WaveSlot(ZOMBIE, 5);
        assertNotEquals(a, b);
    }
}
```

- [ ] **Step 2: Run, expect compile fail**

- [ ] **Step 3: Implementation**

```java
package de.klausiiiii.mobArmyBattle.wave;

import de.klausiiiii.mobArmyBattle.pool.MobEntry;

import java.util.Objects;

public final class WaveSlot {

    private final MobEntry entry;
    private final int count;

    public WaveSlot(MobEntry entry, int count) {
        if (entry == null) {
            throw new IllegalArgumentException("entry darf nicht null sein");
        }
        if (count < 1) {
            throw new IllegalArgumentException("count muss >= 1 sein");
        }
        this.entry = entry;
        this.count = count;
    }

    public MobEntry getEntry() {
        return entry;
    }

    public int getCount() {
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WaveSlot that)) return false;
        return count == that.count && entry.equals(that.entry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entry, count);
    }

    @Override
    public String toString() {
        return "WaveSlot{" + entry + " x " + count + "}";
    }
}
```

- [ ] **Step 4: Tests pass** — 87 (82 + 5).

- [ ] **Step 5: Commit**: `feat: add WaveSlot immutable value class`

---

## Task 2: Wave — Liste, Add/Remove, Finalize (TDD)

**Files:**
- Test: `src/test/java/de/klausiiiii/mobArmyBattle/wave/WaveTest.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/wave/Wave.java`

- [ ] **Step 1: Failing tests**

```java
package de.klausiiiii.mobArmyBattle.wave;

import de.klausiiiii.mobArmyBattle.pool.MobEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WaveTest {

    private static final MobEntry ZOMBIE = new MobEntry("ZOMBIE", "none|none|none|none|none|none");
    private static final MobEntry SKELETON = new MobEntry("SKELETON", "none|none|none|none|none|none");

    @Test
    void newWaveIsEmpty() {
        Wave wave = new Wave();
        assertEquals(0, wave.totalMobCount());
        assertTrue(wave.getSlots().isEmpty());
        assertFalse(wave.isFinalised());
    }

    @Test
    void canAddSlot() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 5);
        assertEquals(5, wave.totalMobCount());
        assertEquals(5, wave.countOf(ZOMBIE));
    }

    @Test
    void addingExistingEntryIncrementsCount() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 3);
        wave.add(ZOMBIE, 4);
        assertEquals(7, wave.countOf(ZOMBIE));
        assertEquals(7, wave.totalMobCount());
    }

    @Test
    void differentEntriesTrackedSeparately() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 5);
        wave.add(SKELETON, 3);
        assertEquals(5, wave.countOf(ZOMBIE));
        assertEquals(3, wave.countOf(SKELETON));
        assertEquals(8, wave.totalMobCount());
    }

    @Test
    void canRemoveCount() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 5);
        int removed = wave.remove(ZOMBIE, 2);
        assertEquals(2, removed);
        assertEquals(3, wave.countOf(ZOMBIE));
    }

    @Test
    void removingMoreThanAvailableRemovesAll() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 3);
        int removed = wave.remove(ZOMBIE, 99);
        assertEquals(3, removed);
        assertEquals(0, wave.countOf(ZOMBIE));
    }

    @Test
    void removingDownToZeroDropsSlot() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 1);
        wave.remove(ZOMBIE, 1);
        assertTrue(wave.getSlots().isEmpty());
    }

    @Test
    void cannotFinaliseEmptyWave() {
        Wave wave = new Wave();
        assertThrows(IllegalStateException.class, wave::finalise);
    }

    @Test
    void canFinaliseNonEmptyWave() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 1);
        wave.finalise();
        assertTrue(wave.isFinalised());
    }

    @Test
    void cannotModifyFinalisedWave() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 1);
        wave.finalise();
        assertThrows(IllegalStateException.class, () -> wave.add(SKELETON, 1));
        assertThrows(IllegalStateException.class, () -> wave.remove(ZOMBIE, 1));
    }

    @Test
    void getSlotsIsImmutable() {
        Wave wave = new Wave();
        wave.add(ZOMBIE, 1);
        List<WaveSlot> slots = wave.getSlots();
        assertThrows(UnsupportedOperationException.class,
                () -> slots.add(new WaveSlot(SKELETON, 1)));
    }
}
```

- [ ] **Step 2: Run, expect fail**

- [ ] **Step 3: Implementation**

```java
package de.klausiiiii.mobArmyBattle.wave;

import de.klausiiiii.mobArmyBattle.pool.MobEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Wave {

    private final Map<MobEntry, Integer> slots = new LinkedHashMap<>();
    private boolean finalised = false;

    public void add(MobEntry entry, int count) {
        if (finalised) {
            throw new IllegalStateException("Welle ist bereits finalisiert");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count muss > 0 sein");
        }
        slots.merge(entry, count, Integer::sum);
    }

    public int remove(MobEntry entry, int count) {
        if (finalised) {
            throw new IllegalStateException("Welle ist bereits finalisiert");
        }
        if (count <= 0) return 0;
        Integer cur = slots.get(entry);
        if (cur == null) return 0;
        int rm = Math.min(cur, count);
        int rest = cur - rm;
        if (rest == 0) {
            slots.remove(entry);
        } else {
            slots.put(entry, rest);
        }
        return rm;
    }

    public int countOf(MobEntry entry) {
        return slots.getOrDefault(entry, 0);
    }

    public int totalMobCount() {
        return slots.values().stream().mapToInt(Integer::intValue).sum();
    }

    public List<WaveSlot> getSlots() {
        List<WaveSlot> result = new ArrayList<>(slots.size());
        slots.forEach((k, v) -> result.add(new WaveSlot(k, v)));
        return Collections.unmodifiableList(result);
    }

    public void finalise() {
        if (slots.isEmpty()) {
            throw new IllegalStateException("Welle braucht mind. 1 Mob");
        }
        finalised = true;
    }

    public boolean isFinalised() {
        return finalised;
    }
}
```

- [ ] **Step 4: Tests pass** — 87 + 11 = 98.

- [ ] **Step 5: Commit**: `feat: add Wave with finalisable slot list and min-1-mob constraint`

---

## Task 3: Team — gets two Waves (TDD)

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/Team.java`
- Modify: `src/test/java/de/klausiiiii/mobArmyBattle/match/TeamTest.java`

- [ ] **Step 1: Add tests** to TeamTest.java (before final `}`):

```java
    @Test
    void newTeamHasTwoEmptyWaves() {
        Team team = new Team(UUID.randomUUID());

        assertNotNull(team.getWave1());
        assertNotNull(team.getWave2());
        assertEquals(0, team.getWave1().totalMobCount());
        assertEquals(0, team.getWave2().totalMobCount());
    }

    @Test
    void wavesFinalisedReturnsTrueOnlyWhenBothFinalised() {
        Team team = new Team(UUID.randomUUID());
        de.klausiiiii.mobArmyBattle.pool.MobEntry zombie =
                new de.klausiiiii.mobArmyBattle.pool.MobEntry("ZOMBIE", "none|none|none|none|none|none");

        assertFalse(team.wavesFinalised());

        team.getWave1().add(zombie, 1);
        team.getWave1().finalise();
        assertFalse(team.wavesFinalised());

        team.getWave2().add(zombie, 1);
        team.getWave2().finalise();
        assertTrue(team.wavesFinalised());
    }
```

- [ ] **Step 2: Run, expect fail**

- [ ] **Step 3: Modify Team.java**

Add import:
```java
import de.klausiiiii.mobArmyBattle.wave.Wave;
```

Add fields after `private final int maxSize;`:
```java
    private final Wave wave1;
    private final Wave wave2;
```

In each constructor (both 2-arg and the empty-sentinel constructor) add at the end:
```java
        this.wave1 = new Wave();
        this.wave2 = new Wave();
```

Add methods (anywhere appropriate):
```java
    public Wave getWave1() {
        return wave1;
    }

    public Wave getWave2() {
        return wave2;
    }

    public boolean wavesFinalised() {
        return wave1.isFinalised() && wave2.isFinalised();
    }
```

- [ ] **Step 4: Tests pass** — 100.

- [ ] **Step 5: Commit**: `feat: add wave1/wave2 to Team`

---

## Task 4: WaveBuildGui (Bukkit-Adapter)

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/wave/WaveBuildGui.java`

Bukkit-bound; manuell getestet. Layout:
- Slot 0-17 (Reihen 1-2): Pool-Mobs als Spawn-Eier mit Lore "x N verfügbar"
- Slot 18-26 (Reihe 3): Tabs + Trennlinien
  - 18: Welle 1 (klick = aktivieren)
  - 26: Welle 2 (klick = aktivieren)
  - 19-25: Glas-Trennlinien (gray stained glass pane)
- Slot 27-44 (Reihen 4-5): Aktuelle Welle als Spawn-Eier mit Lore "x N gewählt"
- Slot 45-53 (Reihe 6): Buttons
  - 45: Reset Welle (orange wool)
  - 49: Bestätigen Welle (lime wool)
  - 53: Abbrechen GUI (red wool, schließt ohne zu finalisieren)

- [ ] **Step 1: Implementation**

```java
package de.klausiiiii.mobArmyBattle.wave;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.pool.MobEntry;
import de.klausiiiii.mobArmyBattle.pool.MobPool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class WaveBuildGui implements Listener {

    private static final String NAMESPACE_KEY = "mab_wave_gui";
    private static final int INVENTORY_SIZE = 54;
    private static final int TAB_WAVE1_SLOT = 18;
    private static final int TAB_WAVE2_SLOT = 26;
    private static final int RESET_BUTTON_SLOT = 45;
    private static final int CONFIRM_BUTTON_SLOT = 49;
    private static final int CANCEL_BUTTON_SLOT = 53;

    private static class GuiSession {
        final UUID playerId;
        final Match match;
        final Team team;
        int activeWave = 1; // 1 or 2

        GuiSession(UUID playerId, Match match, Team team) {
            this.playerId = playerId;
            this.match = match;
            this.team = team;
        }
    }

    private final MatchManager matchManager;
    private final Map<UUID, GuiSession> sessions = new HashMap<>();

    public WaveBuildGui(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    public void open(Player player) {
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) return;
        Team team = match.findTeamOf(player.getUniqueId());
        if (team == null) return;
        if (!team.getCaptainId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Nur der Captain darf Wellen bauen.", NamedTextColor.RED));
            return;
        }
        GuiSession session = new GuiSession(player.getUniqueId(), match, team);
        sessions.put(player.getUniqueId(), session);
        Inventory inv = Bukkit.createInventory(null, INVENTORY_SIZE,
                Component.text("Wellen bauen — Welle " + session.activeWave, NamedTextColor.GOLD));
        renderInto(inv, session);
        player.openInventory(inv);
    }

    private void renderInto(Inventory inv, GuiSession session) {
        inv.clear();
        // Slots 0-17: Pool overview
        renderPool(inv, session);
        // Slot 18: Wave 1 tab
        inv.setItem(TAB_WAVE1_SLOT, tabIcon(1, session.activeWave == 1, session.team.getWave1().isFinalised()));
        // Slot 26: Wave 2 tab
        inv.setItem(TAB_WAVE2_SLOT, tabIcon(2, session.activeWave == 2, session.team.getWave2().isFinalised()));
        // Slots 19-25: separator
        for (int i = 19; i < 26; i++) {
            inv.setItem(i, separator());
        }
        // Slots 27-44: current wave
        renderCurrentWave(inv, session);
        // Buttons
        inv.setItem(RESET_BUTTON_SLOT, button(Material.ORANGE_WOOL, "Welle zurücksetzen", NamedTextColor.GOLD));
        inv.setItem(CONFIRM_BUTTON_SLOT, button(Material.LIME_WOOL, "Welle bestätigen", NamedTextColor.GREEN));
        inv.setItem(CANCEL_BUTTON_SLOT, button(Material.RED_WOOL, "Schließen (ohne speichern)", NamedTextColor.RED));
    }

    private void renderPool(Inventory inv, GuiSession session) {
        MobPool pool = session.team.getPool();
        Wave currentWave = currentWave(session);
        // Compute remaining pool = pool minus what's already in wave
        Map<MobEntry, Integer> remaining = new HashMap<>(pool.getEntries());
        for (WaveSlot slot : currentWave.getSlots()) {
            remaining.merge(slot.getEntry(), -slot.getCount(), Integer::sum);
        }
        int slotIdx = 0;
        for (Map.Entry<MobEntry, Integer> e : remaining.entrySet()) {
            if (slotIdx >= 18) break;
            int count = e.getValue();
            if (count <= 0) continue;
            inv.setItem(slotIdx++, mobIcon(e.getKey(), count, "verfügbar"));
        }
    }

    private void renderCurrentWave(Inventory inv, GuiSession session) {
        Wave wave = currentWave(session);
        int slotIdx = 27;
        for (WaveSlot slot : wave.getSlots()) {
            if (slotIdx >= 45) break;
            inv.setItem(slotIdx++, mobIcon(slot.getEntry(), slot.getCount(), "in Welle"));
        }
    }

    private Wave currentWave(GuiSession session) {
        return session.activeWave == 1 ? session.team.getWave1() : session.team.getWave2();
    }

    private ItemStack tabIcon(int waveNum, boolean active, boolean finalised) {
        Material mat = finalised ? Material.GREEN_CONCRETE : (active ? Material.YELLOW_CONCRETE : Material.GRAY_CONCRETE);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Welle " + waveNum + (finalised ? " (bestätigt)" : ""),
                active ? NamedTextColor.YELLOW : NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack separator() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material mat, String label, NamedTextColor color) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(label, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack mobIcon(MobEntry entry, int count, String suffix) {
        Material mat = spawnEggFor(entry.getEntityTypeName());
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        String name = entry.getEntityTypeName().toLowerCase().replace('_', ' ');
        meta.displayName(Component.text(name, NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("x " + count + " " + suffix, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        if (!entry.getEquipmentSignature().equals("none|none|none|none|none|none")) {
            lore.add(Component.text("(equipped)", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Material spawnEggFor(String entityName) {
        try {
            EntityType type = EntityType.valueOf(entityName);
            Material egg = Material.matchMaterial(entityName.toLowerCase(Locale.ROOT) + "_spawn_egg");
            return egg != null ? egg : Material.PIG_SPAWN_EGG;
        } catch (IllegalArgumentException ignored) {
            return Material.PIG_SPAWN_EGG;
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        // Only handle clicks in our top inventory
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            // shift-click from player inv: cancel
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= INVENTORY_SIZE) return;

        // Tab clicks
        if (slot == TAB_WAVE1_SLOT) {
            session.activeWave = 1;
            refreshTitle(player, session);
            renderInto(event.getInventory(), session);
            return;
        }
        if (slot == TAB_WAVE2_SLOT) {
            session.activeWave = 2;
            refreshTitle(player, session);
            renderInto(event.getInventory(), session);
            return;
        }
        // Buttons
        if (slot == RESET_BUTTON_SLOT) {
            resetWave(session);
            renderInto(event.getInventory(), session);
            return;
        }
        if (slot == CONFIRM_BUTTON_SLOT) {
            tryFinalise(player, session, event);
            return;
        }
        if (slot == CANCEL_BUTTON_SLOT) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            return;
        }
        // Pool slot click (0-17)
        if (slot < 18) {
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType().isAir()) return;
            // Find the matching MobEntry from pool (by spawn-egg material? We need to map back)
            // Simpler: store pool entry list in render order
            MobEntry entry = poolEntryAtSlot(session, slot);
            if (entry == null) return;
            int amount = event.isShiftClick() ? 5 : 1;
            int available = remainingForEntry(session, entry);
            int toAdd = Math.min(amount, available);
            if (toAdd <= 0) return;
            currentWave(session).add(entry, toAdd);
            renderInto(event.getInventory(), session);
            return;
        }
        // Wave slot click (27-44) → remove
        if (slot >= 27 && slot < 45) {
            ItemStack item = event.getCurrentItem();
            if (item == null || item.getType().isAir()) return;
            MobEntry entry = waveEntryAtSlot(session, slot);
            if (entry == null) return;
            int amount = event.isShiftClick() ? 5 : 1;
            currentWave(session).remove(entry, amount);
            renderInto(event.getInventory(), session);
        }
    }

    private void refreshTitle(Player player, GuiSession session) {
        // Bukkit doesn't allow title refresh on existing inventory; reopen
        Inventory newInv = Bukkit.createInventory(null, INVENTORY_SIZE,
                Component.text("Wellen bauen — Welle " + session.activeWave, NamedTextColor.GOLD));
        renderInto(newInv, session);
        player.openInventory(newInv);
    }

    private MobEntry poolEntryAtSlot(GuiSession session, int slot) {
        MobPool pool = session.team.getPool();
        Wave currentWave = currentWave(session);
        Map<MobEntry, Integer> remaining = new HashMap<>(pool.getEntries());
        for (WaveSlot ws : currentWave.getSlots()) {
            remaining.merge(ws.getEntry(), -ws.getCount(), Integer::sum);
        }
        int idx = 0;
        for (Map.Entry<MobEntry, Integer> e : remaining.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (idx == slot) return e.getKey();
            idx++;
        }
        return null;
    }

    private MobEntry waveEntryAtSlot(GuiSession session, int slot) {
        Wave wave = currentWave(session);
        int idx = slot - 27;
        List<WaveSlot> slots = wave.getSlots();
        if (idx < 0 || idx >= slots.size()) return null;
        return slots.get(idx).getEntry();
    }

    private int remainingForEntry(GuiSession session, MobEntry entry) {
        int poolCount = session.team.getPool().countOf(entry);
        int waveCount = currentWave(session).countOf(entry);
        return poolCount - waveCount;
    }

    private void resetWave(GuiSession session) {
        Wave wave = currentWave(session);
        if (wave.isFinalised()) return;
        // Drain by re-creating: easiest is to remove all entries
        for (WaveSlot slot : new ArrayList<>(wave.getSlots())) {
            wave.remove(slot.getEntry(), slot.getCount());
        }
    }

    private void tryFinalise(Player player, GuiSession session, InventoryClickEvent event) {
        Wave wave = currentWave(session);
        if (wave.isFinalised()) {
            player.sendMessage(Component.text("Welle ist bereits bestätigt.", NamedTextColor.YELLOW));
            return;
        }
        try {
            wave.finalise();
        } catch (IllegalStateException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Welle " + session.activeWave + " bestätigt.",
                NamedTextColor.GREEN));
        if (session.team.wavesFinalised()) {
            player.sendMessage(Component.text("Beide Wellen bestätigt.", NamedTextColor.GREEN));
            sessions.remove(player.getUniqueId());
            player.closeInventory();
        } else {
            // Switch to other wave automatically
            session.activeWave = session.activeWave == 1 ? 2 : 1;
            refreshTitle(player, session);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        // session may have been removed by tryFinalise; that's fine
        sessions.remove(player.getUniqueId());
    }
}
```

- [ ] **Step 2: Build OK**

- [ ] **Step 3: Commit**: `feat: add WaveBuildGui inventory adapter for captain wave-building`

---

## Task 5: WaveBuildPhase — onEnter öffnet GUI

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/WaveBuildPhase.java`

- [ ] **Step 1: Replace WaveBuildPhase.java**

```java
package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class WaveBuildPhase implements MatchPhase {

    private final MobArmyBattle plugin;

    public WaveBuildPhase() {
        this(null);
    }

    public WaveBuildPhase(MobArmyBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public MatchPhaseType getType() {
        return MatchPhaseType.WAVE_BUILD;
    }

    @Override
    public void onEnter(Match match) {
        if (plugin == null) return;
        for (Team team : match.getTeams()) {
            if (team.isDisbanded() || team.getCaptainId() == null) continue;
            Player captain = Bukkit.getPlayer(team.getCaptainId());
            if (captain != null) {
                plugin.getWaveBuildGui().open(captain);
            }
        }
    }

    @Override
    public void onExit(Match match) {
    }

    @Override
    public void tick(Match match) {
        if (plugin == null) return;
        // If all active teams have finalised both waves, advance to BATTLE
        boolean allDone = true;
        for (Team team : match.getTeams()) {
            if (team.isDisbanded() || team.size() == 0) continue;
            if (!team.wavesFinalised()) {
                allDone = false;
                break;
            }
        }
        if (allDone) {
            match.transitionTo(new BattlePhase(plugin));
            for (Team team : match.getTeams()) {
                for (UUID memberId : team.getMemberIds()) {
                    Player p = Bukkit.getPlayer(memberId);
                    if (p != null) {
                        p.sendMessage(net.kyori.adventure.text.Component.text(
                                "Alle Wellen bestätigt — Battle-Phase startet (Stub).",
                                net.kyori.adventure.text.format.NamedTextColor.GOLD));
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build OK**

- [ ] **Step 3: Commit**: `feat: WaveBuildPhase opens GUI for captains and advances to BATTLE`

---

## Task 6: /mab endfarm + Phase-Tick

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java`
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java`

- [ ] **Step 1: Add `/mab endfarm` to MabCommand**

In `SUBCOMMANDS`:
```java
    private static final List<String> SUBCOMMANDS = List.of("create", "join", "leave", "start", "pool", "endfarm");
```

In switch:
```java
                case "endfarm" -> handleEndFarm(player);
```

Add method (near `handleStart`):
```java
    private void handleEndFarm(Player player) {
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) {
            player.sendMessage(Component.text("Du bist in keinem Match.", NamedTextColor.RED));
            return;
        }
        if (match.getCurrentPhase().getType() != MatchPhaseType.FARM) {
            player.sendMessage(Component.text("Match ist nicht in Farm-Phase.", NamedTextColor.RED));
            return;
        }
        Team team = match.findTeamOf(player.getUniqueId());
        if (!team.getCaptainId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Nur ein Captain darf die Farm-Phase beenden.", NamedTextColor.RED));
            return;
        }
        match.transitionTo(new WaveBuildPhase(plugin));
        for (Team t : match.getTeams()) {
            for (UUID memberId : t.getMemberIds()) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    member.sendMessage(Component.text(
                            "Farm-Phase beendet — Captains bauen jetzt Wellen.",
                            NamedTextColor.GOLD));
                }
            }
        }
    }
```

Add import:
```java
import de.klausiiiii.mobArmyBattle.match.phase.WaveBuildPhase;
```

In `sendUsage`, add line:
```java
        player.sendMessage(Component.text("/mab endfarm — Farm-Phase beenden (nur Captain)", NamedTextColor.GRAY));
```

- [ ] **Step 2: MainPlugin — schedule tick + register WaveBuildGui listener**

In `MobArmyBattle.java`, add field:
```java
    private WaveBuildGui waveBuildGui;
```

Add import:
```java
import de.klausiiiii.mobArmyBattle.wave.WaveBuildGui;
```

In `onEnable`, after MobKillListener registration:
```java
        waveBuildGui = new WaveBuildGui(matchManager);
        getServer().getPluginManager().registerEvents(waveBuildGui, this);

        getServer().getScheduler().runTaskTimer(this, () -> matchManager.tickAll(), 20L, 20L);
```

Add getter:
```java
    public WaveBuildGui getWaveBuildGui() {
        return waveBuildGui;
    }
```

- [ ] **Step 3: Build + tests** — 100 tests pass.

- [ ] **Step 4: Commit**: `feat: /mab endfarm + plugin tick + WaveBuildGui registration`

---

## Task 7: Manueller Test

- [ ] **Step 1**: `./gradlew runServer`

- [ ] **Step 2**: Im Spiel
1. Player A: `/mab create 1`
2. Player B: `/mab join A` → Solo-FFA, je 1 Spieler pro Team
3. Player A: `/mab start` → Farm-Phase, Welten erstellt
4. **In Farm-Welt:** Mobs killen mit Werkzeug (Creative + Spawn Egg + Schwert)
5. `/mab pool` → zeigt gefarmte Mobs
6. **Player A:** `/mab endfarm` → "Farm-Phase beendet — Captains bauen jetzt Wellen." Bei beiden Captains öffnet sich GUI.
7. **Im GUI:** 
   - Pool-Mobs links (Reihen 1-2)
   - Klicken auf Pool-Mob → Mob in aktuelle Welle
   - Tab-Buttons (18, 26) wechseln zwischen Welle 1/2
   - Lime-Wool (49) bestätigt Welle (geht zu nächster Welle)
   - Orange-Wool (45) resettet Welle
8. Beide Captains bestätigen beide Wellen → "Alle Wellen bestätigt — Battle-Phase startet (Stub)." 
9. `/mab pool` zeigt **Pool unverändert** (Wellen-Bau verbraucht den Pool nicht direkt — der Pool-Wert wird beim Battle-Spawn konsumiert, nicht beim Bauen)

**Hinweis:** Im aktuellen Modell verbraucht der Wave-Build den Pool NICHT — d.h. wenn du 10 Zombies hast und in beiden Wellen 8 Zombies einsetzt (16 total), ist das OK weil GUI das prüft via "remaining = pool - currentWave". Beide Wellen ZUSAMMEN dürfen die Pool-Anzahl nicht überschreiten — aber da jede Welle nur die "aktive" `remaining` zeigt, kannst du in Welle 1 mehr einsetzen als verfügbar wenn Welle 2 schon was hat. **Bekanntes Loose End** — strict inter-wave-sharing-Logik kommt später.

---

## Acceptance Criteria

- [x] 100 Domain-Tests grün
- [x] `./gradlew build` BUILD SUCCESSFUL
- [x] `/mab endfarm` wechselt Phase Farm → WaveBuild
- [x] GUI öffnet sich bei Captains
- [x] Pool-Mobs werden in Wellen verschoben (Welle 1/2 Tab)
- [x] Bestätigen finalisiert Welle (min 1 Mob nötig)
- [x] Wenn alle Captains bestätigen → Phase wechselt zu BATTLE (Stub)

## Bekannte Loose Ends

- **Pool-Verbrauch:** GUI verhindert, dass mehr Mobs als verfügbar in eine *einzelne* Welle gepackt werden, aber wenn beide Wellen den selben Mob nutzen, ist die Aufteilung nicht hart begrenzt. Konsequente Pool↔Wave-Synchronisation kommt mit Battle-Spawning in Plan 5.
- **Vorschlag-Mechanik:** Teammates haben aktuell keine GUI. Read-Only-View kommt später.
- **Auto-Farm-Timer:** Aktuell muss Captain `/mab endfarm` manuell ausführen. 60-Min-Timer kommt mit Config (Plan 9).
- **Wave-Build-Timer:** Auch hier kein Auto-Timeout. Captain kann beliebig lange im GUI sein.
- **Multi-Team-Spectator-View:** Wenn man als Captain im GUI ist und ein anderer Team-Captain auch — sehen sie sich nicht, das ist OK.
