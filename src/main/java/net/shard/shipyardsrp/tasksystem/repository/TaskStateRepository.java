package net.shard.shipyardsrp.tasksystem.repository;

import net.shard.shipyardsrp.tasksystem.data.ActiveTask;
import net.shard.shipyardsrp.tasksystem.data.CompletedTaskRecord;

import java.util.List;
import java.util.UUID;

public interface TaskStateRepository {
    List<ActiveTask> loadActiveTasks(UUID playerUuid);
    List<CompletedTaskRecord> loadCompletedTasks(UUID playerUuid);

    void saveActiveTasks(UUID playerUuid, List<ActiveTask> activeTasks);
    void saveCompletedTasks(UUID playerUuid, List<CompletedTaskRecord> completedTasks);

    void clearPlayerTaskState(UUID playerUuid);
}