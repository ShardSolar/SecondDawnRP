package net.shard.seconddawnrp.tasksystem.event;

import net.shard.seconddawnrp.playerdata.PlayerProfileManager;
import net.shard.seconddawnrp.tasksystem.service.TaskService;

public final class TaskEventRegistrar {

    private TaskEventRegistrar() {}

    public static void register(PlayerProfileManager profileManager, TaskService taskService) {
        new BlockBreakTaskListener(profileManager, taskService).register();
        new CollectItemTaskListener(profileManager, taskService).register();
        new VisitLocationTaskListener(profileManager, taskService).register();
    }
}