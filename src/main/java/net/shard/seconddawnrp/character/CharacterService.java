package net.shard.seconddawnrp.character;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 4.5 stub — manages in-memory CharacterProfile cache.
 *
 * <p>In Phase 4.5 profiles are created automatically on join with the
 * player's Minecraft username as the character name. Persistence and
 * the full creation flow are added in Phase 5.
 *
 * <p>The service is intentionally simple here — no repository, no SQL,
 * no JSON. Just an in-memory map that survives the session. Phase 5
 * replaces this with {@code SqlCharacterRepository}.
 */
public class CharacterService {

    private final Map<UUID, CharacterProfile> cache = new HashMap<>();

    /**
     * Get or create a CharacterProfile for the given player.
     * In Phase 4.5, profiles are always ACTIVE with the Minecraft username.
     */
    public CharacterProfile getOrCreate(ServerPlayerEntity player) {
        return cache.computeIfAbsent(player.getUuid(), uuid ->
                new CharacterProfile(uuid, player.getName().getString(),
                        CharacterStatus.ACTIVE));
    }

    public Optional<CharacterProfile> get(UUID playerUuid) {
        return Optional.ofNullable(cache.get(playerUuid));
    }

    public void unload(UUID playerUuid) {
        cache.remove(playerUuid);
    }

    /** Apply a medical condition to a player's character. */
    public boolean applyCondition(UUID playerUuid, MedicalCondition condition) {
        CharacterProfile profile = cache.get(playerUuid);
        if (profile == null) return false;
        profile.addMedicalCondition(condition);
        return true;
    }

    /** Remove a specific condition from a player's character. */
    public boolean removeCondition(UUID playerUuid, String conditionId) {
        CharacterProfile profile = cache.get(playerUuid);
        if (profile == null) return false;
        profile.removeMedicalCondition(conditionId);
        return true;
    }

    /** Clear all medical conditions from a player's character. */
    public boolean clearConditions(UUID playerUuid) {
        CharacterProfile profile = cache.get(playerUuid);
        if (profile == null) return false;
        profile.clearMedicalConditions();
        return true;
    }
}