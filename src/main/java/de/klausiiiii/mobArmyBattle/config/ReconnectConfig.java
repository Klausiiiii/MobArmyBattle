package de.klausiiiii.mobArmyBattle.config;

public record ReconnectConfig(int graceSec) {
    public ReconnectConfig {
        if (graceSec < 0) throw new IllegalArgumentException("grace-sec muss >= 0 sein");
    }
}
