package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeathFarmListener implements Listener {

    private static final int DEFAULT_PENALTY_PERCENT = 10;

    private final MatchManager matchManager;

    public PlayerDeathFarmListener(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) return;
        if (match.getCurrentPhase().getType() != MatchPhaseType.FARM) return;

        Team team = match.findTeamOf(player.getUniqueId());
        if (team == null) return;

        int lost = team.getPool().applyPenalty(DEFAULT_PENALTY_PERCENT);
        if (lost > 0) {
            player.sendMessage(Component.text(
                    "Tod-Strafe: " + lost + " Mobs aus dem Team-Pool verloren.",
                    NamedTextColor.RED));
        }
    }
}
