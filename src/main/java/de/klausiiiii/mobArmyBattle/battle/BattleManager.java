package de.klausiiiii.mobArmyBattle.battle;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.Team;
import de.klausiiiii.mobArmyBattle.spectator.DeathSpectateGui;
import de.klausiiiii.mobArmyBattle.spectator.SpectatorManager;
import de.klausiiiii.mobArmyBattle.ui.Notifications;
import de.klausiiiii.mobArmyBattle.wave.Wave;
import de.klausiiiii.mobArmyBattle.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Golem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

public class BattleManager {

    /**
     * Per-match tournament state — tracks rounds so a single Match can run
     * multi-round single-elimination until exactly one team survives.
     * Eliminated teams (from wave-build) and per-round byes are kept here so
     * we know who to route into spectator each round.
     */
    private static class MatchTournamentState {
        int currentRound = 0;
        final List<Team> currentRoundByes = new ArrayList<>();
        final Map<Team, Integer> aggregatedKills = new HashMap<>();
        Team finalWinner;
    }

    private final MobArmyBattle plugin;
    private final ArenaLoader arenaLoader;
    private final WaveSpawner waveSpawner = new WaveSpawner();
    private final Map<String, List<BattleSession>> matchSessions = new HashMap<>();
    private final Map<String, MatchTournamentState> tournamentByMatch = new HashMap<>();
    private final Map<UUID, BattleSession> sessionByMobUUID = new HashMap<>();
    private final Map<UUID, Long> mobLastAttackMs = new HashMap<>();
    private final List<BiConsumer<Match, UUID>> battleEndListeners = new ArrayList<>();
    private final List<BiConsumer<Match, List<TeamOutcome>>> matchCompletedListeners = new ArrayList<>();
    private SpectatorManager spectatorManager;
    private DeathSpectateGui deathSpectateGui;

    public void setSpectatorManager(SpectatorManager mgr) {
        this.spectatorManager = mgr;
    }

    public void setDeathSpectateGui(DeathSpectateGui gui) {
        this.deathSpectateGui = gui;
    }

    public BattleManager(MobArmyBattle plugin) {
        this.plugin = plugin;
        this.arenaLoader = new ArenaLoader(plugin);
        if (plugin != null) {
            // Aggression-Ticker: setzt Targets, drückt passive Mobs zum Spieler + Manual-Damage.
            Bukkit.getScheduler().runTaskTimer(plugin, this::tickAggression, 10L, 10L);
        }
    }

    public void startBattlesFor(Match match) {
        List<Team> activeTeams = new ArrayList<>();
        List<Team> eliminatedTeams = new ArrayList<>();
        for (Team t : match.getTeams()) {
            if (t.isDisbanded() || t.size() == 0) continue;
            if (t.isEliminated()) {
                eliminatedTeams.add(t);
            } else {
                activeTeams.add(t);
            }
        }
        if (activeTeams.size() < 2) {
            return;
        }
        MatchTournamentState ts = new MatchTournamentState();
        tournamentByMatch.put(match.getId(), ts);
        startRound(match, activeTeams);
    }

