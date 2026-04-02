package net.shard.seconddawnrp.degradation.item;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.network.LocateComponentS2CPacket;
import net.shard.seconddawnrp.degradation.network.OpenEngineeringPadS2CPacket;

import java.util.List;

/*
 * The Engineering division's handheld PADD.
 *
 * Right-click in air: opens the Engineering PADD screen showing all
 * registered components, their health bars, and status at a glance.
 *
 * While held, nearby registered components emit subtle locator particles
 * to help identify them in-world.
 */
public class EngineeringPadItem extends Item {

    private static final double LOCATE_RADIUS_BLOCKS = 32.0;
    private static final int LOCATE_PULSE_INTERVAL_TICKS = 15;

    public EngineeringPadItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    serverPlayer,
                    OpenEngineeringPadS2CPacket.fromService(SecondDawnRP.DEGRADATION_SERVICE)
            );
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient()) return;
        if (!(entity instanceof ServerPlayerEntity player)) return;
        if (SecondDawnRP.DEGRADATION_SERVICE == null) return;

        boolean inOffhand = ItemStack.areEqual(player.getOffHandStack(), stack);
        boolean activelyHolding = selected || inOffhand;
        if (!activelyHolding) return;

        if ((world.getTime() % LOCATE_PULSE_INTERVAL_TICKS) != 0) return;

        String playerWorldKey = player.getWorld().getRegistryKey().getValue().toString();
        var playerPos = player.getBlockPos();

        for (ComponentEntry entry : SecondDawnRP.DEGRADATION_SERVICE.getAllComponents()) {
            if (!playerWorldKey.equals(entry.getWorldKey())) continue;

            var componentPos = net.minecraft.util.math.BlockPos.fromLong(entry.getBlockPosLong());
            if (!playerPos.isWithinDistance(componentPos, LOCATE_RADIUS_BLOCKS)) continue;

            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player,
                    new LocateComponentS2CPacket(
                            entry.getDisplayName(),
                            entry.getStatus(),
                            componentPos.getX() + 0.5,
                            componentPos.getY() + 0.5,
                            componentPos.getZ() + 0.5
                    )
            );
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Engineering Systems PADD")
                .formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Right-click: component overview")
                .formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Right-click block: inspect component")
                .formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Sneak + right-click block: apply repair")
                .formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("While held: highlights nearby registered components")
                .formatted(Formatting.DARK_AQUA));
    }
}