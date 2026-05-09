package de.klausiiiii.mobArmyBattle.world;

import de.klausiiiii.mobArmyBattle.config.StarterKitConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumMap;
import java.util.Map;

public final class StarterKitApplier {

    private StarterKitApplier() {}

    public static void applyKit(Player player, StarterKitConfig kit) {
        if (kit == null || kit.type() == StarterKitConfig.Type.NONE) return;

        Map<StarterKitConfig.Slot, Material> resolved = resolve(kit);
        PlayerInventory inv = player.getInventory();
        for (Map.Entry<StarterKitConfig.Slot, Material> e : resolved.entrySet()) {
            ItemStack stack = new ItemStack(e.getValue());
            switch (e.getKey()) {
                case MAIN -> inv.setItemInMainHand(stack);
                case OFF -> inv.setItemInOffHand(stack);
                case HELM -> inv.setHelmet(stack);
                case CHEST -> inv.setChestplate(stack);
                case LEGS -> inv.setLeggings(stack);
                case BOOTS -> inv.setBoots(stack);
            }
        }
    }

    private static Map<StarterKitConfig.Slot, Material> resolve(StarterKitConfig kit) {
        Map<StarterKitConfig.Slot, Material> out = new EnumMap<>(StarterKitConfig.Slot.class);
        switch (kit.type()) {
            case LEATHER_FULL -> {
                out.put(StarterKitConfig.Slot.MAIN, Material.STONE_SWORD);
                out.put(StarterKitConfig.Slot.HELM, Material.LEATHER_HELMET);
                out.put(StarterKitConfig.Slot.CHEST, Material.LEATHER_CHESTPLATE);
                out.put(StarterKitConfig.Slot.LEGS, Material.LEATHER_LEGGINGS);
                out.put(StarterKitConfig.Slot.BOOTS, Material.LEATHER_BOOTS);
            }
            case IRON_FULL -> {
                out.put(StarterKitConfig.Slot.MAIN, Material.IRON_SWORD);
                out.put(StarterKitConfig.Slot.OFF, Material.SHIELD);
                out.put(StarterKitConfig.Slot.HELM, Material.IRON_HELMET);
                out.put(StarterKitConfig.Slot.CHEST, Material.IRON_CHESTPLATE);
                out.put(StarterKitConfig.Slot.LEGS, Material.IRON_LEGGINGS);
                out.put(StarterKitConfig.Slot.BOOTS, Material.IRON_BOOTS);
            }
            case CUSTOM -> {
                for (Map.Entry<StarterKitConfig.Slot, String> e : kit.customSlots().entrySet()) {
                    Material mat = Material.matchMaterial(e.getValue());
                    if (mat != null) out.put(e.getKey(), mat);
                }
            }
            case NONE -> { /* no-op */ }
        }
        return out;
    }
}
