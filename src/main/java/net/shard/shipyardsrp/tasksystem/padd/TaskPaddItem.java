package net.shard.shipyardsrp.tasksystem.padd;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class TaskPaddItem extends Item {

    public TaskPaddItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (!world.isClient && player instanceof ServerPlayerEntity serverPlayer) {
            TaskPaddOpeningData openingData = TaskPaddScreenHandler.createOpeningData(serverPlayer);

            serverPlayer.openHandledScreen(new ExtendedScreenHandlerFactory<TaskPaddOpeningData>() {
                @Override
                public Text getDisplayName() {
                    return Text.literal("Task PADD");
                }

                @Override
                public TaskPaddOpeningData getScreenOpeningData(ServerPlayerEntity player) {
                    return openingData;
                }

                @Override
                public TaskPaddScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity playerEntity) {
                    return new TaskPaddScreenHandler(
                            syncId,
                            playerInventory,
                            openingData.activeLines(),
                            openingData.completedLines()
                    );
                }
            });
        }

        return TypedActionResult.success(player.getStackInHand(hand));
    }
}