package net.shard.seconddawnrp.character;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the full lifecycle of {@link CharacterProfile} records.
 *
 * <p>Replaces the Phase 4.5 in-memory stub. Characters are now persisted
 * via {@link SqlCharacterRepository}. A blank placeholder profile is
 * created on first join for players who have not yet visited the
 * Character Creation Terminal.
 *
 * <p>Singletons wired in {@code SecondDawnRP.java}:
 * <ul>
 *   <li>{@code CHARACTER_SERVICE} — this class</li>
 *   <li>{@code LONG_TERM_INJURY_SERVICE} — {@link LongTermInjuryService}</li>
 *   <li>{@code RDM_DETECTION_SERVICE} — {@link RdmDetectionService}</li>
 * </ul>
 */
public class CharacterService {

    /** In-memory cache for currently online players. */
    private final Map<UUID, CharacterProfile> cache = new HashMap<>();

    private final CharacterRepository repository;

    public CharacterService(CharacterRepository repository) {
        this.repository = repository;
    }

    // ── Player join / leave ───────────────────────────────────────────────────

    /**
     * Called on player join. Loads the active character from the database,
     * or creates a blank placeholder if none exists yet.
     *
     * @return the loaded or newly created CharacterProfile
     */
    public CharacterProfile onPlayerJoin(ServerPlayerEntity player) {
        CharacterProfile profile = repository.loadActive(player.getUuid())
                .orElseGet(() -> {
                    CharacterProfile blank = CharacterProfile.createBlank(
                            player.getUuid(),
                            player.getName().getString()
                    );
                    repository.save(blank);
                    return blank;
                });

        cache.put(player.getUuid(), profile);

        // Remind players who haven't completed creation
        if (!profile.isCreationComplete()) {
            player.sendMessage(
                    Text.literal("[Character] Welcome! Visit the Character Creation Terminal "
                                    + "to set your name, species, and biography before playing.")
                            .formatted(Formatting.GOLD),
                    false
            );
        }

        return profile;
    }

    /**
     * Saves the profile and removes it from the cache on disconnect.
     */
    public void onPlayerLeave(UUID playerUuid) {
        CharacterProfile profile = cache.remove(playerUuid);
        if (profile != null) {
            repository.save(profile);
        }
    }

    // ── Legacy stub compatibility (called from SecondDawnRP.java) ─────────────

    /**
     * Compatibility shim used by the existing join/disconnect wiring in
     * {@code SecondDawnRP.java} until it is updated to call
     * {@link #onPlayerJoin} and {@link #onPlayerLeave} directly.
     */
    public CharacterProfile getOrCreate(ServerPlayerEntity player) {
        return cache.computeIfAbsent(player.getUuid(), uuid -> {
            return repository.loadActive(uuid).orElseGet(() -> {
                CharacterProfile blank = CharacterProfile.createBlank(
                        uuid, player.getName().getString());
                repository.save(blank);
                return blank;
            });
        });
    }

    public void unload(UUID playerUuid) {
        CharacterProfile profile = cache.remove(playerUuid);
        if (profile != null) {
            repository.save(profile);
        }
    }

    // ── Lookups ───────────────────────────────────────────────────────────────

    /**
     * Returns the cached CharacterProfile for an online player.
     * Use this for all hot-path lookups (chat, effects, etc.).
     */
    public Optional<CharacterProfile> get(UUID playerUuid) {
        return Optional.ofNullable(cache.get(playerUuid));
    }

    /**
     * Load a specific character by its characterId (not player UUID).
     * Used for historical records and the deceased roster view.
     * Always goes to the database — not cached.
     */
    public Optional<CharacterProfile> loadHistorical(String characterId) {
        return repository.loadById(characterId);
    }

    // ── Character creation terminal flow ──────────────────────────────────────

    /**
     * Complete the character creation flow from the terminal GUI.
     * Updates name, species, and bio; seeds knownLanguages from species defaults.
     *
     * <p>Species language seeding is a stub here — when the full Language
     * Registry is built in Phase 7, the LanguageService will provide the
     * starting language list for each species ID.
     *
     * @param playerUuid     Minecraft account UUID
     * @param characterName  chosen in-character name
     * @param speciesId      species registry key
     * @param bio            freeform biography
     * @return true if successful, false if no active profile found
     */
    public boolean completeCreation(UUID playerUuid, String characterName,
                                    String speciesId, String bio) {
        CharacterProfile profile = cache.get(playerUuid);
        if (profile == null) return false;
        if (profile.getStatus() == CharacterStatus.DECEASED) return false;

        profile.setCharacterName(characterName);
        profile.setSpecies(speciesId);
        profile.setBio(bio);
        // Phase 7: LanguageService.getStartingLanguages(speciesId) → profile.addLanguage(...)
        repository.save(profile);
        return true;
    }

