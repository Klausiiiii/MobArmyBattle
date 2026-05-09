package de.klausiiiii.mobArmyBattle.battle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BattleResultTest {

    private BattleStats stats(int waves, int kills, long timeMs, boolean finished) {
        BattleStats s = new BattleStats();
        for (int i = 0; i < waves; i++) s.recordWaveSurvived();
        for (int i = 0; i < kills; i++) s.recordMobKill();
        if (finished) s.markFinished(timeMs);
        return s;
    }

    @Test
    void winnerByMoreWaves() {
        BattleStats a = stats(2, 5, 1000L, true);
        BattleStats b = stats(1, 50, 100L, true);
        assertEquals(BattleResult.Winner.A, BattleResult.compare(a, b));
    }

    @Test
    void tiedWavesWinnerByMoreKills() {
        BattleStats a = stats(2, 10, 5000L, true);
        BattleStats b = stats(2, 20, 5000L, true);
        assertEquals(BattleResult.Winner.B, BattleResult.compare(a, b));
    }

    @Test
    void tiedWavesAndKillsWinnerByFasterTime() {
        BattleStats a = stats(2, 10, 1000L, true);
        BattleStats b = stats(2, 10, 5000L, true);
        assertEquals(BattleResult.Winner.A, BattleResult.compare(a, b));
    }

    @Test
    void allEqualReturnsDraw() {
        BattleStats a = stats(2, 10, 1000L, true);
        BattleStats b = stats(2, 10, 1000L, true);
        assertEquals(BattleResult.Winner.DRAW, BattleResult.compare(a, b));
    }

    @Test
    void notFinishedTreatedAsTimeMaxValue() {
        BattleStats a = stats(2, 10, 0L, false);
        BattleStats b = stats(2, 10, 5000L, true);
        assertEquals(BattleResult.Winner.B, BattleResult.compare(a, b));
    }
}
