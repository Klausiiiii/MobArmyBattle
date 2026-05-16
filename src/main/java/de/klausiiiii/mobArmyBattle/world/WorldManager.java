package de.klausiiiii.mobArmyBattle.world;

import de.klausiiiii.mobArmyBattle.config.MabConfig;
import de.klausiiiii.mobArmyBattle.config.WorldBorderConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import de.klausiiiii.mobArmyBattle.MobArmyBattle;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Logger;

public class WorldManager {

    public static final String LOBBY_WORLD_NAME = "mab_lobby";
    public static final String FARM_WORLD_PREFIX = "mab_farm_";
    public static final String ARENA_WORLD_PREFIX = "mab_arena_";

    private static final int LOBBY_SPAWN_Y = 64;
    private static final double MENU_VILLAGER_X = 0.5;
    private static final double MENU_VILLAGER_Y = 67.0;
    private static final double MENU_VILLAGER_Z = 46.5;
    private static final float MENU_VILLAGER_YAW = 180.0f;
    public static final String MENU_VILLAGER_TAG_KEY = "mab_menu_villager";

    private final MobArmyBattle plugin;
    private final Logger log;
    private final NamespacedKey menuVillagerKey;
    private World lobbyWorld;

    public WorldManager(MobArmyBattle plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.menuVillagerKey = new NamespacedKey(plugin, MENU_VILLAGER_TAG_KEY);
    }

    public NamespacedKey getMenuVillagerKey() {
        return menuVillagerKey;
    }

    public World getOrCreateLobbyWorld() {
        if (lobbyWorld != null) {
            return lobbyWorld;
        }
        World existing = Bukkit.getWorld(LOBBY_WORLD_NAME);
        if (existing != null) {
            lobbyWorld = existing;
            applyAlwaysDay(existing);
            applyLobbyMobBlock(existing);
            ensureMenuVillager(existing);
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
        applyLobbyMobBlock(lobbyWorld);
        ensureMenuVillager(lobbyWorld);
        log.info("Lobby-Welt initialisiert: " + LOBBY_WORLD_NAME);
        return lobbyWorld;
    }

    private void applyAlwaysDay(World world) {
        world.setTime(6000);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setStorm(false);
        world.setThundering(false);
        world.setClearWeatherDuration(Integer.MAX_VALUE);
    }

    private void disableNaturalMobSpawning(World world) {
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.SPAWNER_BLOCKS_WORK, false);
        world.setSpawnFlags(false, false);
    }

    /**
     * Game rules + PEACEFUL difficulty + spawn limits = 0. Entity removal happens
     * separately in {@link #wipeAllMobs(World)} so chunks can be force-loaded first.
     */
    private void applyLobbyMobBlock(World world) {
        disableNaturalMobSpawning(world);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setMonsterSpawnLimit(0);
        world.setAnimalSpawnLimit(0);
        world.setWaterAnimalSpawnLimit(0);
        world.setWaterAmbientSpawnLimit(0);
        world.setAmbientSpawnLimit(0);
    }

    private boolean isMenuVillager(Entity entity) {
        return entity.getPersistentDataContainer().has(menuVillagerKey, PersistentDataType.BYTE);
    }

