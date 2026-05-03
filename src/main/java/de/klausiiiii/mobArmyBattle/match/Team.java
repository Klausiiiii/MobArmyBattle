package de.klausiiiii.mobArmyBattle.match;

import de.klausiiiii.mobArmyBattle.pool.MobPool;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Collections;

public class Team {
    private UUID captainId;
    private final Set<UUID> memberIds;
    private final MobPool pool;
    private final int maxSize;

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

    public boolean isDisbanded() {
        return captainId == null;
    }

    public void disband() {
        this.captainId = null;
        this.memberIds.clear();
    }
}
