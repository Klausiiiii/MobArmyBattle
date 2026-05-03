package de.klausiiiii.mobArmyBattle.match;

import de.klausiiiii.mobArmyBattle.match.phase.LobbyPhase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Match {
    private final String id;
    private final List<Team> teams;
    private MatchPhase currentPhase;

    public Match(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Match-ID darf nicht leer sein");
        }
        this.id = id;
        this.teams = new ArrayList<>();
        this.currentPhase = new LobbyPhase();
        this.currentPhase.onEnter(this);
    }

    public String getId() {
        return id;
    }

    public List<Team> getTeams() {
        return Collections.unmodifiableList(teams);
    }

    public MatchPhase getCurrentPhase() {
        return currentPhase;
    }

    public void addTeam(Team team) {
        teams.add(team);
    }

    public Team findTeamOf(UUID playerId) {
        for (Team team : teams) {
            if (team.hasMember(playerId)) {
                return team;
            }
        }
        return null;
    }

    public void transitionTo(MatchPhase newPhase) {
        if (currentPhase.getType() == MatchPhaseType.FINISHED) {
            throw new IllegalStateException("Match ist bereits beendet, kein Phase-Wechsel mehr möglich");
        }
        currentPhase.onExit(this);
        currentPhase = newPhase;
        currentPhase.onEnter(this);
    }

    public void tick() {
        currentPhase.tick(this);
    }
}
