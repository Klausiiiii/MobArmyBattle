package de.klausiiiii.mobArmyBattle.match;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MatchManagerTest {

    @Test
    void canCreateMatchWithCaptain() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();

        Match match = manager.createMatch(captain);

        assertNotNull(match);
        assertEquals(MatchPhaseType.LOBBY, match.getCurrentPhase().getType());
        assertEquals(1, match.getTeams().size());
        assertEquals(captain, match.getTeams().get(0).getCaptainId());
    }

    @Test
    void matchIdsAreUnique() {
        MatchManager manager = new MatchManager();

        Match m1 = manager.createMatch(UUID.randomUUID());
        Match m2 = manager.createMatch(UUID.randomUUID());

        assertNotEquals(m1.getId(), m2.getId());
    }

    @Test
    void canFindMatchOfPlayer() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        Match match = manager.createMatch(captain);

        Match found = manager.getMatchOf(captain);

        assertSame(match, found);
    }

    @Test
    void getMatchOfReturnsNullWhenNotInMatch() {
        MatchManager manager = new MatchManager();

        assertNull(manager.getMatchOf(UUID.randomUUID()));
    }

    @Test
    void cannotCreateSecondMatchForSamePlayer() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        manager.createMatch(captain);

        assertThrows(IllegalStateException.class, () -> manager.createMatch(captain));
    }

    @Test
    void canJoinExistingMatch() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        Match match = manager.createMatch(captain);

        manager.joinMatch(joiner, captain);

        assertSame(match, manager.getMatchOf(joiner));
        assertTrue(match.getTeams().get(0).hasMember(joiner));
    }

    @Test
    void joiningUnknownCaptainThrows() {
        MatchManager manager = new MatchManager();

        assertThrows(IllegalArgumentException.class,
                () -> manager.joinMatch(UUID.randomUUID(), UUID.randomUUID()));
    }

    @Test
    void joiningWhileAlreadyInMatchThrows() {
        MatchManager manager = new MatchManager();
        UUID captain1 = UUID.randomUUID();
        UUID captain2 = UUID.randomUUID();
        manager.createMatch(captain1);
        manager.createMatch(captain2);

        assertThrows(IllegalStateException.class,
                () -> manager.joinMatch(captain1, captain2));
    }

    @Test
    void canLeaveMatch() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain);
        manager.joinMatch(joiner, captain);

        manager.leaveMatch(joiner);

        assertNull(manager.getMatchOf(joiner));
    }

    @Test
    void leavingAsCaptainPromotesNextMember() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        Match match = manager.createMatch(captain);
        manager.joinMatch(joiner, captain);

        manager.leaveMatch(captain);

        assertNull(manager.getMatchOf(captain));
        assertEquals(joiner, match.getTeams().get(0).getCaptainId());
    }

    @Test
    void leavingAsLastMemberClosesMatch() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        Match match = manager.createMatch(captain);

        manager.leaveMatch(captain);

        assertNull(manager.getMatchOf(captain));
        assertFalse(manager.getActiveMatches().contains(match));
    }

    @Test
    void getCaptainIdsReturnsAllActiveCaptains() {
        MatchManager manager = new MatchManager();
        UUID captain1 = UUID.randomUUID();
        UUID captain2 = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain1);
        manager.createMatch(captain2);
        manager.joinMatch(joiner, captain1);

        java.util.Set<UUID> captains = manager.getCaptainIds();

        assertEquals(2, captains.size());
        assertTrue(captains.contains(captain1));
        assertTrue(captains.contains(captain2));
        assertFalse(captains.contains(joiner));
    }

    @Test
    void getCaptainIdsExcludesDisbandedTeams() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        manager.createMatch(captain);

        manager.leaveMatch(captain);

        assertTrue(manager.getCaptainIds().isEmpty());
    }

    @Test
    void leavingAsSoloCaptainDisbandsTeam() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        Match match = manager.createMatch(captain);
        Team team = match.getTeams().get(0);

        manager.leaveMatch(captain);

        assertTrue(team.isDisbanded(), "Team should be disbanded after solo captain leaves");
        assertNull(team.getCaptainId());
        assertEquals(0, team.size());
    }
}
