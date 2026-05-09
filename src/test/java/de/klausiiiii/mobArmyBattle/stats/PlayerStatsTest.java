package de.klausiiiii.mobArmyBattle.stats;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerStatsTest {

    @Test
    void emptyHasZeroValues() {
        UUID id = UUID.randomUUID();
        PlayerStats s = PlayerStats.empty(id);
        assertEquals(id, s.getPlayerId());
        assertEquals(0, s.getMatchesTotal());
        assertEquals(0, s.getMatchesWon());
        assertEquals(0, s.getMobKillsTotal());
        assertEquals(0.0, s.getWinRate());
    }

    @Test
    void rejectsNegativeValues() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new PlayerStats(id, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlayerStats(id, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlayerStats(id, 0, 0, -1));
    }

    @Test
    void rejectsMoreWinsThanTotal() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new PlayerStats(id, 5, 6, 0));
    }

    @Test
    void addMatchWonIncrementsBoth() {
        PlayerStats s = PlayerStats.empty(UUID.randomUUID()).addMatch(true, 12);
        assertEquals(1, s.getMatchesTotal());
        assertEquals(1, s.getMatchesWon());
        assertEquals(0, s.getMatchesLost());
        assertEquals(12, s.getMobKillsTotal());
    }

    @Test
    void addMatchLostIncrementsTotalOnly() {
        PlayerStats s = PlayerStats.empty(UUID.randomUUID()).addMatch(false, 5);
        assertEquals(1, s.getMatchesTotal());
        assertEquals(0, s.getMatchesWon());
        assertEquals(1, s.getMatchesLost());
        assertEquals(5, s.getMobKillsTotal());
    }

    @Test
    void addMultipleMatchesAggregates() {
        PlayerStats s = PlayerStats.empty(UUID.randomUUID())
                .addMatch(true, 10)
                .addMatch(false, 3)
                .addMatch(true, 8);
        assertEquals(3, s.getMatchesTotal());
        assertEquals(2, s.getMatchesWon());
        assertEquals(1, s.getMatchesLost());
        assertEquals(21, s.getMobKillsTotal());
    }

    @Test
    void winRateCalculation() {
        PlayerStats s = new PlayerStats(UUID.randomUUID(), 4, 3, 0);
        assertEquals(0.75, s.getWinRate(), 0.001);
    }

    @Test
    void addMatchRejectsNegativeKills() {
        PlayerStats s = PlayerStats.empty(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> s.addMatch(true, -1));
    }
}
