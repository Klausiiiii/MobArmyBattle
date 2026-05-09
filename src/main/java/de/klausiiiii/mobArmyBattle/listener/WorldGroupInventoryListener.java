package de.klausiiiii.mobArmyBattle.listener;

import de.klausiiiii.mobArmyBattle.world.LobbyInventoryManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

public class WorldGroupInventoryListener implements Listener {

    private final LobbyInventoryManager inventoryManager;

    public WorldGroupInventoryListener(LobbyInventoryManager inventoryManager) {
        this.inventoryManager = inventoryManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        String fromGroup = inventoryManager.groupFor(event.getFrom().getName());
        String toGroup = inventoryManager.groupFor(event.getPlayer().getWorld().getName());
        inventoryManager.swap(event.getPlayer(), fromGroup, toGroup);
    }
}
