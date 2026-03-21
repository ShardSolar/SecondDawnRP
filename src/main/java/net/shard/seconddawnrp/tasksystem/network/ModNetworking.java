package net.shard.seconddawnrp.tasksystem.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.divison.Division;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.tasksystem.data.OpsTaskStatus;
import net.shard.seconddawnrp.tasksystem.data.TaskAssignmentSource;
import net.shard.seconddawnrp.tasksystem.data.TaskTemplate;
import net.shard.seconddawnrp.tasksystem.pad.AdminTaskViewModel;
import net.shard.seconddawnrp.tasksystem.network.EditTaskC2SPacket;

import java.util.List;

public class ModNetworking {

    public static void registerC2SPackets() {
        PayloadTypeRegistry.playC2S().register(
                CreateTaskC2SPacket.ID,
                CreateTaskC2SPacket.CODEC
        );

        PayloadTypeRegistry.playC2S().register(
                AssignTaskC2SPacket.ID,
                AssignTaskC2SPacket.CODEC
        );

        PayloadTypeRegistry.playC2S().register(
                ReviewTaskActionC2SPacket.ID,
                ReviewTaskActionC2SPacket.CODEC
        );

        PayloadTypeRegistry.playC2S().register(
                SubmitManualConfirmC2SPacket.ID,
                SubmitManualConfirmC2SPacket.CODEC
        );

        PayloadTypeRegistry.playS2C().register(
                OpsPadRefreshS2CPacket.ID,
                OpsPadRefreshS2CPacket.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
                CreateTaskC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() ->
                        handleCreateTask(context.player(), payload))
        );

