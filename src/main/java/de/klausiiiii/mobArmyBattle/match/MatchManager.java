package de.klausiiiii.mobArmyBattle.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class MatchManager {
    private final Map<String, Match> matchesById = new HashMap<>();
    private final Map<UUID, Match> matchByPlayer = new HashMap<>();
    private final AtomicLong matchIdCounter = new AtomicLong(1);

    public Match createMatch(UUID captainId) {
        return createMatch(captainId, 1);
    }

    public Match createMatch(UUID captainId, int maxTeamSize) {
        if (matchByPlayer.containsKey(captainId)) {
            throw new IllegalStateException("Spieler ist bereits in einem Match: " + captainId);
        }
        if (maxTeamSize < 1) {
            throw new IllegalArgumentException("maxTeamSize muss >= 1 sein");
        }
        String id = "match-" + matchIdCounter.getAndIncrement();
        long seed = new Random().nextLong();
        Match match = new Match(id, seed, maxTeamSize);
        match.addTeam(new Team(captainId, maxTeamSize));
        matchesById.put(id, match);
        matchByPlayer.put(captainId, match);
        return match;
    }

    public void joinMatch(UUID playerId, UUID captainId) {
        if (matchByPlayer.containsKey(playerId)) {
            throw new IllegalStateException("Spieler ist bereits in einem Match: " + playerId);
        }
        Match match = matchByPlayer.get(captainId);
        if (match == null) {
            throw new IllegalArgumentException("Captain hat kein Match: " + captainId);
        }
        int target = pickAutoBalanceTeam(match);
        joinAt(match, playerId, target);
    }

    public void joinMatch(UUID playerId, UUID captainId, int teamIndex) {
        if (matchByPlayer.containsKey(playerId)) {
            throw new IllegalStateException("Spieler ist bereits in einem Match: " + playerId);
        }
        Match match = matchByPlayer.get(captainId);
        if (match == null) {
            throw new IllegalArgumentException("Captain hat kein Match: " + captainId);
        }
        int teamCount = match.getTeams().size();
        if (teamIndex < 0 || teamIndex > teamCount) {
            throw new IllegalArgumentException("Ungültiger Team-Index: " + teamIndex);
        }
        joinAt(match, playerId, teamIndex);
    }

    private void joinAt(Match match, UUID playerId, int teamIndex) {
        if (teamIndex == match.getTeams().size()) {
            // Create a new team with the joiner as its captain
            match.addTeam(new Team(playerId, match.getMaxTeamSize()));
            matchByPlayer.put(playerId, match);
            return;
        }
        Team target = match.getTeams().get(teamIndex);
        if (target.isFull()) {
            throw new IllegalStateException("Team " + (teamIndex + 1) + " ist voll");
        }
        if (target.isDisbanded() || target.getCaptainId() == null) {
            target.promoteEmpty(playerId);
        } else {
            target.addMember(playerId);
        }
        matchByPlayer.put(playerId, match);
    }

    private int pickAutoBalanceTeam(Match match) {
        List<Team> teams = match.getTeams();
        int bestIdx = -1;
        int bestSize = Integer.MAX_VALUE;
        for (int i = 0; i < teams.size(); i++) {
            Team t = teams.get(i);
            if (t.isFull()) continue;
            int size = (t.getCaptainId() == null) ? 0 : t.size();
            if (size < bestSize) {
                bestSize = size;
                bestIdx = i;
            }
        }
        if (bestIdx == -1) {
            // All teams are full -> new team
            return teams.size();
        }
        return bestIdx;
    }

    public void leaveMatch(UUID playerId) {
        Match match = matchByPlayer.get(playerId);
        if (match == null) {
            return;
        }
        Team team = match.findTeamOf(playerId);
        matchByPlayer.remove(playerId);

        if (team != null) {
            UUID captainId = team.getCaptainId();
            boolean wasCaptain = captainId != null && captainId.equals(playerId);

            if (wasCaptain) {
                UUID nextCaptain = team.getMemberIds().stream()
                        .filter(id -> !id.equals(playerId))
                        .findFirst()
                        .orElse(null);
                if (nextCaptain != null) {
                    team.promoteToCaptain(nextCaptain);
                    team.removeMember(playerId);
                } else {
                    team.disband();
                }
            } else if (team.hasMember(playerId)) {
                team.removeMember(playerId);
            }
        }

        boolean stillHasPlayers = matchByPlayer.values().stream()
                .anyMatch(m -> m == match);
        if (!stillHasPlayers) {
            matchesById.remove(match.getId());
        }
    }

    /**
     * Removes a player from match-tracking without going through team-cleanup.
     * Used during forced cleanup (e.g. FinishedPhase) where the team state is
     * being torn down by the caller.
     */
    public void forceRemove(UUID playerId) {
        Match match = matchByPlayer.remove(playerId);
        if (match == null) return;
        boolean stillHasPlayers = matchByPlayer.values().stream()
                .anyMatch(m -> m == match);
        if (!stillHasPlayers) {
            matchesById.remove(match.getId());
        }
    }

    public Match getMatchOf(UUID playerId) {
        return matchByPlayer.get(playerId);
    }

    public Match getMatchById(String matchId) {
        return matchesById.get(matchId);
    }

    public List<Match> getActiveMatches() {
        return Collections.unmodifiableList(new ArrayList<>(matchesById.values()));
    }

    public Set<UUID> getCaptainIds() {
        Set<UUID> captains = new LinkedHashSet<>();
        for (Match match : matchesById.values()) {
            for (Team team : match.getTeams()) {
                UUID cap = team.getCaptainId();
                if (cap != null) {
                    captains.add(cap);
                }
            }
        }
        return Collections.unmodifiableSet(captains);
    }

    public void tickAll() {
        for (Match match : matchesById.values()) {
            match.tick();
        }
    }
}
