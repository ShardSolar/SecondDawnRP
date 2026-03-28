package net.shard.seconddawnrp.terminal;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;

/**
 * Registers a UseBlockCallback that intercepts right-clicks on designated terminal blocks.
 *
 * Priority notes:
 *   - The existing TerminalInteractListener (task terminals) is registered before this one.
 *     If a block is both a task terminal AND a designated terminal, the task terminal wins
 *     (it returns SUCCESS/FAIL before this fires). This matches the documented priority rule:
 *     "component listener takes priority over terminal registry".
 *   - Sneaking players are passed through — the tool item handles sneak+right-click itself.
 *   - This listener only fires on the server side.
 */
public class TerminalDesignatorInteractListener {

    public void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(world instanceof ServerWorld sw)) return ActionResult.PASS;

            // Let sneaking players interact normally (tool sneak behaviour handled by item)
            if (player.isSneaking()) return ActionResult.PASS;

            // Only main hand triggers terminal open
            if (hand != net.minecraft.util.Hand.MAIN_HAND) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();

            boolean consumed = SecondDawnRP.TERMINAL_DESIGNATOR_SERVICE.handleInteract(sp, sw, pos);

            // Return FAIL to prevent vanilla block interaction (e.g. opening a chest that was
            // designated as a terminal). PASS if not a designated terminal.
            return consumed ? ActionResult.FAIL : ActionResult.PASS;
        });
    }
}