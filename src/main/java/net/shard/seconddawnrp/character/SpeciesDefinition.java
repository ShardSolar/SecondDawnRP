package net.shard.seconddawnrp.character;

import java.util.List;

/**
 * A single species entry from the species JSON registry.
 *
 * <p>Defined in {@code data/seconddawnrp/species/<id>.json}.
 * Admin-extensible — no code changes needed to add new species.
 *
 * <p>Example JSON:
 * <pre>
 * {
 *   "id": "human",
 *   "displayName": "Human",
 *   "description": "Adaptable and resilient.",
 *   "startingLanguages": ["galactic_standard"]
 * }
 * </pre>
 */
public class SpeciesDefinition {

    private final String id;
    private final String displayName;
    private final String description;
    private final List<String> startingLanguages;

    public SpeciesDefinition(String id, String displayName,
                             String description, List<String> startingLanguages) {
        this.id               = id;
        this.displayName      = displayName;
        this.description      = description;
        this.startingLanguages = startingLanguages != null ? List.copyOf(startingLanguages) : List.of();
    }

    public String getId()                    { return id; }
    public String getDisplayName()           { return displayName; }
    public String getDescription()           { return description; }
    public List<String> getStartingLanguages() { return startingLanguages; }
}