package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

/**
 * Makes the lobby world ({@link WorldManager#LOBBY_WORLD_NAME}) effectively read-only:
 * block breaking, placing and bucket use are cancelled, and any block right-click is
 * suppressed. Right-clicking the tagged menu villager opens the {@code /mab} menu.
 * Players holding {@link #BYPASS_PERMISSION} (ops by default) are exempt from the
 * build/interact protections. Natural creature spawns in the lobby are cancelled.
 */
public class LobbyProtectionListener implements Listener {

    public static final String BYPASS_PERMISSION = "mobarmybattle.lobby.bypass";

    private static final int VOID_Y_THRESHOLD = 0;

    private final MobArmyBattle plugin;

    public LobbyProtectionListener(MobArmyBattle plugin) {
        this.plugin = plugin;
    }

    private static boolean inLobby(Player player) {
        return WorldManager.LOBBY_WORLD_NAME.equals(player.getWorld().getName());
    }

    private static boolean protectedFor(Player player) {
        return inLobby(player) && !player.hasPermission(BYPASS_PERMISSION);
    }

    private boolean isMenuVillager(Entity entity) {
        return entity.getPersistentDataContainer().has(plugin.getWorldManager().getMenuVillagerKey(), PersistentDataType.BYTE);
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
        if (!protectedFor(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!inLobby(player)) return;
        Entity clicked = event.getRightClicked();
        if (isMenuVillager(clicked)) {
            event.setCancelled(true);
            plugin.getMabMenuGui().open(player);
        }
    }

    @EventHandler
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        if (!inLobby(player)) return;
        Entity clicked = event.getRightClicked();
        if (isMenuVillager(clicked)) {
            event.setCancelled(true);
            plugin.getMabMenuGui().open(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!WorldManager.LOBBY_WORLD_NAME.equals(entity.getWorld().getName())) return;
        if (isMenuVillager(entity)) {
            event.setCancelled(true);
            return;
        }
        if (entity instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!inLobby(player)) return;
        if (player.getY() >= VOID_Y_THRESHOLD) return;
        plugin.getWorldManager().teleportToLobby(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!WorldManager.LOBBY_WORLD_NAME.equals(event.getLocation().getWorld().getName())) return;
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        event.setCancelled(true);
    }
}
