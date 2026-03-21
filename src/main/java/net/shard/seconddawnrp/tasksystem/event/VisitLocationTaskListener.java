package net.shard.seconddawnrp.tasksystem.event;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;
import net.shard.seconddawnrp.tasksystem.data.ActiveTask;
import net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType;
import net.shard.seconddawnrp.tasksystem.data.TaskTemplate;
import net.shard.seconddawnrp.tasksystem.service.TaskService;
import net.shard.seconddawnrp.tasksystem.util.TaskTargetMatcher;

import java.util.List;

public class VisitLocationTaskListener {

    // Check every 20 ticks (1 second) — frequent enough to feel responsive,
    // cheap enough not to matter with many players
    private static final int CHECK_INTERVAL_TICKS = 20;

    private final PlayerProfileManager profileManager;
    private final TaskService taskService;
    private int tickCounter = 0;

    public VisitLocationTaskListener(PlayerProfileManager profileManager, TaskService taskService) {
        this.profileManager = profileManager;
        this.taskService = taskService;
    }

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter < CHECK_INTERVAL_TICKS) return;
        tickCounter = 0;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerProfile profile = profileManager.getLoadedProfile(player.getUuid());
            if (profile == null) continue;

            List<ActiveTask> activeTasks = profile.getActiveTasks();
            if (activeTasks.isEmpty()) continue;

            for (ActiveTask activeTask : List.copyOf(activeTasks)) {
                TaskTemplate template = taskService.resolveTaskTemplate(activeTask.getTemplateId());
                if (template == null) continue;
                if (template.getObjectiveType() != TaskObjectiveType.VISIT_LOCATION) continue;
                if (activeTask.isComplete()) continue;

                if (TaskTargetMatcher.locationMatches(player.getBlockPos(), template.getTargetId())) {
                    // VISIT_LOCATION is a single-completion objective — required amount
                    // should always be 1. incrementProgress handles the completion logic.
                    taskService.incrementProgress(profile, template.getId(), 1);
                }
            }
        }
    }
}