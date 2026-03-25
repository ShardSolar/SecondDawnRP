package net.shard.seconddawnrp.character;

import java.util.UUID;

/**
 * A single long-term injury record attached to a player.
 *
 * <p>Injuries persist across sessions via {@code long_term_injuries} SQL table.
 * The expiry timestamp is an absolute wall-clock value — not playtime.
 * {@link LongTermInjuryService} checks expiry on every tick and on login.
 *
 * <p>One player may only have one <em>active</em> injury at a time.
 * Applying a new injury while one is active replaces it with the more
 * severe tier (the service enforces this).
 */
public class LongTermInjury {

    private final String injuryId;       // UUID string — primary key
    private final UUID playerUuid;
    private final LongTermInjuryTier tier;
    private final long appliedAtMs;

    /** Absolute wall-clock expiry. Can be adjusted by /gm injury modify. */
    private long expiresAtMs;

    /** How many treatment sessions a Medical officer has completed. */
    private int sessionsCompleted;

    /**
     * Timestamp of the last effective treatment session.
     * Used to enforce the 24-hour cooldown between sessions.
     */
    private long lastTreatmentMs;

    /** False once expired or manually cleared. */
    private boolean active;

    public LongTermInjury(
            String injuryId,
            UUID playerUuid,
            LongTermInjuryTier tier,
            long appliedAtMs,
            long expiresAtMs,
            int sessionsCompleted,
            long lastTreatmentMs,
            boolean active
    ) {
        this.injuryId          = injuryId;
        this.playerUuid        = playerUuid;
        this.tier              = tier;
        this.appliedAtMs       = appliedAtMs;
        this.expiresAtMs       = expiresAtMs;
        this.sessionsCompleted = sessionsCompleted;
        this.lastTreatmentMs   = lastTreatmentMs;
        this.active            = active;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Create a fresh injury applied right now.
     * expiry = System.currentTimeMillis() + tier.defaultDurationMs
     */
    public static LongTermInjury createNow(UUID playerUuid, LongTermInjuryTier tier) {
        long now = System.currentTimeMillis();
        return new LongTermInjury(
                UUID.randomUUID().toString(),
                playerUuid,
                tier,
                now,
                now + tier.defaultDurationMs,
                0,
                0L,
                true
        );
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    public void setExpiresAtMs(long expiresAtMs)     { this.expiresAtMs = expiresAtMs; }
    public void setSessionsCompleted(int sessions)   { this.sessionsCompleted = sessions; }
    public void setLastTreatmentMs(long ts)          { this.lastTreatmentMs = ts; }
    public void setActive(boolean active)            { this.active = active; }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getInjuryId()             { return injuryId; }
    public UUID getPlayerUuid()             { return playerUuid; }
    public LongTermInjuryTier getTier()     { return tier; }
    public long getAppliedAtMs()            { return appliedAtMs; }
    public long getExpiresAtMs()            { return expiresAtMs; }
    public int getSessionsCompleted()       { return sessionsCompleted; }
    public long getLastTreatmentMs()        { return lastTreatmentMs; }
    public boolean isActive()               { return active; }

    /** True if the injury has passed its expiry timestamp. */
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtMs;
    }

    /**
     * True if enough time has passed since the last effective treatment
     * to allow another session (24-hour cooldown).
     */
    public boolean isTreatmentCooldownOver() {
        long cooldownMs = 24L * 60 * 60 * 1000;
        return System.currentTimeMillis() >= lastTreatmentMs + cooldownMs;
    }
}