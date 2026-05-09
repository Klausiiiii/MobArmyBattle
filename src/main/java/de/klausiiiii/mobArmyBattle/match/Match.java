package de.klausiiiii.mobArmyBattle.match;

import de.klausiiiii.mobArmyBattle.match.phase.LobbyPhase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class Match {
    private final String id;
    private final long seed;
    private final int maxTeamSize;
    private final List<Team> teams;
    private final Map<Team, String> farmWorldNames;
    private MatchPhase currentPhase;
    private long phaseStartedAt;

    public Match(String id) {
        this(id, new Random().nextLong(), 1);
    }

    public Match(String id, long seed) {
        this(id, seed, 1);
    }

    public Match(String id, long seed, int maxTeamSize) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Match-ID darf nicht leer sein");
        }
        if (maxTeamSize < 1) {
            throw new IllegalArgumentException("maxTeamSize muss >= 1 sein");
        }
        this.id = id;
        this.seed = seed;
        this.maxTeamSize = maxTeamSize;
        this.teams = new ArrayList<>();
        this.farmWorldNames = new HashMap<>();
        this.phaseStartedAt = System.currentTimeMillis();
        this.currentPhase = new LobbyPhase();
        this.currentPhase.onEnter(this);
    }

    public String getId() {
        return id;
    }

    public long getSeed() {
        return seed;
    }

    public int getMaxTeamSize() {
        return maxTeamSize;
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

    public void setFarmWorldName(Team team, String worldName) {
        farmWorldNames.put(team, worldName);
    }

    public String getFarmWorldName(Team team) {
        return farmWorldNames.get(team);
    }

    public Map<Team, String> getAllFarmWorldNames() {
        return Collections.unmodifiableMap(farmWorldNames);
    }

    public boolean canStart() {
        int activeTeams = 0;
        for (Team t : teams) {
            if (!t.isDisbanded() && t.size() > 0) {
                activeTeams++;
            }
        }
        return activeTeams >= 2;
    }

    public void transitionTo(MatchPhase newPhase) {
        if (currentPhase.getType() == MatchPhaseType.FINISHED) {
            throw new IllegalStateException("Match ist bereits beendet, kein Phase-Wechsel mehr möglich");
        }
        currentPhase.onExit(this);
        currentPhase = newPhase;
        phaseStartedAt = System.currentTimeMillis();
        currentPhase.onEnter(this);
    }

    public long getPhaseStartedAt() {
        return phaseStartedAt;
    }

    public void tick() {
        currentPhase.tick(this);
    }
}
