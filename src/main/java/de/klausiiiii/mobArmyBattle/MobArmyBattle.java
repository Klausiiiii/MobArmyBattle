package de.klausiiiii.mobArmyBattle;

import de.klausiiiii.mobArmyBattle.battle.BattleManager;
import de.klausiiiii.mobArmyBattle.bossbar.MatchBossBarManager;
import de.klausiiiii.mobArmyBattle.command.MabCommand;
import de.klausiiiii.mobArmyBattle.command.MabMenuGui;
import de.klausiiiii.mobArmyBattle.listener.BattleEventListener;
import de.klausiiiii.mobArmyBattle.listener.FarmRespawnListener;
import de.klausiiiii.mobArmyBattle.listener.MobKillListener;
import de.klausiiiii.mobArmyBattle.listener.PlayerConnectionListener;
import de.klausiiiii.mobArmyBattle.listener.PlayerDeathFarmListener;
import de.klausiiiii.mobArmyBattle.listener.WorldGroupInventoryListener;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.wave.WaveBuildGui;
import de.klausiiiii.mobArmyBattle.world.LobbyInventoryManager;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobArmyBattle extends JavaPlugin {

    private MatchManager matchManager;
    private WorldManager worldManager;
    private WaveBuildGui waveBuildGui;
    private MabMenuGui mabMenuGui;
    private LobbyInventoryManager lobbyInventoryManager;
    private MatchBossBarManager bossBarManager;
    private BattleManager battleManager;

    @Override
    public void onEnable() {
        worldManager = new WorldManager(this);
        worldManager.cleanupOrphanWorlds();
        worldManager.getOrCreateLobbyWorld();

        matchManager = new MatchManager();

        PluginCommand mabCmd = getCommand("mab");
        if (mabCmd == null) {
            getLogger().severe("Befehl /mab nicht in plugin.yml deklariert!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        MabCommand mabHandler = new MabCommand(this, matchManager);
        mabCmd.setExecutor(mabHandler);
        mabCmd.setTabCompleter(mabHandler);

        lobbyInventoryManager = new LobbyInventoryManager();

        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(matchManager, worldManager), this);
        getServer().getPluginManager().registerEvents(
                new MobKillListener(matchManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerDeathFarmListener(matchManager), this);
        getServer().getPluginManager().registerEvents(
                new FarmRespawnListener(matchManager), this);
        getServer().getPluginManager().registerEvents(
                new WorldGroupInventoryListener(lobbyInventoryManager), this);

        waveBuildGui = new WaveBuildGui(matchManager);
        getServer().getPluginManager().registerEvents(waveBuildGui, this);

        mabMenuGui = new MabMenuGui(this, matchManager);
        getServer().getPluginManager().registerEvents(mabMenuGui, this);

        bossBarManager = new MatchBossBarManager();

        battleManager = new BattleManager(this);
        getServer().getPluginManager().registerEvents(
                new BattleEventListener(battleManager), this);

        getServer().getScheduler().runTaskTimer(this, () -> {
            matchManager.tickAll();
            bossBarManager.tickAll(matchManager.getActiveMatches());
        }, 20L, 20L);

        getLogger().info("MobArmyBattle aktiviert.");
    }

    @Override
    public void onDisable() {
        if (bossBarManager != null) {
            bossBarManager.clear();
        }
        getLogger().info("MobArmyBattle deaktiviert.");
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public WaveBuildGui getWaveBuildGui() {
        return waveBuildGui;
    }

    public LobbyInventoryManager getLobbyInventoryManager() {
        return lobbyInventoryManager;
    }

    public MabMenuGui getMabMenuGui() {
        return mabMenuGui;
    }

    public BattleManager getBattleManager() {
        return battleManager;
    }
}
