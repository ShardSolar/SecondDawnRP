package net.shard.seconddawnrp.tactical.data;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DB migrations for the Tactical system.
 *
 * V13 — Core tactical tables (ship_registry, hardpoints, zones, encounters, shipyard)
 * V14 — Persistent ship position for home ship + is_home_ship column on ship_registry
 * V15 — Ship bounding box on ship_registry + ship_id on components
 */
public class TacticalMigration {

    public static void applyVersion13(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {

            // Ship registry — named vessels
            s.execute("CREATE TABLE IF NOT EXISTS ship_registry (" +
                    "ship_id TEXT PRIMARY KEY, " +
                    "registry_name TEXT NOT NULL, " +
                    "ship_class TEXT NOT NULL, " +
                    "faction TEXT NOT NULL DEFAULT 'FRIENDLY', " +
                    "model_world_key TEXT, " +
                    "model_origin_long INTEGER NOT NULL DEFAULT 0, " +
                    "real_ship_world_key TEXT, " +
                    "real_ship_origin_long INTEGER NOT NULL DEFAULT 0, " +
                    "default_spawn_long INTEGER NOT NULL DEFAULT 0, " +
                    "default_spawn_world_key TEXT, " +
                    "default_pos_x REAL NOT NULL DEFAULT 0, " +
                    "default_pos_z REAL NOT NULL DEFAULT 0, " +
                    "default_heading REAL NOT NULL DEFAULT 0)");

            // Hardpoint registry — weapon mounts
            s.execute("CREATE TABLE IF NOT EXISTS hardpoint_registry (" +
                    "hardpoint_id TEXT PRIMARY KEY, " +
                    "ship_id TEXT NOT NULL, " +
                    "block_pos_long INTEGER NOT NULL, " +
                    "weapon_type TEXT NOT NULL, " +
                    "arc TEXT NOT NULL, " +
                    "power_draw INTEGER NOT NULL DEFAULT 50, " +
                    "reload_ticks INTEGER NOT NULL DEFAULT 20, " +
                    "health INTEGER NOT NULL DEFAULT 100)");

            // Damage zone registry — model + real ship block mappings
            s.execute("CREATE TABLE IF NOT EXISTS damage_zone_registry (" +
                    "zone_id TEXT NOT NULL, " +
                    "ship_id TEXT NOT NULL, " +
                    "max_hp INTEGER NOT NULL DEFAULT 100, " +
                    "PRIMARY KEY (zone_id, ship_id))");

            // Damage zone model blocks
            s.execute("CREATE TABLE IF NOT EXISTS damage_zone_model_blocks (" +
                    "zone_id TEXT NOT NULL, " +
                    "ship_id TEXT NOT NULL, " +
                    "block_pos_long INTEGER NOT NULL)");

            // Damage zone real ship blocks
            s.execute("CREATE TABLE IF NOT EXISTS damage_zone_real_blocks (" +
                    "zone_id TEXT NOT NULL, " +
                    "ship_id TEXT NOT NULL, " +
                    "block_pos_long INTEGER NOT NULL)");

            // Active encounter state — persisted between restarts
            s.execute("CREATE TABLE IF NOT EXISTS encounter_state (" +
                    "encounter_id TEXT PRIMARY KEY, " +
                    "status TEXT NOT NULL DEFAULT 'READY', " +
                    "created_at INTEGER NOT NULL, " +
                    "started_at INTEGER NOT NULL DEFAULT 0, " +
                    "ended_at INTEGER NOT NULL DEFAULT 0)");

            // Ships in active encounters
            s.execute("CREATE TABLE IF NOT EXISTS encounter_ships (" +
                    "ship_id TEXT NOT NULL, " +
                    "encounter_id TEXT NOT NULL, " +
                    "combat_id TEXT NOT NULL, " +
                    "faction TEXT NOT NULL, " +
                    "ship_class TEXT NOT NULL, " +
                    "pos_x REAL NOT NULL DEFAULT 0, " +
                    "pos_z REAL NOT NULL DEFAULT 0, " +
                    "heading REAL NOT NULL DEFAULT 0, " +
                    "speed REAL NOT NULL DEFAULT 0, " +
                    "hull_integrity INTEGER NOT NULL DEFAULT 0, " +
                    "hull_max INTEGER NOT NULL DEFAULT 0, " +
                    "shield_fore INTEGER NOT NULL DEFAULT 0, " +
                    "shield_aft INTEGER NOT NULL DEFAULT 0, " +
                    "shield_port INTEGER NOT NULL DEFAULT 0, " +
                    "shield_starboard INTEGER NOT NULL DEFAULT 0, " +
                    "weapons_power INTEGER NOT NULL DEFAULT 0, " +
                    "shields_power INTEGER NOT NULL DEFAULT 0, " +
                    "engines_power INTEGER NOT NULL DEFAULT 0, " +
                    "sensors_power INTEGER NOT NULL DEFAULT 0, " +
                    "torpedo_count INTEGER NOT NULL DEFAULT 0, " +
                    "control_mode TEXT NOT NULL DEFAULT 'GM_MANUAL', " +
                    "destroyed INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY (ship_id, encounter_id))");

            // Shipyard spawn point
            s.execute("CREATE TABLE IF NOT EXISTS shipyard_config (" +
                    "id INTEGER PRIMARY KEY DEFAULT 1, " +
                    "world_key TEXT NOT NULL DEFAULT 'minecraft:overworld', " +
                    "spawn_x REAL NOT NULL DEFAULT 0, " +
                    "spawn_y REAL NOT NULL DEFAULT 64, " +
                    "spawn_z REAL NOT NULL DEFAULT 0)");

            // Indexes
            s.execute("CREATE INDEX IF NOT EXISTS idx_hardpoints_ship " +
                    "ON hardpoint_registry (ship_id)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_encounter_ships " +
                    "ON encounter_ships (encounter_id)");
        }

        System.out.println("[SecondDawnRP] Database V13 applied: Tactical tables created.");
    }

