package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.config.ReconnectGraceManager;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.spectator.SpectatorManager;
import de.klausiiiii.mobArmyBattle.tournament.TournamentManager;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
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
    private final ReconnectGraceManager graceManager;

    public PlayerConnectionListener(MatchManager matchManager, WorldManager worldManager,
                                    TournamentManager tournamentManager) {
        this(matchManager, worldManager, tournamentManager, null, null);
    }

    public PlayerConnectionListener(MatchManager matchManager, WorldManager worldManager,
                                    TournamentManager tournamentManager,
                                    SpectatorManager spectatorManager) {
        this(matchManager, worldManager, tournamentManager, spectatorManager, null);
    }

    public PlayerConnectionListener(MatchManager matchManager, WorldManager worldManager,
                                    TournamentManager tournamentManager,
                                    SpectatorManager spectatorManager,
                                    ReconnectGraceManager graceManager) {
        this.matchManager = matchManager;
        this.worldManager = worldManager;
        this.tournamentManager = tournamentManager;
        this.spectatorManager = spectatorManager;
        this.graceManager = graceManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (graceManager != null && graceManager.isAbsent(id)) {
            graceManager.restoreAbsent(id);
            Match match = matchManager.getMatchOf(id);
            if (match != null && match.getCurrentPhase().getType() == MatchPhaseType.FARM) {
                Team t = match.findTeamOf(id);
                if (t != null) {
                    String farmName = match.getFarmWorldName(t);
                    if (farmName != null) {
                        World fw = Bukkit.getWorld(farmName);
                        if (fw != null) event.getPlayer().teleport(fw.getSpawnLocation());
                    }
                }
            }
            return;
        }
        if (matchManager.getMatchOf(id) != null) {
            return;
        }
        worldManager.teleportToLobby(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (spectatorManager != null && spectatorManager.isSpectating(id)) {
            spectatorManager.endSpectate(id);
            // Fall through to existing match/tournament cleanup — pair-partner spectators
            // are still match members and need their team membership cleaned up.
        }
        if (tournamentManager != null) {
            tournamentManager.onCaptainQuit(id);
        }
        if (graceManager != null && graceManager.markAbsent(id)) {
            return;  // grace scheduled — eviction will run after delay
        }
        matchManager.leaveMatch(id);
    }
}
