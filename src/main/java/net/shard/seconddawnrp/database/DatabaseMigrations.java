package net.shard.seconddawnrp.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseMigrations {

    public static final int CURRENT_SCHEMA_VERSION = 4;

    public void migrate(Connection connection) throws SQLException {
        createSchemaVersionTableIfMissing(connection);

        int currentVersion = getCurrentSchemaVersion(connection);

        if (currentVersion < 1) {
            applyVersion1(connection);
            setSchemaVersion(connection, 1);
            currentVersion = 1;
        }

        if (currentVersion < 2) {
            applyVersion2(connection);
            setSchemaVersion(connection, 2);
            currentVersion = 2;
        }

        if (currentVersion < 3) {
            applyVersion3(connection);
            setSchemaVersion(connection, 3);
            currentVersion = 3;
        }

        if (currentVersion < 4) {
            applyVersion4(connection);
            setSchemaVersion(connection, 4);
            currentVersion = 4;
        }

        if (currentVersion > CURRENT_SCHEMA_VERSION) {
            throw new SQLException(
                    "Database schema version " + currentVersion
                            + " is newer than supported version " + CURRENT_SCHEMA_VERSION
            );
        }
    }

    // ── Schema version bookkeeping ────────────────────────────────────────────

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

    // ── V1: core player + task tables ─────────────────────────────────────────

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

    // ── V2: ops pool + terminals ──────────────────────────────────────────────

    private void applyVersion2(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS ops_task_pool ("
                            + "task_id TEXT PRIMARY KEY, "
                            + "display_name TEXT NOT NULL, "
                            + "description TEXT NOT NULL, "
                            + "division TEXT NOT NULL, "
                            + "objective_type TEXT NOT NULL, "
                            + "target_id TEXT NOT NULL, "
                            + "required_amount INTEGER NOT NULL, "
                            + "reward_points INTEGER NOT NULL, "
                            + "officer_confirmation_required INTEGER NOT NULL, "
                            + "created_by_uuid TEXT, "
                            + "created_at INTEGER NOT NULL, "
                            + "status TEXT NOT NULL, "
                            + "assigned_player_uuid TEXT, "
                            + "assigned_by_uuid TEXT, "
                            + "pooled_division TEXT, "
                            + "review_note TEXT"
                            + ")"
            );
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS task_terminals ("
                            + "world_key TEXT NOT NULL, "
                            + "block_pos_long INTEGER NOT NULL, "
                            + "terminal_type TEXT NOT NULL, "
                            + "allowed_divisions TEXT NOT NULL, "
                            + "PRIMARY KEY (world_key, block_pos_long)"
                            + ")"
            );
        }
    }

    // ── V3: degradation components ────────────────────────────────────────────

    private void applyVersion3(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS components ("
                            + "component_id TEXT PRIMARY KEY, "
                            + "world_key TEXT NOT NULL, "
                            + "block_pos_long INTEGER NOT NULL, "
                            + "block_type_id TEXT NOT NULL, "
                            + "display_name TEXT NOT NULL, "
                            + "health INTEGER NOT NULL, "
                            + "status TEXT NOT NULL, "
                            + "last_drain_tick_ms INTEGER NOT NULL, "
                            + "last_task_generated_ms INTEGER NOT NULL, "
                            + "registered_by_uuid TEXT, "
                            + "repair_item_id TEXT, "
                            + "repair_item_count INTEGER NOT NULL DEFAULT 0"
                            + ")"
            );
            statement.execute(
                    "CREATE UNIQUE INDEX IF NOT EXISTS idx_components_position "
                            + "ON components (world_key, block_pos_long)"
            );
        }
    }

    // ── V4: character profiles ────────────────────────────────────────────────

    private void applyVersion4(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {

            // Core character record — one per player account
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS character_profiles ("
                            + "character_id TEXT PRIMARY KEY, "          // own UUID, not player UUID
                            + "player_uuid TEXT NOT NULL, "              // Minecraft account link
                            + "character_name TEXT NOT NULL, "
                            + "species TEXT, "                           // null until set at creation
                            + "bio TEXT, "
                            + "status TEXT NOT NULL, "                   // CharacterStatus enum name
                            + "universal_translator INTEGER NOT NULL DEFAULT 0, "
                            + "permadeath_consent INTEGER NOT NULL DEFAULT 0, "
                            + "active_long_term_injury_id TEXT, "        // null = no current LTI
                            + "deceased_at INTEGER, "                    // null = alive
                            + "progression_transfer INTEGER NOT NULL DEFAULT 0, "
                            + "created_at INTEGER NOT NULL"
                            + ")"
            );
            // Index on player_uuid — we always look up by Minecraft account
            statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_character_profiles_player_uuid "
                            + "ON character_profiles (player_uuid)"
            );

            // Per-character language knowledge
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS character_known_languages ("
                            + "character_id TEXT NOT NULL, "
                            + "language_id TEXT NOT NULL, "
                            + "PRIMARY KEY (character_id, language_id)"
                            + ")"
            );

            // Long-term injuries — stored separately so Medical can query them
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS long_term_injuries ("
                            + "injury_id TEXT PRIMARY KEY, "
                            + "player_uuid TEXT NOT NULL, "
                            + "tier TEXT NOT NULL, "                     // LongTermInjuryTier enum name
                            + "applied_at_ms INTEGER NOT NULL, "
                            + "expires_at_ms INTEGER NOT NULL, "         // absolute expiry timestamp
                            + "sessions_completed INTEGER NOT NULL DEFAULT 0, "
                            + "last_treatment_ms INTEGER NOT NULL DEFAULT 0, "
                            + "active INTEGER NOT NULL DEFAULT 1"        // 0 = cleared/expired
                            + ")"
            );
            statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_lti_player "
                            + "ON long_term_injuries (player_uuid)"
            );

            // RDM flags — append-only audit entries for GM review
            statement.execute(
                    "CREATE TABLE IF NOT EXISTS rdm_flags ("
                            + "flag_id TEXT PRIMARY KEY, "
                            + "attacker_uuid TEXT NOT NULL, "
                            + "victim_uuid TEXT NOT NULL, "
                            + "world_key TEXT NOT NULL, "
                            + "block_pos_long INTEGER NOT NULL, "
                            + "flagged_at_ms INTEGER NOT NULL, "
                            + "event_active INTEGER NOT NULL DEFAULT 0, " // was a GM event running?
                            + "last_security_interaction_ms INTEGER NOT NULL DEFAULT 0, "
                            + "reviewed INTEGER NOT NULL DEFAULT 0, "     // 0=pending, 1=confirmed, 2=dismissed
                            + "reviewed_by_uuid TEXT, "
                            + "reviewed_at_ms INTEGER"
                            + ")"
            );
        }
    }
}