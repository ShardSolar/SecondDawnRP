package net.shard.seconddawnrp.tactical.data;

import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.database.DatabaseManager;

import java.sql.*;
import java.util.*;

/**
 * Persistence for ship registry, hardpoints, damage zones, and encounter state.
 */
public class TacticalRepository {

    private final DatabaseManager db;

    public TacticalRepository(DatabaseManager db) {
        this.db = db;
    }

    // ── Ship registry ─────────────────────────────────────────────────────────

    public void saveShipRegistryEntry(ShipRegistryEntry entry) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO ship_registry (ship_id, registry_name, ship_class, faction, " +
                            "model_world_key, model_origin_long, real_ship_world_key, real_ship_origin_long, " +
                            "default_spawn_long, default_spawn_world_key, default_pos_x, default_pos_z, " +
                            "default_heading, is_home_ship) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                            "ON CONFLICT(ship_id) DO UPDATE SET " +
                            "registry_name=excluded.registry_name, ship_class=excluded.ship_class, " +
                            "faction=excluded.faction, model_world_key=excluded.model_world_key, " +
                            "model_origin_long=excluded.model_origin_long, " +
                            "real_ship_world_key=excluded.real_ship_world_key, " +
                            "real_ship_origin_long=excluded.real_ship_origin_long, " +
                            "default_spawn_long=excluded.default_spawn_long, " +
                            "default_spawn_world_key=excluded.default_spawn_world_key, " +
                            "default_pos_x=excluded.default_pos_x, " +
                            "default_pos_z=excluded.default_pos_z, " +
                            "default_heading=excluded.default_heading, " +
                            "is_home_ship=excluded.is_home_ship")) {
                ps.setString(1, entry.getShipId());
                ps.setString(2, entry.getRegistryName());
                ps.setString(3, entry.getShipClass());
                ps.setString(4, entry.getFaction());
                setNullable(ps, 5, entry.getModelWorldKey());
                ps.setLong(6, entry.getModelOrigin().asLong());
                setNullable(ps, 7, entry.getRealShipWorldKey());
                ps.setLong(8, entry.getRealShipOrigin().asLong());
                ps.setLong(9, entry.getDefaultSpawn().asLong());
                setNullable(ps, 10, entry.getDefaultSpawnWorldKey());
                ps.setDouble(11, entry.getDefaultPosX());
                ps.setDouble(12, entry.getDefaultPosZ());
                ps.setFloat(13, entry.getDefaultHeading());
                ps.setInt(14, entry.isHomeShip() ? 1 : 0);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to save ship registry entry: " + e.getMessage());
        }
    }

    public List<ShipRegistryEntry> loadAllShipRegistry() {
        List<ShipRegistryEntry> result = new ArrayList<>();
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM ship_registry");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ShipRegistryEntry e = new ShipRegistryEntry(
                            rs.getString("ship_id"),
                            rs.getString("registry_name"),
                            rs.getString("ship_class"),
                            rs.getString("faction"));
                    String mwk = rs.getString("model_world_key");
                    if (mwk != null) {
                        e.setModelWorldKey(mwk);
                        e.setModelOrigin(BlockPos.fromLong(rs.getLong("model_origin_long")));
                    }
                    String rwk = rs.getString("real_ship_world_key");
                    if (rwk != null) {
                        e.setRealShipWorldKey(rwk);
                        e.setRealShipOrigin(BlockPos.fromLong(rs.getLong("real_ship_origin_long")));
                    }
                    String swk = rs.getString("default_spawn_world_key");
                    if (swk != null)
                        e.setDefaultSpawn(BlockPos.fromLong(rs.getLong("default_spawn_long")), swk);
                    e.setDefaultPosition(
                            rs.getDouble("default_pos_x"),
                            rs.getDouble("default_pos_z"),
                            rs.getFloat("default_heading"));
                    try { e.setHomeShip(rs.getInt("is_home_ship") == 1); }
                    catch (SQLException ignored) {}
                    result.add(e);
                }
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to load ship registry: " + e.getMessage());
        }
        return result;
    }

    public boolean deleteShipRegistryEntry(String shipId) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM ship_registry WHERE ship_id = ?")) {
                ps.setString(1, shipId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to delete ship: " + e.getMessage());
            return false;
        }
    }

    // ── Hardpoints ────────────────────────────────────────────────────────────

    public void saveHardpoint(HardpointEntry h) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO hardpoint_registry " +
                            "(hardpoint_id, ship_id, block_pos_long, weapon_type, arc, power_draw, reload_ticks, health) " +
                            "VALUES (?,?,?,?,?,?,?,?) " +
                            "ON CONFLICT(hardpoint_id) DO UPDATE SET health=excluded.health")) {
                ps.setString(1, h.getHardpointId());
                ps.setString(2, h.getShipId());
                ps.setLong(3, h.getBlockPosLong());
                ps.setString(4, h.getWeaponType().name());
                ps.setString(5, h.getArc().name());
                ps.setInt(6, h.getPowerDraw());
                ps.setInt(7, h.getReloadTicks());
                ps.setInt(8, h.getHealth());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to save hardpoint: " + e.getMessage());
        }
    }

    public List<HardpointEntry> loadHardpointsForShip(String shipId) {
        List<HardpointEntry> result = new ArrayList<>();
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM hardpoint_registry WHERE ship_id = ?")) {
                ps.setString(1, shipId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new HardpointEntry(
                                rs.getString("hardpoint_id"),
                                rs.getString("ship_id"),
                                BlockPos.fromLong(rs.getLong("block_pos_long")),
                                HardpointEntry.WeaponType.valueOf(rs.getString("weapon_type")),
                                HardpointEntry.Arc.valueOf(rs.getString("arc")),
                                rs.getInt("power_draw"),
                                rs.getInt("reload_ticks"),
                                rs.getInt("health")));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to load hardpoints: " + e.getMessage());
        }
        return result;
    }

    public boolean deleteHardpoint(String hardpointId) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM hardpoint_registry WHERE hardpoint_id = ?")) {
                ps.setString(1, hardpointId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to delete hardpoint: " + e.getMessage());
            return false;
        }
    }

    // ── Zone block registrations ──────────────────────────────────────────────

    /**
     * Load all zone block registrations for all ships.
     * Returns: Map<shipId, Map<zoneId, Map<"MODEL"|"REAL", List<Long>>>>
     */
    public Map<String, Map<String, Map<String, List<Long>>>> loadAllZoneBlocks() {
        Map<String, Map<String, Map<String, List<Long>>>> result = new HashMap<>();

        try {
            Connection conn = db.getConnection();

            // Load model blocks
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT ship_id, zone_id, block_pos_long FROM damage_zone_model_blocks");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result
                            .computeIfAbsent(rs.getString("ship_id"), k -> new HashMap<>())
                            .computeIfAbsent(rs.getString("zone_id"), k -> new HashMap<>())
                            .computeIfAbsent("MODEL", k -> new ArrayList<>())
                            .add(rs.getLong("block_pos_long"));
                }
            }

            // Load real blocks
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT ship_id, zone_id, block_pos_long FROM damage_zone_real_blocks");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result
                            .computeIfAbsent(rs.getString("ship_id"), k -> new HashMap<>())
                            .computeIfAbsent(rs.getString("zone_id"), k -> new HashMap<>())
                            .computeIfAbsent("REAL", k -> new ArrayList<>())
                            .add(rs.getLong("block_pos_long"));
                }
            }

        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to load zone blocks: " + e.getMessage());
        }

        return result;
    }

    /** Insert a model block registration. Ignores duplicate. */
    public void saveZoneModelBlock(String shipId, String zoneId, long blockPosLong) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO damage_zone_model_blocks " +
                            "(zone_id, ship_id, block_pos_long) VALUES (?,?,?)")) {
                ps.setString(1, zoneId);
                ps.setString(2, shipId);
                ps.setLong(3, blockPosLong);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to save zone model block: " + e.getMessage());
        }
    }

    /** Insert a real block registration. Ignores duplicate. */
    public void saveZoneRealBlock(String shipId, String zoneId, long blockPosLong) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO damage_zone_real_blocks " +
                            "(zone_id, ship_id, block_pos_long) VALUES (?,?,?)")) {
                ps.setString(1, zoneId);
                ps.setString(2, shipId);
                ps.setLong(3, blockPosLong);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to save zone real block: " + e.getMessage());
        }
    }

    /** Delete a single model block registration. */
    public void deleteZoneModelBlock(String shipId, String zoneId, long blockPosLong) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM damage_zone_model_blocks " +
                            "WHERE ship_id=? AND zone_id=? AND block_pos_long=?")) {
                ps.setString(1, shipId);
                ps.setString(2, zoneId);
                ps.setLong(3, blockPosLong);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to delete zone model block: " + e.getMessage());
        }
    }

    /** Delete a single real block registration. */
    public void deleteZoneRealBlock(String shipId, String zoneId, long blockPosLong) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM damage_zone_real_blocks " +
                            "WHERE ship_id=? AND zone_id=? AND block_pos_long=?")) {
                ps.setString(1, shipId);
                ps.setString(2, zoneId);
                ps.setLong(3, blockPosLong);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to delete zone real block: " + e.getMessage());
        }
    }

    /** Delete all model blocks for a zone. */
    public void deleteAllZoneModelBlocks(String shipId, String zoneId) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM damage_zone_model_blocks WHERE ship_id=? AND zone_id=?")) {
                ps.setString(1, shipId);
                ps.setString(2, zoneId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to delete zone model blocks: " + e.getMessage());
        }
    }

    /** Delete all real blocks for a zone. */
    public void deleteAllZoneRealBlocks(String shipId, String zoneId) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM damage_zone_real_blocks WHERE ship_id=? AND zone_id=?")) {
                ps.setString(1, shipId);
                ps.setString(2, zoneId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to delete zone real blocks: " + e.getMessage());
        }
    }

    /** Delete all block registrations for a ship (used when removing a zone entirely). */
    public void deleteAllZoneBlocksForShipZone(String shipId, String zoneId) {
        deleteAllZoneModelBlocks(shipId, zoneId);
        deleteAllZoneRealBlocks(shipId, zoneId);
    }

    // ── Shipyard ──────────────────────────────────────────────────────────────

    public void saveShipyard(String worldKey, double x, double y, double z) {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO shipyard_config (id, world_key, spawn_x, spawn_y, spawn_z) " +
                            "VALUES (1, ?, ?, ?, ?) " +
                            "ON CONFLICT(id) DO UPDATE SET world_key=excluded.world_key, " +
                            "spawn_x=excluded.spawn_x, spawn_y=excluded.spawn_y, spawn_z=excluded.spawn_z")) {
                ps.setString(1, worldKey);
                ps.setDouble(2, x);
                ps.setDouble(3, y);
                ps.setDouble(4, z);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to save shipyard: " + e.getMessage());
        }
    }

    public Optional<double[]> loadShipyard() {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM shipyard_config WHERE id = 1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new double[]{
                            rs.getDouble("spawn_x"),
                            rs.getDouble("spawn_y"),
                            rs.getDouble("spawn_z")});
                }
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to load shipyard: " + e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<String> loadShipyardWorldKey() {
        try {
            Connection conn = db.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT world_key FROM shipyard_config WHERE id = 1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(rs.getString("world_key"));
            }
        } catch (SQLException e) {
            System.err.println("[Tactical] Failed to load shipyard world key: " + e.getMessage());
        }
        return Optional.empty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setNullable(PreparedStatement ps, int idx, String value) throws SQLException {
        if (value != null) ps.setString(idx, value);
        else ps.setNull(idx, Types.VARCHAR);
    }
}