package net.shard.seconddawnrp.character;

/**
 * A custom medical condition applied by a GM or Environmental Effect Block.
 * Stored on CharacterProfile and readable by the Tricorder in Phase 8.
 *
 * <p>Conditions are identified by a string ID (e.g. "radiation_sickness",
 * "neural_toxin") defined by the GM or environmental block config.
 * Severity is a freeform label — no enum, GMs define it.
 */
public class MedicalCondition {

    private final String conditionId;
    private final String severity;
    private final long appliedAtMs;
    private final String sourceDescription;

    public MedicalCondition(String conditionId, String severity,
                            long appliedAtMs, String sourceDescription) {
        this.conditionId      = conditionId;
        this.severity         = severity;
        this.appliedAtMs      = appliedAtMs;
        this.sourceDescription = sourceDescription;
    }

    public String getConditionId()       { return conditionId; }
    public String getSeverity()          { return severity; }
    public long getAppliedAtMs()         { return appliedAtMs; }
    public String getSourceDescription() { return sourceDescription; }
}