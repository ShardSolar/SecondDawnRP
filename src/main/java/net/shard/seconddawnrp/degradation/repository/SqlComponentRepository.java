package net.shard.seconddawnrp.degradation.repository;

import net.shard.seconddawnrp.database.DatabaseManager;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL-backed component repository targeting the {@code components} table
 * added in schema version 3.
 *
 * <p>V15: {@code ship_id} column added via migration. {@code save()} and
 * {@code saveAll()} write it; {@code fromResultSet()} reads it with a
 * try/catch fallback to null so the repository works against pre-V15
 * databases during the migration window.
 */
public class SqlComponentRepository implements ComponentRepository {

    private final DatabaseManager db;

    public SqlComponentRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public void save(ComponentEntry entry) {
        String sql = "INSERT OR REPLACE INTO components ("
                + "component_id, world_key, block_pos_long, block_type_id, display_name, "
                + "health, status, last_drain_tick_ms, last_task_generated_ms, registered_by_uuid, "
                + "repair_item_id, repair_item_count, missing_block, ship_id"
                + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1,  entry.getComponentId());
            ps.setString(2,  entry.getWorldKey());
            ps.setLong(3,    entry.getBlockPosLong());
            ps.setString(4,  entry.getBlockTypeId());
            ps.setString(5,  entry.getDisplayName());
            ps.setInt(6,     entry.getHealth());
            ps.setString(7,  entry.getStatus().name());
            ps.setLong(8,    entry.getLastDrainTickMs());
            ps.setLong(9,    entry.getLastTaskGeneratedMs());
            ps.setString(10, entry.getRegisteredByUuid() != null
                    ? entry.getRegisteredByUuid().toString() : null);
            ps.setString(11, entry.getRepairItemId());
            ps.setInt(12,    entry.getRepairItemCount());
            ps.setInt(13,    entry.isMissingBlock() ? 1 : 0);
            // V15 — ship_id nullable
            if (entry.getShipId() != null) {
                ps.setString(14, entry.getShipId());
            } else {
                ps.setNull(14, Types.VARCHAR);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save component " + entry.getComponentId(), e);
        }
    }

    @Override
    public void saveAll(Collection<ComponentEntry> entries) {
        String sql = "INSERT OR REPLACE INTO components ("
                + "component_id, world_key, block_pos_long, block_type_id, display_name, "
                + "health, status, last_drain_tick_ms, last_task_generated_ms, registered_by_uuid, "
                + "repair_item_id, repair_item_count, missing_block, ship_id"
                + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (ComponentEntry entry : entries) {
                ps.setString(1,  entry.getComponentId());
                ps.setString(2,  entry.getWorldKey());
                ps.setLong(3,    entry.getBlockPosLong());
                ps.setString(4,  entry.getBlockTypeId());
                ps.setString(5,  entry.getDisplayName());
                ps.setInt(6,     entry.getHealth());
                ps.setString(7,  entry.getStatus().name());
                ps.setLong(8,    entry.getLastDrainTickMs());
                ps.setLong(9,    entry.getLastTaskGeneratedMs());
                ps.setString(10, entry.getRegisteredByUuid() != null
                        ? entry.getRegisteredByUuid().toString() : null);
                ps.setString(11, entry.getRepairItemId());
                ps.setInt(12,    entry.getRepairItemCount());
                ps.setInt(13,    entry.isMissingBlock() ? 1 : 0);
                if (entry.getShipId() != null) {
                    ps.setString(14, entry.getShipId());
                } else {
                    ps.setNull(14, Types.VARCHAR);
                }
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to bulk-save components", e);
        }
    }

    @Override
    public Collection<ComponentEntry> loadAll() {
        List<ComponentEntry> result = new ArrayList<>();
        String sql = "SELECT * FROM components";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(fromResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load all components", e);
        }
        return result;
    }

    @Override
    public Optional<ComponentEntry> findById(String componentId) {
        String sql = "SELECT * FROM components WHERE component_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, componentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(fromResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find component by id: " + componentId, e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<ComponentEntry> findByPosition(String worldKey, long blockPosLong) {
        String sql = "SELECT * FROM components WHERE world_key = ? AND block_pos_long = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, worldKey);
            ps.setLong(2, blockPosLong);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(fromResultSet(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find component by position", e);
        }
        return Optional.empty();
    }

    @Override
    public void delete(String componentId) {
        String sql = "DELETE FROM components WHERE component_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, componentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete component: " + componentId, e);
        }
    }

    @Override
    public void deleteAll() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM components")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete all components", e);
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private static ComponentEntry fromResultSet(ResultSet rs) throws SQLException {
        String componentId     = rs.getString("component_id");
        String worldKey        = rs.getString("world_key");
        long   blockPosLong    = rs.getLong("block_pos_long");
        String blockTypeId     = rs.getString("block_type_id");
        String displayName     = rs.getString("display_name");
        int    health          = rs.getInt("health");
        ComponentStatus status = ComponentStatus.valueOf(rs.getString("status"));
        long   lastDrainTickMs = rs.getLong("last_drain_tick_ms");
        long   lastTaskGeneratedMs = rs.getLong("last_task_generated_ms");

        String uuidStr = rs.getString("registered_by_uuid");
        UUID registeredByUuid = (uuidStr != null && !uuidStr.isBlank())
                ? UUID.fromString(uuidStr) : null;

        String repairItemId  = rs.getString("repair_item_id");
        int    repairItemCount = rs.getInt("repair_item_count");

        // missing_block — in schema since V3 CREATE, always present
        boolean missingBlock = rs.getInt("missing_block") == 1;

        // V15 — ship_id column added by migration. Use try/catch so this works
        // against a DB that hasn't run V15 yet (migration window safety net).
        String shipId = null;
        try {
            shipId = rs.getString("ship_id");
        } catch (SQLException ignored) {
            // Column not present — pre-V15 DB. Leave shipId as null.
        }

        return new ComponentEntry(
                componentId, worldKey, blockPosLong, blockTypeId, displayName,
                health, status, lastDrainTickMs, lastTaskGeneratedMs,
                registeredByUuid, repairItemId, repairItemCount, missingBlock,
                shipId
        );
    }
}