package net.shard.seconddawnrp.tasksystem.data;

import java.util.Objects;

public class TaskProgressSnapshot {

    private final String templateId;
    private final int currentProgress;
    private final int requiredAmount;
    private final boolean complete;

    public TaskProgressSnapshot(String templateId, int currentProgress, int requiredAmount, boolean complete) {
        this.templateId = Objects.requireNonNull(templateId, "templateId");
        this.currentProgress = Math.max(0, currentProgress);
        this.requiredAmount = Math.max(1, requiredAmount);
        this.complete = complete;
    }

    public String getTemplateId() {
        return templateId;
    }

    public int getCurrentProgress() {
        return currentProgress;
    }

    public int getRequiredAmount() {
        return requiredAmount;
    }

    public boolean isComplete() {
        return complete;
    }
}