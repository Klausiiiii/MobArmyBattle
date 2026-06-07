package de.klausiiiii.mobArmyBattle.match;

import org.junit.jupiter.api.Test;

import java.util.Set;
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
        // default maxTeamSize=1: captain fills team 0, joiner creates team 1
        assertEquals(2, match.getTeams().size());
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
        Match match = manager.createMatch(captain, 2);
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
        manager.createMatch(captain1);  // maxTeamSize=1
        manager.createMatch(captain2);
        manager.joinMatch(joiner, captain1);  // joiner creates new team in match-1

        Set<UUID> captains = manager.getCaptainIds();

        // 3 captains: captain1, captain2, joiner (newly created team)
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
    void createMatchWithMaxTeamSize() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();

        Match match = manager.createMatch(captain, 4);

        assertEquals(4, match.getMaxTeamSize());
        assertEquals(1, match.getTeams().size());  // only captain's team
        assertEquals(captain, match.getTeams().get(0).getCaptainId());
    }

    @Test
    void createMatchWithDefaultMaxTeamSize1() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();

        Match match = manager.createMatch(captain);

        assertEquals(1, match.getMaxTeamSize());
        assertEquals(1, match.getTeams().size());
    }

    @Test
    void joinMatchInTeamIndex0AddsToCaptainTeam() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, 2);

        manager.joinMatch(joiner, captain, 0);

        Match m = manager.getMatchOf(joiner);
        assertTrue(m.getTeams().get(0).hasMember(joiner));
    }

    @Test
    void joinMatchInTeamIndex1MakesJoinerCaptainOfTeam2() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, 2);

        manager.joinMatch(joiner, captain, 1);

        Match m = manager.getMatchOf(joiner);
        assertEquals(2, m.getTeams().size());
        assertEquals(joiner, m.getTeams().get(1).getCaptainId());
    }

    @Test
    void joinMatchAutoBalanceJoinsCaptainTeamFirst() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, 2);

        manager.joinMatch(joiner, captain);

        Match m = manager.getMatchOf(joiner);
        assertEquals(1, m.getTeams().size());  // still one team
        assertTrue(m.getTeams().get(0).hasMember(joiner));
    }

    @Test
    void joinMatchAutoBalanceCreatesNewTeamWhenAllFull() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, 1);  // team max 1, so captain fills it

        manager.joinMatch(joiner, captain);

        Match m = manager.getMatchOf(joiner);
        assertEquals(2, m.getTeams().size());
        assertEquals(joiner, m.getTeams().get(1).getCaptainId());
    }

    @Test
    void joinMatchAutoBalanceJoinsTeamWithFewerPlayers() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID j1 = UUID.randomUUID();
        UUID j2 = UUID.randomUUID();
        UUID j3 = UUID.randomUUID();
        manager.createMatch(captain, 2);
        manager.joinMatch(j1, captain, 1);  // create team 2 with j1 as captain
        manager.joinMatch(j2, captain, 0);  // join captain's team

        manager.joinMatch(j3, captain);  // auto-balance: team 1 has 1, team 0 has 2, picks team 1

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
        manager.createMatch(captain, 1);
        manager.joinMatch(j1, captain, 1);  // creates team 2 with j1
        // Now try to join team 2 again - it's full (1/1)
        assertThrows(IllegalStateException.class,
                () -> manager.joinMatch(j2, captain, 1));
    }

    @Test
    void joiningInvalidTeamIndexThrows() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, 1);
        // teams.size() == 1 currently. Index 0 = captain team, index 1 = create new (allowed).
        // Index 2 should throw (gap).
        assertThrows(IllegalArgumentException.class,
                () -> manager.joinMatch(joiner, captain, 2));
        assertThrows(IllegalArgumentException.class,
                () -> manager.joinMatch(joiner, captain, -1));
    }

    @Test
    void leaveAfterTeamDisbandedDoesNotThrow() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        manager.createMatch(captain, 1);
        Match match = manager.getMatchOf(captain);
        // Simulate: team got disbanded externally (e.g. WaveBuildPhase auto-cleanup)
        // but matchByPlayer still tracks the player
        match.findTeamOf(captain).disband();

        assertDoesNotThrow(() -> manager.leaveMatch(captain));
        assertNull(manager.getMatchOf(captain));
    }

    @Test
    void forceRemoveDropsPlayerAndClosesEmptyMatch() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        Match match = manager.createMatch(captain, 1);

        manager.forceRemove(captain);

        assertNull(manager.getMatchOf(captain));
        assertFalse(manager.getActiveMatches().contains(match));
    }

    @Test
    void forceRemoveLeavesMatchOpenWhenOtherPlayersExist() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        Match match = manager.createMatch(captain, 1);
        manager.joinMatch(joiner, captain, 1);

        manager.forceRemove(captain);

        assertNull(manager.getMatchOf(captain));
        assertSame(match, manager.getMatchOf(joiner));
        assertTrue(manager.getActiveMatches().contains(match));
    }

    @Test
    void forceRemoveOnNonMemberDoesNothing() {
        MatchManager manager = new MatchManager();
        UUID stranger = UUID.randomUUID();

        assertDoesNotThrow(() -> manager.forceRemove(stranger));
    }

    @Test
    void joinMatchAsNewTeamCreatesPublicTeam() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, 2);

        Team newTeam = manager.joinMatchAsNewTeam(joiner, captain, TeamVisibility.PUBLIC, null);

        Match match = manager.getMatchOf(joiner);
        assertNotNull(match);
        assertEquals(2, match.getTeams().size());
        assertEquals(joiner, newTeam.getCaptainId());
        assertEquals(TeamVisibility.PUBLIC, newTeam.getVisibility());
    }

    @Test
    void joinMatchAsNewTeamWithPasswordHashesIt() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, 2);

        Team newTeam = manager.joinMatchAsNewTeam(joiner, captain, TeamVisibility.PASSWORD, "pw123");

        assertEquals(TeamVisibility.PASSWORD, newTeam.getVisibility());
        assertTrue(newTeam.hasPassword());
        assertTrue(newTeam.verifyPassword("pw123"));
    }

    @Test
    void joinMatchAsNewTeamRejectsPasswordWithoutValue() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, 2);

        assertThrows(IllegalArgumentException.class,
                () -> manager.joinMatchAsNewTeam(joiner, captain, TeamVisibility.PASSWORD, null));
        assertThrows(IllegalArgumentException.class,
                () -> manager.joinMatchAsNewTeam(joiner, captain, TeamVisibility.PASSWORD, " "));
    }

    @Test
    void joinExistingTeamPublicAlwaysWorks() {
        MatchManager manager = new MatchManager();
        UUID captain = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(captain, 2);

        manager.joinExistingTeam(joiner, captain, 0, null);

        assertEquals(2, manager.getMatchOf(captain).getTeams().get(0).size());
    }

    @Test
    void joinExistingTeamPasswordRejectsWrongPw() {
        MatchManager manager = new MatchManager();
        UUID hostA = UUID.randomUUID();
        UUID hostB = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(hostA, 2);
        manager.joinMatchAsNewTeam(hostB, hostA, TeamVisibility.PASSWORD, "secret");

        assertThrows(IllegalStateException.class,
                () -> manager.joinExistingTeam(joiner, hostA, 1, "wrong"));
        assertNull(manager.getMatchOf(joiner));

        manager.joinExistingTeam(joiner, hostA, 1, "secret");
        assertNotNull(manager.getMatchOf(joiner));
    }

    @Test
    void joinExistingTeamInviteBypassesPassword() {
        MatchManager manager = new MatchManager();
        UUID hostA = UUID.randomUUID();
        UUID hostB = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(hostA, 2);
        manager.joinMatchAsNewTeam(hostB, hostA, TeamVisibility.PASSWORD, "secret");
        manager.getMatchOf(hostA).getTeams().get(1).invite(joiner);

        manager.joinExistingTeam(joiner, hostA, 1, null);

        assertNotNull(manager.getMatchOf(joiner));
        assertFalse(manager.getMatchOf(hostA).getTeams().get(1).isInvited(joiner));
    }

    @Test
    void joinExistingTeamPrivateRequiresInvite() {
        MatchManager manager = new MatchManager();
        UUID hostA = UUID.randomUUID();
        UUID hostB = UUID.randomUUID();
        UUID joiner = UUID.randomUUID();
        manager.createMatch(hostA, 2);
        manager.joinMatchAsNewTeam(hostB, hostA, TeamVisibility.PRIVATE, null);

        assertThrows(IllegalStateException.class,
                () -> manager.joinExistingTeam(joiner, hostA, 1, null));

        manager.getMatchOf(hostA).getTeams().get(1).invite(joiner);
        manager.joinExistingTeam(joiner, hostA, 1, null);

        assertNotNull(manager.getMatchOf(joiner));
        // Invite is one-shot
        assertFalse(manager.getMatchOf(hostA).getTeams().get(1).isInvited(joiner));
    }
}
