package de.klausiiiii.mobArmyBattle.tournament;

import de.klausiiiii.mobArmyBattle.MobArmyBattle;
import de.klausiiiii.mobArmyBattle.match.Match;
import de.klausiiiii.mobArmyBattle.match.MatchManager;
import de.klausiiiii.mobArmyBattle.match.MatchPhaseType;
import de.klausiiiii.mobArmyBattle.match.phase.FarmPhase;
import de.klausiiiii.mobArmyBattle.match.phase.FinishedPhase;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class TournamentManager {

    private static final long ROUND_PAUSE_TICKS = 20L * 15;

    private final MobArmyBattle plugin;
    private final MatchManager matchManager;
    private final Map<String, Tournament> byName = new HashMap<>();
    private final Map<UUID, Tournament> byCaptain = new HashMap<>();
    private final Map<String, Tournament> byMatchId = new HashMap<>();
    private final AtomicLong tournamentIdCounter = new AtomicLong(1);
    private final Random rng = new Random();

    public TournamentManager(MobArmyBattle plugin, MatchManager matchManager) {
        this.plugin = plugin;
        this.matchManager = matchManager;
    }

    public Tournament create(String name, UUID masterId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name darf nicht leer sein");
        }
        String key = name.toLowerCase(Locale.ROOT);
        if (byName.containsKey(key)) {
            throw new IllegalStateException("Tournament '" + name + "' existiert bereits");
        }
        if (byCaptain.containsKey(masterId)) {
            throw new IllegalStateException("Du bist bereits in einem Tournament");
        }
        if (matchManager.getMatchOf(masterId) != null) {
            throw new IllegalStateException("Erst /mab leave bevor du ein Tournament erstellst");
        }
        String id = "tournament-" + tournamentIdCounter.getAndIncrement();
        Tournament t = new Tournament(id, name, masterId);
        byName.put(key, t);
        return t;
    }

    public Tournament getByName(String name) {
        if (name == null) return null;
        return byName.get(name.toLowerCase(Locale.ROOT));
    }

    public Tournament getByCaptain(UUID captainId) {
        return byCaptain.get(captainId);
    }

    public List<Tournament> listAll() {
        return Collections.unmodifiableList(new ArrayList<>(byName.values()));
    }

    public void join(String name, UUID captainId) {
        Tournament t = getByName(name);
        if (t == null) {
            throw new IllegalArgumentException("Tournament nicht gefunden: " + name);
        }
        if (byCaptain.containsKey(captainId)) {
            throw new IllegalStateException("Du bist bereits in einem Tournament");
        }
        if (matchManager.getMatchOf(captainId) != null) {
            throw new IllegalStateException("Erst /mab leave bevor du dem Tournament beitrittst");
        }
        t.register(captainId);
        byCaptain.put(captainId, t);
    }

    public void leave(UUID captainId) {
        Tournament t = byCaptain.get(captainId);
        if (t == null) return;
        t.unregister(captainId);
        byCaptain.remove(captainId);
    }

    public void start(String name, UUID requesterId) {
        Tournament t = getByName(name);
        if (t == null) {
            throw new IllegalArgumentException("Tournament nicht gefunden: " + name);
        }
        if (!t.getMasterId().equals(requesterId)) {
            throw new IllegalStateException("Nur der Master darf das Tournament starten");
        }
        t.start(rng);
        startCurrentRound(t);
    }

    private void startCurrentRound(Tournament t) {
        TournamentRound round = t.getCurrentRound();
        if (round == null) return;
        broadcastTournament(t, "§6Tournament '" + t.getName() + "' — Runde " + round.getNumber() + " startet!");
        for (TournamentPairing pairing : round.getPairings()) {
            createPairingMatch(t, pairing);
        }
        if (round.getByeCaptain() != null) {
            Player p = Bukkit.getPlayer(round.getByeCaptain());
            if (p != null) {
                p.sendMessage("§eDu hast Bye-Slot — direkt in der nächsten Runde.");
            }
        }
    }

    private void createPairingMatch(Tournament t, TournamentPairing pairing) {
        UUID captainA = pairing.getCaptainA();
        UUID captainB = pairing.getCaptainB();
        try {
            Match m = matchManager.createMatch(captainA, 1);
            matchManager.joinMatch(captainB, captainA);
            pairing.setMatchId(m.getId());
            byMatchId.put(m.getId(), t);
            m.transitionTo(new FarmPhase(plugin));
            String aName = nameOf(captainA);
            String bName = nameOf(captainB);
            notifyCaptain(captainA, "§eRunde gestartet — Farm-Phase! Gegner: " + bName);
            notifyCaptain(captainB, "§eRunde gestartet — Farm-Phase! Gegner: " + aName);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Konnte Tournament-Match nicht erstellen: " + e.getMessage());
            pairing.setWinner(captainA);
        }
    }

    public void onCaptainQuit(UUID captainId) {
        Tournament t = byCaptain.get(captainId);
        if (t == null) return;
        if (t.getStatus() == Tournament.Status.REGISTERING) {
            leave(captainId);
            return;
        }
        if (t.getStatus() != Tournament.Status.RUNNING) return;
        TournamentRound round = t.getCurrentRound();
        if (round == null) return;
        TournamentPairing pairing = round.findPairingForCaptain(captainId);
        if (pairing == null || pairing.isFinished()) return;
        UUID opponent = pairing.getOpponent(captainId);
        try {
            pairing.setWinner(opponent);
        } catch (IllegalStateException ignored) {
            return;
        }
        broadcastTournament(t,
                "§c" + nameOf(captainId) + " hat verlassen — " + nameOf(opponent) + " gewinnt durch Forfeit.");
        if (pairing.getMatchId() != null) {
            Match m = matchManager.getMatchById(pairing.getMatchId());
            if (m != null && m.getCurrentPhase().getType() != MatchPhaseType.FINISHED) {
                m.transitionTo(new FinishedPhase(plugin));
            }
            byMatchId.remove(pairing.getMatchId());
        }
        if (round.isComplete()) {
            broadcastTournament(t,
                    "§6Runde " + round.getNumber() + " abgeschlossen — nächste Runde in 15 sec...");
            Bukkit.getScheduler().runTaskLater(plugin, () -> advanceTournament(t), ROUND_PAUSE_TICKS);
        }
    }

    public void onBattleFinished(Match match, UUID winnerCaptain) {
        Tournament t = byMatchId.get(match.getId());
        if (t == null) return;
        t.recordPairingWinner(match.getId(), winnerCaptain);
        TournamentRound round = t.getCurrentRound();
        if (round.isComplete()) {
            broadcastTournament(t,
                    "§6Runde " + round.getNumber() + " abgeschlossen — nächste Runde in 15 sec...");
            Bukkit.getScheduler().runTaskLater(plugin, () -> advanceTournament(t), ROUND_PAUSE_TICKS);
        }
    }

    private void advanceTournament(Tournament t) {
        TournamentRound completed = t.getCurrentRound();
        if (completed != null) {
            for (TournamentPairing p : completed.getPairings()) {
                if (p.getMatchId() != null) {
                    byMatchId.remove(p.getMatchId());
                }
            }
        }
        t.advanceToNextRound(rng);
        if (t.getStatus() == Tournament.Status.FINISHED) {
            announceTournamentWinner(t);
            cleanupTournament(t);
            return;
        }
        startCurrentRound(t);
    }

    private void announceTournamentWinner(Tournament t) {
        String winnerName = nameOf(t.getWinner());
        broadcastTournament(t,
                "§6§lTournament '" + t.getName() + "' beendet! Sieger: " + winnerName);
    }

    private void cleanupTournament(Tournament t) {
        for (UUID c : t.getRegisteredCaptains()) {
            byCaptain.remove(c);
        }
        byName.remove(t.getName().toLowerCase(Locale.ROOT));
    }

    private void broadcastTournament(Tournament t, String message) {
        for (UUID c : t.getRegisteredCaptains()) {
            Player p = Bukkit.getPlayer(c);
            if (p != null) p.sendMessage(message);
        }
        Player master = Bukkit.getPlayer(t.getMasterId());
        if (master != null && !t.getRegisteredCaptains().contains(t.getMasterId())) {
            master.sendMessage(message);
        }
    }

    private void notifyCaptain(UUID captainId, String message) {
        Player p = Bukkit.getPlayer(captainId);
        if (p != null) p.sendMessage(message);
    }

    private String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p != null ? p.getName() : id.toString().substring(0, 8);
    }
}
