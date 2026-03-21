package net.shard.seconddawnrp.tasksystem.terminal;

import java.util.List;

public interface TaskTerminalRepository {
    List<TaskTerminalEntry> loadAll();
    void saveAll(List<TaskTerminalEntry> entries);
}