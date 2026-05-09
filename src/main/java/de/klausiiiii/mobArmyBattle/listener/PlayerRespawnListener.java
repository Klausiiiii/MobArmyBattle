package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
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

public class PlayerRespawnListener implements Listener {

    private final MobArmyBattle plugin;
    private final MatchManager matchManager;

    public PlayerRespawnListener(MobArmyBattle plugin, MatchManager matchManager) {
        this.plugin = plugin;
        this.matchManager = matchManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String deathWorldName = player.getWorld().getName();

        if (deathWorldName.startsWith(WorldManager.ARENA_WORLD_PREFIX)) {
            return;
        }

        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match != null && match.getCurrentPhase().getType() == MatchPhaseType.FARM) {
            Team team = match.findTeamOf(player.getUniqueId());
            String farmName = team != null ? match.getFarmWorldName(team) : null;
            if (farmName != null) {
                World farmWorld = Bukkit.getWorld(farmName);
                if (farmWorld != null) {
                    Location anchor = farmWorld.getSpawnLocation();
                    event.setRespawnLocation(WorldManager.safeSpawnAt(
                            farmWorld, anchor.getBlockX(), anchor.getBlockZ()));
                    return;
                }
            }
        }

        World lobby = plugin.getWorldManager().getOrCreateLobbyWorld();
        event.setRespawnLocation(lobby.getSpawnLocation().clone().add(0.5, 0, 0.5));
    }
}
