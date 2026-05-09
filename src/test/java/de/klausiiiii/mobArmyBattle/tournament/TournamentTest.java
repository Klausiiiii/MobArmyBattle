package de.klausiiiii.mobArmyBattle.tournament;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TournamentTest {

    @Test
    void newTournamentIsRegistering() {
        Tournament t = new Tournament("t1", "Cup", UUID.randomUUID());
        assertEquals(Tournament.Status.REGISTERING, t.getStatus());
        assertNull(t.getWinner());
        assertTrue(t.getRegisteredCaptains().isEmpty());
        assertTrue(t.getRounds().isEmpty());
    }

    @Test
    void rejectsInvalidConstruction() {
        UUID master = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new Tournament(null, "x", master));
        assertThrows(IllegalArgumentException.class, () -> new Tournament("", "x", master));
        assertThrows(IllegalArgumentException.class, () -> new Tournament("t", null, master));
        assertThrows(IllegalArgumentException.class, () -> new Tournament("t", "", master));
        assertThrows(IllegalArgumentException.class, () -> new Tournament("t", "x", null));
    }

    @Test
    void registerAddsCaptain() {
        Tournament t = new Tournament("t1", "Cup", UUID.randomUUID());
        UUID a = UUID.randomUUID();
        t.register(a);
        assertEquals(1, t.getRegisteredCaptains().size());
        assertTrue(t.getRegisteredCaptains().contains(a));
    }

    @Test
    void cannotRegisterTwice() {
        Tournament t = new Tournament("t1", "Cup", UUID.randomUUID());
        UUID a = UUID.randomUUID();
        t.register(a);
        assertThrows(IllegalStateException.class, () -> t.register(a));
    }

    @Test
    void unregisterRemovesCaptain() {
        Tournament t = new Tournament("t1", "Cup", UUID.randomUUID());
        UUID a = UUID.randomUUID();
        t.register(a);
        t.unregister(a);
        assertTrue(t.getRegisteredCaptains().isEmpty());
    }

    @Test
    void cannotStartWithLessThanTwoCaptains() {
        Tournament t = new Tournament("t1", "Cup", UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> t.start(new Random(0)));
        t.register(UUID.randomUUID());
        assertThrows(IllegalStateException.class, () -> t.start(new Random(0)));
    }

    @Test
    void startWithFourCaptainsCreatesTwoPairings() {
        Tournament t = newTournamentWithCaptains(4);
        t.start(new Random(0));
        assertEquals(Tournament.Status.RUNNING, t.getStatus());
        assertEquals(1, t.getRounds().size());
        TournamentRound round = t.getCurrentRound();
        assertEquals(2, round.getPairings().size());
        assertNull(round.getByeCaptain());
    }

    @Test
    void startWithFiveCaptainsCreatesTwoPairingsPlusBye() {
        Tournament t = newTournamentWithCaptains(5);
        t.start(new Random(0));
        TournamentRound round = t.getCurrentRound();
        assertEquals(2, round.getPairings().size());
        assertNotNull(round.getByeCaptain());
    }

    @Test
    void cannotRegisterAfterStart() {
        Tournament t = newTournamentWithCaptains(2);
        t.start(new Random(0));
        assertThrows(IllegalStateException.class, () -> t.register(UUID.randomUUID()));
    }

    @Test
    void recordPairingWinnerStoresWinner() {
        Tournament t = newTournamentWithCaptains(2);
        t.start(new Random(0));
        TournamentRound round = t.getCurrentRound();
        TournamentPairing p = round.getPairings().get(0);
        p.setMatchId("match-1");
        t.recordPairingWinner("match-1", p.getCaptainA());
        assertEquals(p.getCaptainA(), p.getWinner());
    }

    @Test
    void advanceToFinishedWithSingleSurvivor() {
        Tournament t = newTournamentWithCaptains(2);
        t.start(new Random(0));
        TournamentPairing p = t.getCurrentRound().getPairings().get(0);
        p.setMatchId("m1");
        t.recordPairingWinner("m1", p.getCaptainA());
        t.advanceToNextRound(new Random(0));
        assertEquals(Tournament.Status.FINISHED, t.getStatus());
        assertEquals(p.getCaptainA(), t.getWinner());
    }

    @Test
    void advanceCreatesNextRoundWithSurvivors() {
        Tournament t = newTournamentWithCaptains(4);
        t.start(new Random(0));
        TournamentRound round1 = t.getCurrentRound();
        UUID w1 = round1.getPairings().get(0).getCaptainA();
        UUID w2 = round1.getPairings().get(1).getCaptainA();
        round1.getPairings().get(0).setMatchId("m1");
        round1.getPairings().get(1).setMatchId("m2");
        t.recordPairingWinner("m1", w1);
        t.recordPairingWinner("m2", w2);
        t.advanceToNextRound(new Random(0));
        assertEquals(Tournament.Status.RUNNING, t.getStatus());
        assertEquals(2, t.getRounds().size());
        assertEquals(1, t.getCurrentRound().getPairings().size());
    }

    @Test
    void byeCaptainAdvancesToNextRound() {
        Tournament t = newTournamentWithCaptains(3);
        t.start(new Random(0));
        TournamentRound round1 = t.getCurrentRound();
        UUID winner = round1.getPairings().get(0).getCaptainA();
        UUID bye = round1.getByeCaptain();
        round1.getPairings().get(0).setMatchId("m1");
        t.recordPairingWinner("m1", winner);
        t.advanceToNextRound(new Random(0));
        // Round 2 should have winner vs bye (or vice versa)
        TournamentRound round2 = t.getCurrentRound();
        assertEquals(1, round2.getPairings().size());
        assertTrue(round2.getPairings().get(0).involves(winner));
        assertTrue(round2.getPairings().get(0).involves(bye));
    }

    @Test
    void cannotAdvanceIncompleteRound() {
        Tournament t = newTournamentWithCaptains(4);
        t.start(new Random(0));
        assertThrows(IllegalStateException.class, () -> t.advanceToNextRound(new Random(0)));
    }

    @Test
    void cannotRecordWinnerForUnknownMatchId() {
        Tournament t = newTournamentWithCaptains(2);
        t.start(new Random(0));
        UUID anyone = t.getCurrentRound().getPairings().get(0).getCaptainA();
        assertThrows(IllegalArgumentException.class, () -> t.recordPairingWinner("ghost-match", anyone));
    }

    private static Tournament newTournamentWithCaptains(int n) {
        Tournament t = new Tournament("t1", "Cup", UUID.randomUUID());
        for (int i = 0; i < n; i++) t.register(UUID.randomUUID());
        return t;
    }
}
