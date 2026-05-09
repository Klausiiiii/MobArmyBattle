# Plan 9: Config + Permissions — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Alles konfigurierbar über `config.yml`. Bestehende hardcoded Konstanten (Phase-Dauern, Death-Penalty 10%) werden durch Config ersetzt. Neue Behaviors: Starter-Kit, Death-Penalty-Modi, WorldBorder, Mob-Spawn-Multiplier, Auto-Farm-Transition, Reconnect-Grace, granulare Permissions, `/mab reload`.

**Architecture:** Ein immutable `MabConfig` record (Bukkit-frei) mit nested Sub-records (PhaseDurations, StarterKitConfig, DeathPenaltyConfig, WorldBorderConfig, ReconnectConfig). `ConfigLoader` (Bukkit-aware) liest FileConfiguration → MabConfig, mit Per-Sektion-Fallback auf Defaults bei Validation-Fehlern. Consumers halten Referenz auf `MobArmyBattle` und rufen `plugin.getMabConfig().xxx()` zur Tick-Zeit (live-reload-fähig). `/mab reload` ersetzt das aktive `MabConfig`-Feld.

**Tech Stack:** Java 25, Paper API 26.1.2, Bukkit FileConfiguration, JUnit 5.

---

## Architektur-Überblick

| Klasse | Bukkit-frei? | Zweck |
|---|---|---|
| `config/PhaseDurations` | ✅ | record (farmMin, waveBuildMin, prepSec, wavePauseSec, hardTimeoutMin) |
| `config/StarterKitConfig` | ✅ | record + Type enum (NONE/LEATHER_FULL/IRON_FULL/CUSTOM) + customSlots Map |
| `config/DeathPenaltyConfig` | ✅ | record + Mode enum (NONE/SOFT/DROP_ITEMS/HARD) |
| `config/WorldBorderConfig` | ✅ | record (enabled, radius) |
| `config/ReconnectConfig` | ✅ | record (graceSec) |
| `config/MabConfig` | ✅ | Wurzel-record, hält alle Sub-records, hat `defaults()` |
| `config/ConfigLoader` | ❌ | reads `plugin.getConfig()` → `MabConfig` |
| `config/ReconnectGraceManager` | ❌ | Map<UUID, BukkitTask> — schedules deferred leaveMatch |
| `world/StarterKitApplier` | ❌ | static `applyKit(Player, StarterKitConfig)` — von FarmPhase aufgerufen |

## Final config.yml

```yaml
phases:
  farm-duration-min: 60
  wave-build-duration-min: 5
  prep-duration-sec: 30
  wave-pause-sec: 10
  wave-hard-timeout-min: 10
  auto-farm-transition: true

starter-kit:
  type: iron_full
  custom:
    main: IRON_SWORD
    off: SHIELD
    helm: IRON_HELMET
    chest: IRON_CHESTPLATE
    legs: IRON_LEGGINGS
    boots: IRON_BOOTS

death-penalty:
  mode: soft

farm-world:
  border:
    enabled: true
    radius: 200
  mob-spawn-multiplier: 2.0

arena:
  border:
    enabled: true
    radius: 50

reconnect:
  grace-sec: 300
```

## Death-Penalty Mode-Mapping

| Mode | poolPercent | dropItems |
|---|---|---|
| NONE | 0 | false |
| SOFT | 5 | false |
| DROP_ITEMS | 0 | true |
| HARD | 25 | true |

## Granulare Permissions

| Permission | Default | Used by |
|---|---|---|
| `mobarmybattle.create.match` | true | `/mab create` |
| `mobarmybattle.create.tournament` | op | `/mab tournament create` |
| `mobarmybattle.join` | true | `/mab join`, `/mab leave` |
| `mobarmybattle.spectate` | true | `/mab spectate` |
| `mobarmybattle.stats` | true | `/mab stats`, `/mab leaderboard` |
| `mobarmybattle.kick` | op | `/mab kick` (Plan 10) |
| `mobarmybattle.reload` | op | `/mab reload` |
| `mobarmybattle.admin` | op | (Aggregat) |
| `mobarmybattle.*` | op | (Parent für alle) |

Backward-compat: Bestehender `mobarmybattle.create` Permission wird zu Alias / Parent für `mobarmybattle.create.match`. Kein Breaking-Change für bestehende Server-Configs.

## Scope-Cuts (v2)

- Punkte-System für Wellen — separates Plan
- Per-Mode penalty-percent overrides in config (modes haben fixe percents v1)
- Reconnect-Snapshot/Replay (nur "absent"-Modus v1)
- Starter-Kit Enchantments / ItemMeta — v1 nur Material-Names
- `/mab reload`-broadcast (silent v1)

---

## Tasks

### Task 1: PhaseDurations + StarterKitConfig records

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/config/PhaseDurations.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/config/StarterKitConfig.java`
- Create: `src/test/java/de/klausiiiii/mobArmyBattle/config/PhaseDurationsTest.java`
- Create: `src/test/java/de/klausiiiii/mobArmyBattle/config/StarterKitConfigTest.java`

- [ ] **Step 1: Write failing tests**

```java
// PhaseDurationsTest.java
package de.klausiiiii.mobArmyBattle.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhaseDurationsTest {
    @Test
    void createsValidInstance() {
        PhaseDurations p = new PhaseDurations(60, 5, 30, 10, 10, true);
        assertEquals(60, p.farmDurationMin());
        assertEquals(5, p.waveBuildDurationMin());
        assertEquals(30, p.prepDurationSec());
        assertEquals(10, p.wavePauseSec());
        assertEquals(10, p.waveHardTimeoutMin());
        assertTrue(p.autoFarmTransition());
    }

    @Test
    void rejectsNegativeFarmDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhaseDurations(0, 5, 30, 10, 10, true));
    }

    @Test
    void rejectsNegativeWaveBuildDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhaseDurations(60, 0, 30, 10, 10, true));
    }

    @Test
    void allowsZeroPrepWavePauseHardTimeout() {
        // these may be 0 (instant phase, no pause)
        PhaseDurations p = new PhaseDurations(60, 5, 0, 0, 1, false);
        assertEquals(0, p.prepDurationSec());
        assertEquals(0, p.wavePauseSec());
    }

    @Test
    void rejectsNegativeWavePause() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhaseDurations(60, 5, 30, -1, 10, true));
    }
}
```

```java
// StarterKitConfigTest.java
package de.klausiiiii.mobArmyBattle.config;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.*;

