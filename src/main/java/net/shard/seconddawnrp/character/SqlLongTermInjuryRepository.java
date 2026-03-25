package net.shard.seconddawnrp.character;

import net.shard.seconddawnrp.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class SqlLongTermInjuryRepository implements LongTermInjuryRepository {

    private final DatabaseManager databaseManager;

    public SqlLongTermInjuryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public Optional<LongTermInjury> loadActive(UUID playerUuid) {
        try {
            Connection connection = databaseManager.getConnection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM long_term_injuries "
                            + "WHERE player_uuid = ? AND active = 1 LIMIT 1")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load active LTI for " + playerUuid, e);
        }
    }

    @Override
    public void save(LongTermInjury injury) {
        try {
            Connection connection = databaseManager.getConnection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO long_term_injuries "
                            + "(injury_id, player_uuid, tier, applied_at_ms, expires_at_ms, "
                            + "sessions_completed, last_treatment_ms, active) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                            + "ON CONFLICT(injury_id) DO UPDATE SET "
                            + "expires_at_ms       = excluded.expires_at_ms, "
                            + "sessions_completed  = excluded.sessions_completed, "
                            + "last_treatment_ms   = excluded.last_treatment_ms, "
                            + "active              = excluded.active")) {

                ps.setString(1, injury.getInjuryId());
                ps.setString(2, injury.getPlayerUuid().toString());
                ps.setString(3, injury.getTier().name());
                ps.setLong(4,   injury.getAppliedAtMs());
                ps.setLong(5,   injury.getExpiresAtMs());
                ps.setInt(6,    injury.getSessionsCompleted());
                ps.setLong(7,   injury.getLastTreatmentMs());
                ps.setInt(8,    injury.isActive() ? 1 : 0);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save LTI " + injury.getInjuryId(), e);
        }
    }

    @Override
    public void deactivate(String injuryId) {
        try {
            Connection connection = databaseManager.getConnection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE long_term_injuries SET active = 0 WHERE injury_id = ?")) {
                ps.setString(1, injuryId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to deactivate LTI " + injuryId, e);
        }
    }

    @Override
    public List<LongTermInjury> loadAllActive() {
        List<LongTermInjury> result = new ArrayList<>();
        try {
            Connection connection = databaseManager.getConnection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM long_term_injuries WHERE active = 1");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load all active LTIs", e);
        }
        return result;
    }

    private LongTermInjury mapRow(ResultSet rs) throws SQLException {
        return new LongTermInjury(
                rs.getString("injury_id"),
                UUID.fromString(rs.getString("player_uuid")),
                LongTermInjuryTier.valueOf(rs.getString("tier")),
                rs.getLong("applied_at_ms"),
                rs.getLong("expires_at_ms"),
                rs.getInt("sessions_completed"),
                rs.getLong("last_treatment_ms"),
                rs.getInt("active") == 1
        );
    }
}
