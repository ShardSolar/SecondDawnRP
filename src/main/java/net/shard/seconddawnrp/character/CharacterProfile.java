package net.shard.seconddawnrp.character;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The identity layer for all roleplay on Second Dawn RP.
 *
 * <p>Players are not their Minecraft accounts — they play characters.
 * One CharacterProfile exists per active character. On character death
 * the profile is locked to DECEASED and a new one can be created.
 * Deceased profiles are preserved permanently in the database as
 * historical records.
 *
 * <p>Loaded on player join, unloaded on disconnect.
 * Persisted via {@link SqlCharacterRepository}.
 *
 * <p>Expanded from the Phase 4.5 stub in Phase 5.5:
 * <ul>
 *   <li>characterId — own UUID (not the player's Minecraft UUID)</li>
 *   <li>species, bio — from character creation terminal</li>
 *   <li>knownLanguages — seeded by species on creation, extended by certs</li>
 *   <li>permadeathConsent — opt-in death without per-case GM confirmation</li>
 *   <li>activeLongTermInjuryId — nullable FK into long_term_injuries</li>
 *   <li>progressionTransfer — points carried in from a deceased predecessor</li>
 *   <li>createdAt — timestamp, used for historical records</li>
 * </ul>
 */
public class CharacterProfile {

    // ── Identity ──────────────────────────────────────────────────────────────

    /** This character's own UUID — distinct from the Minecraft account UUID. */
    private final String characterId;

    /** The Minecraft account this character belongs to. */
    private final UUID playerUuid;

    /** In-character name shown in chat, Roster GUI, /rp output. */
    private String characterName;

    /**
     * Species registry key — locked after creation.
     * Null until the player completes the Character Creation Terminal.
     */
    private String species;

    /** Freeform biography — editable at any time. */
    private String bio;

    /** Current lifecycle state of this character. */
    private CharacterStatus status;

    // ── Language ──────────────────────────────────────────────────────────────

    /**
     * Language IDs this character understands.
     * Seeded from species defaults on creation.
     * Extended/removed by {@link net.shard.seconddawnrp.character.CharacterService}.
     */
    private final List<String> knownLanguages;

    /**
     * If true, all language scrambling (spoken and written) is bypassed.
     * Does NOT bypass Wordle for encrypted comms.
     * Granted via the Communications Officer certification.
     */
    private boolean universalTranslator;

    // ── Death & progression ───────────────────────────────────────────────────

    /**
     * If true, character death can be executed by a GM without per-case
     * player confirmation. Player opted into this at creation or via command.
     */
    private boolean permadeathConsent;

    /**
     * ID of the player's current active {@link LongTermInjury}, or null.
     * The LTI itself is stored in the long_term_injuries table.
     */
    private String activeLongTermInjuryId;

    /** Wall-clock timestamp of character death. Null if ACTIVE or SPECTATOR. */
    private Long deceasedAt;

    /**
     * Rank points carried forward from a previous deceased character,
     * after the GM-configured transfer percentage was applied.
     */
    private int progressionTransfer;

    /** Wall-clock timestamp when this character was first created. */
    private final long createdAt;

    // ── Medical conditions ────────────────────────────────────────────────────

    /**
     * Custom medical conditions applied by GMs or Environmental Effect Blocks.
     * These are session-only in Phase 5.5 — persistence is added in Phase 8
     * when the full Medical system is built.
     */
    private final List<MedicalCondition> activeMedicalConditions;

    // ── Constructor ───────────────────────────────────────────────────────────

    public CharacterProfile(
            String characterId,
            UUID playerUuid,
            String characterName,
            String species,
            String bio,
            CharacterStatus status,
            List<String> knownLanguages,
            boolean universalTranslator,
            boolean permadeathConsent,
            String activeLongTermInjuryId,
            Long deceasedAt,
            int progressionTransfer,
            long createdAt
    ) {
        this.characterId             = characterId;
        this.playerUuid              = playerUuid;
        this.characterName           = characterName;
        this.species                 = species;
        this.bio                     = bio;
        this.status                  = status;
        this.knownLanguages          = new ArrayList<>(knownLanguages != null ? knownLanguages : List.of());
        this.universalTranslator     = universalTranslator;
        this.permadeathConsent       = permadeathConsent;
        this.activeLongTermInjuryId  = activeLongTermInjuryId;
        this.deceasedAt              = deceasedAt;
        this.progressionTransfer     = progressionTransfer;
        this.createdAt               = createdAt;
        this.activeMedicalConditions = new ArrayList<>();
    }

    // ── Factory: new character with minimal info ──────────────────────────────

    /**
     * Create a fresh ACTIVE character with no species or bio yet.
     * Used when a player first joins and we create a placeholder pending
     * the Character Creation Terminal flow.
     */
    public static CharacterProfile createBlank(UUID playerUuid, String characterName) {
        return new CharacterProfile(
                UUID.randomUUID().toString(),
                playerUuid,
                characterName,
                null,
                null,
                CharacterStatus.ACTIVE,
                new ArrayList<>(),
                false,
                false,
                null,
                null,
                0,
                System.currentTimeMillis()
        );
    }

    // ── Medical conditions ────────────────────────────────────────────────────

    public void addMedicalCondition(MedicalCondition condition) {
        activeMedicalConditions.removeIf(
                c -> c.getConditionId().equals(condition.getConditionId()));
        activeMedicalConditions.add(condition);
    }

    public void removeMedicalCondition(String conditionId) {
        activeMedicalConditions.removeIf(c -> c.getConditionId().equals(conditionId));
    }

    public void clearMedicalConditions() {
        activeMedicalConditions.clear();
    }

    public List<MedicalCondition> getActiveMedicalConditions() {
        return Collections.unmodifiableList(activeMedicalConditions);
    }

    public boolean hasCondition(String conditionId) {
        return activeMedicalConditions.stream()
                .anyMatch(c -> c.getConditionId().equals(conditionId));
    }

    // ── Language helpers ──────────────────────────────────────────────────────

    public boolean knowsLanguage(String languageId) {
        return universalTranslator || knownLanguages.contains(languageId);
    }

    public void addLanguage(String languageId) {
        if (!knownLanguages.contains(languageId)) {
            knownLanguages.add(languageId);
        }
    }

    public void removeLanguage(String languageId) {
        knownLanguages.remove(languageId);
    }

    public List<String> getKnownLanguages() {
        return Collections.unmodifiableList(knownLanguages);
    }

    // ── Creation-complete check ───────────────────────────────────────────────

    /**
     * True if the player has completed the Character Creation Terminal
     * (species is set). A blank placeholder profile returns false.
     */
    public boolean isCreationComplete() {
        return species != null && !species.isBlank();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getCharacterId()              { return characterId; }
    public UUID getPlayerUuid()                 { return playerUuid; }
    public String getCharacterName()            { return characterName; }
    public String getSpecies()                  { return species; }
    public String getBio()                      { return bio; }
    public CharacterStatus getStatus()          { return status; }
    public boolean hasUniversalTranslator()     { return universalTranslator; }
    public boolean isPermadeathConsent()        { return permadeathConsent; }
    public String getActiveLongTermInjuryId()   { return activeLongTermInjuryId; }
    public Long getDeceasedAt()                 { return deceasedAt; }
    public int getProgressionTransfer()         { return progressionTransfer; }
    public long getCreatedAt()                  { return createdAt; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setCharacterName(String name)               { this.characterName = name; }
    public void setSpecies(String species)                  { this.species = species; }
    public void setBio(String bio)                          { this.bio = bio; }
    public void setStatus(CharacterStatus status)           { this.status = status; }
    public void setUniversalTranslator(boolean v)           { this.universalTranslator = v; }
    public void setPermadeathConsent(boolean v)             { this.permadeathConsent = v; }
    public void setActiveLongTermInjuryId(String id)        { this.activeLongTermInjuryId = id; }
    public void setDeceasedAt(Long ts)                      { this.deceasedAt = ts; }
    public void setProgressionTransfer(int pts)             { this.progressionTransfer = pts; }
}