class StarterKitConfigTest {
    @Test
    void createsNoneKit() {
        StarterKitConfig k = new StarterKitConfig(StarterKitConfig.Type.NONE, Map.of());
        assertEquals(StarterKitConfig.Type.NONE, k.type());
        assertTrue(k.customSlots().isEmpty());
    }

    @Test
    void createsIronFullKit() {
        StarterKitConfig k = new StarterKitConfig(StarterKitConfig.Type.IRON_FULL, Map.of());
        assertEquals(StarterKitConfig.Type.IRON_FULL, k.type());
    }

    @Test
    void customRequiresAtLeastOneSlot() {
        assertThrows(IllegalArgumentException.class,
                () -> new StarterKitConfig(StarterKitConfig.Type.CUSTOM, Map.of()));
    }

    @Test
    void customSlotsHeldImmutably() {
        Map<StarterKitConfig.Slot, String> slots = new EnumMap<>(StarterKitConfig.Slot.class);
        slots.put(StarterKitConfig.Slot.MAIN, "DIAMOND_SWORD");
        StarterKitConfig k = new StarterKitConfig(StarterKitConfig.Type.CUSTOM, slots);
        assertEquals("DIAMOND_SWORD", k.customSlots().get(StarterKitConfig.Slot.MAIN));
    }

    @Test
    void rejectsNullType() {
        assertThrows(IllegalArgumentException.class,
                () -> new StarterKitConfig(null, Map.of()));
    }
}
```

- [ ] **Step 2: Run — fail (compile)**

```
./gradlew.bat test --tests de.klausiiiii.mobArmyBattle.config.PhaseDurationsTest
```

- [ ] **Step 3: Implement records**

```java
// PhaseDurations.java
package de.klausiiiii.mobArmyBattle.config;

public record PhaseDurations(
        int farmDurationMin,
        int waveBuildDurationMin,
        int prepDurationSec,
        int wavePauseSec,
        int waveHardTimeoutMin,
        boolean autoFarmTransition
) {
    public PhaseDurations {
        if (farmDurationMin < 1) throw new IllegalArgumentException("farm-duration-min muss >= 1 sein");
        if (waveBuildDurationMin < 1) throw new IllegalArgumentException("wave-build-duration-min muss >= 1 sein");
        if (prepDurationSec < 0) throw new IllegalArgumentException("prep-duration-sec muss >= 0 sein");
        if (wavePauseSec < 0) throw new IllegalArgumentException("wave-pause-sec muss >= 0 sein");
        if (waveHardTimeoutMin < 1) throw new IllegalArgumentException("wave-hard-timeout-min muss >= 1 sein");
    }
}
```

```java
// StarterKitConfig.java
package de.klausiiiii.mobArmyBattle.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record StarterKitConfig(Type type, Map<Slot, String> customSlots) {
    public enum Type { NONE, LEATHER_FULL, IRON_FULL, CUSTOM }
    public enum Slot { MAIN, OFF, HELM, CHEST, LEGS, BOOTS }

    public StarterKitConfig {
        if (type == null) throw new IllegalArgumentException("type darf nicht null sein");
        if (customSlots == null) throw new IllegalArgumentException("customSlots darf nicht null sein");
        if (type == Type.CUSTOM && customSlots.isEmpty()) {
            throw new IllegalArgumentException("custom kit braucht mindestens einen Slot");
        }
        Map<Slot, String> defensive = customSlots.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(customSlots));
        customSlots = defensive;
    }
}
```

- [ ] **Step 4: Run — pass**

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/config/PhaseDurations.java src/main/java/de/klausiiiii/mobArmyBattle/config/StarterKitConfig.java src/test/java/de/klausiiiii/mobArmyBattle/config/
git commit -m "feat(config): PhaseDurations + StarterKitConfig domain records"
```

---

### Task 2: DeathPenaltyConfig + WorldBorderConfig + ReconnectConfig

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/config/DeathPenaltyConfig.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/config/WorldBorderConfig.java`
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/config/ReconnectConfig.java`
- Create: `src/test/java/de/klausiiiii/mobArmyBattle/config/DeathPenaltyConfigTest.java`
- Create: `src/test/java/de/klausiiiii/mobArmyBattle/config/WorldBorderConfigTest.java`
- Create: `src/test/java/de/klausiiiii/mobArmyBattle/config/ReconnectConfigTest.java`

- [ ] **Step 1: Write failing tests**

