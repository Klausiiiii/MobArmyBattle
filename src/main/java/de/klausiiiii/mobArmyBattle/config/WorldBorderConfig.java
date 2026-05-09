package de.klausiiiii.mobArmyBattle.config;

public record WorldBorderConfig(boolean enabled, int radius) {
    public WorldBorderConfig {
        if (radius < 0) throw new IllegalArgumentException("radius muss >= 0 sein");
    }
}
