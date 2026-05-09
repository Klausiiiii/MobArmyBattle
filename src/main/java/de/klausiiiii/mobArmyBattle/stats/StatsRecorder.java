package de.klausiiiii.mobArmyBattle.stats;

import de.klausiiiii.mobArmyBattle.battle.TeamOutcome;
import de.klausiiiii.mobArmyBattle.match.Match;

import java.util.List;
import java.util.UUID;

public class StatsRecorder {

    private final StatsRepository repository;

    public StatsRecorder(StatsRepository repository) {
        this.repository = repository;
    }

    public void onMatchCompleted(Match match, List<TeamOutcome> outcomes) {
        for (TeamOutcome outcome : outcomes) {
            int kills = outcome.mobKills();
            int teamSize = Math.max(1, outcome.memberIds().size());
            int killsPerMember = kills / teamSize;
            for (UUID memberId : outcome.memberIds()) {
                repository.recordMatchResult(memberId, outcome.winner(), killsPerMember);
            }
        }
    }
}