    /**
     * Force-loads a small radius of chunks around the lobby spawn and the menu
     * villager position so saved entities become visible, then removes every
     * {@link Mob} in the world. Players, items, ArmorStands and other non-Mob
     * entities are left untouched.
     */
    private void wipeAllMobs(World world) {
        int radius = 4;
        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                world.getChunkAt(cx, cz);
            }
        }
        int villagerChunkX = (int) Math.floor(MENU_VILLAGER_X) >> 4;
        int villagerChunkZ = (int) Math.floor(MENU_VILLAGER_Z) >> 4;
        for (int cx = villagerChunkX - 2; cx <= villagerChunkX + 2; cx++) {
            for (int cz = villagerChunkZ - 2; cz <= villagerChunkZ + 2; cz++) {
                world.getChunkAt(cx, cz);
            }
        }
        int removed = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Mob) {
                entity.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Lobby-Cleanup: " + removed + " Mob(s) entfernt vor Villager-Spawn.");
        }
    }

    /**
     * Wipes all mobs in the lobby, then spawns a fresh tagged menu villager at the
     * configured position. Called once on lobby load.
     */
    private void ensureMenuVillager(World world) {
        wipeAllMobs(world);
        Location spawnLoc = new Location(world, MENU_VILLAGER_X, MENU_VILLAGER_Y, MENU_VILLAGER_Z,
                MENU_VILLAGER_YAW, 0.0f);
        Villager villager = (Villager) world.spawnEntity(spawnLoc, EntityType.VILLAGER);
        villager.getPersistentDataContainer().set(menuVillagerKey, PersistentDataType.BYTE, (byte) 1);
        applyMenuVillagerProperties(villager);
        log.info("Menü-Villager in der Lobby gespawnt: " + spawnLoc);
    }

    private void applyMenuVillagerProperties(Villager villager) {
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setCollidable(false);
        villager.setGravity(false);
        villager.setPersistent(true);
        villager.setRemoveWhenFarAway(false);
        villager.setProfession(Villager.Profession.LIBRARIAN);
        villager.customName(Component.text("» MobArmy Battle Menü «", NamedTextColor.GOLD));
        villager.setCustomNameVisible(true);
    }

    public World createFarmWorld(String matchId, String teamId, long seed) {
        return createFarmWorld(matchId, teamId, seed, plugin.getMabConfig());
    }

    public World createFarmWorld(String matchId, String teamId, long seed, MabConfig config) {
        String name = FARM_WORLD_PREFIX + matchId + "_" + teamId;
        WorldCreator creator = new WorldCreator(name)
                .seed(seed)
                .type(WorldType.NORMAL);
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            throw new IllegalStateException("Konnte Farm-Welt nicht erstellen: " + name);
        }
        MabConfig cfg = config != null ? config : plugin.getMabConfig();
        WorldBorderConfig borderCfg = cfg.farmBorder();
        if (borderCfg.enabled() && borderCfg.radius() > 0) {
            world.getWorldBorder().setCenter(world.getSpawnLocation());
            world.getWorldBorder().setSize(borderCfg.radius() * 2.0);
        }
        double mult = cfg.farmMobSpawnMultiplier();
        int currentLimit = world.getMonsterSpawnLimit();
        int baseline = currentLimit > 0 ? currentLimit : 70;
        world.setMonsterSpawnLimit((int) Math.max(1, baseline * mult));
        log.info("Farm-Welt erstellt: " + name + " (seed=" + seed + ")");
        return world;
    }

    public World createArenaWorld(String matchId, String teamId) {
        return createArenaWorld(matchId, teamId, plugin.getMabConfig());
    }

    public World createArenaWorld(String matchId, String teamId, MabConfig config) {
        String name = ARENA_WORLD_PREFIX + matchId + "_" + teamId;
        WorldCreator creator = new WorldCreator(name)
                .generator(new LobbyChunkGenerator())
                .type(WorldType.FLAT);
        World world = Bukkit.createWorld(creator);
        if (world == null) {
            throw new IllegalStateException("Konnte Arena-Welt nicht erstellen: " + name);
        }
        // Spawn explizit auf 0,0 setzen — die Arena-Struktur wird mittig darum platziert.
        world.setSpawnLocation(0, 64, 0);
        applyAlwaysDay(world);
        // Nur Wave-Spawns (SpawnReason.CUSTOM) — keine natürlichen Spawns in Arenen.
        disableNaturalMobSpawning(world);
        MabConfig cfg = config != null ? config : plugin.getMabConfig();
        WorldBorderConfig arenaBorderCfg = cfg.arenaBorder();
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

    /**
     * Unloads and deletes every loaded arena world belonging to the given match
     * ({@code mab_arena_<matchId>_*}). Safe to call even if no such worlds exist.
     */
    public void deleteArenaWorldsOf(String matchId) {
        String prefix = ARENA_WORLD_PREFIX + matchId + "_";
        // Copy the list: deleteWorld -> Bukkit.unloadWorld mutates Bukkit.getWorlds().
        for (World w : new ArrayList<>(Bukkit.getWorlds())) {
            if (w.getName().startsWith(prefix)) {
                deleteWorld(w);
            }
        }
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
