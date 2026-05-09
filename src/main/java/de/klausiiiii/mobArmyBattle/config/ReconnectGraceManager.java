package de.klausiiiii.mobArmyBattle.config;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReconnectGraceManager {

    private final MobArmyBattle plugin;
    private final MatchManager matchManager;
    private final Map<UUID, BukkitTask> pendingEvictions = new HashMap<>();

    public ReconnectGraceManager(MobArmyBattle plugin, MatchManager matchManager) {
        this.plugin = plugin;
        this.matchManager = matchManager;
    }

    /**
     * Marks a player as absent. If they reconnect within graceSec, restoreAbsent() will cancel the eviction.
     * If grace expires, matchManager.leaveMatch is called.
     *
     * @return true if grace was scheduled (caller should NOT call leaveMatch); false if grace=0 or no match
     */
    public boolean markAbsent(UUID playerId) {
        if (matchManager.getMatchOf(playerId) == null) return false;
        int graceSec = plugin.getMabConfig().reconnect().graceSec();
        if (graceSec <= 0) return false;

        cancelPending(playerId);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingEvictions.remove(playerId);
            matchManager.leaveMatch(playerId);
        }, graceSec * 20L);
        pendingEvictions.put(playerId, task);
        return true;
    }

    public boolean isAbsent(UUID playerId) {
        return pendingEvictions.containsKey(playerId);
    }

    /**
     * Called on rejoin: cancels pending eviction. Caller is responsible for teleporting
     * the player to the appropriate world (current match phase).
     */
    public void restoreAbsent(UUID playerId) {
        cancelPending(playerId);
    }

    public void cancelAll() {
        for (BukkitTask t : pendingEvictions.values()) t.cancel();
        pendingEvictions.clear();
    }

    private void cancelPending(UUID playerId) {
        BukkitTask existing = pendingEvictions.remove(playerId);
        if (existing != null) existing.cancel();
    }
}
