package de.klausiiiii.mobArmyBattle.stats;

import java.util.UUID;

public final class PlayerStats {

    private final UUID playerId;
    private final int matchesTotal;
    private final int matchesWon;
    private final int mobKillsTotal;

    public PlayerStats(UUID playerId, int matchesTotal, int matchesWon, int mobKillsTotal) {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId darf nicht null sein");
        }
        if (matchesTotal < 0 || matchesWon < 0 || mobKillsTotal < 0) {
            throw new IllegalArgumentException("Werte dürfen nicht negativ sein");
        }
        if (matchesWon > matchesTotal) {
            throw new IllegalArgumentException("matchesWon darf nicht > matchesTotal sein");
        }
        this.playerId = playerId;
        this.matchesTotal = matchesTotal;
        this.matchesWon = matchesWon;
        this.mobKillsTotal = mobKillsTotal;
    }

    public static PlayerStats empty(UUID playerId) {
        return new PlayerStats(playerId, 0, 0, 0);
    }

    public UUID getPlayerId() { return playerId; }
    public int getMatchesTotal() { return matchesTotal; }
    public int getMatchesWon() { return matchesWon; }
    public int getMatchesLost() { return matchesTotal - matchesWon; }
    public int getMobKillsTotal() { return mobKillsTotal; }

    public double getWinRate() {
        return matchesTotal == 0 ? 0.0 : (double) matchesWon / matchesTotal;
    }

    public PlayerStats addMatch(boolean won, int mobKills) {
        if (mobKills < 0) {
            throw new IllegalArgumentException("mobKills darf nicht negativ sein");
        }
        return new PlayerStats(
                playerId,
                matchesTotal + 1,
                matchesWon + (won ? 1 : 0),
                mobKillsTotal + mobKills);
    }
}
