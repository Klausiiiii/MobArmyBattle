package de.klausiiiii.mobArmyBattle.battle;

import de.klausiiiii.mobArmyBattle.wave.Wave;
import de.klausiiiii.mobArmyBattle.wave.WaveSlot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class WaveSpawner {

    public List<LivingEntity> spawnWave(World world, List<Location> spawnPoints, Wave wave) {
        List<LivingEntity> spawned = new ArrayList<>();
        if (spawnPoints.isEmpty() || wave.isForfeited()) return spawned;
        int spawnIdx = 0;
        for (WaveSlot slot : wave.getSlots()) {
            EntityType type = parseType(slot.getEntry().getEntityTypeName());
            if (type == null) continue;
            for (int i = 0; i < slot.getCount(); i++) {
                Location loc = spawnPoints.get(spawnIdx % spawnPoints.size());
                spawnIdx++;
                org.bukkit.entity.Entity ent = world.spawnEntity(loc, type);
                if (ent instanceof LivingEntity living) {
                    applyEquipment(living, slot.getEntry().getEquipmentSignature());
                    spawned.add(living);
                }
            }
        }
        return spawned;
    }

    private EntityType parseType(String name) {
        try {
            return EntityType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void applyEquipment(LivingEntity entity, String signature) {
        if (signature == null || signature.equals("none|none|none|none|none|none")) return;
        EntityEquipment eq = entity.getEquipment();
        if (eq == null) return;
        String[] parts = signature.split("\\|");
        if (parts.length != 6) return;
        eq.setItemInMainHand(itemFor(parts[0]));
        eq.setItemInOffHand(itemFor(parts[1]));
        eq.setHelmet(itemFor(parts[2]));
        eq.setChestplate(itemFor(parts[3]));
        eq.setLeggings(itemFor(parts[4]));
        eq.setBoots(itemFor(parts[5]));
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
    }

    private ItemStack itemFor(String slot) {
        if (slot.equals("none")) return null;
        Material mat = Material.matchMaterial(slot);
        if (mat == null) return null;
        return new ItemStack(mat);
    }
}
