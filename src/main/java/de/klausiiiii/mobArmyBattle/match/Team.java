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

    public Team(UUID captainId) {
        if (captainId == null) {
            throw new IllegalArgumentException("captainId darf nicht null sein");
        }
        this.captainId = captainId;
        this.memberIds = new LinkedHashSet<>();
        this.memberIds.add(captainId);
        this.pool = new MobPool();
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

    public boolean isDisbanded() {
        return captainId == null;
    }

    public void disband() {
        this.captainId = null;
        this.memberIds.clear();
    }
}
