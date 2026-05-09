package de.klausiiiii.mobArmyBattle.stats;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StatsDatabase {

    private final JavaPlugin plugin;
    private final Logger log;
    private Connection connection;

    public StatsDatabase(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public void open() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Konnte Plugin-Data-Folder nicht erstellen: " + dataFolder);
        }
        File dbFile = new File(dataFolder, "data.db");
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            initSchema();
            log.info("Stats-DB geöffnet: " + dbFile.getName());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite-JDBC-Treiber nicht gefunden — plugin.yml libraries-Eintrag prüfen", e);
        } catch (SQLException e) {
            throw new IllegalStateException("Konnte Stats-DB nicht öffnen", e);
        }
    }

    private void initSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    player_uuid TEXT PRIMARY KEY,
                    matches_total INTEGER NOT NULL DEFAULT 0,
                    matches_won INTEGER NOT NULL DEFAULT 0,
                    mob_kills_total INTEGER NOT NULL DEFAULT 0
                )""");
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_wins ON player_stats(matches_won DESC)");
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public boolean isOpen() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public void close() {
        if (connection == null) return;
        try {
            connection.close();
            log.info("Stats-DB geschlossen.");
        } catch (SQLException e) {
            log.log(Level.WARNING, "Fehler beim Schließen der Stats-DB", e);
        } finally {
            connection = null;
        }
    }
}
