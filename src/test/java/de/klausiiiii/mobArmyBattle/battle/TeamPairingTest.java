package de.klausiiiii.mobArmyBattle.battle;

import de.klausiiiii.mobArmyBattle.match.Team;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamPairingTest {

    @Test
    void pairsTwoTeamsIntoOnePair() {
        Team a = new Team(UUID.randomUUID(), 1);
        Team b = new Team(UUID.randomUUID(), 1);

        TeamPairing.Result result = TeamPairing.pair(List.of(a, b));

        assertEquals(1, result.getPairs().size());
        assertTrue(result.getPairs().get(0).contains(a));
        assertTrue(result.getPairs().get(0).contains(b));
        assertTrue(result.getByeTeams().isEmpty());
    }

    @Test
    void pairsFourTeamsIntoTwoPairs() {
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < 4; i++) teams.add(new Team(UUID.randomUUID(), 1));

        TeamPairing.Result result = TeamPairing.pair(teams);

        assertEquals(2, result.getPairs().size());
        assertTrue(result.getByeTeams().isEmpty());
    }

    @Test
    void pairsThreeTeamsLeavesOneBye() {
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < 3; i++) teams.add(new Team(UUID.randomUUID(), 1));

        TeamPairing.Result result = TeamPairing.pair(teams);

        assertEquals(1, result.getPairs().size());
        assertEquals(1, result.getByeTeams().size());
    }

    @Test
    void pairsEightTeamsIntoFourPairs() {
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < 8; i++) teams.add(new Team(UUID.randomUUID(), 1));

        TeamPairing.Result result = TeamPairing.pair(teams);

        assertEquals(4, result.getPairs().size());
        assertTrue(result.getByeTeams().isEmpty());
    }

    @Test
    void rejectsLessThanTwoTeams() {
        Team a = new Team(UUID.randomUUID(), 1);
        assertThrows(IllegalArgumentException.class, () -> TeamPairing.pair(List.of(a)));
        assertThrows(IllegalArgumentException.class, () -> TeamPairing.pair(List.of()));
    }

    @Test
    void everyTeamAppearsExactlyOnce() {
        List<Team> teams = new ArrayList<>();
        for (int i = 0; i < 6; i++) teams.add(new Team(UUID.randomUUID(), 1));

        TeamPairing.Result result = TeamPairing.pair(teams);

        java.util.Set<Team> seen = new java.util.HashSet<>();
        for (TeamPair pair : result.getPairs()) {
            assertTrue(seen.add(pair.getTeamA()), "Team A in pair was already seen");
            assertTrue(seen.add(pair.getTeamB()), "Team B in pair was already seen");
        }
        seen.addAll(result.getByeTeams());
        assertEquals(teams.size(), seen.size());
    }
}
