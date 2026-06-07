package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.util.UUID;

/**
 * While a player's match is in {@link MatchPhaseType#WAVE_BUILD} they may not
 * place or break blocks, use buckets, or damage mobs. Combined with
 * {@code PlayerFreezeManager} and the mob-AI freeze in {@code WaveBuildPhase},
 * this keeps the farm world fully frozen while captains pick waves.
 *
 * <p>Players holding {@link #BYPASS_PERMISSION} (op default) are exempt.
 */
public class WaveBuildProtectionListener implements Listener {

    public static final String BYPASS_PERMISSION = "mobarmybattle.wavebuild.bypass";

    private final MatchManager matchManager;

    public WaveBuildProtectionListener(MatchManager matchManager) {
        this.matchManager = matchManager;
    }

    private boolean isLocked(Player player) {
        if (player.hasPermission(BYPASS_PERMISSION)) return false;
        return isLocked(player.getUniqueId());
    }

    private boolean isLocked(UUID playerId) {
        Match match = matchManager.getMatchOf(playerId);
        if (match == null) return false;
        return match.getCurrentPhase().getType() == MatchPhaseType.WAVE_BUILD;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMobDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!(victim instanceof Mob)) return;
        Entity damager = event.getDamager();
        Player source = null;
        if (damager instanceof Player p) {
            source = p;
        } else if (damager instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) {
            source = p;
        }
        if (source == null) return;
        if (isLocked(source)) {
            event.setCancelled(true);
        }
    }
}
