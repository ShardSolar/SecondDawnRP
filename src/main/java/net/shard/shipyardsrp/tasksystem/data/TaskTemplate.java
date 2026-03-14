package net.shard.shipyardsrp.tasksystem.data;

import net.shard.shipyardsrp.divison.Division;

import java.util.Objects;

public class TaskTemplate {

    private final String id;
    private final String displayName;
    private final String description;
    private final Division division;
    private final TaskObjectiveType objectiveType;
    private final String targetId;
    private final int requiredAmount;
    private final int rewardPoints;
    private final boolean officerConfirmationRequired;

    public TaskTemplate(
            String id,
            String displayName,
            String description,
            Division division,
            TaskObjectiveType objectiveType,
            String targetId,
            int requiredAmount,
            int rewardPoints,
            boolean officerConfirmationRequired
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.description = Objects.requireNonNull(description, "description");
        this.division = Objects.requireNonNull(division, "division");
        this.objectiveType = Objects.requireNonNull(objectiveType, "objectiveType");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        this.requiredAmount = Math.max(1, requiredAmount);
        this.rewardPoints = Math.max(0, rewardPoints);
        this.officerConfirmationRequired = officerConfirmationRequired;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Division getDivision() {
        return division;
    }

    public TaskObjectiveType getObjectiveType() {
        return objectiveType;
    }

    public String getTargetId() {
        return targetId;
    }

    public int getRequiredAmount() {
        return requiredAmount;
    }

    public int getRewardPoints() {
        return rewardPoints;
    }

    public boolean isOfficerConfirmationRequired() {
        return officerConfirmationRequired;
    }
}