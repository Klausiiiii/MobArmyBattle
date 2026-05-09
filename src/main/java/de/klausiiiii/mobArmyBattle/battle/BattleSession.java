package de.klausiiiii.mobArmyBattle.battle;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.Team;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BattleSession {

    public static class TeamState {
        public final Team team;
        public final Team opponent;
        public final World arena;
        public final List<Location> spawnPoints;
        public final BattleStats stats;
        public int currentWaveNumber = 0;
        public final Set<UUID> aliveLivingMobs = new HashSet<>();
        public final Set<UUID> downedPlayers = new HashSet<>();

        public TeamState(Team team, Team opponent, World arena, List<Location> spawnPoints) {
            this.team = team;
            this.opponent = opponent;
            this.arena = arena;
            this.spawnPoints = spawnPoints;
            this.stats = new BattleStats();
        }
    }

    private final Match match;
    private final TeamPair pair;
    private final TeamState stateA;
    private final TeamState stateB;
    private final long startTimeMs;
    private boolean concluded = false;

    public BattleSession(Match match, TeamPair pair,
                         World arenaA, List<Location> spawnsA,
                         World arenaB, List<Location> spawnsB) {
        this.match = match;
        this.pair = pair;
        this.stateA = new TeamState(pair.getTeamA(), pair.getTeamB(), arenaA, spawnsA);
        this.stateB = new TeamState(pair.getTeamB(), pair.getTeamA(), arenaB, spawnsB);
        this.startTimeMs = System.currentTimeMillis();
    }

    public Match getMatch() { return match; }
    public TeamPair getPair() { return pair; }
    public TeamState getStateA() { return stateA; }
    public TeamState getStateB() { return stateB; }

    public TeamState getStateOf(Team team) {
        if (team == stateA.team) return stateA;
        if (team == stateB.team) return stateB;
        return null;
    }

    public TeamState getStateByPlayerUUID(UUID playerUUID) {
        if (stateA.team.hasMember(playerUUID)) return stateA;
        if (stateB.team.hasMember(playerUUID)) return stateB;
        return null;
    }

    public boolean isConcluded() { return concluded; }
    public void markConcluded() { concluded = true; }

    public long elapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }
}
