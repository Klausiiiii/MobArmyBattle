package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;

public class WaveBuildPhase implements MatchPhase {
    @Override
    public MatchPhaseType getType() {
        return MatchPhaseType.WAVE_BUILD;
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
