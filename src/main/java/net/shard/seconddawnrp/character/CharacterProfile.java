package net.shard.seconddawnrp.character;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 5 stub — CharacterProfile is the identity layer for all roleplay.
 *
 * <p>This stub is introduced in Phase 4.5 so Environmental Effect Blocks
 * have a valid target for custom medical conditions. The full implementation
 * (character creation terminal, death framework, species, language lists,
 * progression transfer) is built in Phase 5.
 *
 * <p>One CharacterProfile exists per player account. It is loaded on join
 * and unloaded on disconnect via {@link CharacterService}.
 */
public class CharacterProfile {

    private final UUID playerUuid;
    private String characterName;
    private CharacterStatus status;
    private final List<MedicalCondition> activeMedicalConditions = new ArrayList<>();

    // Phase 5 fields stubbed as null/default — not persisted yet
    private String species        = null;
    private String bio            = null;
    private boolean universalTranslator = false;
    private Long deceasedAt       = null;

    public CharacterProfile(UUID playerUuid, String characterName, CharacterStatus status) {
        this.playerUuid    = playerUuid;
        this.characterName = characterName;
        this.status        = status;
    }

    // ── Medical conditions ────────────────────────────────────────────────────

    public void addMedicalCondition(MedicalCondition condition) {
        // Remove existing entry for the same condition ID before adding new one
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
        return List.copyOf(activeMedicalConditions);
    }

    public boolean hasCondition(String conditionId) {
        return activeMedicalConditions.stream()
                .anyMatch(c -> c.getConditionId().equals(conditionId));
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID getPlayerUuid()       { return playerUuid; }
    public String getCharacterName()  { return characterName; }
    public CharacterStatus getStatus(){ return status; }
    public String getSpecies()        { return species; }
    public String getBio()            { return bio; }
    public boolean hasUniversalTranslator() { return universalTranslator; }
    public Long getDeceasedAt()       { return deceasedAt; }

    public void setCharacterName(String name) { this.characterName = name; }
    public void setStatus(CharacterStatus status) { this.status = status; }
    public void setSpecies(String species) { this.species = species; }
    public void setBio(String bio) { this.bio = bio; }
    public void setUniversalTranslator(boolean v) { this.universalTranslator = v; }
    public void setDeceasedAt(Long ts) { this.deceasedAt = ts; }
}