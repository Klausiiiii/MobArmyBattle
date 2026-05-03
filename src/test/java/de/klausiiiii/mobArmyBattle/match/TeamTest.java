package de.klausiiiii.mobArmyBattle.match;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TeamTest {

    @Test
    void newTeamHasCaptainAsOnlyMember() {
        UUID captain = UUID.randomUUID();
        Team team = new Team(captain);

        assertEquals(captain, team.getCaptainId());
        assertEquals(1, team.getMemberIds().size());
        assertTrue(team.getMemberIds().contains(captain));
    }

    @Test
    void canAddMember() {
        UUID captain = UUID.randomUUID();
        UUID newMember = UUID.randomUUID();
        Team team = new Team(captain);

        team.addMember(newMember);

        assertTrue(team.getMemberIds().contains(newMember));
        assertEquals(2, team.getMemberIds().size());
    }

    @Test
    void cannotAddSameMemberTwice() {
        UUID captain = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Team team = new Team(captain);
        team.addMember(member);

        assertThrows(IllegalArgumentException.class, () -> team.addMember(member));
    }

    @Test
    void canRemoveMember() {
        UUID captain = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Team team = new Team(captain);
        team.addMember(member);

        team.removeMember(member);

        assertFalse(team.getMemberIds().contains(member));
        assertEquals(1, team.getMemberIds().size());
    }

    @Test
    void cannotRemoveCaptainDirectly() {
        UUID captain = UUID.randomUUID();
        Team team = new Team(captain);

        assertThrows(IllegalStateException.class, () -> team.removeMember(captain));
    }

    @Test
    void canPromoteNewCaptain() {
        UUID oldCaptain = UUID.randomUUID();
        UUID newCaptain = UUID.randomUUID();
        Team team = new Team(oldCaptain);
        team.addMember(newCaptain);

        team.promoteToCaptain(newCaptain);

        assertEquals(newCaptain, team.getCaptainId());
        assertTrue(team.getMemberIds().contains(oldCaptain));
        assertTrue(team.getMemberIds().contains(newCaptain));
    }

    @Test
    void cannotPromoteNonMember() {
        UUID captain = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        Team team = new Team(captain);

        assertThrows(IllegalArgumentException.class, () -> team.promoteToCaptain(outsider));
    }

    @Test
    void hasMemberReturnsTrueForCaptain() {
        UUID captain = UUID.randomUUID();
        Team team = new Team(captain);

        assertTrue(team.hasMember(captain));
    }

    @Test
    void disbandClearsCaptainAndMembers() {
        UUID captain = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Team team = new Team(captain);
        team.addMember(member);

        team.disband();

        assertNull(team.getCaptainId());
        assertEquals(0, team.size());
        assertTrue(team.isDisbanded());
    }

    @Test
    void newTeamIsNotDisbanded() {
        Team team = new Team(UUID.randomUUID());

        assertFalse(team.isDisbanded());
    }

    @Test
    void newTeamHasEmptyPool() {
        Team team = new Team(UUID.randomUUID());
        assertNotNull(team.getPool());
        assertEquals(0, team.getPool().totalCount());
    }
}
