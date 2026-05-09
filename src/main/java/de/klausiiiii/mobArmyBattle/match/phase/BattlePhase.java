package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;

public class BattlePhase implements MatchPhase {

    private final MobArmyBattle plugin;

    public BattlePhase() { this(null); }

    public BattlePhase(MobArmyBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public MatchPhaseType getType() { return MatchPhaseType.BATTLE; }

    @Override
    public void onEnter(Match match) {
        if (plugin == null) return;
        plugin.getBattleManager().startBattlesFor(match);
    }

    @Override
    public void onExit(Match match) {
        if (plugin == null) return;
        plugin.getBattleManager().cleanup(match);
    }

    @Override
    public void tick(Match match) {}
}
