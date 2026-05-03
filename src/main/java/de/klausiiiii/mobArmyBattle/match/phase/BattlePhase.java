package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;

public class BattlePhase implements MatchPhase {
    @Override
    public MatchPhaseType getType() {
        return MatchPhaseType.BATTLE;
    }

    @Override
    public void onEnter(Match match) {
    }

    @Override
    public void onExit(Match match) {
    }

    @Override
    public void tick(Match match) {
    }
}
