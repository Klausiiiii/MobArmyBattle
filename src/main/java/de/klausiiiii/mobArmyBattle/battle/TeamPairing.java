package de.klausiiiii.mobArmyBattle.battle;

import de.klausiiiii.mobArmyBattle.match.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TeamPairing {

    private TeamPairing() {
    }

    public static Result pair(List<Team> teams) {
        if (teams == null || teams.size() < 2) {
            throw new IllegalArgumentException("Pairing braucht mindestens 2 Teams");
        }
        List<TeamPair> pairs = new ArrayList<>();
        List<Team> byes = new ArrayList<>();
        int i = 0;
        while (i + 1 < teams.size()) {
            pairs.add(new TeamPair(teams.get(i), teams.get(i + 1)));
            i += 2;
        }
        if (i < teams.size()) {
            byes.add(teams.get(i));
        }
        return new Result(pairs, byes);
    }

    public static final class Result {
        private final List<TeamPair> pairs;
        private final List<Team> byeTeams;

        Result(List<TeamPair> pairs, List<Team> byeTeams) {
            this.pairs = Collections.unmodifiableList(pairs);
            this.byeTeams = Collections.unmodifiableList(byeTeams);
        }

        public List<TeamPair> getPairs() {
            return pairs;
        }

        public List<Team> getByeTeams() {
            return byeTeams;
        }
    }
}