        ServerPlayNetworking.registerGlobalReceiver(
                AssignTaskC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() ->
                        handleAssignTask(context.player(), payload))
        );

        ServerPlayNetworking.registerGlobalReceiver(
                ReviewTaskActionC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() ->
                        handleReviewAction(context.player(), payload))
        );

        ServerPlayNetworking.registerGlobalReceiver(
                SubmitManualConfirmC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() ->
                        handleSubmitManualConfirm(context.player(), payload))
        );

        PayloadTypeRegistry.playC2S().register(
                EditTaskC2SPacket.ID,
                EditTaskC2SPacket.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
                EditTaskC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() ->
                        handleEditTask(context.player(), payload))
        );

        PayloadTypeRegistry.playC2S().register(
                AcceptTerminalTaskC2SPacket.ID,
                AcceptTerminalTaskC2SPacket.CODEC
        );

        ServerPlayNetworking.registerGlobalReceiver(
                AcceptTerminalTaskC2SPacket.ID,
                (payload, context) -> context.player().server.execute(() ->
                        handleAcceptTerminalTask(context.player(), payload))
        );
    }

    private static void handleCreateTask(ServerPlayerEntity player, CreateTaskC2SPacket packet) {
        PlayerProfile actorProfile = getActorProfile(player);
        if (actorProfile == null) {
            player.sendMessage(Text.literal("Your profile is not loaded."), false);
            return;
        }

        if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canCreateTasks(player, actorProfile)) {
            player.sendMessage(Text.literal("You do not have permission to create tasks."), false);
            return;
        }

        var createdEntry = SecondDawnRP.TASK_SERVICE.createPoolTask(
                packet.taskId(),
                packet.displayName(),
                packet.description(),
                packet.getDivision(),
                packet.getObjectiveType(),
                packet.targetId(),
                packet.requiredAmount(),
                packet.rewardPoints(),
                packet.officerConfirmationRequired(),
                player.getUuid()
        );

        if (createdEntry != null) {
            player.sendMessage(
                    Text.literal("Task created: " + createdEntry.getDisplayName() + " [" + createdEntry.getTaskId() + "]"),
                    false
            );
            sendOpsPadRefresh(player);
        } else {
            player.sendMessage(Text.literal("Task creation failed. Check all fields."), false);
        }
    }

    private static void handleAssignTask(ServerPlayerEntity player, AssignTaskC2SPacket packet) {
        PlayerProfile actorProfile = getActorProfile(player);
        if (actorProfile == null) {
            player.sendMessage(Text.literal("Your profile is not loaded."), false);
            return;
        }

        String mode = packet.assignModeName();

        boolean success = switch (mode) {
            case "PUBLIC" -> {
                if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canPublishTasks(player, actorProfile)) {
                    player.sendMessage(Text.literal("You do not have permission to publish tasks."), false);
                    yield false;
                }
                yield SecondDawnRP.TASK_SERVICE.publishPoolTask(packet.taskId());
            }

            case "DIVISION" -> {
                if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canAssignTasks(player, actorProfile)) {
                    player.sendMessage(Text.literal("You do not have permission to assign tasks."), false);
                    yield false;
                }

                try {
                    Division division = Division.valueOf(packet.divisionName());
                    yield SecondDawnRP.TASK_SERVICE.assignPoolTaskToDivisionPool(packet.taskId(), division);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(Text.literal("Assignment failed. Invalid division."), false);
                    yield false;
                }
            }

            case "PLAYER" -> {
                if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canAssignTasks(player, actorProfile)) {
                    player.sendMessage(Text.literal("You do not have permission to assign tasks."), false);
                    yield false;
                }

                String name = packet.playerName().trim();
                if (name.isBlank()) {
                    player.sendMessage(Text.literal("Enter a player name."), false);
                    yield false;
                }

                ServerPlayerEntity targetPlayer = player.getServer()
                        .getPlayerManager()
                        .getPlayer(name);

                if (targetPlayer == null) {
                    player.sendMessage(Text.literal("Player not found or not online."), false);
                    yield false;
                }

                PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(targetPlayer.getUuid());
                if (profile == null) {
                    player.sendMessage(Text.literal("Player profile is not loaded."), false);
                    yield false;
                }

                yield SecondDawnRP.TASK_SERVICE.assignPoolTaskToPlayer(
                        packet.taskId(),
                        profile,
                        player.getUuid()
                );
            }

            default -> {
                player.sendMessage(Text.literal("Assignment failed. Invalid mode."), false);
                yield false;
            }
        };

        if (success) {
            player.sendMessage(Text.literal("Task assignment updated."), false);
            sendOpsPadRefresh(player);
        } else {
            player.sendMessage(Text.literal("Assignment failed."), false);
        }
    }

    private static void handleReviewAction(ServerPlayerEntity player, ReviewTaskActionC2SPacket packet) {
        PlayerProfile actorProfile = getActorProfile(player);
        if (actorProfile == null) {
            player.sendMessage(Text.literal("Your profile is not loaded."), false);
            return;
        }

        String taskId = packet.taskId();
        String action = packet.actionName();

        boolean success = false;

        switch (action) {
            case "APPROVE" -> {
                if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canApproveTasks(player, actorProfile)) {
                    player.sendMessage(Text.literal("You do not have permission to approve tasks."), false);
                    return;
                }

                PlayerProfile targetProfile = findAssignedProfile(taskId);
                if (targetProfile == null) {
                    player.sendMessage(Text.literal("No assigned player profile found for this task."), false);
                    return;
                }
                success = SecondDawnRP.TASK_SERVICE.approveTask(targetProfile, taskId);
            }

            case "RETURN" -> {
                if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canReturnTasks(player, actorProfile)) {
                    player.sendMessage(Text.literal("You do not have permission to return tasks."), false);
                    return;
                }
                success = SecondDawnRP.TASK_SERVICE.returnTaskToInProgress(
                        taskId,
                        "Returned by operations review."
                );
            }

            case "FAIL" -> {
                if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canFailTasks(player, actorProfile)) {
                    player.sendMessage(Text.literal("You do not have permission to fail tasks."), false);
                    return;
                }
                success = SecondDawnRP.TASK_SERVICE.failTask(
                        taskId,
                        "Marked failed by operations review."
                );
            }

            case "CANCEL" -> {
                if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canCancelTasks(player, actorProfile)) {
                    player.sendMessage(Text.literal("You do not have permission to cancel tasks."), false);
                    return;
                }
                success = SecondDawnRP.TASK_SERVICE.cancelPoolTask(taskId);
            }

            default -> {
                player.sendMessage(Text.literal("Invalid review action."), false);
                return;
            }
        }

        if (success) {
            player.sendMessage(Text.literal("Task updated: " + action), false);
            sendOpsPadRefresh(player);
        } else {
            player.sendMessage(Text.literal("Task action failed."), false);
        }

    }

    private static void handleSubmitManualConfirm(ServerPlayerEntity player, SubmitManualConfirmC2SPacket packet) {
        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
        if (profile == null) {
            player.sendMessage(Text.literal("Profile not loaded."), false);
            return;
        }

        boolean success = SecondDawnRP.TASK_SERVICE.submitManualConfirmTaskForReview(profile, packet.taskId());

        if (success) {
            player.sendMessage(Text.literal("Manual confirmation task submitted for review."), false);
        } else {
            player.sendMessage(Text.literal("Selected task could not be submitted."), false);
        }
    }

    private static void sendOpsPadRefresh(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, buildOpsPadRefreshPacket());
    }

    private static OpsPadRefreshS2CPacket buildOpsPadRefreshPacket() {
        List<OpsPadRefreshS2CPacket.TaskEntry> entries = SecondDawnRP.TASK_SERVICE.buildAdminTaskViews()
                .stream()
                .map(ModNetworking::toTaskEntry)
                .toList();

        return new OpsPadRefreshS2CPacket(entries);
    }

    private static OpsPadRefreshS2CPacket.TaskEntry toTaskEntry(AdminTaskViewModel view) {
        return new OpsPadRefreshS2CPacket.TaskEntry(
                view.getTaskId(),
                view.getTitle(),
                view.getStatus(),
                view.getAssigneeLabel(),
                view.getDivisionLabel(),
                view.getProgressLabel(),
                view.getDetailLines()
        );
    }

    private static PlayerProfile findAssignedProfile(String taskId) {
        for (var entry : SecondDawnRP.TASK_SERVICE.getPoolEntries()) {
            if (!entry.getTaskId().equals(taskId)) {
                continue;
            }

            if (entry.getAssignedPlayerUuid() == null) {
                return null;
            }

            return SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(entry.getAssignedPlayerUuid());
        }

        return null;
    }

    private static PlayerProfile getActorProfile(ServerPlayerEntity player) {
        return SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
    }

    private static void handleEditTask(ServerPlayerEntity player, EditTaskC2SPacket packet) {
        PlayerProfile actorProfile = getActorProfile(player);
        if (actorProfile == null) {
            player.sendMessage(Text.literal("Your profile is not loaded."), false);
            return;
        }

        if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canEditTasks(player, actorProfile)) {
            player.sendMessage(Text.literal("You do not have permission to edit tasks."), false);
            return;
        }

        Division division;
        try {
            division = packet.getDivision();
        } catch (IllegalArgumentException e) {
            player.sendMessage(Text.literal("Edit failed. Invalid division."), false);
            return;
        }

        boolean success = SecondDawnRP.TASK_SERVICE.editPoolTask(
                packet.taskId(),
                packet.displayName(),
                packet.description(),
                division,
                packet.requiredAmount(),
                packet.rewardPoints(),
                packet.officerConfirmationRequired()
        );

        if (success) {
            player.sendMessage(Text.literal("Task updated: " + packet.displayName()), false);
            sendOpsPadRefresh(player);
        } else {
            player.sendMessage(Text.literal("Task edit failed."), false);
        }
    }

    private static void handleAcceptTerminalTask(ServerPlayerEntity player, AcceptTerminalTaskC2SPacket packet) {
        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
        if (profile == null) {
            player.sendMessage(Text.literal("Profile not loaded."), false);
            return;
        }

        String taskId = packet.taskId();

        // Validate still available
        var poolEntry = SecondDawnRP.TASK_SERVICE.getPoolEntries().stream()
                .filter(e -> e.getTaskId().equals(taskId))
                .findFirst().orElse(null);

        if (poolEntry == null
                || (poolEntry.getStatus() != OpsTaskStatus.PUBLIC
                && poolEntry.getStatus() != OpsTaskStatus.UNASSIGNED)) {
            player.sendMessage(Text.literal("[Terminal] Task is no longer available."), false);
            return;
        }

        if (SecondDawnRP.TASK_SERVICE.hasActiveTask(profile, taskId)) {
            player.sendMessage(Text.literal("[Terminal] You already have this task."), false);
            return;
        }

        boolean assigned = SecondDawnRP.TASK_SERVICE.assignTask(
                profile, taskId, player.getUuid(), TaskAssignmentSource.SELF
        );

        if (!assigned) {
            player.sendMessage(Text.literal("[Terminal] Could not accept task."), false);
            return;
        }

        SecondDawnRP.TASK_SERVICE.linkPoolTaskToPlayer(taskId, profile);

        TaskTemplate template = SecondDawnRP.TASK_SERVICE.resolveTaskTemplate(taskId);
        String name = template != null ? template.getDisplayName() : taskId;
        player.sendMessage(Text.literal("[Terminal] Task accepted: " + name), false);
    }

}