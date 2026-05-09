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
