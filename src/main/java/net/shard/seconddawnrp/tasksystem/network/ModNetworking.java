package net.shard.seconddawnrp.tasksystem.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.divison.Division;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.tasksystem.pad.AdminTaskScreenHandler;

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
    }

    private static void handleCreateTask(ServerPlayerEntity player, CreateTaskC2SPacket packet) {
        boolean created = SecondDawnRP.TASK_SERVICE.createPoolTask(
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

        if (created) {
            player.sendMessage(Text.literal("Task created: " + packet.displayName()), false);
            ServerPlayNetworking.send(player, new OpsPadRefreshS2CPacket());
        } else {
            player.sendMessage(Text.literal("Task creation failed. Check all fields or duplicate task id."), false);
        }
    }

    private static void handleAssignTask(ServerPlayerEntity player, AssignTaskC2SPacket packet) {
        AdminTaskScreenHandler.AssignMode mode;
        try {
            mode = AdminTaskScreenHandler.AssignMode.valueOf(packet.assignModeName());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Text.literal("Assignment failed. Invalid mode."), false);
            return;
        }

        boolean success = switch (mode) {
            case PUBLIC -> SecondDawnRP.TASK_SERVICE.publishPoolTask(packet.taskId());

            case DIVISION -> {
                try {
                    Division division = Division.valueOf(packet.divisionName());
                    yield SecondDawnRP.TASK_SERVICE.assignPoolTaskToDivisionPool(packet.taskId(), division);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(Text.literal("Assignment failed. Invalid division."), false);
                    yield false;
                }
            }

            case PLAYER -> {
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
        };

        if (success) {
            player.sendMessage(Text.literal("Task assignment updated."), false);
            ServerPlayNetworking.send(player, new OpsPadRefreshS2CPacket());
        } else {
            player.sendMessage(Text.literal("Assignment failed."), false);
        }
    }

    private static void handleReviewAction(ServerPlayerEntity player, ReviewTaskActionC2SPacket packet) {
        String taskId = packet.taskId();
        String action = packet.actionName();

        boolean success = false;

        switch (action) {
            case "APPROVE" -> {
                PlayerProfile targetProfile = findAssignedProfile(taskId);
                if (targetProfile == null) {
                    player.sendMessage(Text.literal("No assigned player profile found for this task."), false);
                    return;
                }
                success = SecondDawnRP.TASK_SERVICE.approveTask(targetProfile, taskId);
            }

            case "RETURN" -> success = SecondDawnRP.TASK_SERVICE.returnTaskToInProgress(taskId, "Returned by operations review.");

            case "FAIL" -> success = SecondDawnRP.TASK_SERVICE.failTask(taskId, "Marked failed by operations review.");

            case "CANCEL" -> success = SecondDawnRP.TASK_SERVICE.cancelPoolTask(taskId);

            default -> {
                player.sendMessage(Text.literal("Invalid review action."), false);
                return;
            }
        }

        if (success) {
            player.sendMessage(Text.literal("Task updated: " + action), false);
            ServerPlayNetworking.send(player, new OpsPadRefreshS2CPacket());
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
}