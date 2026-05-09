package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.spectator.SpectatorManager;
import de.klausiiiii.mobArmyBattle.tournament.TournamentManager;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerConnectionListener implements Listener {

    private final MatchManager matchManager;
    private final WorldManager worldManager;
    private final TournamentManager tournamentManager;
    private final SpectatorManager spectatorManager;

    public PlayerConnectionListener(MatchManager matchManager, WorldManager worldManager,
                                    TournamentManager tournamentManager) {
        this(matchManager, worldManager, tournamentManager, null);
    }

    public PlayerConnectionListener(MatchManager matchManager, WorldManager worldManager,
                                    TournamentManager tournamentManager,
                                    SpectatorManager spectatorManager) {
        this.matchManager = matchManager;
        this.worldManager = worldManager;
        this.tournamentManager = tournamentManager;
        this.spectatorManager = spectatorManager;
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
        UUID id = event.getPlayer().getUniqueId();
        if (spectatorManager != null && spectatorManager.isSpectating(id)) {
            spectatorManager.endSpectate(id);
            return;
        }
        if (tournamentManager != null) {
            tournamentManager.onCaptainQuit(id);
        }
        matchManager.leaveMatch(id);
    }
}
