package net.shard.seconddawnrp.tasksystem.terminal;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

public class TaskTerminalToolItem extends Item {

    public TaskTerminalToolItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        BlockPos pos = context.getBlockPos();

        if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.SUCCESS;
        }

        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(serverPlayer.getUuid());
        if (profile == null) {
            serverPlayer.sendMessage(Text.literal("Profile not loaded."), false);
            return ActionResult.FAIL;
        }

        if (!SecondDawnRP.TASK_PERMISSION_SERVICE.canViewOpsPad(serverPlayer, profile)) {
            serverPlayer.sendMessage(Text.literal("You do not have permission to configure task terminals."), false);
            return ActionResult.FAIL;
        }

        boolean added = SecondDawnRP.TERMINAL_MANAGER.addTerminal(world, pos);
        if (added) {
            serverPlayer.sendMessage(Text.literal("Task terminal assigned at " + pos.toShortString()), false);
        } else {
            boolean removed = SecondDawnRP.TERMINAL_MANAGER.removeTerminal(world, pos);
            if (removed) {
                serverPlayer.sendMessage(Text.literal("Task terminal removed at " + pos.toShortString()), false);
            } else {
                serverPlayer.sendMessage(Text.literal("Task terminal toggle failed."), false);
                return ActionResult.FAIL;
            }
        }

        return ActionResult.SUCCESS;
    }
}