package net.shard.seconddawnrp.character;

import net.shard.seconddawnrp.database.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite-backed implementation of {@link CharacterRepository}.
 *
 * <p>Reads and writes to {@code character_profiles} and
 * {@code character_known_languages} tables (created in migration V4).
 *
 * <p>DECEASED profiles are never deleted — the table is append-only
 * for historical records. {@link #loadActive} always returns the most
 * recently created non-DECEASED record for the player.
 */
public final class SqlCharacterRepository implements CharacterRepository {

    private final DatabaseManager databaseManager;

    public SqlCharacterRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    // ── CharacterRepository ───────────────────────────────────────────────────

    @Override
    public Optional<CharacterProfile> loadActive(UUID playerUuid) {
        try {
            Connection connection = databaseManager.getConnection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM character_profiles "
                            + "WHERE player_uuid = ? "
                            + "AND status != 'DECEASED' "
                            + "ORDER BY created_at DESC LIMIT 1")) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(mapRow(rs, connection));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load active character for " + playerUuid, e);
        }
    }

    @Override
    public Optional<CharacterProfile> loadById(String characterId) {
        try {
            Connection connection = databaseManager.getConnection();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT * FROM character_profiles WHERE character_id = ?")) {
                ps.setString(1, characterId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(mapRow(rs, connection));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load character by id " + characterId, e);
        }
    }

    @Override
    public void save(CharacterProfile profile) {
        Connection connection = null;
        boolean originalAutoCommit = true;

        try {
            connection = databaseManager.getConnection();
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            upsertProfile(profile, connection);
            replaceLanguages(profile, connection);

            connection.commit();
        } catch (SQLException e) {
            if (connection != null) {
                try { connection.rollback(); } catch (SQLException re) { e.addSuppressed(re); }
            }
            throw new RuntimeException(
                    "Failed to save character profile " + profile.getCharacterId(), e);
        } finally {
            if (connection != null) {
                try { connection.setAutoCommit(originalAutoCommit); }
                catch (SQLException e) {
                    throw new RuntimeException("Failed to restore auto-commit", e);
                }
            }
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void upsertProfile(CharacterProfile p, Connection connection) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO character_profiles ("
                        + "character_id, player_uuid, character_name, species, bio, status, "
                        + "universal_translator, permadeath_consent, active_long_term_injury_id, "
                        + "deceased_at, progression_transfer, created_at"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(character_id) DO UPDATE SET "
                        + "character_name              = excluded.character_name, "
                        + "species                     = excluded.species, "
                        + "bio                         = excluded.bio, "
                        + "status                      = excluded.status, "
                        + "universal_translator        = excluded.universal_translator, "
                        + "permadeath_consent          = excluded.permadeath_consent, "
                        + "active_long_term_injury_id  = excluded.active_long_term_injury_id, "
                        + "deceased_at                 = excluded.deceased_at, "
                        + "progression_transfer        = excluded.progression_transfer")) {

            ps.setString(1, p.getCharacterId());
            ps.setString(2, p.getPlayerUuid().toString());
            ps.setString(3, p.getCharacterName());

            if (p.getSpecies() != null) ps.setString(4, p.getSpecies());
            else ps.setNull(4, Types.VARCHAR);

            if (p.getBio() != null) ps.setString(5, p.getBio());
            else ps.setNull(5, Types.VARCHAR);

            ps.setString(6, p.getStatus().name());
            ps.setInt(7,    p.hasUniversalTranslator() ? 1 : 0);
            ps.setInt(8,    p.isPermadeathConsent() ? 1 : 0);

            if (p.getActiveLongTermInjuryId() != null)
                ps.setString(9, p.getActiveLongTermInjuryId());
            else ps.setNull(9, Types.VARCHAR);

            if (p.getDeceasedAt() != null) ps.setLong(10, p.getDeceasedAt());
            else ps.setNull(10, Types.INTEGER);

            ps.setInt(11,  p.getProgressionTransfer());
            ps.setLong(12, p.getCreatedAt());

            ps.executeUpdate();
        }
    }

    private void replaceLanguages(CharacterProfile p, Connection connection) throws SQLException {
        try (PreparedStatement del = connection.prepareStatement(
                "DELETE FROM character_known_languages WHERE character_id = ?")) {
            del.setString(1, p.getCharacterId());
            del.executeUpdate();
        }

        List<String> langs = p.getKnownLanguages();
        if (langs.isEmpty()) return;

        try (PreparedStatement ins = connection.prepareStatement(
                "INSERT INTO character_known_languages (character_id, language_id) VALUES (?, ?)")) {
            for (String lang : langs) {
                ins.setString(1, p.getCharacterId());
                ins.setString(2, lang);
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    private CharacterProfile mapRow(ResultSet rs, Connection connection) throws SQLException {
        String characterId = rs.getString("character_id");
        UUID playerUuid    = UUID.fromString(rs.getString("player_uuid"));

        String speciesRaw = rs.getString("species");
        String bioRaw     = rs.getString("bio");
        String ltiId      = rs.getString("active_long_term_injury_id");

        long deceasedAtRaw = rs.getLong("deceased_at");
        Long deceasedAt = rs.wasNull() ? null : deceasedAtRaw;

        // Load known languages for this characterId
        List<String> langs = loadLanguages(characterId, connection);

        return new CharacterProfile(
                characterId,
                playerUuid,
                rs.getString("character_name"),
                speciesRaw,
                bioRaw,
                CharacterStatus.valueOf(rs.getString("status")),
                langs,
                rs.getInt("universal_translator") == 1,
                rs.getInt("permadeath_consent") == 1,
                ltiId,
                deceasedAt,
                rs.getInt("progression_transfer"),
                rs.getLong("created_at")
        );
    }

    private List<String> loadLanguages(String characterId, Connection connection) throws SQLException {
        List<String> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT language_id FROM character_known_languages WHERE character_id = ?")) {
            ps.setString(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getString("language_id"));
            }
        }
        return result;
    }
}