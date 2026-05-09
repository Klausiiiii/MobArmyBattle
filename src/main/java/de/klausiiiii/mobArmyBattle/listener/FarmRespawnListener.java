package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public class FarmRespawnListener implements Listener {

    private final MatchManager matchManager;

    public FarmRespawnListener(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) return;
        if (match.getCurrentPhase().getType() != MatchPhaseType.FARM) return;

        Team team = match.findTeamOf(player.getUniqueId());
        if (team == null) return;

        String farmWorldName = match.getFarmWorldName(team);
        if (farmWorldName == null) return;

        World farmWorld = Bukkit.getWorld(farmWorldName);
        if (farmWorld == null) return;

        Location anchor = farmWorld.getSpawnLocation();
        Location safe = WorldManager.safeSpawnAt(farmWorld, anchor.getBlockX(), anchor.getBlockZ());
        event.setRespawnLocation(safe);
    }
}
