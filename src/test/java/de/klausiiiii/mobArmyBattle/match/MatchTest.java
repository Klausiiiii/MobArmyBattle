package de.klausiiiii.mobArmyBattle.match;

import de.klausiiiii.mobArmyBattle.match.phase.LobbyPhase;
import de.klausiiiii.mobArmyBattle.match.phase.FarmPhase;
import de.klausiiiii.mobArmyBattle.match.phase.WaveBuildPhase;
import de.klausiiiii.mobArmyBattle.match.phase.BattlePhase;
import de.klausiiiii.mobArmyBattle.match.phase.FinishedPhase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MatchTest {

    @Test
    void newMatchStartsInLobbyPhase() {
        Match match = new Match("test-match-1");

        assertEquals(MatchPhaseType.LOBBY, match.getCurrentPhase().getType());
    }

    @Test
    void newMatchHasNoTeams() {
        Match match = new Match("test-match-1");

        assertTrue(match.getTeams().isEmpty());
    }

    @Test
    void canAddTeam() {
        Match match = new Match("test-match-1");
        UUID captain = UUID.randomUUID();
        Team team = new Team(captain);

        match.addTeam(team);

        assertEquals(1, match.getTeams().size());
        assertTrue(match.getTeams().contains(team));
    }

    @Test
    void canFindTeamByPlayerId() {
        Match match = new Match("test-match-1");
        UUID captain = UUID.randomUUID();
        Team team = new Team(captain);
        match.addTeam(team);

        Team found = match.findTeamOf(captain);

        assertSame(team, found);
    }

    @Test
    void findTeamReturnsNullForUnknownPlayer() {
        Match match = new Match("test-match-1");
        match.addTeam(new Team(UUID.randomUUID()));

        Team found = match.findTeamOf(UUID.randomUUID());

        assertNull(found);
    }

    @Test
    void transitionFromLobbyToFarm() {
        Match match = new Match("test-match-1");

        match.transitionTo(new FarmPhase());

        assertEquals(MatchPhaseType.FARM, match.getCurrentPhase().getType());
    }

    @Test
    void transitionThroughAllPhases() {
        Match match = new Match("test-match-1");

        match.transitionTo(new FarmPhase());
        assertEquals(MatchPhaseType.FARM, match.getCurrentPhase().getType());

        match.transitionTo(new WaveBuildPhase());
        assertEquals(MatchPhaseType.WAVE_BUILD, match.getCurrentPhase().getType());

        match.transitionTo(new BattlePhase());
        assertEquals(MatchPhaseType.BATTLE, match.getCurrentPhase().getType());

        match.transitionTo(new FinishedPhase());
        assertEquals(MatchPhaseType.FINISHED, match.getCurrentPhase().getType());
    }

    @Test
    void cannotTransitionFromFinished() {
        Match match = new Match("test-match-1");
        match.transitionTo(new FinishedPhase());

        assertThrows(IllegalStateException.class, () -> match.transitionTo(new LobbyPhase()));
    }

    @Test
    void matchHasUniqueId() {
        Match match = new Match("foo-id");

        assertEquals("foo-id", match.getId());
    }

    @Test
    void matchHasSeed() {
        Match match = new Match("test-match-1", 12345L);
        assertEquals(12345L, match.getSeed());
    }

    @Test
    void canAssociateFarmWorld() {
        Match match = new Match("test-match-1", 0L);
        UUID captain = UUID.randomUUID();
        Team team = new Team(captain);
        match.addTeam(team);

        match.setFarmWorldName(team, "mab_farm_test-match-1_team-1");

        assertEquals("mab_farm_test-match-1_team-1", match.getFarmWorldName(team));
    }

    @Test
    void getFarmWorldNameReturnsNullIfNotSet() {
        Match match = new Match("test-match-1", 0L);
        Team team = new Team(UUID.randomUUID());
        match.addTeam(team);

        assertNull(match.getFarmWorldName(team));
    }

    @Test
    void backwardsCompatibleConstructorWorks() {
        Match match = new Match("test-match-1");
        assertNotNull(match.getId());
    }

    @Test
    void usesDefaultMaxTeamSize1() {
        Match m1 = new Match("test-match-1");
        Match m2 = new Match("test-match-2", 42L);
        assertEquals(1, m1.getMaxTeamSize());
        assertEquals(1, m2.getMaxTeamSize());
    }

    @Test
    void matchHasMaxTeamSize() {
        Match match = new Match("test-match-1", 0L, 4);
        assertEquals(4, match.getMaxTeamSize());
    }

    @Test
    void rejectsMaxTeamSizeBelow1() {
        assertThrows(IllegalArgumentException.class,
                () -> new Match("test-match-1", 0L, 0));
    }

    @Test
    void canStartReturnsFalseWhenOnlyOneTeamHasPlayers() {
        Match match = new Match("test-match-1", 0L, 2);
        match.addTeam(new Team(UUID.randomUUID(), 2));

        assertFalse(match.canStart());
    }

    @Test
    void canStartReturnsTrueWithTwoActiveTeams() {
        Match match = new Match("test-match-1", 0L, 2);
        match.addTeam(new Team(UUID.randomUUID(), 2));
        match.addTeam(new Team(UUID.randomUUID(), 2));

        assertTrue(match.canStart());
    }

    @Test
    void canStartReturnsFalseWhenSecondTeamWasDisbanded() {
        Match match = new Match("test-match-1", 0L, 1);
        Team team1 = new Team(UUID.randomUUID(), 1);
        Team team2 = new Team(UUID.randomUUID(), 1);
        match.addTeam(team1);
        match.addTeam(team2);
        team2.disband();

        assertFalse(match.canStart());
    }

    @Test
    void canStartReturnsTrueWithThreeActiveTeams() {
        Match match = new Match("test-match-1", 0L, 1);
        match.addTeam(new Team(UUID.randomUUID(), 1));
        match.addTeam(new Team(UUID.randomUUID(), 1));
        match.addTeam(new Team(UUID.randomUUID(), 1));

        assertTrue(match.canStart());
    }
}
