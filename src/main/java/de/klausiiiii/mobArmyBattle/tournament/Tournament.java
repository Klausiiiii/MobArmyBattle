package de.klausiiiii.mobArmyBattle.tournament;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class Tournament {

    public enum Status { REGISTERING, RUNNING, FINISHED }

    private final String id;
    private final String name;
    private final UUID masterId;
    private final List<UUID> registered = new ArrayList<>();
    private final List<TournamentRound> rounds = new ArrayList<>();
    private Status status = Status.REGISTERING;
    private UUID winner;

    public Tournament(String id, String name, UUID masterId) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Tournament-ID darf nicht leer sein");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tournament-Name darf nicht leer sein");
        }
        if (masterId == null) {
            throw new IllegalArgumentException("Master-ID darf nicht null sein");
        }
        this.id = id;
        this.name = name;
        this.masterId = masterId;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public UUID getMasterId() { return masterId; }
    public Status getStatus() { return status; }
    public UUID getWinner() { return winner; }
    public List<UUID> getRegisteredCaptains() { return Collections.unmodifiableList(registered); }
    public List<TournamentRound> getRounds() { return Collections.unmodifiableList(rounds); }

    public TournamentRound getCurrentRound() {
        return rounds.isEmpty() ? null : rounds.get(rounds.size() - 1);
    }

    public void register(UUID captainId) {
        if (status != Status.REGISTERING) {
            throw new IllegalStateException("Tournament akzeptiert keine neuen Captains mehr");
        }
        if (captainId == null) {
            throw new IllegalArgumentException("captainId darf nicht null sein");
        }
        if (registered.contains(captainId)) {
            throw new IllegalStateException("Captain ist bereits registriert: " + captainId);
        }
        registered.add(captainId);
    }

    public void unregister(UUID captainId) {
        if (status != Status.REGISTERING) {
            throw new IllegalStateException("Captain kann nur in REGISTERING-Phase verlassen");
        }
        registered.remove(captainId);
    }

    public void start(Random rng) {
        if (status != Status.REGISTERING) {
            throw new IllegalStateException("Tournament wurde bereits gestartet");
        }
        if (registered.size() < 2) {
            throw new IllegalStateException("Mindestens 2 Captains nötig");
        }
        rounds.add(buildRound(1, registered, rng));
        status = Status.RUNNING;
    }

    public void recordPairingWinner(String matchId, UUID winnerCaptainId) {
        if (status != Status.RUNNING) {
            throw new IllegalStateException("Tournament läuft nicht");
        }
        TournamentRound round = getCurrentRound();
        if (round == null) {
            throw new IllegalStateException("Keine aktive Runde");
        }
        TournamentPairing pairing = round.findPairingByMatchId(matchId);
        if (pairing == null) {
            throw new IllegalArgumentException("Kein Pairing für Match: " + matchId);
        }
        pairing.setWinner(winnerCaptainId);
    }

    public void advanceToNextRound(Random rng) {
        if (status != Status.RUNNING) {
            throw new IllegalStateException("Tournament läuft nicht");
        }
        TournamentRound round = getCurrentRound();
        if (round == null || !round.isComplete()) {
            throw new IllegalStateException("Aktuelle Runde ist nicht abgeschlossen");
        }
        List<UUID> survivors = round.getWinners();
        if (survivors.size() == 1) {
            winner = survivors.get(0);
            status = Status.FINISHED;
            return;
        }
        rounds.add(buildRound(rounds.size() + 1, survivors, rng));
    }

    private static TournamentRound buildRound(int number, List<UUID> participants, Random rng) {
        List<UUID> shuffled = new ArrayList<>(participants);
        Collections.shuffle(shuffled, rng);
        UUID bye = null;
        if (shuffled.size() % 2 != 0) {
            bye = shuffled.remove(shuffled.size() - 1);
        }
        List<TournamentPairing> pairings = new ArrayList<>();
        for (int i = 0; i < shuffled.size(); i += 2) {
            pairings.add(new TournamentPairing(shuffled.get(i), shuffled.get(i + 1)));
        }
        return new TournamentRound(number, pairings, bye);
    }
}
