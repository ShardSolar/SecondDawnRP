package net.shard.seconddawnrp.gmevent.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.data.TriggerEntry;
import net.shard.seconddawnrp.gmevent.network.OpenTriggerConfigS2CPacket;

import java.util.List;
import java.util.Optional;

/**
 * Trigger Tool — GM tool for registering and configuring Trigger Blocks.
 *
 * <ul>
 *   <li>Right-click unregistered block — register + open config GUI
 *   <li>Right-click registered block — open config GUI
 *   <li>Sneak + right-click registered block — remove
 * </ul>
 * Requires {@code st.gm.use}.
 */
public class TriggerToolItem extends Item {

    public TriggerToolItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return TypedActionResult.pass(user.getStackInHand(hand));
        if (!(user instanceof ServerPlayerEntity player)) return TypedActionResult.pass(user.getStackInHand(hand));
        if (!hasPermission(player)) {
            player.sendMessage(Text.literal("Requires st.gm.use.").formatted(Formatting.RED), false);
            return TypedActionResult.fail(user.getStackInHand(hand));
        }

        HitResult hit = player.raycast(5.0, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.sendMessage(Text.literal("Aim at a block.").formatted(Formatting.GRAY), false);
            return TypedActionResult.pass(user.getStackInHand(hand));
        }

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        String worldKey = world.getRegistryKey().getValue().toString();
        long posLong = pos.asLong();

        Optional<TriggerEntry> existing = SecondDawnRP.TRIGGER_SERVICE.getByPosition(worldKey, posLong);

        if (player.isSneaking() && existing.isPresent()) {
            SecondDawnRP.TRIGGER_SERVICE.unregister(worldKey, posLong);
            player.sendMessage(Text.literal("Trigger block removed.").formatted(Formatting.YELLOW), false);
            return TypedActionResult.success(user.getStackInHand(hand));
        }

        TriggerEntry entry = existing.orElseGet(() -> {
            try {
                TriggerEntry e = SecondDawnRP.TRIGGER_SERVICE.register(worldKey, posLong, player.getUuid());
                player.sendMessage(Text.literal("Trigger block registered (id: ")
                        .formatted(Formatting.GREEN)
                        .append(Text.literal(e.getEntryId()).formatted(Formatting.WHITE))
                        .append(Text.literal(").").formatted(Formatting.GREEN)), false);
                return e;
            } catch (IllegalStateException ex) {
                player.sendMessage(Text.literal(ex.getMessage()).formatted(Formatting.RED), false);
                return null;
            }
        });

        if (entry != null) {
            ServerPlayNetworking.send(player, OpenTriggerConfigS2CPacket.from(entry));
        }

        return TypedActionResult.success(user.getStackInHand(hand));
    }

    private static boolean hasPermission(ServerPlayerEntity p) {
        return p.hasPermissionLevel(2)
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(p, "st.gm.use");
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext ctx, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Trigger Tool").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Right-click block: register / configure").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Sneak + right-click: remove").formatted(Formatting.DARK_GRAY));
    }
}