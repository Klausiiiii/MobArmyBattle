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
