package net.shard.seconddawnrp.tasksystem.data;

import java.util.Objects;
import java.util.UUID;

public class CompletedTaskRecord {

    private final String templateId;
    private final UUID assignedByUuid;
    private final TaskAssignmentSource assignmentSource;
    private final long completedAtEpochMillis;
    private final int rewardPointsGranted;

    public CompletedTaskRecord(
            String templateId,
            UUID assignedByUuid,
            TaskAssignmentSource assignmentSource,
            long completedAtEpochMillis,
            int rewardPointsGranted
    ) {
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.assignedByUuid = assignedByUuid;
        this.assignmentSource = Objects.requireNonNull(assignmentSource, "assignmentSource");
        this.completedAtEpochMillis = completedAtEpochMillis;
        this.rewardPointsGranted = Math.max(0, rewardPointsGranted);
    }

    public String getTemplateId() {
        return templateId;
    }

    public UUID getAssignedByUuid() {
        return assignedByUuid;
    }

    public TaskAssignmentSource getAssignmentSource() {
        return assignmentSource;
    }

    public long getCompletedAtEpochMillis() {
        return completedAtEpochMillis;
    }

    public int getRewardPointsGranted() {
        return rewardPointsGranted;
    }
}