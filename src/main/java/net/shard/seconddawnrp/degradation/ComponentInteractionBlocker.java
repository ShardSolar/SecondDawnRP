package net.shard.seconddawnrp.degradation;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.service.DegradationService;
import net.shard.seconddawnrp.registry.ModItems;

public final class ComponentInteractionBlocker {

    private ComponentInteractionBlocker() {}

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;

            DegradationService service = SecondDawnRP.DEGRADATION_SERVICE;
            if (service == null) return ActionResult.PASS;

            ItemStack heldStack = player.getStackInHand(hand);

            // Allow engineering/admin tools to still interact with locked components.
            if (shouldBypassLock(heldStack)) {
                return ActionResult.PASS;
            }

            String worldKey = world.getRegistryKey().getValue().toString();
            long blockPosLong = hitResult.getBlockPos().asLong();

            if (!service.isBlockDisabled(worldKey, blockPosLong)) {
                return ActionResult.PASS;
            }

            if (player instanceof ServerPlayerEntity serverPlayer) {
                service.getByPosition(worldKey, blockPosLong).ifPresent(entry ->
                        serverPlayer.sendMessage(
                                Text.literal("[Engineering] " + entry.getDisplayName()
                                                + " is " + entry.getStatus().name()
                                                + " and will not respond.")
                                        .formatted(Formatting.RED),
                                true
                        )
                );
            }

            return ActionResult.FAIL;
        });
    }

    private static boolean shouldBypassLock(ItemStack stack) {
        if (stack.isEmpty()) return false;

        return stack.isOf(ModItems.ENGINEERING_PAD)
                || stack.isOf(ModItems.COMPONENT_REGISTRATION_TOOL)
                || stack.isOf(ModItems.WARP_CORE_TOOL)
                || stack.isOf(ModItems.ENVIRONMENTAL_EFFECT_TOOL)
                || stack.isOf(ModItems.TRIGGER_TOOL)
                || stack.isOf(ModItems.TASK_TERMINAL_TOOL)
                || stack.isOf(ModItems.TERMINAL_DESIGNATOR_TOOL)
                || stack.isOf(ModItems.OPERATIONS_PAD)
                || stack.isOf(ModItems.TASK_PAD)
                || stack.isOf(ModItems.MEDICAL_PAD)
                || stack.isOf(ModItems.TRICORDER);
    }
}