```java
// DeathPenaltyConfigTest.java
package de.klausiiiii.mobArmyBattle.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeathPenaltyConfigTest {
    @Test
    void noneModeHasZeroPercentNoDrop() {
        assertEquals(0, DeathPenaltyConfig.Mode.NONE.poolPercent());
        assertFalse(DeathPenaltyConfig.Mode.NONE.dropItems());
    }

    @Test
    void softModeHas5PercentNoDrop() {
        assertEquals(5, DeathPenaltyConfig.Mode.SOFT.poolPercent());
        assertFalse(DeathPenaltyConfig.Mode.SOFT.dropItems());
    }

    @Test
    void dropItemsModeHas0PercentWithDrop() {
        assertEquals(0, DeathPenaltyConfig.Mode.DROP_ITEMS.poolPercent());
        assertTrue(DeathPenaltyConfig.Mode.DROP_ITEMS.dropItems());
    }

    @Test
    void hardModeHas25PercentWithDrop() {
        assertEquals(25, DeathPenaltyConfig.Mode.HARD.poolPercent());
        assertTrue(DeathPenaltyConfig.Mode.HARD.dropItems());
    }

    @Test
    void rejectsNullMode() {
        assertThrows(IllegalArgumentException.class, () -> new DeathPenaltyConfig(null));
    }

    @Test
    void exposesModeViaRecord() {
        DeathPenaltyConfig c = new DeathPenaltyConfig(DeathPenaltyConfig.Mode.HARD);
        assertEquals(DeathPenaltyConfig.Mode.HARD, c.mode());
    }
}
```

```java
// WorldBorderConfigTest.java
package de.klausiiiii.mobArmyBattle.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldBorderConfigTest {
    @Test
    void createsEnabledBorder() {
        WorldBorderConfig c = new WorldBorderConfig(true, 200);
        assertTrue(c.enabled());
        assertEquals(200, c.radius());
    }

    @Test
    void createsDisabledBorder() {
        WorldBorderConfig c = new WorldBorderConfig(false, 0);
        assertFalse(c.enabled());
    }

    @Test
    void rejectsNegativeRadius() {
        assertThrows(IllegalArgumentException.class, () -> new WorldBorderConfig(true, -5));
    }

    @Test
    void allowsZeroRadiusWhenDisabled() {
        WorldBorderConfig c = new WorldBorderConfig(false, 0);
        assertEquals(0, c.radius());
    }
}
```

```java
// ReconnectConfigTest.java
package de.klausiiiii.mobArmyBattle.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReconnectConfigTest {
    @Test
    void createsValidGrace() {
        ReconnectConfig c = new ReconnectConfig(300);
        assertEquals(300, c.graceSec());
    }

    @Test
    void allowsZeroGrace() {
        ReconnectConfig c = new ReconnectConfig(0);
        assertEquals(0, c.graceSec());
    }

    @Test
    void rejectsNegativeGrace() {
        assertThrows(IllegalArgumentException.class, () -> new ReconnectConfig(-1));
    }
}
```

- [ ] **Step 2: Run — fail**

- [ ] **Step 3: Implement records**

```java
// DeathPenaltyConfig.java
package de.klausiiiii.mobArmyBattle.config;

public record DeathPenaltyConfig(Mode mode) {
    public enum Mode {
        NONE(0, false),
        SOFT(5, false),
        DROP_ITEMS(0, true),
        HARD(25, true);

        private final int poolPercent;
        private final boolean dropItems;

        Mode(int poolPercent, boolean dropItems) {
            this.poolPercent = poolPercent;
            this.dropItems = dropItems;
        }

        public int poolPercent() { return poolPercent; }
        public boolean dropItems() { return dropItems; }
    }

    public DeathPenaltyConfig {
        if (mode == null) throw new IllegalArgumentException("mode darf nicht null sein");
    }
}
```

```java
// WorldBorderConfig.java
package de.klausiiiii.mobArmyBattle.config;

public record WorldBorderConfig(boolean enabled, int radius) {
    public WorldBorderConfig {
        if (radius < 0) throw new IllegalArgumentException("radius muss >= 0 sein");
    }
}
```

```java
// ReconnectConfig.java
package de.klausiiiii.mobArmyBattle.config;

public record ReconnectConfig(int graceSec) {
    public ReconnectConfig {
        if (graceSec < 0) throw new IllegalArgumentException("grace-sec muss >= 0 sein");
    }
}
```

- [ ] **Step 4: Run — pass**

- [ ] **Step 5: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/config/DeathPenaltyConfig.java src/main/java/de/klausiiiii/mobArmyBattle/config/WorldBorderConfig.java src/main/java/de/klausiiiii/mobArmyBattle/config/ReconnectConfig.java src/test/java/de/klausiiiii/mobArmyBattle/config/DeathPenaltyConfigTest.java src/test/java/de/klausiiiii/mobArmyBattle/config/WorldBorderConfigTest.java src/test/java/de/klausiiiii/mobArmyBattle/config/ReconnectConfigTest.java
git commit -m "feat(config): DeathPenalty + WorldBorder + Reconnect config records"
```

---

### Task 3: MabConfig root record + config.yml resource

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/config/MabConfig.java`
- Create: `src/test/java/de/klausiiiii/mobArmyBattle/config/MabConfigTest.java`
- Create: `src/main/resources/config.yml`

- [ ] **Step 1: Write failing tests**

