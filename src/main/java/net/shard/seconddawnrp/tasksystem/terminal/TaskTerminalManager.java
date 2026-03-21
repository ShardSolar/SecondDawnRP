package net.shard.seconddawnrp.tasksystem.terminal;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.seconddawnrp.divison.Division;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;
import net.shard.seconddawnrp.tasksystem.data.OpsTaskPoolEntry;
import net.shard.seconddawnrp.tasksystem.data.OpsTaskStatus;
import net.shard.seconddawnrp.tasksystem.service.TaskService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class TaskTerminalManager {

    private final TaskTerminalRepository repository;
    private final TaskService taskService;
    private final PlayerProfileManager profileManager;
    private final List<TaskTerminalEntry> entries = new ArrayList<>();

    public TaskTerminalManager(
            TaskTerminalRepository repository,
            TaskService taskService,
            PlayerProfileManager profileManager
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.taskService = Objects.requireNonNull(taskService, "taskService");
        this.profileManager = Objects.requireNonNull(profileManager, "profileManager");
        this.entries.addAll(repository.loadAll());
    }

    public boolean isTerminal(World world, BlockPos pos) {
        String worldKey = world.getRegistryKey().getValue().toString();
        return entries.stream().anyMatch(entry -> entry.matches(worldKey, pos));
    }

    public Optional<TaskTerminalEntry> getTerminal(World world, BlockPos pos) {
        String worldKey = world.getRegistryKey().getValue().toString();
        return entries.stream().filter(entry -> entry.matches(worldKey, pos)).findFirst();
    }

    /**
     * Registers a terminal at the given position with type and division filter.
     * If a terminal already exists at that position, it is replaced with the new config.
     */
    public boolean registerTerminal(World world, BlockPos pos, TerminalType type, List<Division> allowedDivisions) {
        String worldKey = world.getRegistryKey().getValue().toString();
        entries.removeIf(e -> e.matches(worldKey, pos));
        entries.add(new TaskTerminalEntry(worldKey, pos, type, allowedDivisions));
        save();
        return true;
    }

    /**
     * Legacy add — kept so existing call sites outside the tool don't break.
     * Registers a PUBLIC_BOARD with no division filter.
     * Returns false if a terminal already exists at this position.
     */
    public boolean addTerminal(World world, BlockPos pos) {
        if (isTerminal(world, pos)) {
            return false;
        }
        return registerTerminal(world, pos, TerminalType.PUBLIC_BOARD, List.of());
    }

    public boolean removeTerminal(World world, BlockPos pos) {
        String worldKey = world.getRegistryKey().getValue().toString();
        boolean removed = entries.removeIf(entry -> entry.matches(worldKey, pos));
        if (removed) {
            save();
        }
        return removed;
    }

    public boolean toggleTerminal(World world, BlockPos pos) {
        if (isTerminal(world, pos)) {
            return removeTerminal(world, pos);
        }
        return addTerminal(world, pos);
    }

    public List<TaskTerminalEntry> getAll() {
        return List.copyOf(entries);
    }

    public void save() {
        repository.saveAll(entries);
    }

    // -------------------------------------------------------------------------
    // Player interaction
    // -------------------------------------------------------------------------


    private List<OpsTaskPoolEntry> getAvailableTasksForTerminal(TaskTerminalEntry terminal, PlayerProfile profile) {
        List<OpsTaskPoolEntry> result = new ArrayList<>();

        for (OpsTaskPoolEntry entry : taskService.getPoolEntries()) {
            // Only show tasks open for pickup
            if (entry.getStatus() != OpsTaskStatus.PUBLIC
                    && entry.getStatus() != OpsTaskStatus.UNASSIGNED) {
                continue;
            }

            // Skip tasks already active for this player
            if (taskService.hasActiveTask(profile, entry.getTaskId())) {
                continue;
            }

            // Skip tasks this player already completed
            boolean alreadyCompleted = profile.getCompletedTasks().stream()
                    .anyMatch(r -> r.getTemplateId().equals(entry.getTaskId()));
            if (alreadyCompleted) {
                continue;
            }

            // Apply terminal type filter
            if (terminal.getType() == TerminalType.PUBLIC_BOARD) {
                if (entry.getStatus() == OpsTaskStatus.PUBLIC) {
                    result.add(entry);
                }
            } else if (terminal.getType() == TerminalType.DIVISION_BOARD) {
                List<Division> allowed = terminal.getAllowedDivisions();
                if (allowed.isEmpty() || allowed.contains(entry.getDivision())) {
                    result.add(entry);
                }
            }
        }

        return result;
    }

    public TerminalScreenOpenData buildOpeningData(ServerPlayerEntity player, World world, BlockPos pos) {
        PlayerProfile profile = profileManager.getLoadedProfile(player.getUuid());
        if (profile == null) return new TerminalScreenOpenData(List.of());

        Optional<TaskTerminalEntry> optional = getTerminal(world, pos);
        if (optional.isEmpty()) return new TerminalScreenOpenData(List.of());

        TaskTerminalEntry terminal = optional.get();
        List<OpsTaskPoolEntry> available = getAvailableTasksForTerminal(terminal, profile);

        List<TerminalScreenOpenData.TerminalTaskEntry> entries = available.stream()
                .map(e -> new TerminalScreenOpenData.TerminalTaskEntry(
                        e.getTaskId(),
                        e.getDisplayName(),
                        e.getDivision().name(),
                        formatObjectiveType(e.getObjectiveType()),
                        e.getTargetId() != null ? e.getTargetId() : "",
                        e.getRequiredAmount(),
                        e.getRewardPoints(),
                        e.isOfficerConfirmationRequired()
                ))
                .toList();

        return new TerminalScreenOpenData(entries);
    }

    private String formatObjectiveType(net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType type) {
        return switch (type) {
            case BREAK_BLOCK -> "Break Block";
            case COLLECT_ITEM -> "Collect Item";
            case VISIT_LOCATION -> "Visit Location";
            case MANUAL_CONFIRM -> "Manual Confirm";
        };
    }

}