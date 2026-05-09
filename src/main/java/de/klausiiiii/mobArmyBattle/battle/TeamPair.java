package de.klausiiiii.mobArmyBattle.battle;

import de.klausiiiii.mobArmyBattle.match.Team;

public final class TeamPair {

    private final Team teamA;
    private final Team teamB;

    public TeamPair(Team teamA, Team teamB) {
        if (teamA == null || teamB == null) {
            throw new IllegalArgumentException("Beide Teams müssen non-null sein");
        }
        if (teamA == teamB) {
            throw new IllegalArgumentException("Ein Team kann nicht gegen sich selbst kämpfen");
        }
        this.teamA = teamA;
        this.teamB = teamB;
    }

    public Team getTeamA() {
        return teamA;
    }

    public Team getTeamB() {
        return teamB;
    }

    public boolean contains(Team team) {
        return team == teamA || team == teamB;
    }

    public Team other(Team team) {
        if (team == teamA) return teamB;
        if (team == teamB) return teamA;
        throw new IllegalArgumentException("Team ist nicht in diesem Pair");
    }
}
