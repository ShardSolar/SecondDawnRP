package net.shard.seconddawnrp.tasksystem.repository;

import net.shard.seconddawnrp.tasksystem.data.OpsTaskPoolEntry;

import java.util.List;

public interface OpsTaskPoolRepository {
    List<OpsTaskPoolEntry> loadAll();
    void saveAll(List<OpsTaskPoolEntry> entries);
}