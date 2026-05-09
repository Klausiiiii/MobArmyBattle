package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.tournament.TournamentManager;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerConnectionListener implements Listener {

    private final MatchManager matchManager;
    private final WorldManager worldManager;
    private final TournamentManager tournamentManager;

    public PlayerConnectionListener(MatchManager matchManager, WorldManager worldManager,
                                    TournamentManager tournamentManager) {
        this.matchManager = matchManager;
        this.worldManager = worldManager;
        this.tournamentManager = tournamentManager;
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
        if (tournamentManager != null) {
            tournamentManager.onCaptainQuit(event.getPlayer().getUniqueId());
        }
        matchManager.leaveMatch(event.getPlayer().getUniqueId());
    }
}
