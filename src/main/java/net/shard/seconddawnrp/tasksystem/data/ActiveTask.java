package net.shard.seconddawnrp.tasksystem.data;

import java.util.Objects;
import java.util.UUID;

public class ActiveTask {

    private final String templateId;
    private final UUID assignedByUuid;
    private final TaskAssignmentSource assignmentSource;

    private int currentProgress;
    private boolean complete;
    private boolean awaitingOfficerApproval;
    private boolean rewardClaimed;

    public ActiveTask(String templateId, UUID assignedByUuid, TaskAssignmentSource assignmentSource) {
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.assignedByUuid = assignedByUuid;
        this.assignmentSource = Objects.requireNonNull(assignmentSource, "assignmentSource");
        this.currentProgress = 0;
        this.complete = false;
        this.awaitingOfficerApproval = false;
        this.rewardClaimed = false;
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

    public int getCurrentProgress() {
        return currentProgress;
    }

    public void setCurrentProgress(int currentProgress) {
        this.currentProgress = Math.max(0, currentProgress);
    }

    public void addProgress(int amount) {
        if (amount > 0) {
            this.currentProgress += amount;
        }
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public boolean isAwaitingOfficerApproval() {
        return awaitingOfficerApproval;
    }

    public void setAwaitingOfficerApproval(boolean awaitingOfficerApproval) {
        this.awaitingOfficerApproval = awaitingOfficerApproval;
    }

    public boolean isRewardClaimed() {
        return rewardClaimed;
    }

    public void setRewardClaimed(boolean rewardClaimed) {
        this.rewardClaimed = rewardClaimed;
    }
}