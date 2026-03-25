package net.shard.seconddawnrp.character;

import java.util.UUID;

/**
 * An RDM (Random Death Match) flag written when a player causes a downed
 * state under suspicious circumstances.
 *
 * <p>The system flags and logs — it NEVER auto-punishes.
 * Human judgment (GM/admin review) always determines the outcome.
 *
 * <p>Persisted in the {@code rdm_flags} table (migration V4).
 * Reviewed via the Admin Audit GUI (Phase 10.5).
 */
public class RdmFlag {

    public enum ReviewState {
        /** Pending GM review. */
        PENDING,
        /** GM confirmed this was RDM — demerit issued separately. */
        CONFIRMED_RDM,
        /** GM dismissed — flag retained in log as inactive record. */
        DISMISSED
    }

    private final String flagId;
    private final UUID attackerUuid;
    private final UUID victimUuid;
    private final String worldKey;
    private final long blockPosLong;
    private final long flaggedAtMs;
    /** True if a GM event was active at the attack location at time of flag. */
    private final boolean eventActiveAtTime;
    /** Timestamp of last Security interaction involving the victim — 0 if none in window. */
    private final long lastSecurityInteractionMs;

    private ReviewState reviewState;
    private UUID reviewedByUuid;
    private Long reviewedAtMs;

    public RdmFlag(
            String flagId,
            UUID attackerUuid,
            UUID victimUuid,
            String worldKey,
            long blockPosLong,
            long flaggedAtMs,
            boolean eventActiveAtTime,
            long lastSecurityInteractionMs,
            ReviewState reviewState,
            UUID reviewedByUuid,
            Long reviewedAtMs
    ) {
        this.flagId                     = flagId;
        this.attackerUuid               = attackerUuid;
        this.victimUuid                 = victimUuid;
        this.worldKey                   = worldKey;
        this.blockPosLong               = blockPosLong;
        this.flaggedAtMs                = flaggedAtMs;
        this.eventActiveAtTime          = eventActiveAtTime;
        this.lastSecurityInteractionMs  = lastSecurityInteractionMs;
        this.reviewState                = reviewState;
        this.reviewedByUuid             = reviewedByUuid;
        this.reviewedAtMs               = reviewedAtMs;
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    public void setReviewState(ReviewState state) { this.reviewState = state; }
    public void setReviewedByUuid(UUID uuid)       { this.reviewedByUuid = uuid; }
    public void setReviewedAtMs(Long ts)           { this.reviewedAtMs = ts; }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getFlagId()                       { return flagId; }
    public UUID getAttackerUuid()                   { return attackerUuid; }
    public UUID getVictimUuid()                     { return victimUuid; }
    public String getWorldKey()                     { return worldKey; }
    public long getBlockPosLong()                   { return blockPosLong; }
    public long getFlaggedAtMs()                    { return flaggedAtMs; }
    public boolean isEventActiveAtTime()            { return eventActiveAtTime; }
    public long getLastSecurityInteractionMs()      { return lastSecurityInteractionMs; }
    public ReviewState getReviewState()             { return reviewState; }
    public UUID getReviewedByUuid()                 { return reviewedByUuid; }
    public Long getReviewedAtMs()                   { return reviewedAtMs; }
}