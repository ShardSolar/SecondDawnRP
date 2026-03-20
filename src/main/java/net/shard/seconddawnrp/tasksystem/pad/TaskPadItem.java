package net.shard.seconddawnrp.tasksystem.pad;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class TaskPadItem extends Item {

    public TaskPadItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
            TaskPadOpeningData openingData = TaskPadScreenHandler.createOpeningData(serverPlayer);

            serverPlayer.openHandledScreen(new ExtendedScreenHandlerFactory<TaskPadOpeningData>() {
                @Override
                public Text getDisplayName() {
                    return Text.literal("Task PAD");
                }

                @Override
                public TaskPadOpeningData getScreenOpeningData(ServerPlayerEntity player) {
                    return openingData;
                }

                @Override
                public TaskPadScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity playerEntity) {
                    return new TaskPadScreenHandler(
                            syncId,
                            playerInventory,
                            openingData.activeLines(),
                            openingData.completedLines(),
                            openingData.activeTaskIds()
                    );
                }
            });
        }

        return TypedActionResult.success(player.getStackInHand(hand));
    }
}