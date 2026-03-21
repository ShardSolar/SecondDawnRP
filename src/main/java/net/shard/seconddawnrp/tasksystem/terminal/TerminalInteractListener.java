package net.shard.seconddawnrp.tasksystem.terminal;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TerminalInteractListener {

    private final TaskTerminalManager terminalManager;

    public TerminalInteractListener(TaskTerminalManager terminalManager) {
        this.terminalManager = terminalManager;
    }

    public void register() {
        UseBlockCallback.EVENT.register(this::onUseBlock);
    }

    private ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.PASS;
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

        BlockPos pos = hit.getBlockPos();
        if (!terminalManager.isTerminal(world, pos)) return ActionResult.PASS;

        var stack = player.getMainHandStack();
        if (stack.getItem() instanceof TaskTerminalToolItem) return ActionResult.PASS;

        // Build opening data and open the screen
        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
        TerminalScreenOpenData data = terminalManager.buildOpeningData(serverPlayer, world, pos);
        serverPlayer.openHandledScreen(new TerminalScreenHandlerFactory(data));

        return ActionResult.SUCCESS;
    }
}