package de.klausiiiii.mobArmyBattle.stats;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StatsRepository {

    private final StatsDatabase database;
    private final Logger log;

    public StatsRepository(StatsDatabase database, Logger log) {
        this.database = database;
        this.log = log;
    }

    public PlayerStats get(UUID playerId) {
        if (!database.isOpen()) return PlayerStats.empty(playerId);
        Connection c = database.getConnection();
        String sql = "SELECT matches_total, matches_won, mob_kills_total FROM player_stats WHERE player_uuid = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return PlayerStats.empty(playerId);
                return new PlayerStats(playerId,
                        rs.getInt("matches_total"),
                        rs.getInt("matches_won"),
                        rs.getInt("mob_kills_total"));
            }
        } catch (SQLException e) {
            log.log(Level.WARNING, "Stats-Read fehlgeschlagen für " + playerId, e);
            return PlayerStats.empty(playerId);
        }
    }

    public void recordMatchResult(UUID playerId, boolean won, int mobKills) {
        if (!database.isOpen()) return;
        if (mobKills < 0) mobKills = 0;
        Connection c = database.getConnection();
        String sql = """
            INSERT INTO player_stats (player_uuid, matches_total, matches_won, mob_kills_total)
            VALUES (?, 1, ?, ?)
            ON CONFLICT(player_uuid) DO UPDATE SET
                matches_total = matches_total + 1,
                matches_won = matches_won + excluded.matches_won,
                mob_kills_total = mob_kills_total + excluded.mob_kills_total
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setInt(2, won ? 1 : 0);
            ps.setInt(3, mobKills);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.log(Level.WARNING, "Stats-Write fehlgeschlagen für " + playerId, e);
        }
    }

    public List<PlayerStats> getLeaderboard(int limit) {
        if (!database.isOpen()) return List.of();
        Connection c = database.getConnection();
        String sql = """
            SELECT player_uuid, matches_total, matches_won, mob_kills_total
            FROM player_stats
            ORDER BY matches_won DESC, matches_total ASC
            LIMIT ?
            """;
        List<PlayerStats> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new PlayerStats(
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getInt("matches_total"),
                            rs.getInt("matches_won"),
                            rs.getInt("mob_kills_total")));
                }
            }
        } catch (SQLException e) {
            log.log(Level.WARNING, "Leaderboard-Read fehlgeschlagen", e);
        }
        return result;
    }
}
