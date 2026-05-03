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
    private final MatchMode mode;
    private final List<Team> teams;
    private final Map<Team, String> farmWorldNames;
    private MatchPhase currentPhase;

    public Match(String id) {
        this(id, new Random().nextLong(), MatchMode.parse("1v1"));
    }

    public Match(String id, long seed) {
        this(id, seed, MatchMode.parse("1v1"));
    }

    public Match(String id, long seed, MatchMode mode) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Match-ID darf nicht leer sein");
        }
        if (mode == null) {
            throw new IllegalArgumentException("MatchMode darf nicht null sein");
        }
        this.id = id;
        this.seed = seed;
        this.mode = mode;
        this.teams = new ArrayList<>();
        this.farmWorldNames = new HashMap<>();
        this.currentPhase = new LobbyPhase();
        this.currentPhase.onEnter(this);
    }

    public String getId() {
        return id;
    }

    public long getSeed() {
        return seed;
    }

    public MatchMode getMode() {
        return mode;
    }

    public List<Team> getTeams() {
        return Collections.unmodifiableList(teams);
    }

    public boolean canStart() {
        if (teams.size() < mode.getTeamCount()) return false;
        for (Team t : teams) {
            if (t.isDisbanded() || t.size() == 0) return false;
        }
        return true;
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
