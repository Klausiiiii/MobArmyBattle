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
        PhaseDurations p = new PhaseDurations(60, 5, 0, 0, 1, false);
        assertEquals(0, p.prepDurationSec());
        assertEquals(0, p.wavePauseSec());
    }

    @Test
    void rejectsNegativeWavePause() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhaseDurations(60, 5, 30, -1, 10, true));
    }

    @Test
    void sixArgConstructorDefaultsPostBattleViewSec() {
        PhaseDurations p = new PhaseDurations(60, 5, 30, 10, 10, true);
        assertEquals(8, p.postBattleViewSec());
    }

    @Test
    void canSetPostBattleViewSec() {
        PhaseDurations p = new PhaseDurations(60, 5, 30, 10, 10, true, 15);
        assertEquals(15, p.postBattleViewSec());
    }

    @Test
    void allowsZeroPostBattleViewSec() {
        PhaseDurations p = new PhaseDurations(60, 5, 30, 10, 10, true, 0);
        assertEquals(0, p.postBattleViewSec());
    }

    @Test
    void rejectsNegativePostBattleViewSec() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhaseDurations(60, 5, 30, 10, 10, true, -1));
    }
}
