package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Makes the lobby world ({@link WorldManager#LOBBY_WORLD_NAME}) effectively read-only:
 * block breaking, placing and bucket use are cancelled, and right-clicking any block does
 * nothing — except a right-click on a sign, which opens the {@code /mab} menu (same as
 * running {@code /mab} with no arguments). Players holding {@link #BYPASS_PERMISSION}
 * (ops by default) are exempt.
 */
public class LobbyProtectionListener implements Listener {

    public static final String BYPASS_PERMISSION = "mobarmybattle.lobby.bypass";

    private final MobArmyBattle plugin;

    public LobbyProtectionListener(MobArmyBattle plugin) {
        this.plugin = plugin;
    }

    private static boolean protectedFor(Player player) {
        return WorldManager.LOBBY_WORLD_NAME.equals(player.getWorld().getName())
                && !player.hasPermission(BYPASS_PERMISSION);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (protectedFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (protectedFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (protectedFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (protectedFor(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!protectedFor(player)) return;
        Block clicked = event.getClickedBlock();
        event.setCancelled(true);
        if (clicked != null && clicked.getState() instanceof Sign) {
            plugin.getMabMenuGui().open(player);
        }
    }
}
