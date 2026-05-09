package de.klausiiiii.mobArmyBattle.world;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LobbyInventoryManager {

    public static final String GROUP_LOBBY = "lobby";
    public static final String GROUP_GAME = "game";

    private record Snapshot(ItemStack[] storage, ItemStack[] armor, ItemStack offhand) {}

    private final Map<UUID, Map<String, Snapshot>> stash = new HashMap<>();

    public String groupFor(String worldName) {
        return WorldManager.LOBBY_WORLD_NAME.equals(worldName) ? GROUP_LOBBY : GROUP_GAME;
    }

    public void swap(Player player, String fromGroup, String toGroup) {
        if (fromGroup.equals(toGroup)) {
            return;
        }
        save(player, fromGroup);
        restore(player, toGroup);
    }

    private void save(Player player, String group) {
        PlayerInventory inv = player.getInventory();
        Snapshot snap = new Snapshot(
                deepClone(inv.getStorageContents()),
                deepClone(inv.getArmorContents()),
                inv.getItemInOffHand() == null ? null : inv.getItemInOffHand().clone()
        );
        stash.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(group, snap);
    }

    private void restore(Player player, String group) {
        PlayerInventory inv = player.getInventory();
        Map<String, Snapshot> playerStash = stash.get(player.getUniqueId());
        Snapshot snap = playerStash == null ? null : playerStash.get(group);
        if (snap == null) {
            inv.clear();
            inv.setArmorContents(null);
            inv.setItemInOffHand(null);
            return;
        }
        inv.setStorageContents(snap.storage());
        inv.setArmorContents(snap.armor());
        inv.setItemInOffHand(snap.offhand());
    }

    private static ItemStack[] deepClone(ItemStack[] src) {
        ItemStack[] dst = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            dst[i] = src[i] == null ? null : src[i].clone();
        }
        return dst;
    }
}
