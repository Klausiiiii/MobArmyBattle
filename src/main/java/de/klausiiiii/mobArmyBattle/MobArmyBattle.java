package de.klausiiiii.mobArmyBattle;

import de.klausiiiii.mobArmyBattle.battle.BattleManager;
import de.klausiiiii.mobArmyBattle.config.ConfigLoader;
import de.klausiiiii.mobArmyBattle.config.MabConfig;
import de.klausiiiii.mobArmyBattle.config.ReconnectGraceManager;
import de.klausiiiii.mobArmyBattle.bossbar.MatchBossBarManager;
import de.klausiiiii.mobArmyBattle.command.MabCommand;
import de.klausiiiii.mobArmyBattle.command.MabMenuGui;
import de.klausiiiii.mobArmyBattle.listener.BattleEventListener;
import de.klausiiiii.mobArmyBattle.listener.MobKillListener;
import de.klausiiiii.mobArmyBattle.listener.PlayerConnectionListener;
import de.klausiiiii.mobArmyBattle.listener.PlayerDeathFarmListener;
import de.klausiiiii.mobArmyBattle.listener.PlayerRespawnListener;
import de.klausiiiii.mobArmyBattle.listener.WorldGroupInventoryListener;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.spectator.SpectatorManager;
import de.klausiiiii.mobArmyBattle.stats.StatsDatabase;
import de.klausiiiii.mobArmyBattle.stats.StatsRecorder;
import de.klausiiiii.mobArmyBattle.stats.StatsRepository;
import de.klausiiiii.mobArmyBattle.tournament.TournamentManager;
import de.klausiiiii.mobArmyBattle.ui.SidebarManager;
import de.klausiiiii.mobArmyBattle.wave.WaveBuildGui;
import de.klausiiiii.mobArmyBattle.world.LobbyInventoryManager;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobArmyBattle extends JavaPlugin {

    private MabConfig mabConfig;
    private MatchManager matchManager;
    private WorldManager worldManager;
    private WaveBuildGui waveBuildGui;
    private MabMenuGui mabMenuGui;
    private LobbyInventoryManager lobbyInventoryManager;
    private MatchBossBarManager bossBarManager;
    private BattleManager battleManager;
    private TournamentManager tournamentManager;
    private StatsDatabase statsDatabase;
    private StatsRepository statsRepository;
    private SpectatorManager spectatorManager;
    private SidebarManager sidebarManager;
    private ReconnectGraceManager reconnectGraceManager;

    @Override
    public void onEnable() {
        // 0. Config
        saveDefaultConfig();
        mabConfig = ConfigLoader.load(this);

        // 1. WorldManager
        worldManager = new WorldManager(this);
        worldManager.cleanupOrphanWorlds();
        worldManager.getOrCreateLobbyWorld();

        // 2. MatchManager
        matchManager = new MatchManager();

        // 3. LobbyInventoryManager
        lobbyInventoryManager = new LobbyInventoryManager();

        // 4. TournamentManager
        tournamentManager = new TournamentManager(this, matchManager);

        // 5. BattleManager + BattleEventListener + tournament listener
        battleManager = new BattleManager(this);
        getServer().getPluginManager().registerEvents(
                new BattleEventListener(battleManager), this);
        battleManager.addBattleEndListener(tournamentManager::onBattleFinished);

        // 6. SpectatorManager (depends on battleManager + tournamentManager)
        spectatorManager = new SpectatorManager(this, matchManager, battleManager, tournamentManager);
        battleManager.setSpectatorManager(spectatorManager);

        // 7. SidebarManager (depends on battleManager)
        sidebarManager = new SidebarManager(battleManager);

        // 7a. ReconnectGraceManager (depends on matchManager + mabConfig)
        reconnectGraceManager = new ReconnectGraceManager(this, matchManager);

        // 8. StatsDatabase + StatsRecorder
        statsDatabase = new StatsDatabase(this);
        try {
            statsDatabase.open();
            statsRepository = new StatsRepository(statsDatabase, getLogger());
            StatsRecorder statsRecorder = new StatsRecorder(statsRepository);
            battleManager.addMatchCompletedListener(statsRecorder::onMatchCompleted);
        } catch (RuntimeException e) {
            getLogger().warning("Stats-DB nicht verfügbar — Stats werden nicht persistiert: " + e.getMessage());
            statsRepository = new StatsRepository(statsDatabase, getLogger());
        }

        // 9. MabCommand (three-arg: plugin, matchManager, spectatorManager)
        PluginCommand mabCmd = getCommand("mab");
        if (mabCmd == null) {
            getLogger().severe("Befehl /mab nicht in plugin.yml deklariert!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        MabCommand mabHandler = new MabCommand(this, matchManager, spectatorManager);
        mabCmd.setExecutor(mabHandler);
        mabCmd.setTabCompleter(mabHandler);

        // 10. PlayerConnectionListener (five-arg: matchManager, worldManager, tournamentManager, spectatorManager, reconnectGraceManager)
        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(matchManager, worldManager, tournamentManager, spectatorManager, reconnectGraceManager), this);

        // 11. Other listeners
        getServer().getPluginManager().registerEvents(
                new MobKillListener(matchManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerDeathFarmListener(matchManager, this), this);
        getServer().getPluginManager().registerEvents(
                new PlayerRespawnListener(this, matchManager), this);
        getServer().getPluginManager().registerEvents(
                new WorldGroupInventoryListener(lobbyInventoryManager), this);

        // 12. WaveBuildGui + MabMenuGui
        waveBuildGui = new WaveBuildGui(matchManager);
        getServer().getPluginManager().registerEvents(waveBuildGui, this);

        mabMenuGui = new MabMenuGui(this, matchManager);
        getServer().getPluginManager().registerEvents(mabMenuGui, this);

        // 13. BossBarManager
        bossBarManager = new MatchBossBarManager(this);

        // 14. Scheduler: tick all managers
        getServer().getScheduler().runTaskTimer(this, () -> {
            matchManager.tickAll();
            bossBarManager.tickAll(matchManager.getActiveMatches());
            sidebarManager.tickAll(matchManager.getActiveMatches());
        }, 20L, 20L);

        getLogger().info("MobArmyBattle aktiviert.");
    }

    @Override
    public void onDisable() {
        if (reconnectGraceManager != null) {
            reconnectGraceManager.cancelAll();
        }
        if (bossBarManager != null) {
            bossBarManager.clear();
        }
        if (sidebarManager != null) {
            sidebarManager.clear();
        }
        if (statsDatabase != null) {
            statsDatabase.close();
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

    public TournamentManager getTournamentManager() {
        return tournamentManager;
    }

    public StatsRepository getStatsRepository() {
        return statsRepository;
    }

    public SpectatorManager getSpectatorManager() {
        return spectatorManager;
    }

    public SidebarManager getSidebarManager() {
        return sidebarManager;
    }

    public MabConfig getMabConfig() {
        return mabConfig;
    }

    public ReconnectGraceManager getReconnectGraceManager() {
        return reconnectGraceManager;
    }

    public void reloadMabConfig() {
        reloadConfig();
        mabConfig = ConfigLoader.load(this);
    }
}
