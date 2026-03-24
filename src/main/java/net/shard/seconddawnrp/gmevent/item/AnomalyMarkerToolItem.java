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
import net.shard.seconddawnrp.gmevent.data.AnomalyEntry;
import net.shard.seconddawnrp.gmevent.data.AnomalyType;

import java.util.List;
import java.util.Optional;

/**
 * Anomaly Marker Tool — GM tool for registering Anomaly Marker Blocks.
 *
 * <ul>
 *   <li>Right-click unregistered block — register with default type UNKNOWN
 *   <li>Right-click registered block — cycle anomaly type
 *   <li>Sneak + right-click registered block — remove
 * </ul>
 *
 * Name and description are set via {@code /gm anomaly setname} and
 * {@code /gm anomaly setdesc} commands after registration.
 * Requires {@code st.gm.use}.
 */
public class AnomalyMarkerToolItem extends Item {

    public AnomalyMarkerToolItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return TypedActionResult.pass(user.getStackInHand(hand));
        if (!(user instanceof ServerPlayerEntity player))
            return TypedActionResult.pass(user.getStackInHand(hand));
        if (!hasPermission(player)) {
            player.sendMessage(Text.literal("Requires st.gm.use.").formatted(Formatting.RED), false);
            return TypedActionResult.fail(user.getStackInHand(hand));
        }

        HitResult hit = player.raycast(5.0, 0, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            // Right-click in air — list active contacts
            listActive(player);
            return TypedActionResult.success(user.getStackInHand(hand));
        }

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        String worldKey = world.getRegistryKey().getValue().toString();
        long posLong = pos.asLong();

        Optional<AnomalyEntry> existing =
                SecondDawnRP.ANOMALY_SERVICE.getByPosition(worldKey, posLong);

        if (player.isSneaking() && existing.isPresent()) {
            SecondDawnRP.ANOMALY_SERVICE.unregister(worldKey, posLong);
            player.sendMessage(Text.literal("Anomaly marker removed.")
                    .formatted(Formatting.YELLOW), false);
            return TypedActionResult.success(user.getStackInHand(hand));
        }

        if (existing.isPresent()) {
            // Cycle type
            AnomalyEntry entry = existing.get();
            AnomalyType[] types = AnomalyType.values();
            AnomalyType next = types[(entry.getType().ordinal() + 1) % types.length];
            entry.setType(next);
            SecondDawnRP.ANOMALY_SERVICE.saveEntry(entry);
            player.sendMessage(
                    Text.literal("Anomaly type set to: ")
                            .formatted(Formatting.GRAY)
                            .append(Text.literal(next.getDisplayName())
                                    .formatted(next.isCriticalAlert()
                                            ? Formatting.RED : Formatting.YELLOW)),
                    false);
            return TypedActionResult.success(user.getStackInHand(hand));
        }

        // Register new — default name from type, default type UNKNOWN
        try {
            AnomalyEntry entry = SecondDawnRP.ANOMALY_SERVICE.register(
                    worldKey, posLong, player.getUuid(),
                    "Anomaly " + Long.toHexString(posLong & 0xFFFFL).toUpperCase(),
                    AnomalyType.UNKNOWN);
            player.sendMessage(
                    Text.literal("Anomaly marker registered (id: ")
                            .formatted(Formatting.GREEN)
                            .append(Text.literal(entry.getEntryId()).formatted(Formatting.WHITE))
                            .append(Text.literal(")").formatted(Formatting.GREEN)),
                    false);
            player.sendMessage(
                    Text.literal("Use /gm anomaly setname <id> <name> to name it.")
                            .formatted(Formatting.GRAY),
                    false);
            player.sendMessage(
                    Text.literal("Right-click again to cycle type. /gm anomaly activate <id> to activate.")
                            .formatted(Formatting.GRAY),
                    false);
        } catch (IllegalStateException e) {
            player.sendMessage(Text.literal(e.getMessage()).formatted(Formatting.RED), false);
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    private void listActive(ServerPlayerEntity player) {
        var active = SecondDawnRP.ANOMALY_SERVICE.getActiveContacts();
        if (active.isEmpty()) {
            player.sendMessage(Text.literal("No active anomaly contacts.")
                    .formatted(Formatting.GRAY), false);
        } else {
            player.sendMessage(Text.literal("── Active Anomaly Contacts ──")
                    .formatted(Formatting.LIGHT_PURPLE), false);
            active.forEach(e -> player.sendMessage(
                    Text.literal("  " + e.getName() + " — " + e.getType().getDisplayName()
                                    + " [" + e.getEntryId() + "]")
                            .formatted(e.getType().isCriticalAlert()
                                    ? Formatting.RED : Formatting.YELLOW),
                    false));
        }
    }

    private static boolean hasPermission(ServerPlayerEntity p) {
        return p.hasPermissionLevel(2)
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(p, "st.gm.use");
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext ctx,
                              List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Anomaly Marker Tool").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Right-click block: register / cycle type").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Right-click air: list active contacts").formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Sneak + right-click: remove").formatted(Formatting.DARK_GRAY));
    }
}