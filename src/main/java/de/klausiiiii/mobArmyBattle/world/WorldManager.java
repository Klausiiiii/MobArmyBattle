package de.klausiiiii.mobArmyBattle.world;

import de.klausiiiii.mobArmyBattle.config.WorldBorderConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import de.klausiiiii.mobArmyBattle.MobArmyBattle;

import java.io.File;
import java.util.Random;
import java.util.logging.Logger;

public class WorldManager {

    public static final String LOBBY_WORLD_NAME = "mab_lobby";
    public static final String FARM_WORLD_PREFIX = "mab_farm_";
    public static final String ARENA_WORLD_PREFIX = "mab_arena_";

    private static final int LOBBY_SPAWN_Y = 64;
    private static final int LOBBY_PLATFORM_RADIUS = 2; // 5x5 platform

    private final MobArmyBattle plugin;
    private final Logger log;
    private World lobbyWorld;

    public WorldManager(MobArmyBattle plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public World getOrCreateLobbyWorld() {
        if (lobbyWorld != null) {
            return lobbyWorld;
        }
        World existing = Bukkit.getWorld(LOBBY_WORLD_NAME);
        if (existing != null) {
            lobbyWorld = existing;
            applyAlwaysDay(existing);
            return existing;
        }

        WorldCreator creator = new WorldCreator(LOBBY_WORLD_NAME)
                .generator(new LobbyChunkGenerator())
                .type(WorldType.FLAT);
        lobbyWorld = Bukkit.createWorld(creator);
        if (lobbyWorld == null) {
            throw new IllegalStateException("Konnte Lobby-Welt nicht erstellen");
        }
        lobbyWorld.setSpawnLocation(0, LOBBY_SPAWN_Y, 0);
        applyAlwaysDay(lobbyWorld);
        buildLobbyPlatform(lobbyWorld);
        log.info("Lobby-Welt initialisiert: " + LOBBY_WORLD_NAME);
        return lobbyWorld;
    }

    private void applyAlwaysDay(World world) {
        world.setTime(6000);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
    }

    private void buildLobbyPlatform(World world) {
        for (int x = -LOBBY_PLATFORM_RADIUS; x <= LOBBY_PLATFORM_RADIUS; x++) {
            for (int z = -LOBBY_PLATFORM_RADIUS; z <= LOBBY_PLATFORM_RADIUS; z++) {
                world.getBlockAt(x, LOBBY_SPAWN_Y - 1, z).setType(Material.QUARTZ_BLOCK);
            }
        }
        Block signBlock = world.getBlockAt(0, LOBBY_SPAWN_Y, LOBBY_PLATFORM_RADIUS);
        signBlock.setType(Material.OAK_SIGN);
        if (signBlock.getState() instanceof Sign sign) {
            sign.getSide(Side.FRONT).line(0, Component.text("MobArmy", NamedTextColor.GOLD));
            sign.getSide(Side.FRONT).line(1, Component.text("Battle", NamedTextColor.GOLD));
            sign.getSide(Side.FRONT).line(2, Component.text("/mab create", NamedTextColor.GREEN));
            sign.getSide(Side.FRONT).line(3, Component.text("zum Starten", NamedTextColor.GRAY));
            sign.update();
        }
    }

    public World createFarmWorld(String matchId, String teamId, long seed) {
        String name = FARM_WORLD_PREFIX + matchId + "_" + teamId;
        WorldCreator creator = new WorldCreator(name)
                .seed(seed)
                .type(WorldType.NORMAL);
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            throw new IllegalStateException("Konnte Farm-Welt nicht erstellen: " + name);
        }
        // Plan 9: WorldBorder + Mob-Spawn-Multiplier
        WorldBorderConfig borderCfg = plugin.getMabConfig().farmBorder();
        if (borderCfg.enabled() && borderCfg.radius() > 0) {
            world.getWorldBorder().setCenter(world.getSpawnLocation());
            world.getWorldBorder().setSize(borderCfg.radius() * 2.0);
        }
        double mult = plugin.getMabConfig().farmMobSpawnMultiplier();
        int currentLimit = world.getMonsterSpawnLimit();
        int baseline = currentLimit > 0 ? currentLimit : 70;
        world.setMonsterSpawnLimit((int) Math.max(1, baseline * mult));
        log.info("Farm-Welt erstellt: " + name + " (seed=" + seed + ")");
        return world;
    }

    public World createArenaWorld(String matchId, String teamId) {
        String name = ARENA_WORLD_PREFIX + matchId + "_" + teamId;
        WorldCreator creator = new WorldCreator(name)
                .generator(new LobbyChunkGenerator())
                .type(WorldType.FLAT);
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            throw new IllegalStateException("Konnte Arena-Welt nicht erstellen: " + name);
        }
        applyAlwaysDay(world);
        // Plan 9: WorldBorder
        WorldBorderConfig arenaBorderCfg = plugin.getMabConfig().arenaBorder();
        if (arenaBorderCfg.enabled() && arenaBorderCfg.radius() > 0) {
            world.getWorldBorder().setCenter(world.getSpawnLocation());
            world.getWorldBorder().setSize(arenaBorderCfg.radius() * 2.0);
        }
        log.info("Arena-Welt erstellt: " + name);
        return world;
    }

    public void deleteWorld(World world) {
        if (world == null) {
            return;
        }
        String name = world.getName();
        File folder = world.getWorldFolder();
        for (Player p : world.getPlayers()) {
            teleportToLobby(p);
        }
        boolean unloaded = Bukkit.unloadWorld(world, false);
        if (!unloaded) {
            log.warning("Konnte Welt nicht entladen: " + name);
            return;
        }
        WorldCleanup.deleteRecursively(folder);
        log.info("Welt gelöscht: " + name);
    }

    public void cleanupOrphanWorlds() {
        File container = Bukkit.getWorldContainer();
        File[] all = container.listFiles();
        if (all == null) {
            return;
        }
        for (File entry : all) {
            if (!entry.isDirectory()) continue;
            String n = entry.getName();
            if (n.startsWith(FARM_WORLD_PREFIX) || n.startsWith(ARENA_WORLD_PREFIX)) {
                if (Bukkit.getWorld(n) != null) continue;
                log.info("Lösche Orphan-Welt: " + n);
                WorldCleanup.deleteRecursively(entry);
            }
        }
    }

    public void teleportToLobby(Player player) {
        World lobby = getOrCreateLobbyWorld();
        Location spawn = lobby.getSpawnLocation();
        player.teleport(spawn.clone().add(0.5, 0, 0.5));
    }

    public World getLobbyWorld() {
        return lobbyWorld;
    }

    public static long generateSharedSeed() {
        return new Random().nextLong();
    }

    public static Location safeSpawnAt(World world, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.getChunkAt(chunkX, chunkZ);
        }
        int y = world.getHighestBlockYAt(x, z);
        return new Location(world, x + 0.5, y + 1.0, z + 0.5);
    }
}
