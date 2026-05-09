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
