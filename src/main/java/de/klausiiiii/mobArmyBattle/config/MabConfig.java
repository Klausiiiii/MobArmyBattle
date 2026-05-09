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
