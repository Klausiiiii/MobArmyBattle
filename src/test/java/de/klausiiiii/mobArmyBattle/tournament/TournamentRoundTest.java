package de.klausiiiii.mobArmyBattle.tournament;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TournamentRoundTest {

    @Test
    void canCreateRound() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        TournamentPairing p = new TournamentPairing(a, b);
        TournamentRound round = new TournamentRound(1, List.of(p), null);
        assertEquals(1, round.getNumber());
        assertEquals(1, round.getPairings().size());
        assertNull(round.getByeCaptain());
    }

    @Test
    void rejectsZeroOrNegativeNumber() {
        assertThrows(IllegalArgumentException.class, () -> new TournamentRound(0, List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new TournamentRound(-1, List.of(), null));
    }

    @Test
    void incompleteIfAnyPairingUnfinished() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        TournamentPairing p1 = new TournamentPairing(a, b);
        TournamentPairing p2 = new TournamentPairing(c, d);
        p1.setWinner(a);
        TournamentRound round = new TournamentRound(1, List.of(p1, p2), null);
        assertFalse(round.isComplete());
    }

    @Test
    void completeWhenAllPairingsFinished() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        TournamentPairing p1 = new TournamentPairing(a, b);
        TournamentPairing p2 = new TournamentPairing(c, d);
        p1.setWinner(a);
        p2.setWinner(c);
        TournamentRound round = new TournamentRound(1, List.of(p1, p2), null);
        assertTrue(round.isComplete());
    }

    @Test
    void winnersIncludesByeCaptain() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID bye = UUID.randomUUID();
        TournamentPairing p = new TournamentPairing(a, b);
        p.setWinner(a);
        TournamentRound round = new TournamentRound(1, List.of(p), bye);
        List<UUID> winners = round.getWinners();
        assertEquals(2, winners.size());
        assertTrue(winners.contains(a));
        assertTrue(winners.contains(bye));
    }

    @Test
    void findPairingByMatchId() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        TournamentPairing p = new TournamentPairing(a, b);
        p.setMatchId("match-7");
        TournamentRound round = new TournamentRound(1, List.of(p), null);
        assertSame(p, round.findPairingByMatchId("match-7"));
        assertNull(round.findPairingByMatchId("match-9"));
    }

    @Test
    void findPairingForCaptain() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        TournamentPairing p = new TournamentPairing(a, b);
        TournamentRound round = new TournamentRound(1, List.of(p), null);
        assertSame(p, round.findPairingForCaptain(a));
        assertSame(p, round.findPairingForCaptain(b));
        assertNull(round.findPairingForCaptain(c));
    }
}
