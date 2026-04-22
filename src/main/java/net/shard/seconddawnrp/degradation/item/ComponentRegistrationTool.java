package net.shard.seconddawnrp.degradation.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
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
import net.shard.seconddawnrp.degradation.data.ComponentEntry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GM tool for registering and removing maintainable components in-world.
 *
 * Interaction model:
 *   Right-click a block (unregistered): enters naming mode, prompts GM to type
 *     a display name in chat. Intercepted by ComponentNamingChatListener.
 *     If a ship context is set on the tool, the new component is automatically
 *     assigned to that ship.
 *   Right-click a registered block: shows component status including ship binding.
 *   Sneak + right-click a registered block: removes the component.
 *   Right-click air (no block targeted): shows current ship context on the tool.
 *
 * Ship context (V15):
 *   Set via: /engineering ship settarget <shipId>
 *   Stored as "shipId" in the tool's CUSTOM_DATA NBT.
 *   When set, all newly registered components inherit this shipId automatically.
 *   Clear with: /engineering ship settarget clear
 */
public class ComponentRegistrationTool extends Item {

    public static final Map<UUID, PendingRegistration> PENDING =
            new ConcurrentHashMap<>();

    public record PendingRegistration(
            String worldKey,
            long blockPosLong,
            String blockTypeId,
            String shipId        // null if no ship context set
    ) {}

