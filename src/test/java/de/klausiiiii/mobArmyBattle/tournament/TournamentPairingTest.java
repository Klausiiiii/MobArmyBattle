package de.klausiiiii.mobArmyBattle.tournament;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TournamentPairingTest {

    @Test
    void canCreatePairing() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        TournamentPairing p = new TournamentPairing(a, b);
        assertEquals(a, p.getCaptainA());
        assertEquals(b, p.getCaptainB());
        assertNull(p.getWinner());
        assertNull(p.getMatchId());
        assertFalse(p.isFinished());
    }

    @Test
    void rejectsSameCaptainTwice() {
        UUID a = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new TournamentPairing(a, a));
    }

    @Test
    void rejectsNullCaptain() {
        UUID a = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new TournamentPairing(null, a));
        assertThrows(IllegalArgumentException.class, () -> new TournamentPairing(a, null));
    }

    @Test
    void involvesChecksBothSides() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        TournamentPairing p = new TournamentPairing(a, b);
        assertTrue(p.involves(a));
        assertTrue(p.involves(b));
        assertFalse(p.involves(c));
    }

    @Test
    void getOpponentReturnsOtherSide() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        TournamentPairing p = new TournamentPairing(a, b);
        assertEquals(b, p.getOpponent(a));
        assertEquals(a, p.getOpponent(b));
    }

    @Test
    void getOpponentRejectsForeignCaptain() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        TournamentPairing p = new TournamentPairing(a, b);
        assertThrows(IllegalArgumentException.class, () -> p.getOpponent(c));
    }

    @Test
    void setWinnerMarksFinished() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        TournamentPairing p = new TournamentPairing(a, b);
        p.setWinner(a);
        assertEquals(a, p.getWinner());
        assertTrue(p.isFinished());
    }

    @Test
    void setWinnerRejectsForeignWinner() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        TournamentPairing p = new TournamentPairing(a, b);
        assertThrows(IllegalArgumentException.class, () -> p.setWinner(c));
    }

    @Test
    void setWinnerCannotBeOverridden() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        TournamentPairing p = new TournamentPairing(a, b);
        p.setWinner(a);
        assertThrows(IllegalStateException.class, () -> p.setWinner(b));
    }

    @Test
    void setMatchIdStoresValue() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        TournamentPairing p = new TournamentPairing(a, b);
        p.setMatchId("match-42");
        assertEquals("match-42", p.getMatchId());
    }
}
