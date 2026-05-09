package de.klausiiiii.mobArmyBattle;

import de.klausiiiii.mobArmyBattle.battle.BattleManager;
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
import de.klausiiiii.mobArmyBattle.stats.StatsDatabase;
import de.klausiiiii.mobArmyBattle.stats.StatsRecorder;
import de.klausiiiii.mobArmyBattle.stats.StatsRepository;
import de.klausiiiii.mobArmyBattle.tournament.TournamentManager;
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
    private TournamentManager tournamentManager;
    private StatsDatabase statsDatabase;
    private StatsRepository statsRepository;

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
        tournamentManager = new TournamentManager(this, matchManager);

        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(matchManager, worldManager, tournamentManager), this);
        getServer().getPluginManager().registerEvents(
                new MobKillListener(matchManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerDeathFarmListener(matchManager), this);
        getServer().getPluginManager().registerEvents(
                new PlayerRespawnListener(this, matchManager), this);
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
        battleManager.addBattleEndListener(tournamentManager::onBattleFinished);

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
}
