package de.klausiiiii.mobArmyBattle;

import de.klausiiiii.mobArmyBattle.battle.BattleManager;
import de.klausiiiii.mobArmyBattle.config.ConfigLoader;
import de.klausiiiii.mobArmyBattle.config.MabConfig;
import de.klausiiiii.mobArmyBattle.config.ReconnectGraceManager;
import de.klausiiiii.mobArmyBattle.bossbar.MatchBossBarManager;
import de.klausiiiii.mobArmyBattle.command.ChatInputManager;
import de.klausiiiii.mobArmyBattle.command.MabCommand;
import de.klausiiiii.mobArmyBattle.command.MabMenuGui;
import de.klausiiiii.mobArmyBattle.command.TeamSelectorGui;
import de.klausiiiii.mobArmyBattle.listener.BattleEventListener;
import de.klausiiiii.mobArmyBattle.listener.MobKillListener;
import de.klausiiiii.mobArmyBattle.listener.PlayerConnectionListener;
import de.klausiiiii.mobArmyBattle.listener.PlayerDeathFarmListener;
import de.klausiiiii.mobArmyBattle.listener.PlayerRespawnListener;
import de.klausiiiii.mobArmyBattle.listener.LobbyProtectionListener;
import de.klausiiiii.mobArmyBattle.listener.PlayerFreezeManager;
import de.klausiiiii.mobArmyBattle.listener.WaveBuildProtectionListener;
import de.klausiiiii.mobArmyBattle.listener.WorldGroupInventoryListener;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.spectator.DeathSpectateGui;
import de.klausiiiii.mobArmyBattle.spectator.SpectatorManager;
import de.klausiiiii.mobArmyBattle.stats.StatsDatabase;
import de.klausiiiii.mobArmyBattle.stats.StatsRecorder;
import de.klausiiiii.mobArmyBattle.stats.StatsRepository;
import de.klausiiiii.mobArmyBattle.tournament.TournamentManager;
import de.klausiiiii.mobArmyBattle.ui.SidebarManager;
import de.klausiiiii.mobArmyBattle.wave.WaveBuildGui;
import de.klausiiiii.mobArmyBattle.world.LobbyInventoryManager;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private DeathSpectateGui deathSpectateGui;
    private SidebarManager sidebarManager;
    private ReconnectGraceManager reconnectGraceManager;
    private PlayerFreezeManager playerFreezeManager;
    private ChatInputManager chatInputManager;
    private TeamSelectorGui teamSelectorGui;

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

        // 6b. DeathSpectateGui (öffnet sich bei Tod in Battle-Phase + via /mab spectate)
        deathSpectateGui = new DeathSpectateGui(this, battleManager, spectatorManager);
        getServer().getPluginManager().registerEvents(deathSpectateGui, this);
        battleManager.setDeathSpectateGui(deathSpectateGui);

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
        getServer().getPluginManager().registerEvents(
                new LobbyProtectionListener(this), this);

        // 11b. PlayerFreezeManager (used by WaveBuildPhase to lock players in place)
        playerFreezeManager = new PlayerFreezeManager();
        getServer().getPluginManager().registerEvents(playerFreezeManager, this);

        // 11c. WaveBuildProtectionListener — block/bucket/mob-damage gated in WAVE_BUILD
        getServer().getPluginManager().registerEvents(
                new WaveBuildProtectionListener(matchManager), this);

        // 12. WaveBuildGui + MabMenuGui
        waveBuildGui = new WaveBuildGui(matchManager);
        getServer().getPluginManager().registerEvents(waveBuildGui, this);

        mabMenuGui = new MabMenuGui(this, matchManager);
        getServer().getPluginManager().registerEvents(mabMenuGui, this);

        // 12b. ChatInputManager + TeamSelectorGui (depends on matchManager)
        chatInputManager = new ChatInputManager(this);
        getServer().getPluginManager().registerEvents(chatInputManager, this);
        teamSelectorGui = new TeamSelectorGui(this, matchManager, chatInputManager);
        getServer().getPluginManager().registerEvents(teamSelectorGui, this);

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

    public DeathSpectateGui getDeathSpectateGui() {
        return deathSpectateGui;
    }

    public SidebarManager getSidebarManager() {
        return sidebarManager;
    }

    public MabConfig getMabConfig() {
        return mabConfig;
    }

    /**
     * Returns the per-match config snapshot if the match has one, else the
     * plugin-wide global config. Use this at read sites so per-match overrides
     * take effect without breaking call paths (tests, tournament) that don't
     * set a match config.
     */
    public MabConfig effectiveConfig(de.klausiiiii.mobArmyBattle.match.Match match) {
        if (match != null && match.getMabConfig() != null) {
            return match.getMabConfig();
        }
        return mabConfig;
    }

    public ReconnectGraceManager getReconnectGraceManager() {
        return reconnectGraceManager;
    }

    public PlayerFreezeManager getPlayerFreezeManager() {
        return playerFreezeManager;
    }

    public TeamSelectorGui getTeamSelectorGui() {
        return teamSelectorGui;
    }

    public ChatInputManager getChatInputManager() {
        return chatInputManager;
    }

    public void reloadMabConfig() {
        reloadConfig();
        mabConfig = ConfigLoader.load(this);
    }

    /**
     * Broadcasts a clickable join-link for a freshly created match to every player
     * currently in the lobby world (captain included). Called by both the {@code /mab
     * create} command path and the villager-menu create path so neither bypasses it.
     */
    public void broadcastNewMatch(Player captain, int maxTeamSize) {
        String joinCommand = "/mab join " + captain.getName();
        Component joinLink = Component.text("[» Beitreten «]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(joinCommand))
                .hoverEvent(HoverEvent.showText(
                        Component.text("Klicke, um " + captain.getName() + "s Match beizutreten\n", NamedTextColor.GRAY)
                                .append(Component.text(joinCommand, NamedTextColor.YELLOW))));
        Component message = Component.text("» ", NamedTextColor.GOLD)
                .append(Component.text(captain.getName(), NamedTextColor.AQUA))
                .append(Component.text(" hat ein Match erstellt (max " + maxTeamSize + " pro Team). ",
                        NamedTextColor.GRAY))
                .append(joinLink);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(captain)) continue;
            if (!WorldManager.LOBBY_WORLD_NAME.equals(p.getWorld().getName())) continue;
            p.sendMessage(message);
        }
    }

    /**
     * Call BEFORE {@code matchManager.leaveMatch(leaver)}. If the leaver is the
     * match host (= captain of team 1, i.e. the player who created the match),
     * every other member of the match is evicted: removed from the match,
     * teleported to the lobby and notified. No-op if the leaver isn't in a match
     * or isn't the host.
     */
    public void cascadeIfHostLeaving(Player leaver) {
        Match match = matchManager.getMatchOf(leaver.getUniqueId());
        if (match == null) return;
        UUID leaverId = leaver.getUniqueId();
        if (!leaverId.equals(match.getHostId())) return;
        List<UUID> others = new ArrayList<>();
        for (Team team : match.getTeams()) {
            for (UUID memberId : team.getMemberIds()) {
                if (!memberId.equals(leaverId)) {
                    others.add(memberId);
                }
            }
        }
        for (UUID otherId : others) {
            try {
                matchManager.leaveMatch(otherId);
            } catch (RuntimeException ignored) {
                // Already removed by an upstream cleanup — fine.
            }
            Player other = Bukkit.getPlayer(otherId);
            if (other != null) {
                worldManager.teleportToLobby(other);
                other.sendMessage(Component.text(
                        "Der Host (" + leaver.getName() + ") hat das Match verlassen. Du wurdest in die Lobby gebracht.",
                        NamedTextColor.YELLOW));
            }
        }
    }
}
