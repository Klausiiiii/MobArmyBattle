package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.config.DeathPenaltyConfig;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeathFarmListener implements Listener {

    private final MatchManager matchManager;
    private final MobArmyBattle plugin;

    public PlayerDeathFarmListener(MatchManager matchManager) {
        this(matchManager, null);
    }

    public PlayerDeathFarmListener(MatchManager matchManager, MobArmyBattle plugin) {
        this.matchManager = matchManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Match match = matchManager.getMatchOf(player.getUniqueId());
        if (match == null) return;
        if (match.getCurrentPhase().getType() != MatchPhaseType.FARM) return;

        Team team = match.findTeamOf(player.getUniqueId());
        if (team == null) return;

        DeathPenaltyConfig.Mode mode = plugin != null
                ? plugin.getMabConfig().deathPenalty().mode()
                : DeathPenaltyConfig.Mode.SOFT;  // legacy default
        int penaltyPercent = mode.poolPercent();
        if (penaltyPercent > 0) {
            int lost = team.getPool().applyPenalty(penaltyPercent);
            if (lost > 0) {
                player.sendMessage(Component.text(
                        "Tod-Strafe: " + lost + " Mobs aus dem Team-Pool verloren (" + penaltyPercent + "%).",
                        NamedTextColor.RED));
            }
        }
        event.setKeepInventory(!mode.dropItems());
    }
}
