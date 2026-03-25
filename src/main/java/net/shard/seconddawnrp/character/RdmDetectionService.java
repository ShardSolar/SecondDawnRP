package net.shard.seconddawnrp.character;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.database.DatabaseManager;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;

/**
 * Detects and records suspicious player-versus-player downs.
 *
 * <p>Trigger conditions (ALL must be true to generate a flag):
 * <ol>
 *   <li>Player A causes player B to reach a downed state.</li>
 *   <li>No active GM event at that location at the time of the down.</li>
 *   <li>Victim had no Security interaction in the preceding 5 minutes.</li>
 * </ol>
 *
 * <p>This service flags and logs only — it never auto-punishes.
 * GMs are notified in-game immediately on flag generation.
 *
 * <p>Security interaction timestamps are maintained externally by the
 * Security system (Phase 9). Until then the service assumes zero
 * (meaning any PvP down outside a GM event is flagged).
 */
public class RdmDetectionService {

    /** Window within which a prior Security interaction makes a PvP down exempt. */
    private static final long SECURITY_INTERACTION_WINDOW_MS = 5L * 60 * 1000; // 5 minutes

    private final DatabaseManager databaseManager;
    private MinecraftServer server;

    public RdmDetectionService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Evaluate whether a PvP down warrants an RDM flag and, if so, create one.
     *
     * <p>Called from the downed-state system (Phase 8) when a player is downed
     * by another player.
     *
     * @param attacker                  the player who caused the down
     * @param victim                    the player who was downed
     * @param worldKey                  world registry key string
     * @param pos                       block position of the incident
     * @param gmEventActiveAtLocation   true if a GM event was running at this location
     * @param lastSecurityInteractionMs last timestamp of a Security interaction involving
     *                                  the victim, or 0 if none recorded
     */
    public void evaluate(
            ServerPlayerEntity attacker,
            ServerPlayerEntity victim,
            String worldKey,
            BlockPos pos,
            boolean gmEventActiveAtLocation,
            long lastSecurityInteractionMs
    ) {
        // Condition 2: no GM event at location
        if (gmEventActiveAtLocation) return;

        // Condition 3: no Security interaction within 5-minute window
        long now = System.currentTimeMillis();
        if (lastSecurityInteractionMs > 0
                && now - lastSecurityInteractionMs < SECURITY_INTERACTION_WINDOW_MS) {
            return;
        }

        // All conditions met — generate flag
        RdmFlag flag = new RdmFlag(
                UUID.randomUUID().toString(),
                attacker.getUuid(),
                victim.getUuid(),
                worldKey,
                pos.asLong(),
                now,
                false,           // gmEventActiveAtLocation already checked above
                lastSecurityInteractionMs,
                RdmFlag.ReviewState.PENDING,
                null,
                null
        );

        persist(flag);
        notifyGms(attacker, victim, pos);
    }

    /**
     * GM confirms a flag as genuine RDM.
     * Demerit must be issued separately by the GM via the roster system.
     */
    public boolean confirmRdm(String flagId, UUID reviewerUuid) {
        return updateReviewState(flagId, RdmFlag.ReviewState.CONFIRMED_RDM, reviewerUuid);
    }

    /**
     * GM dismisses a flag — the record is preserved but marked inactive.
     */
    public boolean dismiss(String flagId, UUID reviewerUuid) {
        return updateReviewState(flagId, RdmFlag.ReviewState.DISMISSED, reviewerUuid);
    }

    public Optional<RdmFlag> load(String flagId) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM rdm_flags WHERE flag_id = ?")) {
                ps.setString(1, flagId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load RDM flag " + flagId, e);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void persist(RdmFlag flag) {
        try {
            Connection conn = databaseManager.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO rdm_flags (flag_id, attacker_uuid, victim_uuid, world_key, "
                            + "block_pos_long, flagged_at_ms, event_active, "
                            + "last_security_interaction_ms, reviewed) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)")) {
                ps.setString(1, flag.getFlagId());
                ps.setString(2, flag.getAttackerUuid().toString());
                ps.setString(3, flag.getVictimUuid().toString());
                ps.setString(4, flag.getWorldKey());
                ps.setLong(5,   flag.getBlockPosLong());
                ps.setLong(6,   flag.getFlaggedAtMs());
                ps.setInt(7,    flag.isEventActiveAtTime() ? 1 : 0);
                ps.setLong(8,   flag.getLastSecurityInteractionMs());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to persist RDM flag", e);
        }
    }

    private boolean updateReviewState(String flagId, RdmFlag.ReviewState state, UUID reviewer) {
        try {
            Connection conn = databaseManager.getConnection();
            int reviewedInt = switch (state) {
                case CONFIRMED_RDM -> 1;
                case DISMISSED     -> 2;
                default            -> 0;
            };
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE rdm_flags SET reviewed = ?, reviewed_by_uuid = ?, reviewed_at_ms = ? "
                            + "WHERE flag_id = ?")) {
                ps.setInt(1,    reviewedInt);
                ps.setString(2, reviewer.toString());
                ps.setLong(3,   System.currentTimeMillis());
                ps.setString(4, flagId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update RDM flag " + flagId, e);
        }
    }

    private void notifyGms(ServerPlayerEntity attacker, ServerPlayerEntity victim, BlockPos pos) {
        if (server == null) return;

        String attackerName = attacker.getName().getString();
        String victimName   = victim.getName().getString();
        String coords = "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";

        Text message = Text.literal("[RDM Flag] ")
                .formatted(Formatting.RED)
                .append(Text.literal(attackerName)
                        .formatted(Formatting.YELLOW))
                .append(Text.literal(" downed ")
                        .formatted(Formatting.RED))
                .append(Text.literal(victimName)
                        .formatted(Formatting.YELLOW))
                .append(Text.literal(" at " + coords + " — no active event / Security interaction.")
                        .formatted(Formatting.RED));

        // Notify all online players with GM permission
        // GM permission check: rely on LuckPerms op or permission node
        // Phase 10.5 adds the full Permissions.java constant — for now notify ops
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.hasPermissionLevel(3)) { // op level 3 = GM-equivalent for now
                player.sendMessage(message, false);
            }
        }
    }

    private RdmFlag mapRow(ResultSet rs) throws SQLException {
        int reviewedInt = rs.getInt("reviewed");
        RdmFlag.ReviewState state = switch (reviewedInt) {
            case 1  -> RdmFlag.ReviewState.CONFIRMED_RDM;
            case 2  -> RdmFlag.ReviewState.DISMISSED;
            default -> RdmFlag.ReviewState.PENDING;
        };

        String reviewerStr = rs.getString("reviewed_by_uuid");
        long reviewedAtRaw = rs.getLong("reviewed_at_ms");
        Long reviewedAt    = rs.wasNull() ? null : reviewedAtRaw;

        return new RdmFlag(
                rs.getString("flag_id"),
                UUID.fromString(rs.getString("attacker_uuid")),
                UUID.fromString(rs.getString("victim_uuid")),
                rs.getString("world_key"),
                rs.getLong("block_pos_long"),
                rs.getLong("flagged_at_ms"),
                rs.getInt("event_active") == 1,
                rs.getLong("last_security_interaction_ms"),
                state,
                reviewerStr != null ? UUID.fromString(reviewerStr) : null,
                reviewedAt
        );
    }
}