    /**
     * V14 — Persistent ship position for home ship passive movement.
     * One row per home ship — stores posX/Z, heading, speed, warpSpeed between restarts.
     */
    public static void applyVersion14(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS ship_position (" +
                    "ship_id       TEXT PRIMARY KEY, " +
                    "pos_x         REAL NOT NULL DEFAULT 0.0, " +
                    "pos_z         REAL NOT NULL DEFAULT 0.0, " +
                    "heading       REAL NOT NULL DEFAULT 0.0, " +
                    "speed         REAL NOT NULL DEFAULT 0.0, " +
                    "warp_speed    INTEGER NOT NULL DEFAULT 0, " +
                    "warp_capable  INTEGER NOT NULL DEFAULT 0, " +
                    "target_heading REAL NOT NULL DEFAULT 0.0, " +
                    "target_speed   REAL NOT NULL DEFAULT 0.0, " +
                    "last_updated  INTEGER NOT NULL DEFAULT 0" +
                    ")");
            // is_home_ship — safe to re-run, ignore if already present
            try {
                s.execute("ALTER TABLE ship_registry ADD COLUMN is_home_ship INTEGER NOT NULL DEFAULT 0");
            } catch (java.sql.SQLException ignored) {
                // Column already exists — safe to ignore
            }
        }
        System.out.println("[SecondDawnRP] Database V14 applied: ship_position table created.");
    }

    /**
     * V15 — Ship bounding box for player-on-ship resolution.
     *
     * ship_registry gains two packed BlockPos longs representing the
     * min and max corners of the real ship build's 3D bounding box.
     * Used by TacticalService.getPlayersOnShip() to scope damage effects,
     * announcements, and the Engineering Pad component filter to the correct vessel.
     *
     * components gains ship_id (nullable) — null = unowned / global component,
     * non-null = belongs to a specific registered ship. Used to filter the
     * Engineering Pad to show only components registered to the ship the
     * Engineering player is currently standing on.
     */
    public static void applyVersion15(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {

            // Ship bounding box corners — packed BlockPos longs.
            // Both default to 0 (BlockPos.ORIGIN) which is the "not set" sentinel.
            // Admin sets via: /tactical ship bounds <shipId> <pos1> <pos2>
            try {
                s.execute("ALTER TABLE ship_registry " +
                        "ADD COLUMN real_bounds_min_long INTEGER NOT NULL DEFAULT 0");
            } catch (java.sql.SQLException ignored) {}

            try {
                s.execute("ALTER TABLE ship_registry " +
                        "ADD COLUMN real_bounds_max_long INTEGER NOT NULL DEFAULT 0");
            } catch (java.sql.SQLException ignored) {}

            // Component ship binding — null = no ship assigned yet (legacy / global).
            // Components registered before V15 are left null and continue to
            // appear in the Engineering Pad regardless of ship context until
            // an admin re-registers them with a ship assignment.
            try {
                s.execute("ALTER TABLE components ADD COLUMN ship_id TEXT");
            } catch (java.sql.SQLException ignored) {}

            // Index for fast ship-filtered component lookups
            try {
                s.execute("CREATE INDEX IF NOT EXISTS idx_components_ship_id " +
                        "ON components (ship_id)");
            } catch (java.sql.SQLException ignored) {}
        }

        System.out.println("[SecondDawnRP] Database V15 applied: " +
                "ship bounding box columns + component ship_id added.");
    }
}