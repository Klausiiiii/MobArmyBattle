package de.klausiiiii.mobArmyBattle.battle;

import de.klausiiiii.mobArmyBattle.match.Team;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamPairTest {

    @Test
    void pairHoldsTwoTeams() {
        Team a = new Team(UUID.randomUUID(), 1);
        Team b = new Team(UUID.randomUUID(), 1);
        TeamPair pair = new TeamPair(a, b);

        assertSame(a, pair.getTeamA());
        assertSame(b, pair.getTeamB());
    }

    @Test
    void rejectsSameTeamTwice() {
        Team a = new Team(UUID.randomUUID(), 1);
        assertThrows(IllegalArgumentException.class, () -> new TeamPair(a, a));
    }

    @Test
    void rejectsNullTeam() {
        Team a = new Team(UUID.randomUUID(), 1);
        assertThrows(IllegalArgumentException.class, () -> new TeamPair(null, a));
        assertThrows(IllegalArgumentException.class, () -> new TeamPair(a, null));
    }

    @Test
    void otherReturnsTheOtherTeam() {
        Team a = new Team(UUID.randomUUID(), 1);
        Team b = new Team(UUID.randomUUID(), 1);
        TeamPair pair = new TeamPair(a, b);

        assertSame(b, pair.other(a));
        assertSame(a, pair.other(b));
    }

    @Test
    void otherThrowsForUnknownTeam() {
        Team a = new Team(UUID.randomUUID(), 1);
        Team b = new Team(UUID.randomUUID(), 1);
        Team c = new Team(UUID.randomUUID(), 1);
        TeamPair pair = new TeamPair(a, b);

        assertThrows(IllegalArgumentException.class, () -> pair.other(c));
    }

    @Test
    void containsReturnsTrueForBothTeams() {
        Team a = new Team(UUID.randomUUID(), 1);
        Team b = new Team(UUID.randomUUID(), 1);
        Team c = new Team(UUID.randomUUID(), 1);
        TeamPair pair = new TeamPair(a, b);

        assertTrue(pair.contains(a));
        assertTrue(pair.contains(b));
        assertFalse(pair.contains(c));
    }
}