```java
// MabConfigTest.java
package de.klausiiiii.mobArmyBattle.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MabConfigTest {
    @Test
    void defaultsAreValid() {
        MabConfig c = MabConfig.defaults();
        assertNotNull(c.phaseDurations());
        assertNotNull(c.starterKit());
        assertNotNull(c.deathPenalty());
        assertNotNull(c.farmBorder());
        assertNotNull(c.arenaBorder());
        assertNotNull(c.reconnect());
        assertEquals(60, c.phaseDurations().farmDurationMin());
        assertEquals(StarterKitConfig.Type.IRON_FULL, c.starterKit().type());
        assertEquals(DeathPenaltyConfig.Mode.SOFT, c.deathPenalty().mode());
        assertEquals(2.0, c.farmMobSpawnMultiplier(), 0.001);
        assertEquals(300, c.reconnect().graceSec());
    }

    @Test
    void rejectsNegativeMobMultiplier() {
        assertThrows(IllegalArgumentException.class,
                () -> new MabConfig(
                        new PhaseDurations(60, 5, 30, 10, 10, true),
                        new StarterKitConfig(StarterKitConfig.Type.NONE, java.util.Map.of()),
                        new DeathPenaltyConfig(DeathPenaltyConfig.Mode.NONE),
                        new WorldBorderConfig(true, 200),
                        new WorldBorderConfig(true, 50),
                        -0.5,
                        new ReconnectConfig(300)));
    }
}
```

- [ ] **Step 2: Run — fail**

- [ ] **Step 3: Implement MabConfig**

```java
// MabConfig.java
package de.klausiiiii.mobArmyBattle.config;

import java.util.Map;

public record MabConfig(
        PhaseDurations phaseDurations,
        StarterKitConfig starterKit,
        DeathPenaltyConfig deathPenalty,
        WorldBorderConfig farmBorder,
        WorldBorderConfig arenaBorder,
        double farmMobSpawnMultiplier,
        ReconnectConfig reconnect
) {
    public MabConfig {
        if (phaseDurations == null) throw new IllegalArgumentException("phaseDurations darf nicht null sein");
        if (starterKit == null) throw new IllegalArgumentException("starterKit darf nicht null sein");
        if (deathPenalty == null) throw new IllegalArgumentException("deathPenalty darf nicht null sein");
        if (farmBorder == null) throw new IllegalArgumentException("farmBorder darf nicht null sein");
        if (arenaBorder == null) throw new IllegalArgumentException("arenaBorder darf nicht null sein");
        if (reconnect == null) throw new IllegalArgumentException("reconnect darf nicht null sein");
        if (farmMobSpawnMultiplier <= 0) {
            throw new IllegalArgumentException("farm-mob-spawn-multiplier muss > 0 sein");
        }
    }

    public static MabConfig defaults() {
        return new MabConfig(
                new PhaseDurations(60, 5, 30, 10, 10, true),
                new StarterKitConfig(StarterKitConfig.Type.IRON_FULL, Map.of()),
                new DeathPenaltyConfig(DeathPenaltyConfig.Mode.SOFT),
                new WorldBorderConfig(true, 200),
                new WorldBorderConfig(true, 50),
                2.0,
                new ReconnectConfig(300));
    }
}
```

- [ ] **Step 4: Create config.yml resource**

Create `src/main/resources/config.yml` with the contents shown in the architecture overview above.

- [ ] **Step 5: Run — pass**

- [ ] **Step 6: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/config/MabConfig.java src/test/java/de/klausiiiii/mobArmyBattle/config/MabConfigTest.java src/main/resources/config.yml
git commit -m "feat(config): MabConfig root record + config.yml default resource"
```

---

### Task 4: ConfigLoader (Bukkit-aware)

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/config/ConfigLoader.java`

(No unit tests — Bukkit-aware. Manual smoke-test deferred to Task 15.)

- [ ] **Step 1: Implement ConfigLoader**

