package de.klausiiiii.mobArmyBattle.match.phase;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhase;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

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

        Set<UUID> allPlayers = new LinkedHashSet<>();
        for (Team team : match.getTeams()) {
            allPlayers.addAll(team.getMemberIds());
        }
        for (Match m : plugin.getMatchManager().getActiveMatches()) {
            if (m == match) {
                for (Team team : m.getTeams()) {
                    allPlayers.addAll(team.getMemberIds());
                }
            }
        }
        for (UUID id : allPlayers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                wm.teleportToLobby(p);
            }
            plugin.getMatchManager().forceRemove(id);
        }

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
