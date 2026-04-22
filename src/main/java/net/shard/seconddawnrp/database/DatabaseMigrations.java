package net.shard.seconddawnrp.database;

import java.sql.*;

import static net.shard.seconddawnrp.tactical.data.TacticalMigration.applyVersion13;
import static net.shard.seconddawnrp.tactical.data.TacticalMigration.applyVersion14;
import static net.shard.seconddawnrp.tactical.data.TacticalMigration.applyVersion15;

public final class DatabaseMigrations {

    public static final int CURRENT_SCHEMA_VERSION = 15;

    public void migrate(Connection connection) throws SQLException {
        createSchemaVersionTableIfMissing(connection);
        int v = getCurrentSchemaVersion(connection);
        if (v < 1)  { applyVersion1(connection);  setSchemaVersion(connection, 1);  v = 1;  }
        if (v < 2)  { applyVersion2(connection);  setSchemaVersion(connection, 2);  v = 2;  }
        if (v < 3)  { applyVersion3(connection);  setSchemaVersion(connection, 3);  v = 3;  }
        if (v < 4)  { applyVersion4(connection);  setSchemaVersion(connection, 4);  v = 4;  }
        if (v < 5)  { applyVersion5(connection);  setSchemaVersion(connection, 5);  v = 5;  }
        if (v < 6)  { applyVersion6(connection);  setSchemaVersion(connection, 6);  v = 6;  }
        if (v < 7)  { applyVersion7(connection);  setSchemaVersion(connection, 7);  v = 7;  }
        if (v < 8)  { applyVersion8(connection);  setSchemaVersion(connection, 8);  v = 8;  }
        if (v < 9)  { applyVersion9(connection);  setSchemaVersion(connection, 9);  v = 9;  }
        if (v < 10) { applyVersion10(connection); setSchemaVersion(connection, 10); v = 10; }
        if (v < 11) { applyVersion11(connection); setSchemaVersion(connection, 11); v = 11; }
        if (v < 12) { applyVersion12(connection); setSchemaVersion(connection, 12); v = 12; }
        if (v < 13) { applyVersion13(connection); setSchemaVersion(connection, 13); v = 13; }
        if (v < 14) { applyVersion14(connection); setSchemaVersion(connection, 14); v = 14; }
        if (v < 15) { applyVersion15(connection); setSchemaVersion(connection, 15); v = 15; }
        if (v > CURRENT_SCHEMA_VERSION)
            throw new SQLException("DB schema " + v + " newer than supported " + CURRENT_SCHEMA_VERSION);
    }

    // ── Schema version tracking ───────────────────────────────────────────────

