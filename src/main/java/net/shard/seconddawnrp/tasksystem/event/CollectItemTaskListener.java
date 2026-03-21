package net.shard.seconddawnrp.tasksystem.event;

import net.fabricmc.fabric.api.event.player.PlayerPickupItemCallback;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.playerdata.PlayerProfileManager;
import net.shard.seconddawnrp.tasksystem.data.ActiveTask;
import net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType;
import net.shard.seconddawnrp.tasksystem.data.TaskTemplate;
import net.shard.seconddawnrp.tasksystem.service.TaskService;
import net.shard.seconddawnrp.tasksystem.util.TaskTargetMatcher;

import java.util.List;

public class CollectItemTaskListener {

    private final PlayerProfileManager profileManager;
    private final TaskService taskService;

    public CollectItemTaskListener(PlayerProfileManager profileManager, TaskService taskService) {
        this.profileManager = profileManager;
        this.taskService = taskService;
    }

    public void register() {
        PlayerPickupItemCallback.EVENT.register(this::onItemPickup);
    }

    private void onItemPickup(PlayerEntity player, ItemEntity itemEntity) {
        if (player.getWorld().isClient()) return;

        PlayerProfile profile = profileManager.getLoadedProfile(player.getUuid());
        if (profile == null) return;

        List<ActiveTask> activeTasks = profile.getActiveTasks();
        if (activeTasks.isEmpty()) return;

        ItemStack stack = itemEntity.getStack();
        int pickedUpCount = stack.getCount();

        for (ActiveTask activeTask : List.copyOf(activeTasks)) {
            TaskTemplate template = taskService.resolveTaskTemplate(activeTask.getTemplateId());
            if (template == null) continue;
            if (template.getObjectiveType() != TaskObjectiveType.COLLECT_ITEM) continue;
            if (activeTask.isComplete()) continue;

            if (TaskTargetMatcher.itemMatches(stack, template.getTargetId())) {
                taskService.incrementProgress(profile, template.getId(), pickedUpCount);
            }
        }
    }
}