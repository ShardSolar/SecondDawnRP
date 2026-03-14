package net.shard.shipyardsrp.tasksystem.event;

import net.shard.shipyardsrp.starfleetarchives.PlayerProfileManager;
import net.shard.shipyardsrp.tasksystem.service.TaskService;

public final class TaskEventRegistrar {

    private TaskEventRegistrar() {
    }

    public static void register(PlayerProfileManager profileManager, TaskService taskService) {
        new BlockBreakTaskListener(profileManager, taskService).register();
    }
}