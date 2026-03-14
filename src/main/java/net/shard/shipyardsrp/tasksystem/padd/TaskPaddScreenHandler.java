package net.shard.shipyardsrp.tasksystem.padd;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.shipyardsrp.ShipyardsRP;
import net.shard.shipyardsrp.registry.ModScreenHandlers;
import net.shard.shipyardsrp.starfleetarchives.PlayerProfile;
import net.shard.shipyardsrp.tasksystem.data.ActiveTask;
import net.shard.shipyardsrp.tasksystem.data.CompletedTaskRecord;
import net.shard.shipyardsrp.tasksystem.data.TaskTemplate;
import net.shard.shipyardsrp.tasksystem.registry.TaskRegistry;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TaskPaddScreenHandler extends ScreenHandler {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private final List<String> activeLines;
    private final List<String> completedLines;

    // Client-side constructor used by ExtendedScreenHandlerType
    public TaskPaddScreenHandler(int syncId, PlayerInventory playerInventory, TaskPaddOpeningData data) {
        this(syncId, playerInventory, data.activeLines(), data.completedLines());
    }

    // Server-side constructor used when opening the screen
    public TaskPaddScreenHandler(int syncId, PlayerInventory playerInventory, List<String> activeLines, List<String> completedLines) {
        super(ModScreenHandlers.TASK_PADD_SCREEN, syncId);
        this.activeLines = new ArrayList<>(activeLines);
        this.completedLines = new ArrayList<>(completedLines);
    }

    public List<String> getActiveLines() {
        return Collections.unmodifiableList(activeLines);
    }

    public List<String> getCompletedLines() {
        return Collections.unmodifiableList(completedLines);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    public static TaskPaddOpeningData createOpeningData(ServerPlayerEntity player) {
        return new TaskPaddOpeningData(
                buildActiveLines(player),
                buildCompletedLines(player)
        );
    }

    public static List<String> buildActiveLines(ServerPlayerEntity player) {
        PlayerProfile profile = ShipyardsRP.PROFILE_MANAGER.getOrLoadProfile(
                player.getUuid(),
                player.getName().getString()
        );

        List<String> lines = new ArrayList<>();
        lines.add("Active Tasks");

        if (profile.getActiveTasks().isEmpty()) {
            lines.add("No active tasks.");
            return lines;
        }

        for (ActiveTask activeTask : profile.getActiveTasks()) {
            TaskTemplate template = TaskRegistry.get(activeTask.getTemplateId());

            if (template == null) {
                lines.add(" ");
                lines.add("[Missing Template] " + activeTask.getTemplateId());
                continue;
            }

            lines.add(" ");
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
        }

        return lines;
    }

    public static List<String> buildCompletedLines(ServerPlayerEntity player) {
        PlayerProfile profile = ShipyardsRP.PROFILE_MANAGER.getOrLoadProfile(
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
            TaskTemplate template = TaskRegistry.get(record.getTemplateId());
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
}