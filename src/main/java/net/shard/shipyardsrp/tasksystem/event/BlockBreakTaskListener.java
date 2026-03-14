package net.shard.shipyardsrp.tasksystem.event;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.shipyardsrp.starfleetarchives.PlayerProfile;
import net.shard.shipyardsrp.starfleetarchives.PlayerProfileManager;
import net.shard.shipyardsrp.tasksystem.data.ActiveTask;
import net.shard.shipyardsrp.tasksystem.data.TaskObjectiveType;
import net.shard.shipyardsrp.tasksystem.data.TaskTemplate;
import net.shard.shipyardsrp.tasksystem.registry.TaskRegistry;
import net.shard.shipyardsrp.tasksystem.service.TaskService;
import net.shard.shipyardsrp.tasksystem.util.TaskTargetMatcher;

import java.util.List;

public class BlockBreakTaskListener {

    private final PlayerProfileManager profileManager;
    private final TaskService taskService;

    public BlockBreakTaskListener(PlayerProfileManager profileManager, TaskService taskService) {
        this.profileManager = profileManager;
        this.taskService = taskService;
    }

    public void register() {
        PlayerBlockBreakEvents.AFTER.register(this::onBlockBreak);
    }

    private void onBlockBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        PlayerProfile profile = profileManager.getLoadedProfile(player.getUuid());
        if (profile == null) {
            return;
        }

        List<ActiveTask> activeTasks = profile.getActiveTasks();
        if (activeTasks.isEmpty()) {
            return;
        }

        for (ActiveTask activeTask : List.copyOf(activeTasks)) {
            TaskTemplate template = TaskRegistry.get(activeTask.getTemplateId());
            if (template == null) {
                continue;
            }

            if (template.getObjectiveType() != TaskObjectiveType.BREAK_BLOCK) {
                continue;
            }

            if (TaskTargetMatcher.blockMatches(state.getBlock(), template.getTargetId())) {
                taskService.incrementProgress(profile, template.getId(), 1);
                profileManager.markDirty(player.getUuid());
            }
        }
    }
}