```java
package de.klausiiiii.mobArmyBattle.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ConfigLoader {

    private ConfigLoader() {}

    public static MabConfig load(Plugin plugin) {
        FileConfiguration cfg = plugin.getConfig();
        Logger log = plugin.getLogger();

        PhaseDurations phases = loadPhases(cfg, log);
        StarterKitConfig kit = loadStarterKit(cfg, log);
        DeathPenaltyConfig dp = loadDeathPenalty(cfg, log);
        WorldBorderConfig farmBorder = loadBorder(cfg, "farm-world.border", log, MabConfig.defaults().farmBorder());
        WorldBorderConfig arenaBorder = loadBorder(cfg, "arena.border", log, MabConfig.defaults().arenaBorder());
        double mobMult = cfg.getDouble("farm-world.mob-spawn-multiplier", 2.0);
        ReconnectConfig reconnect = loadReconnect(cfg, log);

        try {
            return new MabConfig(phases, kit, dp, farmBorder, arenaBorder, mobMult, reconnect);
        } catch (IllegalArgumentException e) {
            log.log(Level.WARNING, "Config-Validation fehlgeschlagen — verwende defaults: " + e.getMessage());
            return MabConfig.defaults();
        }
    }

    private static PhaseDurations loadPhases(FileConfiguration cfg, Logger log) {
        PhaseDurations defaults = MabConfig.defaults().phaseDurations();
        try {
            return new PhaseDurations(
                    cfg.getInt("phases.farm-duration-min", defaults.farmDurationMin()),
                    cfg.getInt("phases.wave-build-duration-min", defaults.waveBuildDurationMin()),
                    cfg.getInt("phases.prep-duration-sec", defaults.prepDurationSec()),
                    cfg.getInt("phases.wave-pause-sec", defaults.wavePauseSec()),
                    cfg.getInt("phases.wave-hard-timeout-min", defaults.waveHardTimeoutMin()),
                    cfg.getBoolean("phases.auto-farm-transition", defaults.autoFarmTransition()));
        } catch (IllegalArgumentException e) {
            log.warning("phases section invalid, using defaults: " + e.getMessage());
            return defaults;
        }
    }

    private static StarterKitConfig loadStarterKit(FileConfiguration cfg, Logger log) {
        StarterKitConfig defaults = MabConfig.defaults().starterKit();
        String typeStr = cfg.getString("starter-kit.type", "iron_full").toUpperCase(Locale.ROOT);
        StarterKitConfig.Type type;
        try {
            type = StarterKitConfig.Type.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            log.warning("starter-kit.type invalid: '" + typeStr + "', using IRON_FULL");
            type = StarterKitConfig.Type.IRON_FULL;
        }

        Map<StarterKitConfig.Slot, String> custom = new EnumMap<>(StarterKitConfig.Slot.class);
        if (type == StarterKitConfig.Type.CUSTOM) {
            ConfigurationSection sec = cfg.getConfigurationSection("starter-kit.custom");
            if (sec != null) {
                for (StarterKitConfig.Slot slot : StarterKitConfig.Slot.values()) {
                    String key = slot.name().toLowerCase(Locale.ROOT);
                    String value = sec.getString(key);
                    if (value != null && !value.isBlank()) {
                        custom.put(slot, value.toUpperCase(Locale.ROOT));
                    }
                }
            }
            if (custom.isEmpty()) {
                log.warning("starter-kit.type=custom but no slots defined; falling back to NONE");
                return new StarterKitConfig(StarterKitConfig.Type.NONE, Map.of());
            }
        }
        try {
            return new StarterKitConfig(type, custom);
        } catch (IllegalArgumentException e) {
            log.warning("starter-kit invalid: " + e.getMessage());
            return defaults;
        }
    }

    private static DeathPenaltyConfig loadDeathPenalty(FileConfiguration cfg, Logger log) {
        String modeStr = cfg.getString("death-penalty.mode", "soft").toUpperCase(Locale.ROOT);
        try {
            return new DeathPenaltyConfig(DeathPenaltyConfig.Mode.valueOf(modeStr));
        } catch (IllegalArgumentException e) {
            log.warning("death-penalty.mode invalid: '" + modeStr + "', using SOFT");
            return new DeathPenaltyConfig(DeathPenaltyConfig.Mode.SOFT);
        }
    }

    private static WorldBorderConfig loadBorder(FileConfiguration cfg, String path, Logger log, WorldBorderConfig defaults) {
        try {
            return new WorldBorderConfig(
                    cfg.getBoolean(path + ".enabled", defaults.enabled()),
                    cfg.getInt(path + ".radius", defaults.radius()));
        } catch (IllegalArgumentException e) {
            log.warning(path + " invalid: " + e.getMessage());
            return defaults;
        }
    }

    private static ReconnectConfig loadReconnect(FileConfiguration cfg, Logger log) {
        try {
            return new ReconnectConfig(cfg.getInt("reconnect.grace-sec", 300));
        } catch (IllegalArgumentException e) {
            log.warning("reconnect invalid: " + e.getMessage());
            return new ReconnectConfig(300);
        }
    }
}
```

- [ ] **Step 2: Verify compile**

```
./gradlew.bat compileJava
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/config/ConfigLoader.java
git commit -m "feat(config): ConfigLoader reads FileConfiguration into MabConfig"
```

---

### Task 5: MobArmyBattle wiring (saveDefaultConfig + getMabConfig)

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java`

- [ ] **Step 1: Add field + getter + load**

In `MobArmyBattle.java`:

Add field:
```java
    private MabConfig mabConfig;
```

Imports:
```java
import de.klausiiiii.mobArmyBattle.config.ConfigLoader;
import de.klausiiiii.mobArmyBattle.config.MabConfig;
```

In `onEnable()`, very early (after class field declarations, before WorldManager):
```java
        saveDefaultConfig();
        mabConfig = ConfigLoader.load(this);
```

Getter:
```java
    public MabConfig getMabConfig() {
        return mabConfig;
    }

    public void reloadMabConfig() {
        reloadConfig();
        mabConfig = ConfigLoader.load(this);
    }
```

- [ ] **Step 2: Build**

```
./gradlew.bat build
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java
git commit -m "feat: wire ConfigLoader into MobArmyBattle.onEnable"
```

---

### Task 6: Granular permissions in plugin.yml + MabCommand

**Files:**
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java`

- [ ] **Step 1: Update plugin.yml permissions**

Replace the existing `permissions:` section with:

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
  mobarmybattle.create:
    default: true
    description: Backwards-compat alias for create.match
    children:
      mobarmybattle.create.match: true
  mobarmybattle.create.match:
    default: true
    description: Erlaubt /mab create
  mobarmybattle.create.tournament:
    default: op
    description: Erlaubt /mab tournament create
  mobarmybattle.join:
    default: true
    description: Erlaubt /mab join, leave
  mobarmybattle.spectate:
    default: true
    description: Erlaubt /mab spectate
  mobarmybattle.stats:
    default: true
    description: Erlaubt /mab stats, leaderboard
  mobarmybattle.kick:
    default: op
    description: Erlaubt /mab kick (Plan 10)
  mobarmybattle.reload:
    default: op
    description: Erlaubt /mab reload
  mobarmybattle.admin:
    default: op
    description: Admin-Befehle
```

- [ ] **Step 2: Update MabCommand permission checks**

Read existing MabCommand. Replace permission checks:
- `/mab create` → `mobarmybattle.create.match`
- `/mab join`, `/mab leave` → `mobarmybattle.join`
- `/mab spectate` → `mobarmybattle.spectate`
- `/mab stats`, `/mab leaderboard` → `mobarmybattle.stats`
- `/mab tournament create` → `mobarmybattle.create.tournament`
- `/mab tournament join`, `leave`, `start`, `list` → `mobarmybattle.join`

If existing checks use `mobarmybattle.create` (broad), narrow them per command.

- [ ] **Step 3: Build**

```
./gradlew.bat build
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/plugin.yml src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java
git commit -m "feat(perms): granular permissions for create/join/spectate/stats/kick/reload"
```

---

### Task 7: MatchBossBarManager reads from config

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/bossbar/MatchBossBarManager.java`

