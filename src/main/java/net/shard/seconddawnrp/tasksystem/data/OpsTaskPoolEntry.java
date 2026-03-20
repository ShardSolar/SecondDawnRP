package net.shard.seconddawnrp.tasksystem.data;

import net.shard.seconddawnrp.divison.Division;

import java.util.Objects;
import java.util.UUID;

public class OpsTaskPoolEntry {

    private String taskId;
    private String displayName;
    private String description;
    private Division division;
    private TaskObjectiveType objectiveType;
    private String targetId;
    private int requiredAmount;
    private int rewardPoints;
    private boolean officerConfirmationRequired;

    private UUID createdBy;
    private long createdAtEpochMillis;

    private OpsTaskStatus status;
    private UUID assignedPlayerUuid;
    private UUID assignedByUuid;
    private Division pooledDivision;
    private String reviewNote;

    public OpsTaskPoolEntry() {
    }

    public OpsTaskPoolEntry(
            String taskId,
            String displayName,
            String description,
            Division division,
            TaskObjectiveType objectiveType,
            String targetId,
            int requiredAmount,
            int rewardPoints,
            boolean officerConfirmationRequired,
            UUID createdBy,
            long createdAtEpochMillis,
            OpsTaskStatus status
    ) {
        this.taskId = Objects.requireNonNull(taskId, "taskId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.description = Objects.requireNonNull(description, "description");
        this.division = Objects.requireNonNull(division, "division");
        this.objectiveType = Objects.requireNonNull(objectiveType, "objectiveType");
        this.targetId = targetId == null ? "" : targetId;
        this.requiredAmount = Math.max(1, requiredAmount);
        this.rewardPoints = Math.max(0, rewardPoints);
        this.officerConfirmationRequired = officerConfirmationRequired;
        this.createdBy = createdBy;
        this.createdAtEpochMillis = createdAtEpochMillis;
        this.status = Objects.requireNonNull(status, "status");
    }

    public String getTaskId() { return taskId; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Division getDivision() { return division; }
    public TaskObjectiveType getObjectiveType() { return objectiveType; }
    public String getTargetId() { return targetId; }
    public int getRequiredAmount() { return requiredAmount; }
    public int getRewardPoints() { return rewardPoints; }
    public boolean isOfficerConfirmationRequired() { return officerConfirmationRequired; }
    public UUID getCreatedBy() { return createdBy; }
    public long getCreatedAtEpochMillis() { return createdAtEpochMillis; }

    public OpsTaskStatus getStatus() { return status; }
    public void setStatus(OpsTaskStatus status) { this.status = status; }

    public UUID getAssignedPlayerUuid() { return assignedPlayerUuid; }
    public void setAssignedPlayerUuid(UUID assignedPlayerUuid) { this.assignedPlayerUuid = assignedPlayerUuid; }

    public UUID getAssignedByUuid() { return assignedByUuid; }
    public void setAssignedByUuid(UUID assignedByUuid) { this.assignedByUuid = assignedByUuid; }

    public Division getPooledDivision() { return pooledDivision; }
    public void setPooledDivision(Division pooledDivision) { this.pooledDivision = pooledDivision; }

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
}