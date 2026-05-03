package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final MatchManager matchManager;
    private final WorldManager worldManager;

    public PlayerConnectionListener(MatchManager matchManager, WorldManager worldManager) {
        this.matchManager = matchManager;
        this.worldManager = worldManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        if (matchManager.getMatchOf(event.getPlayer().getUniqueId()) != null) {
            return;
        }
        worldManager.teleportToLobby(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        matchManager.leaveMatch(event.getPlayer().getUniqueId());
    }
}
