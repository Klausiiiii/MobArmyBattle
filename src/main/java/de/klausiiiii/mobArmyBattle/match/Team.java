package de.klausiiiii.mobArmyBattle.match;

import de.klausiiiii.mobArmyBattle.pool.MobPool;
import de.klausiiiii.mobArmyBattle.wave.Wave;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class Team {
    private UUID captainId;
    private final Set<UUID> memberIds;
    private final MobPool pool;
    private final int maxSize;
    private final Wave wave1;
    private final Wave wave2;
    private boolean eliminated = false;
    private TeamVisibility visibility = TeamVisibility.PUBLIC;
    private String passwordHash;
    private final Set<UUID> invitedPlayers = new HashSet<>();

    public Team(UUID captainId) {
        this(captainId, 0);
    }

    public Team(UUID captainId, int maxSize) {
        if (captainId == null) {
            throw new IllegalArgumentException("captainId darf nicht null sein");
        }
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize darf nicht negativ sein");
        }
        this.captainId = captainId;
        this.memberIds = new LinkedHashSet<>();
        this.memberIds.add(captainId);
        this.pool = new MobPool();
        this.maxSize = maxSize;
        this.wave1 = new Wave();
        this.wave2 = new Wave();
    }

    /**
     * Creates an empty team without a captain (placeholder for the second team
     * in a multi-team match before anyone has joined it).
     */
    public static Team empty(int maxSize) {
        return new Team(maxSize, true);
    }

    private Team(int maxSize, boolean emptySentinel) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize darf nicht negativ sein");
        }
        this.captainId = null;
        this.memberIds = new LinkedHashSet<>();
        this.pool = new MobPool();
        this.maxSize = maxSize;
        this.wave1 = new Wave();
        this.wave2 = new Wave();
    }

    /**
     * Sets the captain on a previously empty team and adds them as the only member.
     */
    public void promoteEmpty(UUID newCaptainId) {
        if (captainId != null && !memberIds.isEmpty()) {
            throw new IllegalStateException("Team ist nicht leer");
        }
        if (newCaptainId == null) {
            throw new IllegalArgumentException("newCaptainId darf nicht null sein");
        }
        this.captainId = newCaptainId;
        this.memberIds.add(newCaptainId);
    }

    public UUID getCaptainId() {
        return captainId;
    }

    public Set<UUID> getMemberIds() {
        return Collections.unmodifiableSet(memberIds);
    }

    public MobPool getPool() {
        return pool;
    }

    public boolean hasMember(UUID playerId) {
        return memberIds.contains(playerId);
    }

    public void addMember(UUID playerId) {
        if (memberIds.contains(playerId)) {
            throw new IllegalArgumentException("Spieler ist bereits Team-Mitglied: " + playerId);
        }
        if (maxSize > 0 && memberIds.size() >= maxSize) {
            throw new IllegalStateException("Team ist voll: " + maxSize + " Mitglieder");
        }
        memberIds.add(playerId);
    }

    public void removeMember(UUID playerId) {
        if (playerId.equals(captainId)) {
            throw new IllegalStateException("Captain kann nicht direkt entfernt werden, erst promoteToCaptain");
        }
        memberIds.remove(playerId);
    }

    public void promoteToCaptain(UUID newCaptainId) {
        if (!memberIds.contains(newCaptainId)) {
            throw new IllegalArgumentException("Neuer Captain muss bereits Team-Mitglied sein: " + newCaptainId);
        }
        this.captainId = newCaptainId;
    }

    public int size() {
        return memberIds.size();
    }

    public int getMaxSize() {
        return maxSize;
    }

    public boolean isFull() {
        return maxSize > 0 && memberIds.size() >= maxSize;
    }

    public Wave getWave1() {
        return wave1;
    }

    public Wave getWave2() {
        return wave2;
    }

    public boolean wavesFinalised() {
        return wave1.isFinalised() && wave2.isFinalised();
    }

    public boolean isDisbanded() {
        return captainId == null;
    }

    public void disband() {
        this.captainId = null;
        this.memberIds.clear();
    }

    /**
     * Marks the team as out of competition (e.g. empty pool at end of farming).
     * Members are intentionally kept so they can be routed into spectator mode in
     * the battle phase and cleaned up by {@code FinishedPhase} like everyone else.
     */
    public void eliminate() {
        this.eliminated = true;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public TeamVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(TeamVisibility visibility) {
        if (visibility == null) {
            throw new IllegalArgumentException("visibility darf nicht null sein");
        }
        this.visibility = visibility;
    }

    /**
     * Stores a hashed copy of {@code rawPassword} and forces visibility to
     * {@link TeamVisibility#PASSWORD}. Pass {@code null} to clear the password
     * (visibility is left untouched in that case).
     */
    public void setPassword(String rawPassword) {
        if (rawPassword == null) {
            this.passwordHash = null;
            return;
        }
        if (rawPassword.isBlank()) {
            throw new IllegalArgumentException("Passwort darf nicht leer sein");
        }
        this.passwordHash = sha256(rawPassword);
        this.visibility = TeamVisibility.PASSWORD;
    }

    public boolean verifyPassword(String rawPassword) {
        if (passwordHash == null || rawPassword == null) return false;
        return passwordHash.equals(sha256(rawPassword));
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public void invite(UUID playerId) {
        if (playerId == null) throw new IllegalArgumentException("playerId darf nicht null sein");
        invitedPlayers.add(playerId);
    }

    public boolean isInvited(UUID playerId) {
        return invitedPlayers.contains(playerId);
    }

    public void consumeInvite(UUID playerId) {
        invitedPlayers.remove(playerId);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }
}
