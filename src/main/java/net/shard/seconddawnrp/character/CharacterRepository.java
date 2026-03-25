package net.shard.seconddawnrp.character;

import java.util.Optional;
import java.util.UUID;

/**
 * Storage contract for {@link CharacterProfile}.
 *
 * <p>Implementations must preserve DECEASED profiles permanently —
 * they are never deleted, only superseded by a new ACTIVE record
 * for the same player UUID.
 */
public interface CharacterRepository {

    /**
     * Load the most recent ACTIVE (or SPECTATOR) CharacterProfile for
     * the given Minecraft account UUID.
     *
     * <p>Returns empty if no active character exists for this player —
     * this is the signal to create a new blank placeholder.
     */
    Optional<CharacterProfile> loadActive(UUID playerUuid);

    /**
     * Persist or update a CharacterProfile.
     * Both new profiles (INSERT) and existing ones (UPDATE) are handled.
     */
    void save(CharacterProfile profile);

    /**
     * Load a specific character by its own characterId (not the player UUID).
     * Used for historical lookups and the deceased records view.
     */
    Optional<CharacterProfile> loadById(String characterId);
}