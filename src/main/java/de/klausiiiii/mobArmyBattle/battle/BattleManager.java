package de.klausiiiii.mobArmyBattle.battle;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.spectator.SpectatorManager;
import de.klausiiiii.mobArmyBattle.ui.Notifications;
import de.klausiiiii.mobArmyBattle.wave.Wave;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class BattleManager {

    private final MobArmyBattle plugin;
    private final ArenaLoader arenaLoader;
    private final WaveSpawner waveSpawner = new WaveSpawner();
    private final Map<String, List<BattleSession>> matchSessions = new HashMap<>();
    private final Map<UUID, BattleSession> sessionByMobUUID = new HashMap<>();
    private final List<BiConsumer<Match, UUID>> battleEndListeners = new ArrayList<>();
    private final List<BiConsumer<Match, List<TeamOutcome>>> matchCompletedListeners = new ArrayList<>();
    private SpectatorManager spectatorManager;

    public void setSpectatorManager(SpectatorManager mgr) {
        this.spectatorManager = mgr;
    }

    public BattleManager(MobArmyBattle plugin) {
        this.plugin = plugin;
        this.arenaLoader = new ArenaLoader(plugin);
    }

    public void startBattlesFor(Match match) {
        List<Team> activeTeams = new ArrayList<>();
        for (Team t : match.getTeams()) {
            if (!t.isDisbanded() && t.size() > 0) activeTeams.add(t);
        }
        if (activeTeams.size() < 2) {
            return;
        }
        TeamPairing.Result pairing = TeamPairing.pair(activeTeams);
        List<BattleSession> sessions = new ArrayList<>();
        WorldManager wm = plugin.getWorldManager();

        for (TeamPair pair : pairing.getPairs()) {
            World arenaA = wm.createArenaWorld(match.getId(), idOf(match, pair.getTeamA()));
            arenaLoader.loadInto(arenaA);
            List<Location> spawnsA = ArenaSpawnScanner.scanAndConsume(arenaA, 50);

            World arenaB = wm.createArenaWorld(match.getId(), idOf(match, pair.getTeamB()));
            arenaLoader.loadInto(arenaB);
            List<Location> spawnsB = ArenaSpawnScanner.scanAndConsume(arenaB, 50);

            BattleSession session = new BattleSession(match, pair, arenaA, spawnsA, arenaB, spawnsB);
            teleportTeam(pair.getTeamA(), arenaA);
            teleportTeam(pair.getTeamB(), arenaB);

            schedulePrep(session, session.getStateA(), 1);
            schedulePrep(session, session.getStateB(), 1);

            sessions.add(session);
        }
        matchSessions.put(match.getId(), sessions);

        for (Team byeTeam : pairing.getByeTeams()) {
            broadcastTeam(byeTeam, "§eDu hast einen Bye — automatischer Sieg.");
        }
    }

    private String idOf(Match match, Team team) {
        int idx = match.getTeams().indexOf(team);
        return "team-" + idx + "-arena";
    }

    private void teleportTeam(Team team, World arena) {
        Location spawn = arenaLoader.getPlayerSpawn(arena);
        for (UUID id : team.getMemberIds()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.teleport(spawn);
                Double maxHp = p.getAttribute(Attribute.MAX_HEALTH) != null
                        ? p.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
                p.setHealth(maxHp);
                p.setFoodLevel(20);
                p.setGameMode(GameMode.SURVIVAL);
            }
        }
    }

    private void schedulePrep(BattleSession session, BattleSession.TeamState state, int waveNum) {
        state.currentWaveNumber = waveNum;
        int prepSec = plugin != null ? plugin.getMabConfig().phaseDurations().prepDurationSec() : 30;
        state.prepEndsAt = System.currentTimeMillis() + prepSec * 1000L;
        de.klausiiiii.mobArmyBattle.ui.Notifications.wavePrep(state.team, waveNum, prepSec);
        broadcastTeam(state.team, "§eBauphase: Welle " + waveNum + " in " + prepSec + "s.");
        if (plugin != null) {
            state.prepTask = Bukkit.getScheduler().runTaskLater(plugin,
                    () -> spawnWaveActual(session, state),
                    prepSec * 20L);
        } else {
            // tests: invoke directly
            spawnWaveActual(session, state);
        }
    }

    private void spawnWaveActual(BattleSession session, BattleSession.TeamState state) {
        state.prepTask = null;
        state.prepEndsAt = 0L;
        Wave wave = state.currentWaveNumber == 1 ? state.opponent.getWave1() : state.opponent.getWave2();
        if (wave == null || wave.isForfeited() || wave.totalMobCount() == 0) {
            broadcastTeam(state.team, "§eGegner-Welle " + state.currentWaveNumber + " ist leer/forfeit — übersprungen.");
            state.stats.recordWaveSurvived();
            checkAdvance(session, state);
            return;
        }
        List<LivingEntity> mobs = waveSpawner.spawnWave(state.arena, state.spawnPoints, wave);
        for (LivingEntity m : mobs) {
            state.aliveLivingMobs.add(m.getUniqueId());
            sessionByMobUUID.put(m.getUniqueId(), session);
        }
        state.currentWaveSpawnedTotal = mobs.size();
        state.currentWaveSpawnAt = System.currentTimeMillis();
        broadcastTeam(state.team, "§6Welle " + state.currentWaveNumber + " gestartet — " + mobs.size() + " Mobs.");
        de.klausiiiii.mobArmyBattle.ui.Notifications.waveSpawned(state.team, state.currentWaveNumber, mobs.size());
        int hardTimeoutMin = plugin != null ? plugin.getMabConfig().phaseDurations().waveHardTimeoutMin() : 10;
        if (plugin != null) {
            state.hardTimeoutTask = Bukkit.getScheduler().runTaskLater(plugin,
                    () -> onHardTimeout(session, state),
                    hardTimeoutMin * 60L * 20L);
        }
    }

    private void onHardTimeout(BattleSession session, BattleSession.TeamState state) {
        state.hardTimeoutTask = null;
        for (UUID mobUUID : new java.util.HashSet<>(state.aliveLivingMobs)) {
            sessionByMobUUID.remove(mobUUID);
            org.bukkit.entity.Entity e = Bukkit.getEntity(mobUUID);
            if (e != null) e.remove();
        }
        state.aliveLivingMobs.clear();
        de.klausiiiii.mobArmyBattle.ui.Notifications.waveTimedOut(state.team, state.currentWaveNumber);
        broadcastTeam(state.team, "§cZeit abgelaufen — Welle " + state.currentWaveNumber + " verloren.");
        if (!state.stats.isFinished()) {
            state.stats.markFinished(session.elapsedMs());
        }
        checkSessionEnd(session);
    }

    private void scheduleWavePause(BattleSession session, BattleSession.TeamState state) {
        int pauseSec = plugin != null ? plugin.getMabConfig().phaseDurations().wavePauseSec() : 10;
        if (pauseSec <= 0 || plugin == null) {
            schedulePrep(session, state, state.currentWaveNumber + 1);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> schedulePrep(session, state, state.currentWaveNumber + 1),
                pauseSec * 20L);
    }

    private void cancelTasks(BattleSession.TeamState state) {
        if (state.prepTask != null) {
            state.prepTask.cancel();
            state.prepTask = null;
        }
        if (state.hardTimeoutTask != null) {
            state.hardTimeoutTask.cancel();
            state.hardTimeoutTask = null;
        }
        state.prepEndsAt = 0L;
    }

    public BattleSession getSessionByPlayer(UUID playerUUID) {
        for (java.util.List<BattleSession> sessions : matchSessions.values()) {
            for (BattleSession session : sessions) {
                if (session.getStateByPlayerUUID(playerUUID) != null) return session;
            }
        }
        return null;
    }

    public void onMobKilled(UUID mobUUID, UUID killerUUID) {
        BattleSession session = sessionByMobUUID.remove(mobUUID);
        if (session == null) return;
        BattleSession.TeamState state = null;
        if (session.getStateA().aliveLivingMobs.contains(mobUUID)) state = session.getStateA();
        else if (session.getStateB().aliveLivingMobs.contains(mobUUID)) state = session.getStateB();
        if (state == null) return;
        state.aliveLivingMobs.remove(mobUUID);
        if (killerUUID != null && state.team.hasMember(killerUUID)) {
            state.stats.recordMobKill();
        }
        if (state.aliveLivingMobs.isEmpty()) {
            if (state.hardTimeoutTask != null) {
                state.hardTimeoutTask.cancel();
                state.hardTimeoutTask = null;
            }
            state.stats.recordWaveSurvived();
            checkAdvance(session, state);
        }
    }

    private void checkAdvance(BattleSession session, BattleSession.TeamState state) {
        if (state.currentWaveNumber >= 2) {
            if (!state.stats.isFinished()) {
                Notifications.wavePassed(state.team, state.currentWaveNumber);
                state.stats.markFinished(session.elapsedMs());
                broadcastTeam(state.team, "§aDu hast beide Wellen überlebt!");
            }
            checkSessionEnd(session);
        } else {
            Notifications.wavePassed(state.team, state.currentWaveNumber);
            scheduleWavePause(session, state);
        }
    }

    public void onPlayerDeath(UUID playerUUID) {
        for (List<BattleSession> sessions : matchSessions.values()) {
            for (BattleSession session : sessions) {
                BattleSession.TeamState state = session.getStateByPlayerUUID(playerUUID);
                if (state == null) continue;
                state.downedPlayers.add(playerUUID);
                Player p = Bukkit.getPlayer(playerUUID);
                if (p != null) {
                    p.setGameMode(GameMode.SPECTATOR);
                }
                boolean allDown = true;
                for (UUID id : state.team.getMemberIds()) {
                    if (!state.downedPlayers.contains(id)) {
                        allDown = false;
                        break;
                    }
                }
                if (allDown) {
                    if (!state.stats.isFinished()) {
                        state.stats.markFinished(session.elapsedMs());
                    }
                    cancelTasks(state);
                    broadcastTeam(state.team, "§cAlle Spieler tot — Battle für euer Team beendet.");
                    checkSessionEnd(session);
                }
                return;
            }
        }
    }

    private void checkSessionEnd(BattleSession session) {
        BattleSession.TeamState a = session.getStateA();
        BattleSession.TeamState b = session.getStateB();
        if (!a.stats.isFinished() || !b.stats.isFinished()) return;
        if (session.isConcluded()) return;
        session.markConcluded();
        BattleResult.Winner winner = BattleResult.compare(a.stats, b.stats);
        announceResult(session, winner);
        Match match = session.getMatch();
        Team winningTeam = (winner == BattleResult.Winner.B) ? b.team : a.team;
        UUID winnerCaptain = winningTeam.getCaptainId();
        if (winnerCaptain != null) {
            for (BiConsumer<Match, UUID> listener : battleEndListeners) {
                listener.accept(match, winnerCaptain);
            }
        }
        List<BattleSession> all = matchSessions.get(match.getId());
        if (all != null && all.stream().allMatch(BattleSession::isConcluded)) {
            notifyMatchCompleted(match, all);
            if (spectatorManager != null) spectatorManager.evictAll(match.getId());
            match.transitionTo(new de.klausiiiii.mobArmyBattle.match.phase.FinishedPhase(plugin));
        }
    }

    private void notifyMatchCompleted(Match match, List<BattleSession> sessions) {
        if (matchCompletedListeners.isEmpty()) return;
        List<TeamOutcome> outcomes = new ArrayList<>();
        for (BattleSession session : sessions) {
            BattleSession.TeamState a = session.getStateA();
            BattleSession.TeamState b = session.getStateB();
            BattleResult.Winner winner = BattleResult.compare(a.stats, b.stats);
            boolean aWon = winner != BattleResult.Winner.B;
            outcomes.add(new TeamOutcome(
                    a.team.getCaptainId(),
                    java.util.Set.copyOf(a.team.getMemberIds()),
                    aWon,
                    a.stats.getMobKills()));
            outcomes.add(new TeamOutcome(
                    b.team.getCaptainId(),
                    java.util.Set.copyOf(b.team.getMemberIds()),
                    !aWon,
                    b.stats.getMobKills()));
        }
        for (BiConsumer<Match, List<TeamOutcome>> listener : matchCompletedListeners) {
            listener.accept(match, outcomes);
        }
    }

    public void addBattleEndListener(BiConsumer<Match, UUID> listener) {
        battleEndListeners.add(listener);
    }

    public void addMatchCompletedListener(BiConsumer<Match, List<TeamOutcome>> listener) {
        matchCompletedListeners.add(listener);
    }

    private void announceResult(BattleSession session, BattleResult.Winner winner) {
        BattleSession.TeamState a = session.getStateA();
        BattleSession.TeamState b = session.getStateB();
        String msg;
        if (winner == BattleResult.Winner.A) {
            msg = "§a" + teamName(a.team) + " gewinnt gegen " + teamName(b.team);
        } else if (winner == BattleResult.Winner.B) {
            msg = "§a" + teamName(b.team) + " gewinnt gegen " + teamName(a.team);
        } else {
            msg = "§e" + teamName(a.team) + " vs " + teamName(b.team) + ": Unentschieden";
        }
        broadcastTeam(a.team, msg);
        broadcastTeam(b.team, msg);
        String aName = teamName(a.team);
        String bName = teamName(b.team);
        if (winner == BattleResult.Winner.A) {
            Notifications.victory(a.team, aName);
            Notifications.defeat(b.team, aName);
        } else if (winner == BattleResult.Winner.B) {
            Notifications.victory(b.team, bName);
            Notifications.defeat(a.team, bName);
        }
    }

    private String teamName(Team team) {
        UUID cap = team.getCaptainId();
        if (cap == null) return "Team";
        Player p = Bukkit.getPlayer(cap);
        return p != null ? "Team " + p.getName() : "Team";
    }

    private void broadcastTeam(Team team, String message) {
        for (UUID id : team.getMemberIds()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(message);
        }
    }

    public void cleanup(Match match) {
        List<BattleSession> sessions = matchSessions.remove(match.getId());
        if (sessions == null) return;
        for (BattleSession s : sessions) {
            cancelTasks(s.getStateA());
            cancelTasks(s.getStateB());
            for (UUID mob : s.getStateA().aliveLivingMobs) sessionByMobUUID.remove(mob);
            for (UUID mob : s.getStateB().aliveLivingMobs) sessionByMobUUID.remove(mob);
        }
        if (spectatorManager != null) {
            spectatorManager.evictAll(match.getId());
        }
    }
}
