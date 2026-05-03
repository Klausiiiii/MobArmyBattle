package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class FinishedPhase implements MatchPhase {

    private final MobArmyBattle plugin;

    public FinishedPhase() {
        this(null);
    }

    public FinishedPhase(MobArmyBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public MatchPhaseType getType() {
        return MatchPhaseType.FINISHED;
    }

    @Override
    public void onEnter(Match match) {
        if (plugin == null) {
            return;
        }
        WorldManager wm = plugin.getWorldManager();
        for (String worldName : match.getAllFarmWorldNames().values()) {
            World w = Bukkit.getWorld(worldName);
            if (w != null) {
                wm.deleteWorld(w);
            }
        }
    }

    @Override
    public void onExit(Match match) {
    }

    @Override
    public void tick(Match match) {
    }
}
