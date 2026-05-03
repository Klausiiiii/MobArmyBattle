package de.klausiiiii.mobArmyBattle.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class MatchManager {
    private final Map<String, Match> matchesById = new HashMap<>();
    private final Map<UUID, Match> matchByPlayer = new HashMap<>();
    private final AtomicLong matchIdCounter = new AtomicLong(1);

    public Match createMatch(UUID captainId) {
        if (matchByPlayer.containsKey(captainId)) {
            throw new IllegalStateException("Spieler ist bereits in einem Match: " + captainId);
        }
        String id = "match-" + matchIdCounter.getAndIncrement();
        Match match = new Match(id);
        match.addTeam(new Team(captainId));
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
        Team captainsTeam = match.findTeamOf(captainId);
        if (captainsTeam == null || !captainsTeam.getCaptainId().equals(captainId)) {
            throw new IllegalArgumentException("Spieler " + captainId + " ist nicht Captain");
        }
        captainsTeam.addMember(playerId);
        matchByPlayer.put(playerId, match);
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

    public void tickAll() {
        for (Match match : matchesById.values()) {
            match.tick();
        }
    }
}
