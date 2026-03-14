package net.shard.shipyardsrp.tasksystem.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shard.shipyardsrp.starfleetarchives.PlayerProfile;
import net.shard.shipyardsrp.starfleetarchives.PlayerProfileManager;
import net.shard.shipyardsrp.tasksystem.data.ActiveTask;
import net.shard.shipyardsrp.tasksystem.data.CompletedTaskRecord;
import net.shard.shipyardsrp.tasksystem.data.TaskAssignmentSource;
import net.shard.shipyardsrp.tasksystem.data.TaskTemplate;
import net.shard.shipyardsrp.tasksystem.registry.TaskRegistry;
import net.shard.shipyardsrp.tasksystem.service.TaskService;

import java.util.List;
import java.util.Optional;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class TaskCommands {

    private TaskCommands() {
    }

    public static void register(
            CommandDispatcher<ServerCommandSource> dispatcher,
            PlayerProfileManager profileManager,
            TaskService taskService
    ) {
        dispatcher.register(literal("task")

                .then(literal("assign")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(argument("player", EntityArgumentType.player())
                                .then(argument("taskId", StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
                                            String taskId = StringArgumentType.getString(context, "taskId");

                                            PlayerProfile profile = profileManager.getOrLoadProfile(
                                                    targetPlayer.getUuid(),
                                                    targetPlayer.getName().getString()
                                            );

                                            TaskTemplate template = TaskRegistry.get(taskId);
                                            if (template == null) {
                                                context.getSource().sendError(Text.literal("Unknown task id: " + taskId));
                                                return 0;
                                            }

                                            ServerPlayerEntity sourcePlayer = getOptionalPlayer(context.getSource());

                                            boolean assigned = taskService.assignTask(
                                                    profile,
                                                    taskId,
                                                    sourcePlayer != null ? sourcePlayer.getUuid() : null,
                                                    TaskAssignmentSource.ADMIN
                                            );

                                            if (!assigned) {
                                                context.getSource().sendError(Text.literal("Could not assign task. It may already be active."));
                                                return 0;
                                            }

                                            context.getSource().sendMessage(
                                                    Text.literal("Assigned task '" + taskId + "' to " + targetPlayer.getName().getString())
                                            );
                                            return 1;
                                        }))))


                .then(literal("list")
                        .executes(context -> {
                            ServerPlayerEntity self = getRequiredPlayer(context.getSource());
                            if (self == null) {
                                context.getSource().sendError(Text.literal("Only players can use /task list without a target."));
                                return 0;
                            }

                            return sendTaskList(context.getSource(), profileManager, self);
                        })
                        .then(argument("player", EntityArgumentType.player())
                                .executes(context -> {
                                    ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
                                    ServerPlayerEntity sourcePlayer = getOptionalPlayer(context.getSource());

                                    if (!canViewOther(sourcePlayer, targetPlayer, context.getSource())) {
                                        context.getSource().sendError(Text.literal("You do not have permission to view another player's tasks."));
                                        return 0;
                                    }

                                    return sendTaskList(context.getSource(), profileManager, targetPlayer);
                                })))


                .then(literal("progress")
                        .executes(context -> {
                            ServerPlayerEntity self = getRequiredPlayer(context.getSource());
                            if (self == null) {
                                context.getSource().sendError(Text.literal("Only players can use /task progress without a target."));
                                return 0;
                            }

                            return sendTaskProgress(context.getSource(), profileManager, self);
                        })
                        .then(argument("player", EntityArgumentType.player())
                                .executes(context -> {
                                    ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
                                    ServerPlayerEntity sourcePlayer = getOptionalPlayer(context.getSource());

                                    if (!canViewOther(sourcePlayer, targetPlayer, context.getSource())) {
                                        context.getSource().sendError(Text.literal("You do not have permission to view another player's task progress."));
                                        return 0;
                                    }

                                    return sendTaskProgress(context.getSource(), profileManager, targetPlayer);
                                })))


                .then(literal("approve")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(argument("player", EntityArgumentType.player())
                                .then(argument("taskId", StringArgumentType.word())
                                        .executes(context -> {
                                            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
                                            String taskId = StringArgumentType.getString(context, "taskId");

                                            PlayerProfile profile = profileManager.getOrLoadProfile(
                                                    targetPlayer.getUuid(),
                                                    targetPlayer.getName().getString()
                                            );

                                            Optional<ActiveTask> activeTask = taskService.findActiveTask(profile, taskId);
                                            if (activeTask.isEmpty()) {
                                                context.getSource().sendError(Text.literal("That player does not have active task: " + taskId));
                                                return 0;
                                            }

                                            boolean approved = taskService.approveTask(profile, taskId);
                                            if (!approved) {
                                                context.getSource().sendError(Text.literal("Task could not be approved."));
                                                return 0;
                                            }

                                            context.getSource().sendMessage(
                                                    Text.literal("Approved task '" + taskId + "' for " + targetPlayer.getName().getString())
                                            );
                                            return 1;
                                        }))))
                .then(literal("completed")
                        .executes(context -> {
                            ServerPlayerEntity self = getRequiredPlayer(context.getSource());
                            if (self == null) {
                                context.getSource().sendError(Text.literal("Only players can use /task completed without a target."));
                                return 0;
                            }

                            return sendCompletedTasks(context.getSource(), profileManager, self);
                        })
                        .then(argument("player", EntityArgumentType.player())
                                .executes(context -> {
                                    ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
                                    ServerPlayerEntity sourcePlayer = getOptionalPlayer(context.getSource());

                                    if (!canViewOther(sourcePlayer, targetPlayer, context.getSource())) {
                                        context.getSource().sendError(Text.literal("You do not have permission to view another player's completed tasks."));
                                        return 0;
                                    }

                                    return sendCompletedTasks(context.getSource(), profileManager, targetPlayer);
                                })))

                .then(literal("history")
                        .executes(context -> {
                            ServerPlayerEntity self = getRequiredPlayer(context.getSource());
                            if (self == null) {
                                context.getSource().sendError(Text.literal("Only players can use /task history without a target."));
                                return 0;
                            }

                            return sendCompletedTasks(context.getSource(), profileManager, self);
                        })
                        .then(argument("player", EntityArgumentType.player())
                                .executes(context -> {
                                    ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
                                    ServerPlayerEntity sourcePlayer = getOptionalPlayer(context.getSource());

                                    if (!canViewOther(sourcePlayer, targetPlayer, context.getSource())) {
                                        context.getSource().sendError(Text.literal("You do not have permission to view another player's task history."));
                                        return 0;
                                    }

                                    return sendCompletedTasks(context.getSource(), profileManager, targetPlayer);
                                })))

                .then(literal("debug")
                        .requires(source -> source.hasPermissionLevel(2))

                        .then(literal("advance")
                                .then(argument("player", EntityArgumentType.player())
                                        .then(argument("taskId", StringArgumentType.word())
                                                .then(argument("amount", IntegerArgumentType.integer(1))
                                                        .executes(context -> {
                                                            ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "player");
                                                            String taskId = StringArgumentType.getString(context, "taskId");
                                                            int amount = IntegerArgumentType.getInteger(context, "amount");

                                                            PlayerProfile profile = profileManager.getOrLoadProfile(
                                                                    targetPlayer.getUuid(),
                                                                    targetPlayer.getName().getString()
                                                            );

                                                            TaskTemplate template = TaskRegistry.get(taskId);
                                                            if (template == null) {
                                                                context.getSource().sendError(Text.literal("Unknown task id: " + taskId));
                                                                return 0;
                                                            }

                                                            Optional<ActiveTask> activeTask = taskService.findActiveTask(profile, taskId);
                                                            if (activeTask.isEmpty()) {
                                                                context.getSource().sendError(Text.literal("That player does not have active task: " + taskId));
                                                                return 0;
                                                            }

                                                            boolean changed = taskService.incrementProgress(profile, taskId, amount);
                                                            if (!changed) {
                                                                context.getSource().sendError(Text.literal("Could not advance task progress."));
                                                                return 0;
                                                            }

                                                            Optional<ActiveTask> updatedTask = taskService.findActiveTask(profile, taskId);

                                                            if (updatedTask.isPresent()) {
                                                                context.getSource().sendMessage(Text.literal(
                                                                        "Advanced '" + taskId + "' for "
                                                                                + targetPlayer.getName().getString()
                                                                                + " by " + amount
                                                                                + ". Progress: "
                                                                                + updatedTask.get().getCurrentProgress()
                                                                                + "/" + template.getRequiredAmount()
                                                                ));
                                                            } else {
                                                                context.getSource().sendMessage(Text.literal(
                                                                        "Advanced '" + taskId + "' for "
                                                                                + targetPlayer.getName().getString()
                                                                                + " by " + amount
                                                                                + ". Task is now complete."
                                                                ));
                                                            }

                                                            return 1;
                                                        }))))))

        );
    }

    private static int sendTaskList(ServerCommandSource source, PlayerProfileManager profileManager, ServerPlayerEntity targetPlayer) {
        PlayerProfile profile = profileManager.getOrLoadProfile(
                targetPlayer.getUuid(),
                targetPlayer.getName().getString()
        );

        List<ActiveTask> activeTasks = profile.getActiveTasks();
        if (activeTasks.isEmpty()) {
            source.sendMessage(Text.literal(targetPlayer.getName().getString() + " has no active tasks."));
            return 1;
        }

        source.sendMessage(Text.literal("Active tasks for " + targetPlayer.getName().getString() + ":"));

        for (ActiveTask activeTask : activeTasks) {
            TaskTemplate template = TaskRegistry.get(activeTask.getTemplateId());

            if (template == null) {
                source.sendMessage(Text.literal("- " + activeTask.getTemplateId() + " (missing template)"));
                continue;
            }

            source.sendMessage(Text.literal("- " + template.getDisplayName() + " [" + activeTask.getTemplateId() + "]"));
            source.sendMessage(Text.literal("  Objective: " + formatObjectiveType(template)));
            source.sendMessage(Text.literal("  Target: " + formatTarget(template)));
            source.sendMessage(Text.literal("  Progress: " + activeTask.getCurrentProgress() + "/" + template.getRequiredAmount()));

            if (activeTask.isAwaitingOfficerApproval()) {
                source.sendMessage(Text.literal("  Status: Awaiting approval"));
            }
        }

        return 1;
    }

    private static int sendTaskProgress(ServerCommandSource source, PlayerProfileManager profileManager, ServerPlayerEntity targetPlayer) {
        PlayerProfile profile = profileManager.getOrLoadProfile(
                targetPlayer.getUuid(),
                targetPlayer.getName().getString()
        );

        List<ActiveTask> activeTasks = profile.getActiveTasks();
        if (activeTasks.isEmpty()) {
            source.sendMessage(Text.literal(targetPlayer.getName().getString() + " has no active tasks."));
            return 1;
        }

        source.sendMessage(Text.literal("Task progress for " + targetPlayer.getName().getString() + ":"));

        for (ActiveTask activeTask : activeTasks) {
            TaskTemplate template = TaskRegistry.get(activeTask.getTemplateId());
            if (template == null) {
                source.sendMessage(Text.literal("- " + activeTask.getTemplateId() + ": missing template"));
                continue;
            }

            source.sendMessage(Text.literal("- " + template.getDisplayName() + " [" + activeTask.getTemplateId() + "]"));
            source.sendMessage(Text.literal("  Objective: " + formatObjectiveType(template)));
            source.sendMessage(Text.literal("  Target: " + formatTarget(template)));
            source.sendMessage(Text.literal("  Progress: " + activeTask.getCurrentProgress() + "/" + template.getRequiredAmount()));

            if (activeTask.isAwaitingOfficerApproval()) {
                source.sendMessage(Text.literal("  Status: Awaiting approval"));
            } else if (activeTask.isComplete()) {
                source.sendMessage(Text.literal("  Status: Complete"));
            } else {
                source.sendMessage(Text.literal("  Status: In progress"));
            }
        }

        return 1;
    }

    private static boolean canViewOther(ServerPlayerEntity sourcePlayer, ServerPlayerEntity targetPlayer, ServerCommandSource source) {
        if (source.hasPermissionLevel(2)) {
            return true;
        }

        if (sourcePlayer == null) {
            return false;
        }

        return sourcePlayer.getUuid().equals(targetPlayer.getUuid());
    }

    private static ServerPlayerEntity getOptionalPlayer(ServerCommandSource source) {
        try {
            return source.getPlayer();
        } catch (Exception e) {
            return null;
        }
    }

    private static ServerPlayerEntity getRequiredPlayer(ServerCommandSource source) {
        try {
            return source.getPlayer();
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatObjectiveType(TaskTemplate template) {
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

    private static String formatTimestamp(long epochMillis) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(epochMillis);
        java.time.ZonedDateTime dateTime = instant.atZone(java.time.ZoneId.systemDefault());
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(dateTime);
    }

    private static int sendCompletedTasks(ServerCommandSource source, PlayerProfileManager profileManager, ServerPlayerEntity targetPlayer) {

        PlayerProfile profile = profileManager.getOrLoadProfile(
                targetPlayer.getUuid(),
                targetPlayer.getName().getString()
        );

        List<CompletedTaskRecord> completed = profile.getCompletedTasks();

        if (completed.isEmpty()) {
            source.sendMessage(Text.literal(targetPlayer.getName().getString() + " has not completed any tasks yet."));
            return 1;
        }

        source.sendMessage(Text.literal("Completed tasks for " + targetPlayer.getName().getString() + ":"));

        for (CompletedTaskRecord record : completed) {

            TaskTemplate template = TaskRegistry.get(record.getTemplateId());

            String name = template != null ? template.getDisplayName() : record.getTemplateId();
            int reward = record.getRewardPointsGranted();
            String time = formatTimestamp(record.getCompletedAtEpochMillis());

            source.sendMessage(Text.literal("- " + name + " [" + record.getTemplateId() + "]"));
            source.sendMessage(Text.literal("  Reward: " + reward + " RP"));
            source.sendMessage(Text.literal("  Completed: " + time));
        }

        return 1;
    }



}