    private void createSchemaVersionTableIfMissing(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
        }
    }

    private int getCurrentSchemaVersion(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
            return rs.next() ? rs.getInt("version") : 0;
        }
    }

    private void setSchemaVersion(Connection c, int v) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM schema_version");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO schema_version(version) VALUES (?)")) {
            ps.setInt(1, v);
            ps.executeUpdate();
        }
    }

    // ── Migrations ────────────────────────────────────────────────────────────

    private void applyVersion1(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS players ("
                    + "player_uuid TEXT PRIMARY KEY, "
                    + "player_name TEXT NOT NULL, "
                    + "division_id TEXT, "
                    + "progression_path_id TEXT, "
                    + "rank_id TEXT, "
                    + "rank_points INTEGER NOT NULL, "
                    + "duty_status TEXT, "
                    + "supervisor_uuid TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS player_billets ("
                    + "player_uuid TEXT NOT NULL, "
                    + "billet_id TEXT NOT NULL, "
                    + "PRIMARY KEY (player_uuid, billet_id))");
            s.execute("CREATE TABLE IF NOT EXISTS player_certifications ("
                    + "player_uuid TEXT NOT NULL, "
                    + "certification_id TEXT NOT NULL, "
                    + "PRIMARY KEY (player_uuid, certification_id))");
            s.execute("CREATE TABLE IF NOT EXISTS player_active_tasks ("
                    + "player_uuid TEXT NOT NULL, "
                    + "task_id TEXT NOT NULL, "
                    + "assigned_by_uuid TEXT, "
                    + "assignment_source TEXT NOT NULL, "
                    + "current_progress INTEGER NOT NULL, "
                    + "complete INTEGER NOT NULL, "
                    + "awaiting_officer_approval INTEGER NOT NULL, "
                    + "reward_claimed INTEGER NOT NULL, "
                    + "PRIMARY KEY (player_uuid, task_id))");
            s.execute("CREATE TABLE IF NOT EXISTS player_completed_tasks ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "player_uuid TEXT NOT NULL, "
                    + "task_id TEXT NOT NULL, "
                    + "assigned_by_uuid TEXT, "
                    + "assignment_source TEXT NOT NULL, "
                    + "completed_at INTEGER NOT NULL, "
                    + "reward_points_granted INTEGER NOT NULL)");
        }
    }

    private void applyVersion2(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS ops_task_pool ("
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
                    + "review_note TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS task_terminals ("
                    + "world_key TEXT NOT NULL, "
                    + "block_pos_long INTEGER NOT NULL, "
                    + "terminal_type TEXT NOT NULL, "
                    + "allowed_divisions TEXT NOT NULL, "
                    + "PRIMARY KEY (world_key, block_pos_long))");
        }
    }

    private void applyVersion3(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS components ("
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
                    + "repair_item_count INTEGER NOT NULL DEFAULT 0, "
                    + "missing_block INTEGER NOT NULL DEFAULT 0)");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_components_position "
                    + "ON components (world_key, block_pos_long)");
        }
    }

    private void applyVersion4(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS character_profiles ("
                    + "character_id TEXT PRIMARY KEY, "
                    + "player_uuid TEXT NOT NULL, "
                    + "character_name TEXT NOT NULL, "
                    + "species TEXT, "
                    + "bio TEXT, "
                    + "status TEXT NOT NULL, "
                    + "universal_translator INTEGER NOT NULL DEFAULT 0, "
                    + "permadeath_consent INTEGER NOT NULL DEFAULT 0, "
                    + "active_long_term_injury_id TEXT, "
                    + "deceased_at INTEGER, "
                    + "progression_transfer INTEGER NOT NULL DEFAULT 0, "
                    + "created_at INTEGER NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_character_profiles_player_uuid "
                    + "ON character_profiles (player_uuid)");
            s.execute("CREATE TABLE IF NOT EXISTS character_known_languages ("
                    + "character_id TEXT NOT NULL, "
                    + "language_id TEXT NOT NULL, "
                    + "PRIMARY KEY (character_id, language_id))");
            s.execute("CREATE TABLE IF NOT EXISTS long_term_injuries ("
                    + "injury_id TEXT PRIMARY KEY, "
                    + "player_uuid TEXT NOT NULL, "
                    + "tier TEXT NOT NULL, "
                    + "applied_at_ms INTEGER NOT NULL, "
                    + "expires_at_ms INTEGER NOT NULL, "
                    + "sessions_completed INTEGER NOT NULL DEFAULT 0, "
                    + "last_treatment_ms INTEGER NOT NULL DEFAULT 0, "
                    + "active INTEGER NOT NULL DEFAULT 1)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_lti_player "
                    + "ON long_term_injuries (player_uuid)");
            s.execute("CREATE TABLE IF NOT EXISTS rdm_flags ("
                    + "flag_id TEXT PRIMARY KEY, "
                    + "attacker_uuid TEXT NOT NULL, "
                    + "victim_uuid TEXT NOT NULL, "
                    + "world_key TEXT NOT NULL, "
                    + "block_pos_long INTEGER NOT NULL, "
                    + "flagged_at_ms INTEGER NOT NULL, "
                    + "event_active INTEGER NOT NULL DEFAULT 0, "
                    + "last_security_interaction_ms INTEGER NOT NULL DEFAULT 0, "
                    + "reviewed INTEGER NOT NULL DEFAULT 0, "
                    + "reviewed_by_uuid TEXT, "
                    + "reviewed_at_ms INTEGER)");
        }
    }

    private void applyVersion5(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            execSafe(c, "ALTER TABLE players ADD COLUMN character_id TEXT");
            execSafe(c, "ALTER TABLE players ADD COLUMN character_name TEXT");
            execSafe(c, "ALTER TABLE players ADD COLUMN species TEXT");
            execSafe(c, "ALTER TABLE players ADD COLUMN bio TEXT");
            execSafe(c, "ALTER TABLE players ADD COLUMN character_status TEXT NOT NULL DEFAULT 'ACTIVE'");
            s.execute("CREATE TABLE IF NOT EXISTS player_known_languages ("
                    + "player_uuid TEXT NOT NULL, "
                    + "language_id TEXT NOT NULL, "
                    + "PRIMARY KEY (player_uuid, language_id))");
            execSafe(c, "ALTER TABLE players ADD COLUMN universal_translator INTEGER NOT NULL DEFAULT 0");
            execSafe(c, "ALTER TABLE players ADD COLUMN permadeath_consent INTEGER NOT NULL DEFAULT 0");
            execSafe(c, "ALTER TABLE players ADD COLUMN active_long_term_injury_id TEXT");
            execSafe(c, "ALTER TABLE players ADD COLUMN deceased_at INTEGER");
            execSafe(c, "ALTER TABLE players ADD COLUMN progression_transfer INTEGER NOT NULL DEFAULT 0");
            execSafe(c, "ALTER TABLE players ADD COLUMN character_created_at INTEGER NOT NULL DEFAULT 0");
            execSafe(c, "ALTER TABLE players ADD COLUMN service_record INTEGER NOT NULL DEFAULT 0");
        }
    }

    private void applyVersion6(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS rp_padd_submissions ("
                    + "submission_id TEXT PRIMARY KEY, "
                    + "submitter_uuid TEXT NOT NULL, "
                    + "submitter_name TEXT NOT NULL, "
                    + "submitted_at_ms INTEGER NOT NULL, "
                    + "entry_count INTEGER NOT NULL, "
                    + "log_text TEXT NOT NULL, "
                    + "status TEXT NOT NULL DEFAULT 'PENDING', "
                    + "reviewed_by_uuid TEXT, "
                    + "reviewed_at_ms INTEGER, "
                    + "review_note TEXT, "
                    + "linked_task_id TEXT)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_submissions_status "
                    + "ON rp_padd_submissions (status)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_submissions_submitter "
                    + "ON rp_padd_submissions (submitter_uuid)");
        }
    }

    private void applyVersion7(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            execSafe(c, "ALTER TABLE players ADD COLUMN mustang INTEGER NOT NULL DEFAULT 0");
            execSafe(c, "ALTER TABLE players ADD COLUMN ship_position TEXT NOT NULL DEFAULT 'NONE'");
            s.execute("CREATE TABLE IF NOT EXISTS officer_slot_queue ("
                    + "player_uuid TEXT NOT NULL, "
                    + "target_rank TEXT NOT NULL, "
                    + "service_record INTEGER NOT NULL DEFAULT 0, "
                    + "queued_at INTEGER NOT NULL, "
                    + "time_at_rank INTEGER NOT NULL DEFAULT 0, "
                    + "PRIMARY KEY (player_uuid, target_rank))");
        }
    }

    private void applyVersion8(Connection c) throws SQLException {
        execSafe(c, "ALTER TABLE long_term_injuries ADD COLUMN condition_key TEXT");
        execSafe(c, "ALTER TABLE long_term_injuries ADD COLUMN display_name_override TEXT");
        execSafe(c, "ALTER TABLE long_term_injuries ADD COLUMN description_override TEXT");
        execSafe(c, "ALTER TABLE long_term_injuries ADD COLUMN requires_surgery INTEGER NOT NULL DEFAULT 0");
        execSafe(c, "ALTER TABLE long_term_injuries ADD COLUMN treatment_steps_completed TEXT NOT NULL DEFAULT '[]'");
        execSafe(c, "ALTER TABLE long_term_injuries ADD COLUMN resolved_by TEXT");
        execSafe(c, "ALTER TABLE long_term_injuries ADD COLUMN resolution_note TEXT");
        execSafe(c, "ALTER TABLE long_term_injuries ADD COLUMN is_death_cause INTEGER NOT NULL DEFAULT 0");
        execSafe(c, "ALTER TABLE long_term_injuries ADD COLUMN applied_by TEXT");
        execSafe(c, "ALTER TABLE long_term_injuries ADD COLUMN notes TEXT");
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS medical_terminal_visitors ("
                    + "player_uuid TEXT NOT NULL, "
                    + "first_visit_at INTEGER NOT NULL, "
                    + "last_visit_at INTEGER NOT NULL, "
                    + "PRIMARY KEY (player_uuid))");
        }
    }

    private void applyVersion9(Connection c) throws SQLException {
        execSafe(c, "ALTER TABLE long_term_injuries ADD COLUMN effects_suppressed_until_ms INTEGER NOT NULL DEFAULT 0");
        execSafe(c, "ALTER TABLE long_term_injuries ADD COLUMN last_milk_use_ms INTEGER NOT NULL DEFAULT 0");
    }

    private void applyVersion10(Connection c) throws SQLException {
        // -1 = use tier default (backward compatible with all existing rows)
        execSafe(c, "ALTER TABLE long_term_injuries ADD COLUMN sessions_required INTEGER NOT NULL DEFAULT -1");
    }

    private void applyVersion11(Connection c) throws SQLException {
        // Dimension activation state — persisted across restarts
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS dimension_registry ("
                    + "dimension_id TEXT PRIMARY KEY, "
                    + "active INTEGER NOT NULL DEFAULT 0)");
        }

        // Named ship coordinate destinations (Sickbay, Bridge, Brig, etc.)
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS ship_locations ("
                    + "name TEXT PRIMARY KEY, "
                    + "x REAL NOT NULL, "
                    + "y REAL NOT NULL, "
                    + "z REAL NOT NULL, "
                    + "world_key TEXT NOT NULL DEFAULT 'minecraft:overworld', "
                    + "registered_by TEXT, "
                    + "registered_at INTEGER NOT NULL)");
        }

        // Beam-up requests from colony dimension players
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS beam_up_requests ("
                    + "request_id TEXT PRIMARY KEY, "
                    + "player_uuid TEXT NOT NULL, "
                    + "player_name TEXT NOT NULL, "
                    + "source_dimension TEXT NOT NULL, "
                    + "requested_at INTEGER NOT NULL, "
                    + "status TEXT NOT NULL DEFAULT 'PENDING', "
                    + "handled_by TEXT, "
                    + "handled_at INTEGER)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_beamup_status "
                    + "ON beam_up_requests (status)");
        }

        // Registered transporter controllers
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS transporter_controllers ("
                    + "controller_id TEXT PRIMARY KEY, "
                    + "world_key TEXT NOT NULL, "
                    + "block_pos_long INTEGER NOT NULL, "
                    + "registered_by TEXT, "
                    + "registered_at INTEGER NOT NULL)");
            s.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_controller_pos "
                    + "ON transporter_controllers (world_key, block_pos_long)");
        }
    }

    private void applyVersion12(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS service_record_entries ("
                    + "entry_id TEXT PRIMARY KEY, "
                    + "player_uuid TEXT NOT NULL, "
                    + "timestamp INTEGER NOT NULL, "
                    + "type TEXT NOT NULL, "
                    + "points_delta INTEGER NOT NULL DEFAULT 0, "
                    + "actor_uuid TEXT, "
                    + "actor_name TEXT, "
                    + "reason TEXT NOT NULL DEFAULT '', "
                    + "division_context TEXT NOT NULL DEFAULT '')");
            s.execute("CREATE INDEX IF NOT EXISTS idx_service_record_player "
                    + "ON service_record_entries (player_uuid)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_service_record_type "
                    + "ON service_record_entries (player_uuid, type)");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void execSafe(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            if (!e.getMessage().toLowerCase().contains("duplicate column")) {
                throw e;
            }
        }
    }
}