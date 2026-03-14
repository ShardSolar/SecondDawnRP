package net.shard.shipyardsrp.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseMigrations {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public void migrate(Connection connection) throws SQLException {
        createSchemaVersionTableIfMissing(connection);

        int currentVersion = getCurrentSchemaVersion(connection);

        if (currentVersion < 1) {
            applyVersion1(connection);
            setSchemaVersion(connection, 1);
            currentVersion = 1;
        }

        if (currentVersion > CURRENT_SCHEMA_VERSION) {
            throw new SQLException(
                    "Database schema version " + currentVersion
                            + " is newer than supported version " + CURRENT_SCHEMA_VERSION
            );
        }
    }

    private void createSchemaVersionTableIfMissing(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS schema_version ("
                            + "version INTEGER NOT NULL"
                            + ")"
            );
        }
    }

    private int getCurrentSchemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            if (rs.next()) {
                return rs.getInt("version");
            }
        }
        return 0;
    }

    private void setSchemaVersion(Connection connection, int version) throws SQLException {
        try (Statement delete = connection.createStatement()) {
            delete.executeUpdate("DELETE FROM schema_version");
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO schema_version(version) VALUES (?)")) {
            insert.setInt(1, version);
            insert.executeUpdate();
        }
    }

    private void applyVersion1(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS players ("
                            + "player_uuid TEXT PRIMARY KEY, "
                            + "player_name TEXT NOT NULL, "
                            + "division_id TEXT, "
                            + "progression_path_id TEXT, "
                            + "rank_id TEXT, "
                            + "rank_points INTEGER NOT NULL, "
                            + "duty_status TEXT, "
                            + "supervisor_uuid TEXT"
                            + ")"
            );

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS player_billets ("
                            + "player_uuid TEXT NOT NULL, "
                            + "billet_id TEXT NOT NULL, "
                            + "PRIMARY KEY (player_uuid, billet_id)"
                            + ")"
            );

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS player_certifications ("
                            + "player_uuid TEXT NOT NULL, "
                            + "certification_id TEXT NOT NULL, "
                            + "PRIMARY KEY (player_uuid, certification_id)"
                            + ")"
            );

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS player_active_tasks ("
                            + "player_uuid TEXT NOT NULL, "
                            + "task_id TEXT NOT NULL, "
                            + "assigned_by_uuid TEXT, "
                            + "assignment_source TEXT NOT NULL, "
                            + "current_progress INTEGER NOT NULL, "
                            + "complete INTEGER NOT NULL, "
                            + "awaiting_officer_approval INTEGER NOT NULL, "
                            + "reward_claimed INTEGER NOT NULL, "
                            + "PRIMARY KEY (player_uuid, task_id)"
                            + ")"
            );

            statement.execute(
                    "CREATE TABLE IF NOT EXISTS player_completed_tasks ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                            + "player_uuid TEXT NOT NULL, "
                            + "task_id TEXT NOT NULL, "
                            + "assigned_by_uuid TEXT, "
                            + "assignment_source TEXT NOT NULL, "
                            + "completed_at INTEGER NOT NULL, "
                            + "reward_points_granted INTEGER NOT NULL"
                            + ")"
            );
        }
    }
}