package net.shard.seconddawnrp.tasksystem.pad;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.registry.ModScreenHandlers;
import net.shard.seconddawnrp.tasksystem.data.ActiveTask;
import net.shard.seconddawnrp.tasksystem.data.CompletedTaskRecord;
import net.shard.seconddawnrp.tasksystem.data.TaskTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TaskPadScreenHandler extends ScreenHandler {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private final List<String> activeLines;
    private final List<String> completedLines;
    private final List<String> activeTaskIds;
    private int selectedActiveTaskIndex;

    public TaskPadScreenHandler(int syncId, PlayerInventory playerInventory, TaskPadOpeningData data) {
        this(syncId, playerInventory, data.activeLines(), data.completedLines(), data.activeTaskIds());
    }

    public TaskPadScreenHandler(
            int syncId,
            PlayerInventory playerInventory,
            List<String> activeLines,
            List<String> completedLines,
            List<String> activeTaskIds
    ) {
        super(ModScreenHandlers.TASK_PAD_SCREEN, syncId);
        this.activeLines = new ArrayList<>(activeLines);
        this.completedLines = new ArrayList<>(completedLines);
        this.activeTaskIds = new ArrayList<>(activeTaskIds);
        this.selectedActiveTaskIndex = this.activeTaskIds.isEmpty() ? -1 : 0;
    }

    public List<String> getActiveLines() {
        return Collections.unmodifiableList(activeLines);
    }

    public List<String> getCompletedLines() {
        return Collections.unmodifiableList(completedLines);
    }

    public List<String> getActiveTaskIds() {
        return Collections.unmodifiableList(activeTaskIds);
    }

    public int getSelectedActiveTaskIndex() {
        return selectedActiveTaskIndex;
    }

    public void setSelectedActiveTaskIndex(int index) {
        if (index >= 0 && index < activeTaskIds.size()) {
            this.selectedActiveTaskIndex = index;
        }
    }

    public String getSelectedActiveTaskId() {
        if (selectedActiveTaskIndex < 0 || selectedActiveTaskIndex >= activeTaskIds.size()) {
            return null;
        }
        return activeTaskIds.get(selectedActiveTaskIndex);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    public static TaskPadOpeningData createOpeningData(ServerPlayerEntity player) {
        ActiveTaskViewData activeData = buildActiveData(player);

        return new TaskPadOpeningData(
                activeData.lines(),
                buildCompletedLines(player),
                activeData.taskIds()
        );
    }

    public static ActiveTaskViewData buildActiveData(ServerPlayerEntity player) {
        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getOrLoadProfile(
                player.getUuid(),
                player.getName().getString()
        );

        List<String> lines = new ArrayList<>();
        List<String> taskIds = new ArrayList<>();

        lines.add("Active Tasks");

        if (profile.getActiveTasks().isEmpty()) {
            lines.add("No active tasks.");
            return new ActiveTaskViewData(lines, taskIds);
        }

        for (ActiveTask activeTask : profile.getActiveTasks()) {
            TaskTemplate template = SecondDawnRP.TASK_SERVICE.resolveTaskTemplate(activeTask.getTemplateId());

            lines.add(" ");

            if (template == null) {
                lines.add("[Missing Template] " + activeTask.getTemplateId());
                taskIds.add(activeTask.getTemplateId());
                continue;
            }

            lines.add(template.getDisplayName());
            lines.add("[" + template.getId() + "]");
            lines.add("Objective: " + formatObjective(template));
            lines.add("Target: " + formatTarget(template));
            lines.add("Progress: " + activeTask.getCurrentProgress() + "/" + template.getRequiredAmount());

            if (activeTask.isAwaitingOfficerApproval()) {
                lines.add("Status: Awaiting approval");
            } else if (activeTask.isComplete()) {
                lines.add("Status: Complete");
            } else {
                lines.add("Status: In progress");
            }

            lines.add("Reward: " + template.getRewardPoints() + " RP");
            taskIds.add(activeTask.getTemplateId());
        }

        return new ActiveTaskViewData(lines, taskIds);
    }

    public static List<String> buildCompletedLines(ServerPlayerEntity player) {
        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getOrLoadProfile(
                player.getUuid(),
                player.getName().getString()
        );

        List<String> lines = new ArrayList<>();
        lines.add("Completed Tasks");

        if (profile.getCompletedTasks().isEmpty()) {
            lines.add("No completed tasks yet.");
            return lines;
        }

        for (CompletedTaskRecord record : profile.getCompletedTasks()) {
            TaskTemplate template = SecondDawnRP.TASK_SERVICE.resolveTaskTemplate(record.getTemplateId());
            String displayName = template != null ? template.getDisplayName() : record.getTemplateId();

            lines.add(" ");
            lines.add(displayName);
            lines.add("[" + record.getTemplateId() + "]");
            lines.add("Reward: " + record.getRewardPointsGranted() + " RP");
            lines.add("Completed: " + DATE_FORMAT.format(Instant.ofEpochMilli(record.getCompletedAtEpochMillis())));
        }

        return lines;
    }

    private static String formatObjective(TaskTemplate template) {
        return switch (template.getObjectiveType()) {
            case BREAK_BLOCK -> "Break Block";
            case COLLECT_ITEM -> "Collect Item";
            case VISIT_LOCATION -> "Visit Location";
            case MANUAL_CONFIRM -> "Manual Confirmation";
        };
    }

    private static String formatTarget(TaskTemplate template) {
        String targetId = template.getTargetId();

        if (targetId == null || targetId.isBlank()) {
            return "None";
        }

        return switch (template.getObjectiveType()) {
            case BREAK_BLOCK -> "Break " + targetId;
            case COLLECT_ITEM -> "Collect " + targetId;
            case VISIT_LOCATION -> "Visit " + targetId;
            case MANUAL_CONFIRM -> "Officer approval required";
        };
    }

    public record ActiveTaskViewData(List<String> lines, List<String> taskIds) {
    }
}