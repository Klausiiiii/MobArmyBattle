package de.klausiiiii.mobArmyBattle.battle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class ArenaSpawnScanner {

    public static final String SPAWN_TAG = "[MAB_SPAWN]";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ArenaSpawnScanner() {}

    public static List<Location> scanAndConsume(World world, int radius) {
        List<Location> spawns = new ArrayList<>();
        for (Chunk chunk : world.getLoadedChunks()) {
            for (BlockState state : chunk.getTileEntities()) {
                if (!(state instanceof Banner banner)) continue;
                if (!hasSpawnTag(banner)) continue;
                Location loc = banner.getLocation().add(0.5, 0, 0.5);
                spawns.add(loc);
                banner.getBlock().setType(Material.AIR);
            }
        }
        if (spawns.isEmpty()) {
            spawns.add(world.getSpawnLocation().add(0.5, 1, 0.5));
        }
        return spawns;
    }

    private static boolean hasSpawnTag(Banner banner) {
        Component name = banner.customName();
        if (name != null) {
            String text = PLAIN.serialize(name);
            if (text.contains(SPAWN_TAG)) return true;
        }
        return false;
    }
}
