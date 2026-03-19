package net.shard.seconddawnrp.tasksystem.pad;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.shard.seconddawnrp.tasksystem.util.TaskPermissionUtil;

public class OperationsPadItem extends Item {

    public OperationsPadItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        if (world.isClient()) {
            return TypedActionResult.success(stack);
        }

        if (!(user instanceof ServerPlayerEntity serverPlayer)) {
            return TypedActionResult.pass(stack);
        }

        if (!TaskPermissionUtil.canOpenOperationsPad(serverPlayer)) {
            serverPlayer.sendMessage(Text.literal("You do not have permission to use the Operations PAD."), false);
            return TypedActionResult.fail(stack);
        }

        serverPlayer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, player) -> new AdminTaskScreenHandler(syncId, inventory),
                Text.literal("Operations PAD")
        ));

        return TypedActionResult.success(stack);
    }
}