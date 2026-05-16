package de.klausiiiii.mobArmyBattle.battle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.BlockState;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ArenaLoader {

    private static final int FALLBACK_RADIUS = 15;
    private static final int FALLBACK_Y = 64;

    private final JavaPlugin plugin;
    private final Logger log;
    private final File arenasFolder;

    public ArenaLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.arenasFolder = new File(plugin.getDataFolder(), "arenas");
        if (!arenasFolder.exists()) {
            arenasFolder.mkdirs();
        }
    }

    public void loadInto(World world) {
        File[] files = arenasFolder.listFiles((dir, name) -> name.endsWith(".nbt"));
        if (files != null && files.length > 0) {
            File chosen = files[(int) (Math.random() * files.length)];
            try {
                org.bukkit.structure.Structure structure = Bukkit.getStructureManager().loadStructure(chosen);
                if (structure != null) {
                    org.bukkit.util.BlockVector size = structure.getSize();
                    int sizeX = size.getBlockX();
                    int sizeZ = size.getBlockZ();
                    // Struktur-Mitte auf world spawn (0,0) legen — Origin ist die Ecke,
                    // also um halbe Größe nach -X/-Z verschieben.
                    Location origin = world.getSpawnLocation().clone()
                            .add(-(sizeX / 2.0), 0, -(sizeZ / 2.0));
                    preloadChunksFor(world, origin.getBlockX(), origin.getBlockZ(), sizeX, sizeZ);
                    structure.place(origin, true,
                            org.bukkit.block.structure.StructureRotation.NONE,
                            org.bukkit.block.structure.Mirror.NONE,
                            -1, 1.0f,
                            new java.util.Random());
                    log.info("Arena geladen aus " + chosen.getName()
                            + " (" + sizeX + "x" + sizeZ + " mittig auf 0,0)");
                    return;
                }
            } catch (IOException | NoSuchMethodError | NoClassDefFoundError e) {
                log.log(Level.WARNING, "Arena-Datei " + chosen.getName() + " nicht ladbar, fallback aktiv: " + e.getMessage());
            }
        }
        buildFallbackArena(world);
    }

    private void preloadChunksFor(World world, int minX, int minZ, int sizeX, int sizeZ) {
        int minCx = minX >> 4;
        int minCz = minZ >> 4;
        int maxCx = (minX + sizeX) >> 4;
        int maxCz = (minZ + sizeZ) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                world.getChunkAt(cx, cz).load();
            }
        }
    }

    private void buildFallbackArena(World world) {
        log.info("Fallback-Arena wird gebaut in " + world.getName());
        for (int x = -FALLBACK_RADIUS; x <= FALLBACK_RADIUS; x++) {
            for (int z = -FALLBACK_RADIUS; z <= FALLBACK_RADIUS; z++) {
                world.getBlockAt(x, FALLBACK_Y - 1, z).setType(Material.BEDROCK);
            }
        }
        placeMarker(world, FALLBACK_RADIUS - 1, FALLBACK_Y, FALLBACK_RADIUS - 1);
        placeMarker(world, FALLBACK_RADIUS - 1, FALLBACK_Y, -FALLBACK_RADIUS + 1);
        placeMarker(world, -FALLBACK_RADIUS + 1, FALLBACK_Y, FALLBACK_RADIUS - 1);
        placeMarker(world, -FALLBACK_RADIUS + 1, FALLBACK_Y, -FALLBACK_RADIUS + 1);
        world.setSpawnLocation(0, FALLBACK_Y, 0);
    }

    private void placeMarker(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z).setType(Material.RED_BANNER);
        BlockState state = world.getBlockAt(x, y, z).getState();
        if (state instanceof Banner banner) {
            banner.customName(Component.text(ArenaSpawnScanner.SPAWN_TAG, NamedTextColor.RED));
            banner.update();
        }
    }

    public Location getPlayerSpawn(World world) {
        Location base = world.getSpawnLocation();
        int x = base.getBlockX();
        int z = base.getBlockZ();
        int y = world.getHighestBlockYAt(x, z);
        return new Location(world, x + 0.5, y + 1.0, z + 0.5);
    }
}
