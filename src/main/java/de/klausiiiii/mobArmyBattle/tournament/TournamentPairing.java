package de.klausiiiii.mobArmyBattle.tournament;

import java.util.UUID;

public class TournamentPairing {

    private final UUID captainA;
    private final UUID captainB;
    private String matchId;
    private UUID winner;

    public TournamentPairing(UUID captainA, UUID captainB) {
        if (captainA == null || captainB == null) {
            throw new IllegalArgumentException("Captain darf nicht null sein");
        }
        if (captainA.equals(captainB)) {
            throw new IllegalArgumentException("Captains müssen unterschiedlich sein");
        }
        this.captainA = captainA;
        this.captainB = captainB;
    }

    public UUID getCaptainA() { return captainA; }
    public UUID getCaptainB() { return captainB; }
    public String getMatchId() { return matchId; }
    public UUID getWinner() { return winner; }

    public boolean isFinished() { return winner != null; }
    public boolean involves(UUID captainId) {
        return captainA.equals(captainId) || captainB.equals(captainId);
    }

    public UUID getOpponent(UUID captainId) {
        if (captainA.equals(captainId)) return captainB;
        if (captainB.equals(captainId)) return captainA;
        throw new IllegalArgumentException("Captain ist nicht in diesem Pairing: " + captainId);
    }

    public void setMatchId(String matchId) {
        if (matchId == null || matchId.isBlank()) {
            throw new IllegalArgumentException("matchId darf nicht leer sein");
        }
        this.matchId = matchId;
    }

    public void setWinner(UUID winnerId) {
        if (winnerId == null) {
            throw new IllegalArgumentException("winnerId darf nicht null sein");
        }
        if (!involves(winnerId)) {
            throw new IllegalArgumentException("Winner ist nicht Teil des Pairings: " + winnerId);
        }
        if (this.winner != null) {
            throw new IllegalStateException("Pairing hat bereits einen Sieger");
        }
        this.winner = winnerId;
    }
}
