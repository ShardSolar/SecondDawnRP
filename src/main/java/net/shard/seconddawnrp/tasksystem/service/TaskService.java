package net.shard.seconddawnrp.tasksystem.service;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.divison.Division;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;
import net.shard.seconddawnrp.tasksystem.data.ActiveTask;
import net.shard.seconddawnrp.tasksystem.data.CompletedTaskRecord;
import net.shard.seconddawnrp.tasksystem.data.OpsTaskPoolEntry;
import net.shard.seconddawnrp.tasksystem.data.OpsTaskStatus;
import net.shard.seconddawnrp.tasksystem.data.TaskAssignmentSource;
import net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType;
import net.shard.seconddawnrp.tasksystem.data.TaskTemplate;
import net.shard.seconddawnrp.tasksystem.pad.AdminTaskViewModel;
import net.shard.seconddawnrp.tasksystem.registry.TaskRegistry;
import net.shard.seconddawnrp.tasksystem.repository.OpsTaskPoolRepository;
import net.shard.seconddawnrp.tasksystem.repository.TaskStateRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class TaskService {

    private final PlayerProfileManager profileManager;
    private final TaskRewardService rewardService;
    private final TaskStateRepository taskStateRepository;
    private final OpsTaskPoolRepository opsTaskPoolRepository;

    private final List<OpsTaskPoolEntry> poolEntries = new ArrayList<>();

    private static MinecraftServer server;

    static {
        ServerLifecycleEvents.SERVER_STARTED.register(startedServer -> server = startedServer);
    }

    public TaskService(
            PlayerProfileManager profileManager,
            TaskRewardService rewardService,
            TaskStateRepository taskStateRepository,
            OpsTaskPoolRepository opsTaskPoolRepository
    ) {
        this.profileManager = Objects.requireNonNull(profileManager, "profileManager");
        this.rewardService = Objects.requireNonNull(rewardService, "rewardService");
        this.taskStateRepository = Objects.requireNonNull(taskStateRepository, "taskStateRepository");
        this.opsTaskPoolRepository = Objects.requireNonNull(opsTaskPoolRepository, "opsTaskPoolRepository");

        this.poolEntries.addAll(this.opsTaskPoolRepository.loadAll());
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

    private void savePoolState() {
        opsTaskPoolRepository.saveAll(poolEntries);
    }

    public List<OpsTaskPoolEntry> getPoolEntries() {
        return List.copyOf(poolEntries);
    }

    public boolean assignTask(PlayerProfile profile, String taskId, UUID assignedByUuid, TaskAssignmentSource source) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(source, "source");

        TaskTemplate template = resolveTaskTemplate(taskId);
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

        TaskTemplate template = resolveTaskTemplate(taskId);
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
        syncPoolStatusFromProfiles();
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

        TaskTemplate template = resolveTaskTemplate(taskId);
        if (template == null) {
            return false;
        }

        completeAndReward(profile, activeTask, template);

        saveTaskState(profile);
        profileManager.markDirty(profile.getPlayerId());
        syncPoolStatusFromProfiles();
        return true;
    }

    public boolean submitManualConfirmTaskForReview(PlayerProfile profile, String taskId) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(taskId, "taskId");

        Optional<ActiveTask> optionalTask = findActiveTask(profile, taskId);
        if (optionalTask.isEmpty()) {
            return false;
        }

        ActiveTask activeTask = optionalTask.get();
        TaskTemplate template = resolveTaskTemplate(taskId);
        if (template == null) {
            return false;
        }

        if (template.getObjectiveType() != TaskObjectiveType.MANUAL_CONFIRM) {
            return false;
        }

        if (activeTask.isAwaitingOfficerApproval() || activeTask.isRewardClaimed() || activeTask.isComplete()) {
            return false;
        }

        activeTask.setCurrentProgress(template.getRequiredAmount());
        markTaskComplete(profile, activeTask, template);

        saveTaskState(profile);
        profileManager.markDirty(profile.getPlayerId());
        syncPoolStatusFromProfiles();
        return true;
    }

    public OpsTaskPoolEntry createPoolTask(
            String taskId,
            String displayName,
            String description,
            Division division,
            TaskObjectiveType objectiveType,
            String targetId,
            int requiredAmount,
            int rewardPoints,
            boolean officerConfirmationRequired,
            UUID createdBy
    ) {
        if (displayName == null || displayName.isBlank()) return null;
        if (description == null || description.isBlank()) return null;
        if (division == null) return null;
        if (objectiveType == null) return null;
        if (targetId == null || targetId.isBlank()) return null;
        if (requiredAmount <= 0) return null;
        if (rewardPoints < 0) return null;

        String finalTaskId = makeUniqueTaskId(taskId);

        OpsTaskPoolEntry entry = new OpsTaskPoolEntry(
                finalTaskId,
                displayName.trim(),
                description.trim(),
                division,
                objectiveType,
                targetId.trim(),
                requiredAmount,
                rewardPoints,
                officerConfirmationRequired,
                createdBy,
                System.currentTimeMillis(),
                OpsTaskStatus.UNASSIGNED
        );

        poolEntries.add(entry);
        savePoolState();
        return entry;
    }

    public boolean publishPoolTask(String taskId) {
        OpsTaskPoolEntry entry = findPoolEntry(taskId).orElse(null);
        if (entry == null) {
            return false;
        }

        entry.setAssignedPlayerUuid(null);
        entry.setAssignedByUuid(null);
        entry.setPooledDivision(null);
        entry.setStatus(OpsTaskStatus.PUBLIC);
        savePoolState();
        return true;
    }

    public boolean assignPoolTaskToDivisionPool(String taskId, Division division) {
        OpsTaskPoolEntry entry = findPoolEntry(taskId).orElse(null);
        if (entry == null || division == null) {
            return false;
        }

        entry.setAssignedPlayerUuid(null);
        entry.setAssignedByUuid(null);
        entry.setPooledDivision(division);
        entry.setStatus(OpsTaskStatus.UNASSIGNED);
        savePoolState();
        return true;
    }

    public boolean assignPoolTaskToPlayer(String taskId, PlayerProfile profile, UUID assignedByUuid) {
        Objects.requireNonNull(profile, "profile");

        OpsTaskPoolEntry entry = findPoolEntry(taskId).orElse(null);
        if (entry == null) {
            return false;
        }

        boolean assigned = assignTask(profile, taskId, assignedByUuid, TaskAssignmentSource.OFFICER);
        if (!assigned) {
            return false;
        }

        entry.setAssignedPlayerUuid(profile.getPlayerId());
        entry.setAssignedByUuid(assignedByUuid);
        entry.setPooledDivision(null);
        entry.setStatus(OpsTaskStatus.ASSIGNED);
        savePoolState();
        return true;
    }



    public boolean cancelPoolTask(String taskId) {
        OpsTaskPoolEntry entry = findPoolEntry(taskId).orElse(null);
        if (entry == null) {
            return false;
        }

        PlayerProfile assignedProfile = getAssignedLoadedProfile(entry);

        if (assignedProfile != null) {
            Optional<ActiveTask> activeTask = findActiveTask(assignedProfile, taskId);
            if (activeTask.isPresent()) {
                assignedProfile.getActiveTasks().remove(activeTask.get());
                saveTaskState(assignedProfile);
                profileManager.markDirty(assignedProfile.getPlayerId());
            }

            notifyPlayer(assignedProfile.getPlayerId(), "Task canceled: " + entry.getDisplayName());
        }

        entry.setReviewNote("Canceled by operations.");
        entry.setStatus(OpsTaskStatus.CANCELED);
        savePoolState();
        syncPoolStatusFromProfiles();
        return true;
    }

    public boolean returnTaskToInProgress(String taskId, String denialNote) {
        OpsTaskPoolEntry entry = findPoolEntry(taskId).orElse(null);
        if (entry == null) {
            return false;
        }

        PlayerProfile assignedProfile = getAssignedLoadedProfile(entry);
        if (assignedProfile == null) {
            return false;
        }

        Optional<ActiveTask> optionalTask = findActiveTask(assignedProfile, taskId);
        if (optionalTask.isEmpty()) {
            return false;
        }

        ActiveTask activeTask = optionalTask.get();
        activeTask.setAwaitingOfficerApproval(false);
        activeTask.setComplete(false);

        entry.setReviewNote(denialNote);
        entry.setStatus(OpsTaskStatus.IN_PROGRESS);

        saveTaskState(assignedProfile);
        profileManager.markDirty(assignedProfile.getPlayerId());
        savePoolState();
        syncPoolStatusFromProfiles();

        notifyPlayer(
                assignedProfile.getPlayerId(),
                "Task returned for more work: " + entry.getDisplayName()
        );

        return true;
    }

    public boolean failTask(String taskId, String failureNote) {
        OpsTaskPoolEntry entry = findPoolEntry(taskId).orElse(null);
        if (entry == null) {
            return false;
        }

        PlayerProfile assignedProfile = getAssignedLoadedProfile(entry);

        if (assignedProfile != null) {
            Optional<ActiveTask> activeTask = findActiveTask(assignedProfile, taskId);
            if (activeTask.isPresent()) {
                assignedProfile.getActiveTasks().remove(activeTask.get());
                saveTaskState(assignedProfile);
                profileManager.markDirty(assignedProfile.getPlayerId());
            }

            notifyPlayer(
                    assignedProfile.getPlayerId(),
                    "Task failed: " + entry.getDisplayName()
            );
        }

        entry.setReviewNote(failureNote);
        entry.setStatus(OpsTaskStatus.FAILED);
        savePoolState();
        syncPoolStatusFromProfiles();
        return true;
    }

    public List<AdminTaskViewModel> buildAdminTaskViews() {
        syncPoolStatusFromProfiles();

        List<AdminTaskViewModel> views = new ArrayList<>();

        for (OpsTaskPoolEntry entry : poolEntries.stream()
                .filter(entry -> entry.getStatus() != OpsTaskStatus.ARCHIVED)
                .filter(entry -> entry.getStatus() != OpsTaskStatus.CANCELED)
                .filter(entry -> entry.getStatus() != OpsTaskStatus.COMPLETED)
                .sorted(Comparator.comparingLong(OpsTaskPoolEntry::getCreatedAtEpochMillis).reversed())
                .toList()) {

            String assigneeLabel = "Unassigned";
            if (entry.getAssignedPlayerUuid() != null) {
                assigneeLabel = entry.getAssignedPlayerUuid().toString();
            } else if (entry.getStatus() == OpsTaskStatus.PUBLIC) {
                assigneeLabel = "Public Pool";
            } else if (entry.getPooledDivision() != null) {
                assigneeLabel = entry.getPooledDivision().name();
            }

            String progressLabel = buildProgressLabel(entry);

            views.add(new AdminTaskViewModel(
                    entry.getTaskId(),
                    entry.getDisplayName(),
                    formatStatus(entry.getStatus()),
                    assigneeLabel,
                    entry.getDivision().name(),
                    progressLabel,
                    List.of(
                            "Task ID: " + entry.getTaskId(),
                            "Description: " + entry.getDescription(),
                            "Objective: " + formatObjective(entry.getObjectiveType()),
                            "Target: " + formatTarget(entry.getObjectiveType(), entry.getTargetId()),
                            "Division: " + entry.getDivision().name(),
                            "Reward: " + entry.getRewardPoints() + " rank points",
                            "Status: " + formatStatus(entry.getStatus()),
                            entry.getReviewNote() == null || entry.getReviewNote().isBlank()
                                    ? "Review Note: None"
                                    : "Review Note: " + entry.getReviewNote()
                    )
            ));
        }

        return views;
    }

    private void syncPoolStatusFromProfiles() {
        boolean changed = false;

        for (OpsTaskPoolEntry entry : poolEntries) {
            if (entry.getStatus() == OpsTaskStatus.ARCHIVED) {
                continue;
            }

            if (entry.getAssignedPlayerUuid() == null) {
                continue;
            }

            PlayerProfile profile = profileManager.getLoadedProfile(entry.getAssignedPlayerUuid());
            if (profile == null) {
                continue;
            }

            Optional<ActiveTask> activeTask = findActiveTask(profile, entry.getTaskId());
            if (activeTask.isPresent()) {
                ActiveTask task = activeTask.get();
                OpsTaskStatus newStatus;

                if (task.isAwaitingOfficerApproval()) {
                    newStatus = OpsTaskStatus.AWAITING_REVIEW;
                } else if (task.getCurrentProgress() > 0) {
                    newStatus = OpsTaskStatus.IN_PROGRESS;
                } else {
                    newStatus = OpsTaskStatus.ASSIGNED;
                }

                if (entry.getStatus() != newStatus) {
                    entry.setStatus(newStatus);
                    changed = true;
                }
                continue;
            }

            boolean completed = profile.getCompletedTasks().stream()
                    .anyMatch(record -> record.getTemplateId().equals(entry.getTaskId()));

            if (completed && entry.getStatus() != OpsTaskStatus.COMPLETED) {
                entry.setStatus(OpsTaskStatus.COMPLETED);
                changed = true;
            }
        }

        if (changed) {
            savePoolState();
        }
    }

    private String buildProgressLabel(OpsTaskPoolEntry entry) {
        if (entry.getAssignedPlayerUuid() == null) {
            return entry.getRequiredAmount() + " required";
        }

        PlayerProfile profile = profileManager.getLoadedProfile(entry.getAssignedPlayerUuid());
        if (profile == null) {
            return "0 / " + entry.getRequiredAmount();
        }

        Optional<ActiveTask> activeTask = findActiveTask(profile, entry.getTaskId());
        if (activeTask.isPresent()) {
            return activeTask.get().getCurrentProgress() + " / " + entry.getRequiredAmount();
        }

        boolean completed = profile.getCompletedTasks().stream()
                .anyMatch(record -> record.getTemplateId().equals(entry.getTaskId()));

        if (completed) {
            return entry.getRequiredAmount() + " / " + entry.getRequiredAmount();
        }

        return "0 / " + entry.getRequiredAmount();
    }

    private Optional<OpsTaskPoolEntry> findPoolEntry(String taskId) {
        return poolEntries.stream()
                .filter(entry -> entry.getTaskId().equals(taskId))
                .findFirst();
    }

    public TaskTemplate resolveTaskTemplate(String taskId) {
        TaskTemplate registered = TaskRegistry.get(taskId);
        if (registered != null) {
            return registered;
        }

        OpsTaskPoolEntry poolEntry = findPoolEntry(taskId).orElse(null);
        if (poolEntry == null) {
            return null;
        }

        return new TaskTemplate(
                poolEntry.getTaskId(),
                poolEntry.getDisplayName(),
                poolEntry.getDescription(),
                poolEntry.getDivision(),
                poolEntry.getObjectiveType(),
                poolEntry.getTargetId(),
                poolEntry.getRequiredAmount(),
                poolEntry.getRewardPoints(),
                poolEntry.isOfficerConfirmationRequired()
        );
    }

    private String formatStatus(OpsTaskStatus status) {
        return switch (status) {
            case UNASSIGNED -> "UNASSIGNED";
            case PUBLIC -> "PUBLIC";
            case ASSIGNED -> "ASSIGNED";
            case IN_PROGRESS -> "IN PROGRESS";
            case AWAITING_REVIEW -> "AWAITING REVIEW";
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
            case CANCELED -> "CANCELED";
            case ARCHIVED -> "ARCHIVED";
        };
    }

    private String formatObjective(TaskObjectiveType objectiveType) {
        return switch (objectiveType) {
            case BREAK_BLOCK -> "Break Block";
            case COLLECT_ITEM -> "Collect Item";
            case VISIT_LOCATION -> "Visit Location";
            case MANUAL_CONFIRM -> "Manual Confirmation";
        };
    }

    private String formatTarget(TaskObjectiveType objectiveType, String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return "None";
        }

        return switch (objectiveType) {
            case BREAK_BLOCK -> "Break " + targetId;
            case COLLECT_ITEM -> "Collect " + targetId;
            case VISIT_LOCATION -> "Visit " + targetId;
            case MANUAL_CONFIRM -> targetId;
        };
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
        if (server == null) {
            return;
        }

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(Text.literal(message), false);
        }
    }

    private PlayerProfile getAssignedLoadedProfile(OpsTaskPoolEntry entry) {
        if (entry == null || entry.getAssignedPlayerUuid() == null) {
            return null;
        }

        return profileManager.getLoadedProfile(entry.getAssignedPlayerUuid());
    }

    public boolean editPoolTask(
            String taskId,
            String displayName,
            String description,
            Division division,
            int requiredAmount,
            int rewardPoints,
            boolean officerConfirmationRequired
    ) {
        if (taskId == null || taskId.isBlank()) return false;
        if (displayName == null || displayName.isBlank()) return false;
        if (description == null || description.isBlank()) return false;
        if (division == null) return false;
        if (requiredAmount <= 0) return false;
        if (rewardPoints < 0) return false;

        OpsTaskPoolEntry entry = findPoolEntry(taskId).orElse(null);
        if (entry == null) {
            return false;
        }

        entry.setDisplayName(displayName.trim());
        entry.setDescription(description.trim());
        entry.setDivision(division);
        entry.setRequiredAmount(requiredAmount);
        entry.setRewardPoints(rewardPoints);
        entry.setOfficerConfirmationRequired(officerConfirmationRequired);

        savePoolState();
        syncPoolStatusFromProfiles();
        return true;
    }

    private String sanitizeTaskId(String raw) {
        if (raw == null) {
            return "";
        }

        return raw.trim()
                .toLowerCase()
                .replace(' ', '_')
                .replaceAll("[^a-z0-9_-]", "");
    }

    private String makeUniqueTaskId(String baseTaskId) {
        String sanitized = sanitizeTaskId(baseTaskId);
        if (sanitized.isBlank()) {
            sanitized = "task";
        }

        String candidate = sanitized;
        int counter = 2;

        while (findPoolEntryIgnoreCase(candidate).isPresent() || TaskRegistry.get(candidate) != null) {
            candidate = sanitized + "_" + counter;
            counter++;
        }

        return candidate;
    }

    private Optional<OpsTaskPoolEntry> findPoolEntryIgnoreCase(String taskId) {
        return poolEntries.stream()
                .filter(entry -> entry.getTaskId().equalsIgnoreCase(taskId))
                .findFirst();
    }

    public boolean linkPoolTaskToPlayer(String taskId, PlayerProfile profile) {
        OpsTaskPoolEntry entry = findPoolEntry(taskId).orElse(null);
        if (entry == null) return false;

        // Only update if the task is actually active for this player
        if (!hasActiveTask(profile, taskId)) return false;

        entry.setAssignedPlayerUuid(profile.getPlayerId());
        entry.setAssignedByUuid(profile.getPlayerId());
        entry.setPooledDivision(null);
        entry.setStatus(OpsTaskStatus.ASSIGNED);
        savePoolState();
        return true;
    }

}