- [ ] **Step 1: Inject plugin reference**

Replace constants `FARM_DURATION_SEC` and `WAVE_BUILD_DURATION_SEC` with config-driven lookup. Add MobArmyBattle plugin field, update constructor.

```java
    private final MobArmyBattle plugin;

    public MatchBossBarManager() {
        this(null);
    }

    public MatchBossBarManager(MobArmyBattle plugin) {
        this.plugin = plugin;
    }
```

Replace `durationFor(MatchPhaseType phase)`:

```java
    private int durationFor(MatchPhaseType phase) {
        if (plugin == null) {
            // legacy defaults for tests
            return switch (phase) {
                case FARM -> 60 * 60;
                case WAVE_BUILD -> 5 * 60;
                default -> -1;
            };
        }
        var phases = plugin.getMabConfig().phaseDurations();
        return switch (phase) {
            case FARM -> phases.farmDurationMin() * 60;
            case WAVE_BUILD -> phases.waveBuildDurationMin() * 60;
            default -> -1;
        };
    }
```

Update `MobArmyBattle.onEnable` to use `new MatchBossBarManager(this)` instead of `new MatchBossBarManager()`.

- [ ] **Step 2: Build**

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/bossbar/MatchBossBarManager.java src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java
git commit -m "feat(bossbar): MatchBossBarManager reads phase durations from MabConfig"
```

---

### Task 8: StarterKitApplier + FarmPhase integration

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/world/StarterKitApplier.java`
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FarmPhase.java`

- [ ] **Step 1: Create StarterKitApplier**

```java
package de.klausiiiii.mobArmyBattle.world;

import de.klausiiiii.mobArmyBattle.config.StarterKitConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumMap;
import java.util.Map;

public final class StarterKitApplier {

    private StarterKitApplier() {}

    public static void applyKit(Player player, StarterKitConfig kit) {
        if (kit == null || kit.type() == StarterKitConfig.Type.NONE) return;

        Map<StarterKitConfig.Slot, Material> resolved = resolve(kit);
        PlayerInventory inv = player.getInventory();
        for (Map.Entry<StarterKitConfig.Slot, Material> e : resolved.entrySet()) {
            ItemStack stack = new ItemStack(e.getValue());
            switch (e.getKey()) {
                case MAIN -> inv.setItemInMainHand(stack);
                case OFF -> inv.setItemInOffHand(stack);
                case HELM -> inv.setHelmet(stack);
                case CHEST -> inv.setChestplate(stack);
                case LEGS -> inv.setLeggings(stack);
                case BOOTS -> inv.setBoots(stack);
            }
        }
    }

    private static Map<StarterKitConfig.Slot, Material> resolve(StarterKitConfig kit) {
        Map<StarterKitConfig.Slot, Material> out = new EnumMap<>(StarterKitConfig.Slot.class);
        switch (kit.type()) {
            case LEATHER_FULL -> {
                out.put(StarterKitConfig.Slot.MAIN, Material.STONE_SWORD);
                out.put(StarterKitConfig.Slot.HELM, Material.LEATHER_HELMET);
                out.put(StarterKitConfig.Slot.CHEST, Material.LEATHER_CHESTPLATE);
                out.put(StarterKitConfig.Slot.LEGS, Material.LEATHER_LEGGINGS);
                out.put(StarterKitConfig.Slot.BOOTS, Material.LEATHER_BOOTS);
            }
            case IRON_FULL -> {
                out.put(StarterKitConfig.Slot.MAIN, Material.IRON_SWORD);
                out.put(StarterKitConfig.Slot.OFF, Material.SHIELD);
                out.put(StarterKitConfig.Slot.HELM, Material.IRON_HELMET);
                out.put(StarterKitConfig.Slot.CHEST, Material.IRON_CHESTPLATE);
                out.put(StarterKitConfig.Slot.LEGS, Material.IRON_LEGGINGS);
                out.put(StarterKitConfig.Slot.BOOTS, Material.IRON_BOOTS);
            }
            case CUSTOM -> {
                for (Map.Entry<StarterKitConfig.Slot, String> e : kit.customSlots().entrySet()) {
                    Material mat = Material.matchMaterial(e.getValue());
                    if (mat != null) out.put(e.getKey(), mat);
                }
            }
            case NONE -> { /* no-op */ }
        }
        return out;
    }
}
```

- [ ] **Step 2: Integrate in FarmPhase.onEnter**

In `FarmPhase.onEnter`, in the Bukkit-aware section (after teleport), iterate team members and apply kit:

```java
        for (Team t : match.getTeams()) {
            for (UUID id : t.getMemberIds()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    StarterKitApplier.applyKit(p, plugin.getMabConfig().starterKit());
                }
            }
        }
```

(Place this BEFORE the existing Notifications loop. Add imports as needed.)

- [ ] **Step 3: Build**

- [ ] **Step 4: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/world/StarterKitApplier.java src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FarmPhase.java
git commit -m "feat(world): StarterKitApplier + FarmPhase gives starter kit on entry"
```

---

