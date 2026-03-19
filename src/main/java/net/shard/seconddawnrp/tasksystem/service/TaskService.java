package net.shard.seconddawnrp.tasksystem.service;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;
import net.shard.seconddawnrp.tasksystem.data.ActiveTask;
import net.shard.seconddawnrp.tasksystem.data.CompletedTaskRecord;
import net.shard.seconddawnrp.tasksystem.data.TaskAssignmentSource;
import net.shard.seconddawnrp.tasksystem.data.TaskTemplate;
import net.shard.seconddawnrp.tasksystem.registry.TaskRegistry;
import net.shard.seconddawnrp.tasksystem.repository.TaskStateRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class TaskService {

    private final PlayerProfileManager profileManager;
    private final TaskRewardService rewardService;
    private final TaskStateRepository taskStateRepository;

    private static MinecraftServer server;

    static {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
    }

    public TaskService(
            PlayerProfileManager profileManager,
            TaskRewardService rewardService,
            TaskStateRepository taskStateRepository
    ) {
        this.profileManager = Objects.requireNonNull(profileManager, "profileManager");
        this.rewardService = Objects.requireNonNull(rewardService, "rewardService");
        this.taskStateRepository = Objects.requireNonNull(taskStateRepository, "taskStateRepository");
    }

    public void loadTaskState(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");

        profile.getActiveTasks().clear();
        profile.getActiveTasks().addAll(taskStateRepository.loadActiveTasks(profile.getPlayerId()));

        profile.getCompletedTasks().clear();
        profile.getCompletedTasks().addAll(taskStateRepository.loadCompletedTasks(profile.getPlayerId()));
    }

    public void saveTaskState(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");

        taskStateRepository.saveActiveTasks(profile.getPlayerId(), profile.getActiveTasks());
        taskStateRepository.saveCompletedTasks(profile.getPlayerId(), profile.getCompletedTasks());
    }

    public boolean assignTask(PlayerProfile profile, String taskId, UUID assignedByUuid, TaskAssignmentSource source) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(source, "source");

        TaskTemplate template = TaskRegistry.get(taskId);
        if (template == null) {
            return false;
        }

        if (hasActiveTask(profile, taskId)) {
            return false;
        }

        ActiveTask activeTask = new ActiveTask(taskId, assignedByUuid, source);
        profile.getActiveTasks().add(activeTask);

        saveTaskState(profile);
        profileManager.markDirty(profile.getPlayerId());

        notifyPlayer(profile.getPlayerId(), "New task assigned: " + template.getDisplayName());
        return true;
    }

    public List<ActiveTask> getActiveTasks(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return profile.getActiveTasks();
    }

    public Optional<ActiveTask> findActiveTask(PlayerProfile profile, String taskId) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(taskId, "taskId");

        return profile.getActiveTasks()
                .stream()
                .filter(task -> task.getTemplateId().equals(taskId))
                .findFirst();
    }

    public boolean hasActiveTask(PlayerProfile profile, String taskId) {
        return findActiveTask(profile, taskId).isPresent();
    }

    public boolean incrementProgress(PlayerProfile profile, String taskId, int amount) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(taskId, "taskId");

        if (amount <= 0) {
            return false;
        }

        TaskTemplate template = TaskRegistry.get(taskId);
        if (template == null) {
            return false;
        }

        Optional<ActiveTask> optionalTask = findActiveTask(profile, taskId);
        if (optionalTask.isEmpty()) {
            return false;
        }

        ActiveTask activeTask = optionalTask.get();
        if (activeTask.isComplete()) {
            return false;
        }

        int newProgress = Math.min(
                activeTask.getCurrentProgress() + amount,
                template.getRequiredAmount()
        );

        activeTask.setCurrentProgress(newProgress);

        if (newProgress >= template.getRequiredAmount()) {
            markTaskComplete(profile, activeTask, template);
        }

        saveTaskState(profile);
        profileManager.markDirty(profile.getPlayerId());
        return true;
    }

    public boolean approveTask(PlayerProfile profile, String taskId) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(taskId, "taskId");

        Optional<ActiveTask> optionalTask = findActiveTask(profile, taskId);
        if (optionalTask.isEmpty()) {
            return false;
        }

        ActiveTask activeTask = optionalTask.get();
        if (!activeTask.isAwaitingOfficerApproval() || activeTask.isRewardClaimed()) {
            return false;
        }

        TaskTemplate template = TaskRegistry.get(taskId);
        if (template == null) {
            return false;
        }

        completeAndReward(profile, activeTask, template);

        saveTaskState(profile);
        profileManager.markDirty(profile.getPlayerId());
        return true;
    }

    private void markTaskComplete(PlayerProfile profile, ActiveTask activeTask, TaskTemplate template) {
        activeTask.setComplete(true);

        if (template.isOfficerConfirmationRequired()) {
            activeTask.setAwaitingOfficerApproval(true);
            notifyPlayer(profile.getPlayerId(), "Task ready for approval: " + template.getDisplayName());
        } else {
            completeAndReward(profile, activeTask, template);
        }
    }

    private void completeAndReward(PlayerProfile profile, ActiveTask activeTask, TaskTemplate template) {
        if (activeTask.isRewardClaimed()) {
            return;
        }

        activeTask.setAwaitingOfficerApproval(false);
        activeTask.setRewardClaimed(true);

        rewardService.grantReward(profile, template);

        CompletedTaskRecord completedRecord = new CompletedTaskRecord(
                template.getId(),
                activeTask.getAssignedByUuid(),
                activeTask.getAssignmentSource(),
                System.currentTimeMillis(),
                template.getRewardPoints()
        );

        profile.getCompletedTasks().add(completedRecord);
        profile.getActiveTasks().remove(activeTask);

        notifyPlayer(
                profile.getPlayerId(),
                "Task completed: " + template.getDisplayName() + " | +" + template.getRewardPoints() + " rank points"
        );
    }

    private void notifyPlayer(UUID playerId, String message) {
        if (server == null) return;

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(Text.literal(message), false);
        }
    }

}