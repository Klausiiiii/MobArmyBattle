package de.klausiiiii.mobArmyBattle.ui;

import de.klausiiiii.mobArmyBattle.battle.BattleManager;
import de.klausiiiii.mobArmyBattle.battle.BattleSession;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.Team;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SidebarManager {

    private static final String OBJECTIVE = "mab_sidebar";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final BattleManager battleManager;
    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();

    public SidebarManager(BattleManager battleManager) {
        this.battleManager = battleManager;
    }

    public void tickAll(Iterable<Match> matches) {
        long now = System.currentTimeMillis();
        Set<UUID> seenPlayers = new HashSet<>();
        for (Match match : matches) {
            MatchPhaseType phase = match.getCurrentPhase().getType();
            if (phase == MatchPhaseType.LOBBY || phase == MatchPhaseType.FINISHED) continue;
            for (Team team : match.getTeams()) {
                for (UUID id : team.getMemberIds()) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) continue;
                    BattleContext ctx = phase == MatchPhaseType.BATTLE ? buildBattleContext(id, team) : null;
                    List<String> lines = SidebarRenderer.render(match, team, ctx, now);
                    apply(p, lines);
                    seenPlayers.add(id);
                }
            }
        }
        clearStale(seenPlayers);
    }

    private BattleContext buildBattleContext(UUID viewer, Team team) {
        BattleSession session = battleManager.getSessionByPlayer(viewer);
        if (session == null) return null;
        BattleSession.TeamState own = session.getStateByPlayerUUID(viewer);
        if (own == null) return null;
        Team opponent = own.opponent;
        UUID oppCaptain = opponent != null ? opponent.getCaptainId() : null;
        Player oppCaptainPlayer = oppCaptain != null ? Bukkit.getPlayer(oppCaptain) : null;
        String pairName = oppCaptainPlayer != null
                ? oppCaptainPlayer.getName()
                : (oppCaptain != null ? oppCaptain.toString().substring(0, 8) : "?");
        int alive = team.getMemberIds().size() - own.downedPlayers.size();
        return new BattleContext(
                own.aliveLivingMobs.size(),
                own.currentWaveSpawnedTotal,
                own.stats.getMobKills(),
                Math.max(0, alive),
                team.getMemberIds().size(),
                own.currentWaveNumber,
                pairName,
                false,
                0);
    }

    private void apply(Player player, List<String> lines) {
        Scoreboard board = scoreboards.computeIfAbsent(player.getUniqueId(), id -> {
            Scoreboard b = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = b.registerNewObjective(
                    OBJECTIVE,
                    Criteria.DUMMY,
                    LEGACY.deserialize("§e§lMobArmyBattle"));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            return b;
        });
        Objective obj = board.getObjective(OBJECTIVE);
        if (obj == null) return;
        // clear all existing entries before re-drawing
        for (String entry : board.getEntries()) board.resetScores(entry);
        int score = lines.size();
        Set<String> used = new HashSet<>();
        for (String raw : lines) {
            String unique = raw;
            int suffix = 0;
            while (used.contains(unique)) {
                suffix++;
                unique = raw + " ".repeat(suffix);
            }
            used.add(unique);
            obj.getScore(unique).setScore(score);
            score--;
        }
        if (player.getScoreboard() != board) player.setScoreboard(board);
    }

    private void clearStale(Set<UUID> active) {
        Iterator<UUID> it = scoreboards.keySet().iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            if (active.contains(id)) continue;
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            it.remove();
        }
    }

    public void clear() {
        for (UUID id : scoreboards.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        scoreboards.clear();
    }
}
