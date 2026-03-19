package net.shard.seconddawnrp.tasksystem.pad;

import java.util.List;

public class AdminTaskViewModel {

    private final String taskId;
    private final String title;
    private final String status;
    private final String assigneeLabel;
    private final String divisionLabel;
    private final String progressLabel;
    private final List<String> detailLines;

    public AdminTaskViewModel(
            String taskId,
            String title,
            String status,
            String assigneeLabel,
            String divisionLabel,
            String progressLabel,
            List<String> detailLines
    ) {
        this.taskId = taskId;
        this.title = title;
        this.status = status;
        this.assigneeLabel = assigneeLabel;
        this.divisionLabel = divisionLabel;
        this.progressLabel = progressLabel;
        this.detailLines = detailLines;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }

    public String getAssigneeLabel() {
        return assigneeLabel;
    }

    public String getDivisionLabel() {
        return divisionLabel;
    }

    public String getProgressLabel() {
        return progressLabel;
    }

    public List<String> getDetailLines() {
        return detailLines;
    }
}