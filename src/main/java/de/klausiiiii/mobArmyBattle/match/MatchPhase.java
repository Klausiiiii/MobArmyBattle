package de.klausiiiii.mobArmyBattle.match;

public interface MatchPhase {
    MatchPhaseType getType();
    void onEnter(Match match);
    void onExit(Match match);
    void tick(Match match);
}