### Task 9: PlayerDeathFarmListener config-driven death penalty

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/listener/PlayerDeathFarmListener.java`

- [ ] **Step 1: Inject plugin + config-driven penalty**

Read existing listener. Add MobArmyBattle plugin field (constructor injection). Replace hardcoded `applyPenalty(10)` with `applyPenalty(plugin.getMabConfig().deathPenalty().mode().poolPercent())`.

For drop-items: `event.setKeepInventory(!plugin.getMabConfig().deathPenalty().mode().dropItems())`. Note: PlayerDeathFarmListener handles PlayerDeathEvent which has `setKeepInventory(boolean)`.

Also update `MobArmyBattle.onEnable` to pass `this` to the listener constructor.

- [ ] **Step 2: Build**

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/listener/PlayerDeathFarmListener.java src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java
git commit -m "feat(listener): death penalty now config-driven (NONE/SOFT/DROP_ITEMS/HARD)"
```

---

### Task 10: WorldManager border + spawn-multiplier

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/world/WorldManager.java`

- [ ] **Step 1: Apply borders + spawn limits**

In `WorldManager.createFarmWorld(matchId, teamId, seed)`, after world is created, before return:

```java
        WorldBorderConfig borderCfg = plugin.getMabConfig().farmBorder();
        if (borderCfg.enabled() && borderCfg.radius() > 0) {
            World w = world; // existing variable
            w.getWorldBorder().setCenter(w.getSpawnLocation());
            w.getWorldBorder().setSize(borderCfg.radius() * 2.0);
        }
        double mult = plugin.getMabConfig().farmMobSpawnMultiplier();
        int defaultLimit = world.getMonsterSpawnLimit();
        // setMonsterSpawnLimit takes int; default ~70 in vanilla
        world.setMonsterSpawnLimit((int) Math.max(1, defaultLimit * mult));
```

In `WorldManager.createArenaWorld(matchId, teamId)`, similarly apply arenaBorder:

```java
        WorldBorderConfig arenaBorderCfg = plugin.getMabConfig().arenaBorder();
        if (arenaBorderCfg.enabled() && arenaBorderCfg.radius() > 0) {
            world.getWorldBorder().setCenter(world.getSpawnLocation());
            world.getWorldBorder().setSize(arenaBorderCfg.radius() * 2.0);
        }
```

(Add `WorldBorderConfig` import. Verify the `World` API method names: `World.getMonsterSpawnLimit()` and `World.setMonsterSpawnLimit(int)` exist on Paper 26.1.2 — adapt if needed.)

- [ ] **Step 2: Build**

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/world/WorldManager.java
git commit -m "feat(world): WorldBorder + Mob-Spawn-Multiplier from config"
```

---

### Task 11: Auto-Farm-Phase-Timer in FarmPhase.tick

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FarmPhase.java`

- [ ] **Step 1: Implement auto-transition**

In `FarmPhase.tick(Match match)`:

```java
    @Override
    public void tick(Match match) {
        if (plugin == null) return;
        var phases = plugin.getMabConfig().phaseDurations();
        if (!phases.autoFarmTransition()) return;
        long elapsedMs = System.currentTimeMillis() - match.getPhaseStartedAt();
        long durationMs = phases.farmDurationMin() * 60_000L;
        if (elapsedMs < durationMs) return;
        // Auto-transition to WaveBuild — mirror /mab endfarm logic
        match.transitionTo(new WaveBuildPhase(plugin));
    }
```

(Verify whether existing `/mab endfarm` does anything beyond `match.transitionTo(WaveBuildPhase)`. If it does additional disband-empty-pool-team logic, replicate that here too.)

- [ ] **Step 2: Build**

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/match/phase/FarmPhase.java
git commit -m "feat(phase): FarmPhase auto-transitions after farm-duration when enabled"
```

---

### Task 12: ReconnectGraceManager

**Files:**
- Create: `src/main/java/de/klausiiiii/mobArmyBattle/config/ReconnectGraceManager.java`

- [ ] **Step 1: Implement manager**

```java
package de.klausiiiii.mobArmyBattle.config;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReconnectGraceManager {

    private final MobArmyBattle plugin;
    private final MatchManager matchManager;
    private final Map<UUID, BukkitTask> pendingEvictions = new HashMap<>();

    public ReconnectGraceManager(MobArmyBattle plugin, MatchManager matchManager) {
        this.plugin = plugin;
        this.matchManager = matchManager;
    }

    /**
     * Marks a player as absent. If they reconnect within graceSec, restoreAbsent() will cancel the eviction.
     * If grace expires, matchManager.leaveMatch is called.
     *
     * @return true if grace was scheduled (caller should NOT call leaveMatch); false if grace=0 or no match
     */
    public boolean markAbsent(UUID playerId) {
        if (matchManager.getMatchOf(playerId) == null) return false;
        int graceSec = plugin.getMabConfig().reconnect().graceSec();
        if (graceSec <= 0) return false;

        cancelPending(playerId);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingEvictions.remove(playerId);
            matchManager.leaveMatch(playerId);
        }, graceSec * 20L);
        pendingEvictions.put(playerId, task);
        return true;
    }

    public boolean isAbsent(UUID playerId) {
        return pendingEvictions.containsKey(playerId);
    }

    /**
     * Called on rejoin: cancels pending eviction. Caller is responsible for teleporting
     * the player to the appropriate world (current match phase).
     */
    public void restoreAbsent(UUID playerId) {
        cancelPending(playerId);
    }

    public void cancelAll() {
        for (BukkitTask t : pendingEvictions.values()) t.cancel();
        pendingEvictions.clear();
    }

    private void cancelPending(UUID playerId) {
        BukkitTask existing = pendingEvictions.remove(playerId);
        if (existing != null) existing.cancel();
    }
}
```

