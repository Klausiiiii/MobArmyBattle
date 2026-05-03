package de.klausiiiii.mobArmyBattle.pool;

import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

public final class EquipmentSerializer {

    private static final String NONE = "none";

    private EquipmentSerializer() {
    }

    public static String serialize(LivingEntity entity) {
        EntityEquipment eq = entity.getEquipment();
        if (eq == null) {
            return joinSlots(NONE, NONE, NONE, NONE, NONE, NONE);
        }
        return joinSlots(
                slotOf(eq.getItemInMainHand()),
                slotOf(eq.getItemInOffHand()),
                slotOf(eq.getHelmet()),
                slotOf(eq.getChestplate()),
                slotOf(eq.getLeggings()),
                slotOf(eq.getBoots())
        );
    }

    private static String slotOf(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return NONE;
        }
        return item.getType().name().toLowerCase();
    }

    private static String joinSlots(String... slots) {
        return String.join("|", slots);
    }
}
