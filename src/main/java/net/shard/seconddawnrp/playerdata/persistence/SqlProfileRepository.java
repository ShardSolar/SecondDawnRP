package net.shard.seconddawnrp.playerdata.persistence;

import net.shard.seconddawnrp.database.DatabaseManager;
import net.shard.seconddawnrp.divison.Division;
import net.shard.seconddawnrp.divison.Rank;
import net.shard.seconddawnrp.playerdata.Billet;
import net.shard.seconddawnrp.playerdata.Certification;
import net.shard.seconddawnrp.playerdata.DutyStatus;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.ProgressionPath;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SqlProfileRepository implements ProfileRepository {

    private final DatabaseManager databaseManager;

    public SqlProfileRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public Optional<PlayerProfile> load(UUID playerUuid) {
        try {
            Connection connection = databaseManager.getConnection();

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT player_uuid, player_name, division_id, progression_path_id, rank_id, rank_points, duty_status, supervisor_uuid " +
                            "FROM players WHERE player_uuid = ?")) {

                statement.setString(1, playerUuid.toString());

                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    return Optional.of(mapPlayerRow(rs, connection));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load player profile for uuid " + playerUuid, e);
        }
    }

    @Override
    public void save(PlayerProfile profile) {
        Connection connection = null;
        boolean originalAutoCommit = true;

        try {
            connection = databaseManager.getConnection();
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            upsertPlayer(profile, connection);
            replaceBillets(profile, connection);
            replaceCertifications(profile, connection);

            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    e.addSuppressed(rollbackException);
                }
            }
            throw new RuntimeException("Failed to save player profile for uuid " + profile.getPlayerId(), e);
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(originalAutoCommit);
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to restore auto-commit after saving profile", e);
                }
            }
        }
    }

    @Override
    public void delete(UUID playerUuid) {
        Connection connection = null;
        boolean originalAutoCommit = true;

        try {
            connection = databaseManager.getConnection();
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            deleteBillets(playerUuid, connection);
            deleteCertifications(playerUuid, connection);

            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM players WHERE player_uuid = ?")) {
                statement.setString(1, playerUuid.toString());
                statement.executeUpdate();
            }

            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    e.addSuppressed(rollbackException);
                }
            }
            throw new RuntimeException("Failed to delete player profile for uuid " + playerUuid, e);
        } finally {
            if (connection != null) {
                try {
                    connection.setAutoCommit(originalAutoCommit);
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to restore auto-commit after deleting profile", e);
                }
            }
        }
    }

    @Override
    public Collection<PlayerProfile> loadAll() {
        List<PlayerProfile> profiles = new ArrayList<>();

        try {
            Connection connection = databaseManager.getConnection();

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT player_uuid, player_name, division_id, progression_path_id, rank_id, rank_points, duty_status, supervisor_uuid FROM players");
                 ResultSet rs = statement.executeQuery()) {

                while (rs.next()) {
                    profiles.add(mapPlayerRow(rs, connection));
                }
            }

            return profiles;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load all player profiles", e);
        }
    }

    private void upsertPlayer(PlayerProfile profile, Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO players (player_uuid, player_name, division_id, progression_path_id, rank_id, rank_points, duty_status, supervisor_uuid) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT(player_uuid) DO UPDATE SET " +
                        "player_name = excluded.player_name, " +
                        "division_id = excluded.division_id, " +
                        "progression_path_id = excluded.progression_path_id, " +
                        "rank_id = excluded.rank_id, " +
                        "rank_points = excluded.rank_points, " +
                        "duty_status = excluded.duty_status, " +
                        "supervisor_uuid = excluded.supervisor_uuid")) {

            statement.setString(1, profile.getPlayerId().toString());
            statement.setString(2, safeServiceName(profile.getServiceName()));
            statement.setString(3, profile.getDivision() == null ? Division.UNASSIGNED.name() : profile.getDivision().name());
            statement.setString(4, profile.getProgressionPath() == null ? ProgressionPath.ENLISTED.name() : profile.getProgressionPath().name());
            statement.setString(5, profile.getRank() == null ? Rank.JUNIOR_CREWMAN.name() : profile.getRank().name());
            statement.setInt(6, profile.getRankPoints());
            statement.setString(7, profile.getDutyStatus() == null ? DutyStatus.OFF_DUTY.name() : profile.getDutyStatus().name());

            if (profile.getSupervisorId() != null) {
                statement.setString(8, profile.getSupervisorId().toString());
            } else {
                statement.setNull(8, Types.VARCHAR);
            }

            statement.executeUpdate();
        }
    }

    private void replaceBillets(PlayerProfile profile, Connection connection) throws SQLException {
        deleteBillets(profile.getPlayerId(), connection);

        if (profile.getBillets().isEmpty()) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO player_billets (player_uuid, billet_id) VALUES (?, ?)")) {

            for (Billet billet : profile.getBillets()) {
                statement.setString(1, profile.getPlayerId().toString());
                statement.setString(2, billet.name());
                statement.addBatch();
            }

            statement.executeBatch();
        }
    }

    private void replaceCertifications(PlayerProfile profile, Connection connection) throws SQLException {
        deleteCertifications(profile.getPlayerId(), connection);

        if (profile.getCertifications().isEmpty()) {
            return;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO player_certifications (player_uuid, certification_id) VALUES (?, ?)")) {

            for (Certification certification : profile.getCertifications()) {
                statement.setString(1, profile.getPlayerId().toString());
                statement.setString(2, certification.name());
                statement.addBatch();
            }

            statement.executeBatch();
        }
    }

    private void deleteBillets(UUID playerUuid, Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM player_billets WHERE player_uuid = ?")) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    private void deleteCertifications(UUID playerUuid, Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM player_certifications WHERE player_uuid = ?")) {
            statement.setString(1, playerUuid.toString());
            statement.executeUpdate();
        }
    }

    private PlayerProfile mapPlayerRow(ResultSet rs, Connection connection) throws SQLException {
        UUID playerId = UUID.fromString(rs.getString("player_uuid"));

        String serviceName = rs.getString("player_name");
        String divisionId = rs.getString("division_id");
        String progressionPathId = rs.getString("progression_path_id");
        String rankId = rs.getString("rank_id");
        int rankPoints = rs.getInt("rank_points");
        String dutyStatusId = rs.getString("duty_status");
        String supervisorUuid = rs.getString("supervisor_uuid");

        Set<Billet> billets = loadBillets(playerId, connection);
        Set<Certification> certifications = loadCertifications(playerId, connection);

        return new PlayerProfile(
                playerId,
                serviceName == null ? "" : serviceName,
                divisionId == null ? Division.UNASSIGNED : Division.valueOf(divisionId),
                progressionPathId == null ? ProgressionPath.ENLISTED : ProgressionPath.valueOf(progressionPathId),
                rankId == null ? Rank.JUNIOR_CREWMAN : Rank.valueOf(rankId),
                rankPoints,
                billets,
                certifications,
                dutyStatusId == null ? DutyStatus.OFF_DUTY : DutyStatus.valueOf(dutyStatusId),
                supervisorUuid == null || supervisorUuid.isBlank() ? null : UUID.fromString(supervisorUuid)
        );
    }

    private Set<Billet> loadBillets(UUID playerUuid, Connection connection) throws SQLException {
        Set<Billet> billets = new HashSet<>();

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT billet_id FROM player_billets WHERE player_uuid = ?")) {
            statement.setString(1, playerUuid.toString());

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String billetId = rs.getString("billet_id");
                    if (billetId != null && !billetId.isBlank()) {
                        billets.add(Billet.valueOf(billetId));
                    }
                }
            }
        }

        return billets;
    }

    private Set<Certification> loadCertifications(UUID playerUuid, Connection connection) throws SQLException {
        Set<Certification> certifications = new HashSet<>();

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT certification_id FROM player_certifications WHERE player_uuid = ?")) {
            statement.setString(1, playerUuid.toString());

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String certificationId = rs.getString("certification_id");
                    if (certificationId != null && !certificationId.isBlank()) {
                        certifications.add(Certification.valueOf(certificationId));
                    }
                }
            }
        }

        return certifications;
    }

    private String safeServiceName(String serviceName) {
        return serviceName == null ? "" : serviceName;
    }
}