package net.shard.seconddawnrp.character;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages long-term injuries across the server lifetime.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Apply the correct status effects on player login and refresh them
 *       every 5 minutes via the server tick (6000 ticks).</li>
 *   <li>Check expiry on login and every tick cycle.</li>
 *   <li>Provide the Medical treatment API consumed by Phase 8 tools.</li>
 *   <li>Provide the GM override API ({@code /gm injury modify}).</li>
 * </ul>
 *
 * <p>The in-memory cache maps player UUID → active injury. It is warm
 * as long as the player is online. Offline players' injuries are loaded
 * on join via {@link CharacterService#onPlayerJoin}.
 */
public class LongTermInjuryService {

    /** How often to reapply effects — every 6000 ticks = 5 minutes. */
    private static final int REFRESH_INTERVAL_TICKS = 6000;

    /** Duration of each applied effect in ticks. Must exceed refresh interval. */
    private static final int EFFECT_DURATION_TICKS  = 7200; // 6 minutes

    // ── Specialist cert multiplier (placeholder — cert system Phase 10.5) ──
    /** Reduction multiplier applied when the treating Medical officer holds
     *  the {@code medical.specialist} certification. */
    public static final float SPECIALIST_BONUS_MULTIPLIER = 1.5f;

    private final LongTermInjuryRepository repository;
    private final CharacterRepository characterRepository;

    /** player UUID → active injury (online players only) */
    private final Map<UUID, LongTermInjury> cache = new ConcurrentHashMap<>();

    private MinecraftServer server;

    public LongTermInjuryService(LongTermInjuryRepository repository,
                                 CharacterRepository characterRepository) {
        this.repository          = repository;
        this.characterRepository = characterRepository;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    // ── Server startup ────────────────────────────────────────────────────────

    /**
     * Called on SERVER_STARTED. Loads all active injuries into the cache
     * so the tick handler can reapply effects without a DB lookup per tick.
     */
    public void reload() {
        cache.clear();
        for (LongTermInjury injury : repository.loadAllActive()) {
            if (injury.isActive() && !injury.isExpired()) {
                cache.put(injury.getPlayerUuid(), injury);
            } else if (injury.isActive() && injury.isExpired()) {
                // Expired while server was offline — deactivate now
                expire(injury);
            }
        }
    }

    // ── Player join ───────────────────────────────────────────────────────────

    /**
     * Called when a player joins. Loads their injury (if any), checks
     * expiry, caches it, and immediately applies the status effects.
     */
    public void onPlayerJoin(ServerPlayerEntity player) {
        repository.loadActive(player.getUuid()).ifPresent(injury -> {
            if (injury.isExpired()) {
                expire(injury);
                return;
            }
            cache.put(player.getUuid(), injury);
            applyEffects(player, injury);
        });
    }

    /** Remove from cache on disconnect — injury record stays in DB. */
    public void onPlayerLeave(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    /**
     * Called on END_SERVER_TICK. Refreshes effects every 5 minutes and
     * expires injuries whose timestamps have passed.
     */
    public void tick(MinecraftServer server, int currentTick) {
        if (currentTick % REFRESH_INTERVAL_TICKS != 0) return;

        Iterator<Map.Entry<UUID, LongTermInjury>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, LongTermInjury> entry = it.next();
            UUID uuid   = entry.getKey();
            LongTermInjury injury = entry.getValue();

            if (injury.isExpired()) {
                expire(injury);
                it.remove();
                // Notify player if online
                notifyPlayerExpired(server, uuid);
                continue;
            }

            // Reapply effects for online players
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                applyEffects(player, injury);
            }
        }
    }

    // ── Application ───────────────────────────────────────────────────────────

    /**
     * Apply a new injury to a player.
     *
     * <p>If the player already has an active injury, the more severe tier
     * is kept (existing SEVERE is never downgraded to MINOR by a new hit).
     * The expiry resets to the new injury's full duration.
     *
     * @return the applied injury
     */
    public LongTermInjury applyInjury(UUID playerUuid, LongTermInjuryTier tier) {
        LongTermInjury existing = cache.get(playerUuid);

        // Keep the worse tier
        LongTermInjuryTier effectiveTier = tier;
        if (existing != null && existing.isActive()) {
            if (existing.getTier().ordinal() > tier.ordinal()) {
                effectiveTier = existing.getTier();
            }
            // Deactivate old record
            existing.setActive(false);
            repository.save(existing);
        }

        LongTermInjury injury = LongTermInjury.createNow(playerUuid, effectiveTier);
        cache.put(playerUuid, injury);
        repository.save(injury);

        // Update character profile FK
        characterRepository.loadActive(playerUuid).ifPresent(cp -> {
            cp.setActiveLongTermInjuryId(injury.getInjuryId());
            characterRepository.save(cp);
        });

        // Apply effects immediately if online
        if (server != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
            if (player != null) {
                applyEffects(player, injury);
                player.sendMessage(Text.literal(
                                "[Injury] You have sustained a " + effectiveTier.name().toLowerCase()
                                        + " long-term injury. Seek Medical attention.")
                        .formatted(Formatting.RED), false);
            }
        }

        return injury;
    }

    /**
     * Called by a Medical officer's treatment tool.
     *
     * <p>Reduces the remaining duration by the tier's reductionPerSession
     * (optionally boosted by the medical.specialist cert).
     * Enforces the 24-hour treatment cooldown.
     *
     * @param specialist true if the treating officer holds medical.specialist
     * @return a result describing the outcome
     */
    public TreatmentResult treat(UUID patientUuid, boolean specialist) {
        LongTermInjury injury = cache.get(patientUuid);
        if (injury == null) {
            return TreatmentResult.NO_INJURY;
        }
        if (!injury.isTreatmentCooldownOver()) {
            long remainMs = (injury.getLastTreatmentMs() + 24L * 3600_000)
                    - System.currentTimeMillis();
            return TreatmentResult.onCooldown(remainMs);
        }

        float reduction = injury.getTier().reductionPerSession;
        if (specialist) reduction *= SPECIALIST_BONUS_MULTIPLIER;

        long remaining = injury.getExpiresAtMs() - System.currentTimeMillis();
        long reduce    = (long) (remaining * reduction);
        long newExpiry = Math.max(0, injury.getExpiresAtMs() - reduce);

        injury.setExpiresAtMs(newExpiry);
        injury.setSessionsCompleted(injury.getSessionsCompleted() + 1);
        injury.setLastTreatmentMs(System.currentTimeMillis());

        if (newExpiry <= System.currentTimeMillis()) {
            // Fully cleared
            expire(injury);
            cache.remove(patientUuid);
            repository.save(injury);
            clearCharacterInjuryRef(patientUuid);
            return TreatmentResult.CLEARED;
        }

        repository.save(injury);
        return TreatmentResult.REDUCED;
    }

    /**
     * GM command: adjust expiry by a number of days (positive = extend,
     * negative = reduce). Audited by the caller.
     */
    public void adjustExpiry(UUID playerUuid, int days) {
        LongTermInjury injury = cache.get(playerUuid);
        if (injury == null) {
            // Try loading from DB — offline player
            injury = repository.loadActive(playerUuid).orElse(null);
            if (injury == null) return;
        }
        long adjustment = (long) days * 24 * 3600_000;
        long newExpiry  = Math.max(System.currentTimeMillis() + 1000,
                injury.getExpiresAtMs() + adjustment);
        injury.setExpiresAtMs(newExpiry);
        repository.save(injury);

        if (newExpiry <= System.currentTimeMillis()) {
            expire(injury);
            cache.remove(playerUuid);
        }
    }

    /** Returns the active injury for a player if one exists in the cache. */
    public Optional<LongTermInjury> getActive(UUID playerUuid) {
        return Optional.ofNullable(cache.get(playerUuid));
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void applyEffects(ServerPlayerEntity player, LongTermInjury injury) {
        switch (injury.getTier()) {
            case MINOR ->
                    player.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.WEAKNESS, EFFECT_DURATION_TICKS, 0, false, false, true));

            case MODERATE -> {
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.WEAKNESS, EFFECT_DURATION_TICKS, 0, false, false, true));
                // Occasional Slowness — apply at 33% probability each refresh
                if (Math.random() < 0.33) {
                    player.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.SLOWNESS, EFFECT_DURATION_TICKS, 0, false, false, true));
                }
            }
            case SEVERE -> {
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.WEAKNESS, EFFECT_DURATION_TICKS, 1, false, false, true));
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SLOWNESS, EFFECT_DURATION_TICKS, 0, false, false, true));
                // Periodic Nausea — apply at 20% probability each refresh
                if (Math.random() < 0.20) {
                    player.addStatusEffect(new StatusEffectInstance(
                            StatusEffects.NAUSEA, EFFECT_DURATION_TICKS, 0, false, false, true));
                }
            }
        }
    }

    private void expire(LongTermInjury injury) {
        injury.setActive(false);
        repository.save(injury);
        clearCharacterInjuryRef(injury.getPlayerUuid());
    }

    private void clearCharacterInjuryRef(UUID playerUuid) {
        characterRepository.loadActive(playerUuid).ifPresent(cp -> {
            cp.setActiveLongTermInjuryId(null);
            characterRepository.save(cp);
        });
    }

    private void notifyPlayerExpired(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
        if (p != null) {
            p.sendMessage(Text.literal(
                            "[Medical] Your long-term injury has fully healed.")
                    .formatted(Formatting.GREEN), false);
        }
    }

    // ── Treatment result ──────────────────────────────────────────────────────

    public static final class TreatmentResult {
        public enum Type { NO_INJURY, ON_COOLDOWN, REDUCED, CLEARED }

        public static final TreatmentResult NO_INJURY = new TreatmentResult(Type.NO_INJURY, 0);
        public static final TreatmentResult REDUCED   = new TreatmentResult(Type.REDUCED,   0);
        public static final TreatmentResult CLEARED   = new TreatmentResult(Type.CLEARED,   0);

        public static TreatmentResult onCooldown(long remainingMs) {
            return new TreatmentResult(Type.ON_COOLDOWN, remainingMs);
        }

        public final Type type;
        /** Remaining cooldown in ms — only meaningful when type == ON_COOLDOWN. */
        public final long remainingCooldownMs;

        private TreatmentResult(Type type, long remainingCooldownMs) {
            this.type = type;
            this.remainingCooldownMs = remainingCooldownMs;
        }
    }
}