    // ── Character death ───────────────────────────────────────────────────────

    /**
     * Execute character death. Called from {@link GmCharacterCommands}.
     *
     * <p>Requirements (enforced by the command, not here):
     * <ul>
     *   <li>Second confirmation prompt shown to GM before calling.</li>
     *   <li>Player consent obtained unless {@code permadeathConsent} is true.</li>
     * </ul>
     *
     * <p>Effects:
     * <ul>
     *   <li>Status set to DECEASED, deceasedAt timestamped.</li>
     *   <li>Profile saved (preserved permanently as historical record).</li>
     *   <li>Player's division set to UNASSIGNED in their PlayerProfile
     *       (caller's responsibility — CharacterService has no PlayerProfile ref).</li>
     *   <li>A new blank placeholder is created immediately so the player
     *       can start the creation terminal flow for their next character.</li>
     * </ul>
     *
     * @param playerUuid       target player
     * @param transferPercent  0.0–1.0 fraction of rank points to carry forward
     * @return the newly created blank replacement CharacterProfile
     */
    public CharacterProfile executeCharacterDeath(UUID playerUuid, float transferPercent,
                                                  int currentRankPoints,
                                                  ServerPlayerEntity playerIfOnline) {
        CharacterProfile deceased = cache.get(playerUuid);
        if (deceased == null) {
            deceased = repository.loadActive(playerUuid).orElse(null);
        }

        if (deceased != null) {
            deceased.setStatus(CharacterStatus.DECEASED);
            deceased.setDeceasedAt(System.currentTimeMillis());
            repository.save(deceased);
            cache.remove(playerUuid);
        }

        int transferred = (int) (currentRankPoints * Math.max(0f, Math.min(1f, transferPercent)));

        // Create the new blank character immediately
        CharacterProfile next = new CharacterProfile(
                UUID.randomUUID().toString(),
                playerUuid,
                playerIfOnline != null ? playerIfOnline.getName().getString() : "Unnamed",
                null,
                null,
                CharacterStatus.ACTIVE,
                java.util.List.of(),
                false,
                false,
                null,
                null,
                transferred,
                System.currentTimeMillis()
        );
        repository.save(next);
        cache.put(playerUuid, next);

        if (playerIfOnline != null) {
            playerIfOnline.sendMessage(
                    Text.literal("[Character] Your character has died. Visit the Character "
                                    + "Creation Terminal to begin a new character. "
                                    + transferred + " points have been carried forward.")
                            .formatted(Formatting.DARK_RED),
                    false
            );
        }

        return next;
    }

    // ── Medical condition delegation (backward-compat with Phase 4.5 callers) ─

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

    // ── Language mutations ────────────────────────────────────────────────────

    /**
     * Grant a language to the active character.
     * Called by CertificationService when a language cert is granted.
     */
    public boolean grantLanguage(UUID playerUuid, String languageId) {
        CharacterProfile profile = cache.get(playerUuid);
        if (profile == null) {
            profile = repository.loadActive(playerUuid).orElse(null);
            if (profile == null) return false;
        }
        profile.addLanguage(languageId);
        repository.save(profile);
        return true;
    }

    /**
     * Revoke a language from the active character.
     * Called by CertificationService when a language cert is revoked.
     */
    public boolean revokeLanguage(UUID playerUuid, String languageId) {
        CharacterProfile profile = cache.get(playerUuid);
        if (profile == null) {
            profile = repository.loadActive(playerUuid).orElse(null);
            if (profile == null) return false;
        }
        profile.removeLanguage(languageId);
        repository.save(profile);
        return true;
    }

    // ── Persistence on server stop ────────────────────────────────────────────

    /** Save all cached (online) character profiles. Called on SERVER_STOPPING. */
    public void saveAll() {
        for (CharacterProfile profile : cache.values()) {
            try {
                repository.save(profile);
            } catch (Exception e) {
                System.err.println("[SecondDawnRP] Failed to save character "
                        + profile.getCharacterId() + ": " + e.getMessage());
            }
        }
    }
}