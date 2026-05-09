package de.klausiiiii.mobArmyBattle.tournament;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class TournamentRound {

    private final int number;
    private final List<TournamentPairing> pairings;
    private final UUID byeCaptain;

    public TournamentRound(int number, List<TournamentPairing> pairings, UUID byeCaptain) {
        if (number < 1) {
            throw new IllegalArgumentException("Round-Nummer muss >= 1 sein");
        }
        if (pairings == null) {
            throw new IllegalArgumentException("Pairings dürfen nicht null sein");
        }
        this.number = number;
        this.pairings = new ArrayList<>(pairings);
        this.byeCaptain = byeCaptain;
    }

    public int getNumber() { return number; }
    public List<TournamentPairing> getPairings() { return Collections.unmodifiableList(pairings); }
    public UUID getByeCaptain() { return byeCaptain; }

    public TournamentPairing findPairingByMatchId(String matchId) {
        if (matchId == null) return null;
        for (TournamentPairing p : pairings) {
            if (matchId.equals(p.getMatchId())) return p;
        }
        return null;
    }

    public TournamentPairing findPairingForCaptain(UUID captainId) {
        for (TournamentPairing p : pairings) {
            if (p.involves(captainId)) return p;
        }
        return null;
    }

    public boolean isComplete() {
        for (TournamentPairing p : pairings) {
            if (!p.isFinished()) return false;
        }
        return true;
    }

    public List<UUID> getWinners() {
        List<UUID> winners = new ArrayList<>();
        for (TournamentPairing p : pairings) {
            if (p.getWinner() != null) winners.add(p.getWinner());
        }
        if (byeCaptain != null) winners.add(byeCaptain);
        return winners;
    }
}
