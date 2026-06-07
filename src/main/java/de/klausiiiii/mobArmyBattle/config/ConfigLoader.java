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
        double mobMult = loadMobMultiplier(cfg, log);
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
                    cfg.getBoolean("phases.auto-farm-transition", defaults.autoFarmTransition()),
                    cfg.getInt("phases.post-battle-view-sec", defaults.postBattleViewSec()));
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

    private static double loadMobMultiplier(FileConfiguration cfg, Logger log) {
        double mult = cfg.getDouble("farm-world.mob-spawn-multiplier", 2.0);
        if (mult <= 0) {
            log.warning("farm-world.mob-spawn-multiplier muss > 0 sein, war " + mult + " — verwende 2.0");
            return 2.0;
        }
        return mult;
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
