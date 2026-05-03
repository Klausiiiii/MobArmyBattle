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
        assertEquals(2, match.getTeams().size());
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
        // 1v1 default: team 0 has captain (full), team 1 is empty -> auto-balance routes joiner to team 1
        assertTrue(match.getTeams().get(1).hasMember(joiner));
        assertEquals(joiner, match.getTeams().get(1).getCaptainId());
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
        Match match = manager.createMatch(captain, MatchMode.parse("2v2"));
        manager.joinMatch(joiner, captain, 0);

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
        manager.joinMatch(joiner, captain1);  // joiner auto-balances to team 1, becomes captain there

        java.util.Set<UUID> captains = manager.getCaptainIds();

        assertEquals(3, captains.size());
        assertTrue(captains.contains(captain1));
        assertTrue(captains.contains(captain2));
        assertTrue(captains.contains(joiner));
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

    @Test
    void createMatchWithModeSetsMatchMode() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();

        Match match = manager.createMatch(captain, MatchMode.parse("2v2"));

        assertEquals(MatchMode.parse("2v2"), match.getMode());
        assertEquals(2, match.getTeams().size());
        assertEquals(captain, match.getTeams().get(0).getCaptainId());
        assertNull(match.getTeams().get(1).getCaptainId());
    }

    @Test
    void createMatchWithDefaultModeIs1v1() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();

        Match match = manager.createMatch(captain);

        assertEquals(MatchMode.parse("1v1"), match.getMode());
        assertEquals(2, match.getTeams().size());
    }

    @Test
    void joinMatchInTeamIndex0AddsToCaptainTeam() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, MatchMode.parse("2v2"));

        manager.joinMatch(joiner, captain, 0);

        Match m = manager.getMatchOf(joiner);
        assertTrue(m.getTeams().get(0).hasMember(joiner));
        assertFalse(m.getTeams().get(1).hasMember(joiner));
    }

    @Test
    void joinMatchInTeamIndex1MakesJoinerCaptainOfTeam2() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, MatchMode.parse("2v2"));

        manager.joinMatch(joiner, captain, 1);

        Match m = manager.getMatchOf(joiner);
        assertEquals(joiner, m.getTeams().get(1).getCaptainId());
        assertTrue(m.getTeams().get(1).hasMember(joiner));
    }

    @Test
    void joinMatchAutoBalanceJoinsEmptyTeam() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, MatchMode.parse("2v2"));

        manager.joinMatch(joiner, captain);

        Match m = manager.getMatchOf(joiner);
        assertEquals(joiner, m.getTeams().get(1).getCaptainId());
    }

    @Test
    void joinMatchAutoBalanceJoinsTeamWithFewerPlayers() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID j1 = UUID.randomUUID();
        UUID j2 = UUID.randomUUID();
        UUID j3 = UUID.randomUUID();
        manager.createMatch(captain, MatchMode.parse("2v2"));
        manager.joinMatch(j1, captain, 1);
        manager.joinMatch(j2, captain, 0);

        manager.joinMatch(j3, captain);

        Match m = manager.getMatchOf(j3);
        assertEquals(2, m.getTeams().get(0).size());
        assertEquals(2, m.getTeams().get(1).size());
    }

    @Test
    void joiningFullTeamThrows() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID j1 = UUID.randomUUID();
        UUID j2 = UUID.randomUUID();
        manager.createMatch(captain, MatchMode.parse("1v1"));
        manager.joinMatch(j1, captain, 1);

        assertThrows(IllegalStateException.class,
                () -> manager.joinMatch(j2, captain, 1));
    }

    @Test
    void joiningInvalidTeamIndexThrows() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, MatchMode.parse("1v1"));

        assertThrows(IllegalArgumentException.class,
                () -> manager.joinMatch(joiner, captain, 2));
        assertThrows(IllegalArgumentException.class,
                () -> manager.joinMatch(joiner, captain, -1));
    }
}