    public ComponentRegistrationTool(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return TypedActionResult.pass(user.getStackInHand(hand));
        if (!(user instanceof ServerPlayerEntity player))
            return TypedActionResult.pass(user.getStackInHand(hand));

        if (!hasPermission(player)) {
            player.sendMessage(Text.literal("You don't have permission to use this tool.")
                    .formatted(Formatting.RED), false);
            return TypedActionResult.fail(user.getStackInHand(hand));
        }

        HitResult hit = player.raycast(5.0, 0, false);

        // Right-click air — show current ship context
        if (hit.getType() != HitResult.Type.BLOCK) {
            String shipId = getShipContext(user.getStackInHand(hand));
            if (shipId == null) {
                player.sendMessage(Text.literal(
                                "[ComponentTool] No ship context set. "
                                        + "Run: /engineering ship settarget <shipId>")
                        .formatted(Formatting.GRAY), false);
            } else {
                player.sendMessage(Text.literal(
                                "[ComponentTool] Ship context: §b" + shipId
                                        + "§r — new registrations will be assigned to this ship.")
                        .formatted(Formatting.GREEN), false);
            }
            return TypedActionResult.success(user.getStackInHand(hand));
        }

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        String worldKey = world.getRegistryKey().getValue().toString();
        long posLong = pos.asLong();
        String blockTypeId = world.getBlockState(pos).getBlock().getTranslationKey();
        String shipId = getShipContext(user.getStackInHand(hand));

        Optional<ComponentEntry> existing =
                SecondDawnRP.DEGRADATION_SERVICE.getByPosition(worldKey, posLong);

        if (player.isSneaking() && existing.isPresent()) {
            handleRemove(player, worldKey, posLong, existing);
        } else if (player.isSneaking()) {
            handleBeginNaming(player, worldKey, posLong, blockTypeId, pos, shipId);
        } else if (existing.isPresent()) {
            handleInspect(player, existing.get());
        } else {
            player.sendMessage(Text.literal(
                            "Sneak + right-click to register this block as a component.")
                    .formatted(Formatting.DARK_GRAY), false);
        }

        return TypedActionResult.success(user.getStackInHand(hand));
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handleBeginNaming(ServerPlayerEntity player, String worldKey,
                                   long posLong, String blockTypeId, BlockPos pos,
                                   String shipId) {
        PENDING.put(player.getUuid(),
                new PendingRegistration(worldKey, posLong, blockTypeId, shipId));

        player.sendMessage(
                Text.literal("── Component Registration ──").formatted(Formatting.GOLD), false);
        player.sendMessage(
                Text.literal("Block: ").formatted(Formatting.GRAY)
                        .append(Text.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                                .formatted(Formatting.WHITE)), false);

        if (shipId != null) {
            player.sendMessage(
                    Text.literal("Ship: ").formatted(Formatting.GRAY)
                            .append(Text.literal(shipId).formatted(Formatting.AQUA)), false);
        } else {
            player.sendMessage(
                    Text.literal("Ship: §8unassigned §7(set context with /engineering ship settarget <shipId>)")
                            .formatted(Formatting.GRAY), false);
        }

        player.sendMessage(
                Text.literal("Type a display name in chat to register, or ")
                        .formatted(Formatting.GRAY)
                        .append(Text.literal("cancel").formatted(Formatting.RED))
                        .append(Text.literal(" to abort.").formatted(Formatting.GRAY)), false);
    }

    private void handleInspect(ServerPlayerEntity player, ComponentEntry entry) {
        player.sendMessage(
                Text.literal("── Component Status ──").formatted(Formatting.GOLD), false);
        player.sendMessage(
                Text.literal("Name:   ").formatted(Formatting.GRAY)
                        .append(Text.literal(entry.getDisplayName()).formatted(Formatting.WHITE)),
                false);
        player.sendMessage(
                Text.literal("Health: ").formatted(Formatting.GRAY)
                        .append(Text.literal(entry.getHealth() + "/100")
                                .formatted(healthColor(entry.getStatus()))), false);
        player.sendMessage(
                Text.literal("Status: ").formatted(Formatting.GRAY)
                        .append(Text.literal(entry.getStatus().name())
                                .formatted(healthColor(entry.getStatus()))), false);
        player.sendMessage(
                Text.literal("Ship:   ").formatted(Formatting.GRAY)
                        .append(entry.getShipId() != null
                                ? Text.literal(entry.getShipId()).formatted(Formatting.AQUA)
                                : Text.literal("unassigned").formatted(Formatting.DARK_GRAY)),
                false);
        player.sendMessage(
                Text.literal("ID:     ").formatted(Formatting.GRAY)
                        .append(Text.literal(entry.getComponentId())
                                .formatted(Formatting.DARK_GRAY)), false);
        player.sendMessage(
                Text.literal("Sneak + right-click to remove.").formatted(Formatting.DARK_GRAY),
                false);
    }

    private void handleRemove(ServerPlayerEntity player, String worldKey,
                              long posLong, Optional<ComponentEntry> existing) {
        if (existing.isEmpty()) {
            player.sendMessage(Text.literal("No component registered at this block.")
                    .formatted(Formatting.RED), false);
            return;
        }
        String name = existing.get().getDisplayName();
        SecondDawnRP.DEGRADATION_SERVICE.unregister(worldKey, posLong);
        player.sendMessage(
                Text.literal("Component '").formatted(Formatting.YELLOW)
                        .append(Text.literal(name).formatted(Formatting.WHITE))
                        .append(Text.literal("' removed.").formatted(Formatting.YELLOW)), false);
    }

    // ── Ship context (NBT on tool) ────────────────────────────────────────────

    /**
     * Read the ship context from the tool's NBT. Returns null if not set.
     */
    public static String getShipContext(ItemStack stack) {
        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (comp == null) return null;
        NbtCompound nbt = comp.copyNbt();
        String id = nbt.getString("shipId");
        return id.isEmpty() ? null : id;
    }

    /**
     * Set the ship context on the tool item in the player's main hand.
     * Pass null or "clear" to remove the binding.
     * Called by: /engineering ship settarget <shipId|clear>
     */
    public static void setShipContext(ServerPlayerEntity player, String shipId) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof ComponentRegistrationTool)) {
            player.sendMessage(Text.literal(
                            "[ComponentTool] Hold the Component Registration Tool.")
                    .formatted(Formatting.RED), false);
            return;
        }

        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = comp != null ? comp.copyNbt() : new NbtCompound();

        boolean clearing = shipId == null || shipId.equalsIgnoreCase("clear");

        if (clearing) {
            nbt.remove("shipId");
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            player.sendMessage(Text.literal(
                            "[ComponentTool] Ship context cleared. New registrations will be unassigned.")
                    .formatted(Formatting.YELLOW), false);
        } else {
            nbt.putString("shipId", shipId);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            player.sendMessage(Text.literal(
                            "[ComponentTool] Ship context set to: §b" + shipId
                                    + "§r\nNew component registrations will be assigned to this ship.")
                    .formatted(Formatting.GREEN), false);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean hasPermission(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2)
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(player, "st.gm.use")
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(player, "st.engineering.admin");
    }

    private static Formatting healthColor(
            net.shard.seconddawnrp.degradation.data.ComponentStatus status) {
        return switch (status) {
            case NOMINAL  -> Formatting.GREEN;
            case DEGRADED -> Formatting.YELLOW;
            case CRITICAL -> Formatting.RED;
            case OFFLINE  -> Formatting.DARK_RED;
        };
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Component Registration Tool").formatted(Formatting.GRAY));
        String shipId = getShipContext(stack);
        if (shipId != null) {
            tooltip.add(Text.literal("Ship: " + shipId).formatted(Formatting.AQUA));
        } else {
            tooltip.add(Text.literal("No ship context — use /engineering ship settarget")
                    .formatted(Formatting.DARK_GRAY));
        }
        tooltip.add(Text.literal("Right-click registered block: inspect")
                .formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Sneak + right-click block: register")
                .formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Sneak + right-click registered: remove")
                .formatted(Formatting.DARK_GRAY));
    }
}