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
        return createMatch(captainId, MatchMode.parse("1v1"));
    }

    public Match createMatch(UUID captainId, MatchMode mode) {
        if (matchByPlayer.containsKey(captainId)) {
            throw new IllegalStateException("Spieler ist bereits in einem Match: " + captainId);
        }
        String id = "match-" + matchIdCounter.getAndIncrement();
        long seed = new Random().nextLong();
        Match match = new Match(id, seed, mode);

        Team firstTeam = new Team(captainId, mode.getMaxSizeOfTeam(0));
        match.addTeam(firstTeam);

        for (int i = 1; i < mode.getTeamCount(); i++) {
            match.addTeam(Team.empty(mode.getMaxSizeOfTeam(i)));
        }

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
        joinMatch(playerId, captainId, target);
    }

    public void joinMatch(UUID playerId, UUID captainId, int teamIndex) {
        if (matchByPlayer.containsKey(playerId)) {
            throw new IllegalStateException("Spieler ist bereits in einem Match: " + playerId);
        }
        Match match = matchByPlayer.get(captainId);
        if (match == null) {
            throw new IllegalArgumentException("Captain hat kein Match: " + captainId);
        }
        if (teamIndex < 0 || teamIndex >= match.getTeams().size()) {
            throw new IllegalArgumentException("Ungültiger Team-Index: " + teamIndex);
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
        int bestIdx = 0;
        int bestSize = Integer.MAX_VALUE;
        List<Team> teams = match.getTeams();
        for (int i = 0; i < teams.size(); i++) {
            Team t = teams.get(i);
            if (t.isFull()) continue;
            int size = (t.getCaptainId() == null) ? 0 : t.size();
            if (size < bestSize) {
                bestSize = size;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    public void leaveMatch(UUID playerId) {
        Match match = matchByPlayer.get(playerId);
        if (match == null) {
            return;
        }
        Team team = match.findTeamOf(playerId);
        boolean wasCaptain = team.getCaptainId().equals(playerId);

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
        } else {
            team.removeMember(playerId);
        }
        matchByPlayer.remove(playerId);

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
