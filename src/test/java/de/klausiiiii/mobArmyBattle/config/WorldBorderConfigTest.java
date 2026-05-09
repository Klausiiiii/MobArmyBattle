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