    private void startRound(Match match, List<Team> teams) {
        MatchTournamentState ts = tournamentByMatch.get(match.getId());
        if (ts == null) return;
        ts.currentRound++;

        if (ts.currentRound > 1) {
            broadcastMatch(match, "§6Runde " + ts.currentRound + " startet!");
        }

        TeamPairing.Result pairing = TeamPairing.pair(teams);
        List<BattleSession> sessions = new ArrayList<>();
        WorldManager wm = plugin.getWorldManager();
        de.klausiiiii.mobArmyBattle.config.MabConfig matchCfg =
                plugin != null ? plugin.effectiveConfig(match) : null;

        for (TeamPair pair : pairing.getPairs()) {
            World arenaA = wm.createArenaWorld(match.getId(),
                    roundedIdOf(match, pair.getTeamA(), ts.currentRound), matchCfg);
            arenaLoader.loadInto(arenaA);
            List<Location> spawnsA = ArenaSpawnScanner.scanAndConsume(arenaA, 50);

            World arenaB = wm.createArenaWorld(match.getId(),
                    roundedIdOf(match, pair.getTeamB(), ts.currentRound), matchCfg);
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

        ts.currentRoundByes.clear();
        ts.currentRoundByes.addAll(pairing.getByeTeams());

        // Route every team that's not actively paired this round into a random
        // session as a spectator: byes (auto-advance), eliminated teams (empty
        // pool at end of farm), and losers from previous rounds.
        Set<Team> paired = new HashSet<>();
        for (TeamPair pair : pairing.getPairs()) {
            paired.add(pair.getTeamA());
            paired.add(pair.getTeamB());
        }
        for (Team t : match.getTeams()) {
            if (t.isDisbanded() || t.size() == 0) continue;
            if (paired.contains(t)) continue;
            String label;
            if (pairing.getByeTeams().contains(t)) {
                label = "§eDu hast einen Bye in Runde " + ts.currentRound
                        + " — automatisch in die nächste Runde.";
            } else {
                label = "§7Zuschauer in Runde " + ts.currentRound
                        + " — du wirst zu einer zufälligen Arena teleportiert.";
            }
            broadcastTeam(t, label);
            sendByeToRandomArena(t, sessions);
        }
    }

    private String roundedIdOf(Match match, Team team, int round) {
        int idx = match.getTeams().indexOf(team);
        return "team-" + idx + "-r" + round + "-arena";
    }

    private void sendByeToRandomArena(Team byeTeam, List<BattleSession> sessions) {
        if (spectatorManager == null || sessions.isEmpty()) return;
        for (UUID memberId : byeTeam.getMemberIds()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p == null) continue;
            BattleSession randomSession = sessions.get(ThreadLocalRandom.current().nextInt(sessions.size()));
            Team randomTeam = ThreadLocalRandom.current().nextBoolean()
                    ? randomSession.getStateA().team
                    : randomSession.getStateB().team;
            UUID cap = randomTeam.getCaptainId();
            if (cap != null) {
                spectatorManager.startDeathSpectate(p, cap);
            }
        }
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
        Wave opponentWave = waveNum == 1 ? state.opponent.getWave1() : state.opponent.getWave2();
        if (opponentWave == null || opponentWave.isForfeited() || opponentWave.totalMobCount() == 0) {
            broadcastTeam(state.team, "§eGegner-Welle " + waveNum + " ist leer/forfeit — übersprungen.");
            // Don't record this as "survived" — only real waves count toward the
            // tiebreaker, otherwise forfeiting your own wave 2 hands the opponent a
            // free wave-survival and skews the result.
            checkAdvance(session, state);
            return;
        }
        int prepSec = plugin != null ? plugin.effectiveConfig(session.getMatch()).phaseDurations().prepDurationSec() : 30;
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
            // No recordWaveSurvived here either — see schedulePrep comment.
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
        int hardTimeoutMin = plugin != null ? plugin.effectiveConfig(session.getMatch()).phaseDurations().waveHardTimeoutMin() : 10;
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
        int pauseSec = plugin != null ? plugin.effectiveConfig(session.getMatch()).phaseDurations().wavePauseSec() : 10;
        if (pauseSec <= 0 || plugin == null) {
            schedulePrep(session, state, state.currentWaveNumber + 1);
            return;
        }
        state.wavePauseTask = Bukkit.getScheduler().runTaskLater(plugin,
                () -> {
                    state.wavePauseTask = null;
                    schedulePrep(session, state, state.currentWaveNumber + 1);
                },
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
        if (state.wavePauseTask != null) {
            state.wavePauseTask.cancel();
            state.wavePauseTask = null;
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
        mobLastAttackMs.remove(mobUUID);
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
                for (UUID id : state.team.getMemberIds()) {
                    if (state.downedPlayers.contains(id)) continue;
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) p.setGameMode(GameMode.SPECTATOR);
                }
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
                    // Spectator-Mode während PlayerDeathEvent skipt den Respawn-Screen
                    // (Paper-Verhalten) — Spieler bleibt in der eigenen Arena als Zuschauer
                    // und kann Teamkollegen kämpfen sehen.
                    p.setGameMode(GameMode.SPECTATOR);
                    // Belt-and-suspenders: re-apply next tick in case the in-death
                    // transition didn't stick (Paper version drift / respawn screen race).
                    if (plugin != null) {
                        UUID id = playerUUID;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player live = Bukkit.getPlayer(id);
                            if (live != null && live.isOnline()
                                    && live.getGameMode() != GameMode.SPECTATOR) {
                                live.setGameMode(GameMode.SPECTATOR);
                            }
                        });
                    }
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
                    // Team ist komplett raus — jetzt darf zum Pair-Partner gespectated werden.
                    routeTeamToOpponentArena(state);
                    checkSessionEnd(session);
                }
                return;
            }
        }
    }

    private void routeTeamToOpponentArena(BattleSession.TeamState state) {
        if (spectatorManager == null) return;
        UUID opponentCap = state.opponent.getCaptainId();
        if (opponentCap == null) return;
        // Defer one tick: when this is called from inside PlayerDeathEvent the
        // dying player is mid-respawn and a sync teleport may not stick.
        for (UUID memberId : state.team.getMemberIds()) {
            Player p = Bukkit.getPlayer(memberId);
            if (p == null) continue;
            UUID cap = opponentCap;
            if (plugin != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player live = Bukkit.getPlayer(memberId);
                    if (live != null && live.isOnline()) {
                        spectatorManager.startDeathSpectate(live, cap);
                    }
                });
            } else {
                spectatorManager.startDeathSpectate(p, cap);
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
            onRoundComplete(match, all);
        }
    }

    private void onRoundComplete(Match match, List<BattleSession> roundSessions) {
        MatchTournamentState ts = tournamentByMatch.get(match.getId());
        if (ts == null) {
            // Should not happen — startBattlesFor always initialises tournament state.
            scheduleFinalize(match);
            return;
        }
        // Aggregate kills + collect winners.
        List<Team> survivors = new ArrayList<>();
        for (BattleSession s : roundSessions) {
            BattleSession.TeamState sa = s.getStateA();
            BattleSession.TeamState sb = s.getStateB();
            ts.aggregatedKills.merge(sa.team, sa.stats.getMobKills(), Integer::sum);
            ts.aggregatedKills.merge(sb.team, sb.stats.getMobKills(), Integer::sum);
            BattleResult.Winner w = BattleResult.compare(sa.stats, sb.stats);
            Team roundWinner = (w == BattleResult.Winner.B) ? sb.team : sa.team;
            survivors.add(roundWinner);
        }
        survivors.addAll(ts.currentRoundByes);

        // Losers of this round are no longer in the running.
        for (Team t : new ArrayList<>(match.getTeams())) {
            if (survivors.contains(t)) continue;
            if (t.isDisbanded() || t.size() == 0) continue;
            if (t.isEliminated()) continue;
            t.eliminate();
        }

        if (survivors.size() <= 1) {
            ts.finalWinner = survivors.isEmpty() ? null : survivors.get(0);
            announceFinalWinner(match, ts);
            notifyMatchCompletedFromTournament(match, ts);
            scheduleFinalize(match);
            return;
        }

        // More rounds to go — schedule next round after the post-battle view delay.
        int delaySec = plugin != null
                ? plugin.effectiveConfig(match).phaseDurations().postBattleViewSec()
                : 0;
        int delayTicks = delaySec * 20;
        broadcastMatch(match, "§6Runde " + ts.currentRound + " abgeschlossen — Runde "
                + (ts.currentRound + 1) + " in " + Math.max(delaySec, 1) + "s. Überlebende: " + survivors.size());
        Runnable nextRound = () -> {
            cleanupRoundResources(match);
            startRound(match, survivors);
        };
        if (plugin != null && delayTicks > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, nextRound, delayTicks);
        } else {
            nextRound.run();
        }
    }

    private void scheduleFinalize(Match match) {
        int delaySec = plugin != null
                ? plugin.effectiveConfig(match).phaseDurations().postBattleViewSec()
                : 0;
        int delayTicks = delaySec * 20;
        if (plugin != null && delayTicks > 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> finishMatch(match), delayTicks);
        } else {
            finishMatch(match);
        }
    }

    private void finishMatch(Match match) {
        if (match.getCurrentPhase().getType() == de.klausiiiii.mobArmyBattle.match.MatchPhaseType.FINISHED) {
            return;
        }
        if (spectatorManager != null) spectatorManager.evictAll(match.getId());
        match.transitionTo(new de.klausiiiii.mobArmyBattle.match.phase.FinishedPhase(plugin));
    }

    private void cleanupRoundResources(Match match) {
        List<BattleSession> sessions = matchSessions.remove(match.getId());
        if (sessions == null) return;
        Set<World> arenas = new HashSet<>();
        for (BattleSession s : sessions) {
            cancelTasks(s.getStateA());
            cancelTasks(s.getStateB());
            cleanupMobs(s.getStateA().aliveLivingMobs);
            cleanupMobs(s.getStateB().aliveLivingMobs);
            if (s.getStateA().arena != null) arenas.add(s.getStateA().arena);
            if (s.getStateB().arena != null) arenas.add(s.getStateB().arena);
        }
        if (spectatorManager != null) spectatorManager.evictAll(match.getId());
        for (World w : arenas) {
            plugin.getWorldManager().deleteWorld(w);
        }
    }

    private void announceFinalWinner(Match match, MatchTournamentState ts) {
        Team winner = ts.finalWinner;
        if (winner == null) {
            broadcastMatch(match, "§eMatch endet ohne Sieger.");
            return;
        }
        String name = teamName(winner);
        broadcastMatch(match, "§6§lMATCH-SIEGER: " + name + " (Runde " + ts.currentRound + ")");
        Notifications.victory(winner, name);
        for (Team t : match.getTeams()) {
            if (t == winner) continue;
            if (t.isDisbanded() || t.size() == 0) continue;
            Notifications.defeat(t, name);
        }
    }

    private void notifyMatchCompletedFromTournament(Match match, MatchTournamentState ts) {
        if (matchCompletedListeners.isEmpty()) return;
        List<TeamOutcome> outcomes = new ArrayList<>();
        for (Team t : match.getTeams()) {
            if (t.getCaptainId() == null) continue;
            boolean won = (t == ts.finalWinner);
            int kills = ts.aggregatedKills.getOrDefault(t, 0);
            outcomes.add(new TeamOutcome(
                    t.getCaptainId(),
                    java.util.Set.copyOf(t.getMemberIds()),
                    won,
                    kills));
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

    private void broadcastMatch(Match match, String message) {
        for (Team t : match.getTeams()) {
            broadcastTeam(t, message);
        }
    }

    public void cleanup(Match match) {
        tournamentByMatch.remove(match.getId());
        List<BattleSession> sessions = matchSessions.remove(match.getId());
        if (sessions == null) return;
        for (BattleSession s : sessions) {
            cancelTasks(s.getStateA());
            cancelTasks(s.getStateB());
            cleanupMobs(s.getStateA().aliveLivingMobs);
            cleanupMobs(s.getStateB().aliveLivingMobs);
        }
        if (spectatorManager != null) {
            spectatorManager.evictAll(match.getId());
        }
    }

    private void cleanupMobs(java.util.Set<UUID> mobIds) {
        for (UUID mob : mobIds) {
            sessionByMobUUID.remove(mob);
            mobLastAttackMs.remove(mob);
            Entity e = Bukkit.getEntity(mob);
            if (e != null) e.remove();
        }
        mobIds.clear();
    }

    /**
     * Captains der noch laufenden Teams im Match (Stats nicht finished),
     * exklusive Teams die {@code excludeViewer} enthalten. Wird von der
     * DeathSpectateGui genutzt, um die Auswahl zu füllen.
     */
    public List<UUID> findActiveCaptainsForSpectate(String matchId, UUID excludeViewer) {
        List<UUID> captains = new ArrayList<>();
        List<BattleSession> sessions = matchSessions.get(matchId);
        if (sessions == null) return captains;
        for (BattleSession s : sessions) {
            for (BattleSession.TeamState st : new BattleSession.TeamState[]{s.getStateA(), s.getStateB()}) {
                if (st.team.hasMember(excludeViewer)) continue;
                if (st.stats.isFinished()) continue;
                UUID cap = st.team.getCaptainId();
                if (cap != null) captains.add(cap);
            }
        }
        return captains;
    }

    private void tickAggression() {
        if (matchSessions.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (List<BattleSession> sessions : matchSessions.values()) {
            for (BattleSession session : sessions) {
                tickStateAggression(session.getStateA(), now);
                tickStateAggression(session.getStateB(), now);
            }
        }
    }

    private void tickStateAggression(BattleSession.TeamState state, long now) {
        if (state.aliveLivingMobs.isEmpty()) return;
        World arena = state.arena;
        if (arena == null) return;
        List<Player> targets = new ArrayList<>();
        for (UUID memberId : state.team.getMemberIds()) {
            if (state.downedPlayers.contains(memberId)) continue;
            Player p = Bukkit.getPlayer(memberId);
            if (p == null) continue;
            if (!p.getWorld().equals(arena)) continue;
            GameMode gm = p.getGameMode();
            if (gm == GameMode.SPECTATOR || gm == GameMode.CREATIVE) continue;
            targets.add(p);
        }
        if (targets.isEmpty()) return;

        for (UUID mobUUID : new ArrayList<>(state.aliveLivingMobs)) {
            Entity ent = Bukkit.getEntity(mobUUID);
            if (!(ent instanceof Mob mob)) continue;
            if (mob.isDead() || !mob.isValid()) continue;

            Player nearest = nearestPlayer(mob.getLocation(), targets);
            if (nearest == null) continue;
            if (mob.getTarget() == null || !nearest.equals(mob.getTarget())) {
                mob.setTarget(nearest);
            }
            // Monster + Golems greifen natürlich an — passive Mobs müssen wir manuell pushen.
            if (mob instanceof Monster) continue;
            if (mob instanceof Golem) continue;
            try {
                mob.getPathfinder().moveTo(nearest, 1.2);
            } catch (Throwable ignore) {
                // Pathfinder kann je nach Mob fehlen — ignorieren, Damage greift trotzdem.
            }
            double dist = mob.getLocation().distanceSquared(nearest.getLocation());
            if (dist <= 4.0) { // 2 Block-Radius (squared)
                Long last = mobLastAttackMs.get(mobUUID);
                if (last == null || (now - last) >= 1000L) {
                    nearest.damage(3.0, mob);
                    mobLastAttackMs.put(mobUUID, now);
                }
            }
        }
    }

    private static Player nearestPlayer(Location loc, List<Player> players) {
        Player best = null;
        double bestSq = Double.MAX_VALUE;
        for (Player p : players) {
            if (!p.getWorld().equals(loc.getWorld())) continue;
            double sq = p.getLocation().distanceSquared(loc);
            if (sq < bestSq) {
                bestSq = sq;
                best = p;
            }
        }
        return best;
    }
}
