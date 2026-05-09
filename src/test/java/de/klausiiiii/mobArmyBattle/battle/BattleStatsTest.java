package de.klausiiiii.mobArmyBattle.battle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BattleStatsTest {

    @Test
    void newStatsHasZeroEverything() {
        BattleStats stats = new BattleStats();
        assertEquals(0, stats.getWavesSurvived());
        assertEquals(0, stats.getMobKills());
        assertEquals(0L, stats.getFinishTimeMs());
        assertFalse(stats.isFinished());
    }

    @Test
    void canIncrementKills() {
        BattleStats stats = new BattleStats();
        stats.recordMobKill();
        stats.recordMobKill();
        assertEquals(2, stats.getMobKills());
    }

    @Test
    void canMarkWaveSurvived() {
        BattleStats stats = new BattleStats();
        stats.recordWaveSurvived();
        assertEquals(1, stats.getWavesSurvived());
    }

    @Test
    void canMarkFinished() {
        BattleStats stats = new BattleStats();
        stats.markFinished(1234L);
        assertTrue(stats.isFinished());
        assertEquals(1234L, stats.getFinishTimeMs());
    }

    @Test
    void cannotMarkFinishedTwice() {
        BattleStats stats = new BattleStats();
        stats.markFinished(100L);
        assertThrows(IllegalStateException.class, () -> stats.markFinished(200L));
    }
}
