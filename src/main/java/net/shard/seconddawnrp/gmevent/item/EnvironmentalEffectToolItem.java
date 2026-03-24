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
import net.shard.seconddawnrp.gmevent.data.EnvironmentalEffectEntry;
import net.shard.seconddawnrp.gmevent.network.EnvRegistryS2CPacket;
import net.shard.seconddawnrp.gmevent.network.OpenEnvConfigS2CPacket;

import java.util.List;
import java.util.Optional;

/**
 * Environmental Effect Tool — GM tool for registering and configuring
 * Environmental Effect Blocks.
 *
 * <ul>
 *   <li>Right-click unregistered block — register it as an env effect block
 *   <li>Right-click registered block — open config GUI
 *   <li>Sneak + right-click registered block — remove registration
 * </ul>
 *
 * Requires {@code st.gm.use} permission.
 */
public class EnvironmentalEffectToolItem extends Item {

    public EnvironmentalEffectToolItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return TypedActionResult.pass(user.getStackInHand(hand));
        if (!(user instanceof ServerPlayerEntity player)) return TypedActionResult.pass(user.getStackInHand(hand));
        if (!hasPermission(player)) {
            player.sendMessage(Text.literal("Requires st.gm.use permission.").formatted(Formatting.RED), false);
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

        Optional<EnvironmentalEffectEntry> existing =
                SecondDawnRP.ENV_EFFECT_SERVICE.getByPosition(worldKey, posLong);

        if (player.isSneaking() && existing.isPresent()) {
            SecondDawnRP.ENV_EFFECT_SERVICE.unregister(worldKey, posLong);
            player.sendMessage(Text.literal("Environmental effect block removed.")
                    .formatted(Formatting.YELLOW), false);
            return TypedActionResult.success(user.getStackInHand(hand));
        }

        if (existing.isPresent()) {
            // Open config GUI
            ServerPlayNetworking.send(player, OpenEnvConfigS2CPacket.from(existing.get()));
            return TypedActionResult.success(user.getStackInHand(hand));
        }

        // Register new
        try {
            EnvironmentalEffectEntry entry = SecondDawnRP.ENV_EFFECT_SERVICE
                    .register(worldKey, posLong, player.getUuid());
            player.sendMessage(
                    Text.literal("Registered env effect block (id: ")
                            .formatted(Formatting.GREEN)
                            .append(Text.literal(entry.getEntryId()).formatted(Formatting.WHITE))
                            .append(Text.literal("). Right-click to configure.").formatted(Formatting.GREEN)),
                    false);
            // Send registry then config GUI
            ServerPlayNetworking.send(player, EnvRegistryS2CPacket.fromService());
            ServerPlayNetworking.send(player, OpenEnvConfigS2CPacket.from(entry));
        } catch (IllegalStateException e) {
            player.sendMessage(Text.literal(e.getMessage()).formatted(Formatting.RED), false);
        }

        return TypedActionResult.success(user.getStackInHand(hand));
    }

    private static boolean hasPermission(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2)
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(player, "st.gm.use");
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext ctx, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Environmental Effect Tool").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Right-click block: register / configure").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Sneak + right-click: remove").formatted(Formatting.DARK_GRAY));
    }
}