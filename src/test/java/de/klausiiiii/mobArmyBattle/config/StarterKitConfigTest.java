package de.klausiiiii.mobArmyBattle.config;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.*;

class StarterKitConfigTest {
    @Test
    void createsNoneKit() {
        StarterKitConfig k = new StarterKitConfig(StarterKitConfig.Type.NONE, Map.of());
        assertEquals(StarterKitConfig.Type.NONE, k.type());
        assertTrue(k.customSlots().isEmpty());
    }

    @Test
    void createsIronFullKit() {
        StarterKitConfig k = new StarterKitConfig(StarterKitConfig.Type.IRON_FULL, Map.of());
        assertEquals(StarterKitConfig.Type.IRON_FULL, k.type());
    }

    @Test
    void customRequiresAtLeastOneSlot() {
        assertThrows(IllegalArgumentException.class,
                () -> new StarterKitConfig(StarterKitConfig.Type.CUSTOM, Map.of()));
    }

    @Test
    void customSlotsHeldImmutably() {
        Map<StarterKitConfig.Slot, String> slots = new EnumMap<>(StarterKitConfig.Slot.class);
        slots.put(StarterKitConfig.Slot.MAIN, "DIAMOND_SWORD");
        StarterKitConfig k = new StarterKitConfig(StarterKitConfig.Type.CUSTOM, slots);
        assertEquals("DIAMOND_SWORD", k.customSlots().get(StarterKitConfig.Slot.MAIN));
    }

    @Test
    void rejectsNullType() {
        assertThrows(IllegalArgumentException.class,
                () -> new StarterKitConfig(null, Map.of()));
    }
}
