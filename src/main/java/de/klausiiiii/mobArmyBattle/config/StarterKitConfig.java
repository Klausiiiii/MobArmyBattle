package de.klausiiiii.mobArmyBattle.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record StarterKitConfig(Type type, Map<Slot, String> customSlots) {
    public enum Type { NONE, LEATHER_FULL, IRON_FULL, CUSTOM }
    public enum Slot { MAIN, OFF, HELM, CHEST, LEGS, BOOTS }

    public StarterKitConfig {
        if (type == null) throw new IllegalArgumentException("type darf nicht null sein");
        if (customSlots == null) throw new IllegalArgumentException("customSlots darf nicht null sein");
        if (type == Type.CUSTOM && customSlots.isEmpty()) {
            throw new IllegalArgumentException("custom kit braucht mindestens einen Slot");
        }
        Map<Slot, String> defensive = customSlots.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(customSlots));
        customSlots = defensive;
    }
}
