package net.shard.seconddawnrp.tactical.data;

import net.shard.seconddawnrp.database.DatabaseManager;

import java.sql.*;
import java.util.Optional;

/**
 * Reads and writes the home ship's persistent position from the ship_position table.
 *
 * One row per home ship — upserted on every passive tick and on SERVER_STOPPING.
 * Loaded once on SERVER_STARTED to restore position after restart.
 */
public class SqlShipPositionRepository {

    private final DatabaseManager db;

    public SqlShipPositionRepository(DatabaseManager db) {
        this.db = db;
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    public Optional<ShipPositionSnapshot> load(String shipId) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM ship_position WHERE ship_id = ?")) {
            ps.setString(1, shipId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new ShipPositionSnapshot(
                        rs.getString("ship_id"),
                        rs.getDouble("pos_x"),
                        rs.getDouble("pos_z"),
                        rs.getFloat("heading"),
                        rs.getFloat("speed"),
                        rs.getInt("warp_speed"),
                        rs.getBoolean("warp_capable"),
                        rs.getFloat("target_heading"),
                        rs.getFloat("target_speed"),
                        rs.getLong("last_updated")));
            }
        } catch (SQLException e) {
            System.err.println("[SecondDawnRP] Failed to load ship position for "
                    + shipId + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    public void save(ShipState ship) {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ship_position " +
                             "(ship_id, pos_x, pos_z, heading, speed, warp_speed, warp_capable, " +
                             " target_heading, target_speed, last_updated) " +
                             "VALUES (?,?,?,?,?,?,?,?,?,?) " +
                             "ON CONFLICT(ship_id) DO UPDATE SET " +
                             "  pos_x          = excluded.pos_x, " +
                             "  pos_z          = excluded.pos_z, " +
                             "  heading        = excluded.heading, " +
                             "  speed          = excluded.speed, " +
                             "  warp_speed     = excluded.warp_speed, " +
                             "  warp_capable   = excluded.warp_capable, " +
                             "  target_heading = excluded.target_heading, " +
                             "  target_speed   = excluded.target_speed, " +
                             "  last_updated   = excluded.last_updated")) {
            ps.setString(1, ship.getShipId());
            ps.setDouble(2, ship.getPosX());
            ps.setDouble(3, ship.getPosZ());
            ps.setFloat(4,  ship.getHeading());
            ps.setFloat(5,  ship.getSpeed());
            ps.setInt(6,    ship.getWarpSpeed());
            ps.setBoolean(7, ship.isWarpCapable());
            ps.setFloat(8,  ship.getTargetHeading());
            ps.setFloat(9,  ship.getTargetSpeed());
            ps.setLong(10,  System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[SecondDawnRP] Failed to save ship position for "
                    + ship.getShipId() + ": " + e.getMessage());
        }
    }

    // ── Snapshot record ───────────────────────────────────────────────────────

    public record ShipPositionSnapshot(
            String shipId,
            double posX,
            double posZ,
            float  heading,
            float  speed,
            int    warpSpeed,
            boolean warpCapable,
            float  targetHeading,
            float  targetSpeed,
            long   lastUpdated
    ) {}
}