- [ ] **Step 2: Build**

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/config/ReconnectGraceManager.java
git commit -m "feat(config): ReconnectGraceManager defers leaveMatch by config grace-sec"
```

---

### Task 13: PlayerConnectionListener integration with grace

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/listener/PlayerConnectionListener.java`
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java`

- [ ] **Step 1: Inject ReconnectGraceManager**

Add 5-arg constructor (with grace):

```java
    public PlayerConnectionListener(MatchManager matchManager,
                                    WorldManager worldManager,
                                    TournamentManager tournamentManager,
                                    SpectatorManager spectatorManager,
                                    ReconnectGraceManager graceManager) {
        // existing assignments
        this.graceManager = graceManager;
    }
```

(Keep older 3-arg + 4-arg for backward-compat, defaulting graceManager to null.)

- [ ] **Step 2: onQuit grace integration**

After spectator-cleanup section, before existing leaveMatch logic:

```java
        if (graceManager != null && graceManager.markAbsent(id)) {
            return;  // grace scheduled — eviction will run after delay
        }
        // existing fall-through to matchManager.leaveMatch + tournament cleanup
```

- [ ] **Step 3: onJoin grace restoration**

In the existing `onPlayerJoin` handler (or add one if missing), at the top:

```java
        UUID id = event.getPlayer().getUniqueId();
        if (graceManager != null && graceManager.isAbsent(id)) {
            graceManager.restoreAbsent(id);
            // teleport to current match world if still active
            Match match = matchManager.getMatchOf(id);
            if (match != null) {
                MatchPhaseType phase = match.getCurrentPhase().getType();
                if (phase == MatchPhaseType.FARM) {
                    Team t = match.findTeamOf(id);
                    if (t != null) {
                        String farmName = match.getFarmWorldName(t);
                        if (farmName != null) {
                            World fw = Bukkit.getWorld(farmName);
                            if (fw != null) event.getPlayer().teleport(fw.getSpawnLocation());
                        }
                    }
                }
                // For other phases (WAVE_BUILD, BATTLE), let normal logic teleport
            }
            return;
        }
```

(Adjust import path for `MatchPhaseType` etc.)

- [ ] **Step 4: Wire ReconnectGraceManager in MobArmyBattle.onEnable**

After SpectatorManager construction, add:

```java
        reconnectGraceManager = new ReconnectGraceManager(this, matchManager);
```

Update PlayerConnectionListener registration to use 5-arg constructor.

Add field + getter on MobArmyBattle.

- [ ] **Step 5: Build**

- [ ] **Step 6: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/listener/PlayerConnectionListener.java src/main/java/de/klausiiiii/mobArmyBattle/MobArmyBattle.java
git commit -m "feat(listener): reconnect-grace defers match leave for graceSec"
```

---

### Task 14: /mab reload command

**Files:**
- Modify: `src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java`

- [ ] **Step 1: Add reload subcommand**

In the `onCommand` switch:

```java
        case "reload":
            if (!sender.hasPermission("mobarmybattle.reload")) {
                sender.sendMessage("§cKeine Berechtigung.");
                return true;
            }
            plugin.reloadMabConfig();
            sender.sendMessage("§aMabConfig neu geladen.");
            return true;
```

Add to tab completion: `"reload"` in first-arg list (but only when `sender.hasPermission("mobarmybattle.reload")`).

Add to SUBCOMMANDS / sendUsage as appropriate.

- [ ] **Step 2: Build**

- [ ] **Step 3: Commit**

```bash
git add src/main/java/de/klausiiiii/mobArmyBattle/command/MabCommand.java
git commit -m "feat(command): /mab reload reloads MabConfig at runtime"
```

---

### Task 15: Final code review + verification

This task has no commits — verification gate.

- [ ] **Step 1: Full build**

```
./gradlew.bat build
```
Expected: BUILD SUCCESSFUL, ≥203 tests pass (188 baseline + ~15 new).

- [ ] **Step 2: Manual smoke-test (deferred to user)**

User runs `./gradlew.bat runServer` and tests:
- config.yml is created on first start in `plugins/MobArmyBattle/`
- Edit config: change starter-kit type → restart → players get new kit
- Edit config: change death-penalty.mode → /mab reload → next death uses new mode
- Test border: enter farm world, walk to edge — border visible
- Test reconnect-grace: quit during FARM, rejoin within 5 min → still in match
- Test auto-farm-transition: set farm-duration-min: 1 → wait 1 min → auto-WaveBuild
- Test granular permissions: revoke create.tournament from non-op → /mab tournament create denied

## Acceptance Criteria

- [ ] config.yml created on first onEnable
- [ ] All 6 config records validate inputs (negative values throw)
- [ ] MabConfig.defaults() returns valid instance
- [ ] ConfigLoader gracefully handles missing/invalid sections (defaults + warning)
- [ ] `/mab reload` reloads config without server restart
- [ ] Phase BossBar timer reflects config farm-duration-min / wave-build-duration-min
- [ ] Starter kit applied on FarmPhase entry per config.type
- [ ] Death-penalty mode applies correct pool % and item-drop behavior
- [ ] Farm world has border at config.farm-world.border.radius
- [ ] Arena world has border at config.arena.border.radius
- [ ] Farm world mob spawn limit = vanilla * config.mob-spawn-multiplier
- [ ] FarmPhase auto-transitions to WaveBuild after farm-duration-min when auto-farm-transition=true
- [ ] Player quit during match: grace started; rejoin within graceSec restores membership
- [ ] Grace expires: leaveMatch called normally
- [ ] Granular permissions in plugin.yml; MabCommand checks correct permission per subcommand
- [ ] Backwards-compat: existing `mobarmybattle.create` permission still grants /mab create
- [ ] All ~15 new tests green; total ≥203
