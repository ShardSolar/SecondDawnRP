package net.shard.seconddawnrp.character;

/**
 * Severity tiers for long-term injuries.
 *
 * <p>Duration is real-world calendar time (absolute expiry timestamp).
 * Sessions required and reduction percentage per session drive the
 * Medical treatment loop. Effects are applied on login and refreshed
 * every 5 minutes via {@link LongTermInjuryService}.
 *
 * <p>From the scope:
 * <pre>
 *  Minor    — 3 days  — 1 session  — 100% reduction — Weakness I
 *  Moderate — 1 week  — 3 sessions — 33%  reduction — Weakness I + occasional Slowness
 *  Severe   — 2 weeks — 5 sessions — 20%  reduction — Weakness II + Slowness + periodic Nausea
 * </pre>
 */
public enum LongTermInjuryTier {

    MINOR(
            3 * 24 * 60 * 60 * 1000L,  // 3 days in ms
            1,
            1.00f                        // 100% cleared per session
    ),
    MODERATE(
            7 * 24 * 60 * 60 * 1000L,  // 1 week in ms
            3,
            0.33f
    ),
    SEVERE(
            14 * 24 * 60 * 60 * 1000L, // 2 weeks in ms
            5,
            0.20f
    );

    /** Default real-world duration in milliseconds. */
    public final long defaultDurationMs;

    /** How many completed treatment sessions are needed to fully clear the injury. */
    public final int sessionsToFullClear;

    /**
     * Fraction of remaining duration reduced per Medical treatment session.
     * Specialist cert multiplies this value.
     */
    public final float reductionPerSession;

    LongTermInjuryTier(long defaultDurationMs, int sessionsToFullClear, float reductionPerSession) {
        this.defaultDurationMs   = defaultDurationMs;
        this.sessionsToFullClear = sessionsToFullClear;
        this.reductionPerSession = reductionPerSession;
    }
}