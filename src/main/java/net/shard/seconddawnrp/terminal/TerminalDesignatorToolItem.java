package net.shard.seconddawnrp.terminal;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;

import java.util.List;

/**
 * Terminal Designator Tool — right-click any block to assign a TerminalDesignatorType to it,
 * optionally binding it to a registered ship.
 *
 * Controls:
 *   Right-click block              → designate block with current type + ship context
 *   Sneak + right-click block      → remove designation from block
 *   Right-click air                → cycle terminal type
 *   Sneak + right-click air        → show current status (type + ship context)
 *
 * Ship context:
 *   Set via: /terminal ship settarget <shipId>   (while holding this tool)
 *   Clear via: /terminal ship settarget clear
 *   When set, all designations also bind to that ship.
 *   Terminals without a ship binding fall back to position-based resolution.
 *
 * Permission: op level 2 or st.gm.use.
 */
public class TerminalDesignatorToolItem extends Item {

    private static final String NBT_TYPE = "DesignatorType";
    private static final String NBT_SHIP = "ShipContext";

    public TerminalDesignatorToolItem(Settings settings) {
        super(settings);
    }

    // ── Right-click on a block ────────────────────────────────────────────────

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayerEntity player)) return ActionResult.PASS;

        if (!hasPermission(player)) {
            player.sendMessage(Text.literal("[Terminal] No permission."), false);
            return ActionResult.FAIL;
        }

        BlockPos pos = context.getBlockPos();
        String worldKey = world.getRegistryKey().getValue().toString();

        if (player.isSneaking()) {
            // Sneak + right-click block = remove designation
            boolean removed = SecondDawnRP.TERMINAL_DESIGNATOR_REGISTRY.remove(worldKey, pos);
            player.sendMessage(Text.literal(
                    removed
                            ? "§a[Terminal] Removed designation at " + pos.toShortString()
                            : "§e[Terminal] No designation found at " + pos.toShortString()
            ), false);
            return ActionResult.SUCCESS;
        }

        // Right-click block = designate with current type + ship context
        TerminalDesignatorType type = readType(context.getStack());
        String shipId = readShip(context.getStack()); // may be null

        SecondDawnRP.TERMINAL_DESIGNATOR_REGISTRY.register(worldKey, pos, type, shipId);

        String shipSuffix = shipId != null ? " §7[ship: §b" + shipId + "§7]" : " §8[no ship binding]";
        player.sendMessage(Text.literal(
                "§b[Terminal] Designated §f" + type.getDisplayName()
                        + "§b at " + pos.toShortString() + shipSuffix
        ), false);

        // Refresh glow immediately
        if (world instanceof ServerWorld sw) {
            SecondDawnRP.TERMINAL_DESIGNATOR_SERVICE.refreshGlowForPlayer(player, sw);
        }

        return ActionResult.SUCCESS;
    }

    // ── Right-click in air ────────────────────────────────────────────────────

    @Override
    public TypedActionResult<ItemStack> use(World world,
                                            net.minecraft.entity.player.PlayerEntity playerEntity,
                                            Hand hand) {
        ItemStack stack = playerEntity.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        if (!(playerEntity instanceof ServerPlayerEntity player))
            return TypedActionResult.pass(stack);

        if (!hasPermission(player)) {
            player.sendMessage(Text.literal("[Terminal] No permission."), false);
            return TypedActionResult.fail(stack);
        }

        if (player.isSneaking()) {
            // Sneak + right-click air = show current context
            showStatus(player, stack);
            return TypedActionResult.success(stack);
        }

        // Right-click air = cycle type
        TerminalDesignatorType current = readType(stack);
        TerminalDesignatorType next = cycleNext(current);
        writeType(stack, next);

        player.sendMessage(Text.literal(
                "§b[Terminal] Type: §f" + next.getDisplayName()
                        + (next.isImplemented() ? "" : " §7(not yet implemented)")
        ), false);

        return TypedActionResult.success(stack);
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        TerminalDesignatorType current = readType(stack);
        String shipId = readShip(stack);

        tooltip.add(Text.literal("Type: " + current.getDisplayName())
                .withColor(current.getGlowColor()));
        if (!current.isImplemented()) {
            tooltip.add(Text.literal("  (screen not yet built)").withColor(0x888888));
        }

        if (shipId != null) {
            tooltip.add(Text.literal("Ship: " + shipId).withColor(0x55FFFF));
        } else {
            tooltip.add(Text.literal("Ship: none — /terminal ship settarget <id>")
                    .withColor(0x555555));
        }

        tooltip.add(Text.literal("Right-click air: cycle type").withColor(0x888888));
        tooltip.add(Text.literal("Sneak+right-click air: show status").withColor(0x888888));
        tooltip.add(Text.literal("Right-click block: designate").withColor(0x888888));
        tooltip.add(Text.literal("Sneak+right-click block: remove").withColor(0x888888));
    }

    // ── Ship context (called from command) ────────────────────────────────────

    /**
     * Set the ship context on the tool item in the player's main hand.
     * Pass null or "clear" to remove the binding.
     * Called by: /terminal ship settarget <shipId|clear>
     */
    public static void setShipContext(ServerPlayerEntity player, String shipId) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof TerminalDesignatorToolItem)) {
            player.sendMessage(Text.literal(
                            "[Terminal] Hold the Terminal Designator Tool.")
                    .formatted(net.minecraft.util.Formatting.RED), false);
            return;
        }

        NbtCompound nbt = getOrCreateNbt(stack);
        boolean clearing = shipId == null || shipId.equalsIgnoreCase("clear");

        if (clearing) {
            nbt.remove(NBT_SHIP);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            player.sendMessage(Text.literal(
                            "[Terminal] Ship context cleared. Designations will have no ship binding.")
                    .formatted(net.minecraft.util.Formatting.YELLOW), false);
        } else {
            nbt.putString(NBT_SHIP, shipId);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            player.sendMessage(Text.literal(
                            "[Terminal] Ship context: §b" + shipId
                                    + "§r\nNew designations will be bound to this ship.")
                    .formatted(net.minecraft.util.Formatting.GREEN), false);
        }
    }

    /**
     * Read the ship context from the tool. Returns null if not set.
     * Public so TerminalDesignatorService can read it if needed.
     */
    public static String getShipContext(ItemStack stack) {
        NbtCompound nbt = getOrCreateNbt(stack);
        String id = nbt.getString(NBT_SHIP);
        return id.isEmpty() ? null : id;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showStatus(ServerPlayerEntity player, ItemStack stack) {
        TerminalDesignatorType type = readType(stack);
        String shipId = readShip(stack);

        player.sendMessage(Text.literal("§b[Terminal] Current context:"), false);
        player.sendMessage(Text.literal(
                "  Type: §f" + type.getDisplayName()
                        + (type.isImplemented() ? "" : " §7(not yet implemented)")), false);
        player.sendMessage(Text.literal(
                "  Ship: " + (shipId != null
                        ? "§b" + shipId
                        : "§8none — use /terminal ship settarget <shipId>")), false);
        player.sendMessage(Text.literal(
                "§7Right-click air to cycle type. "
                        + "Right-click block to designate."), false);

        // Also list nearby designations
        String worldKey = player.getWorld().getRegistryKey().getValue().toString();
        BlockPos center = player.getBlockPos();
        var nearby = SecondDawnRP.TERMINAL_DESIGNATOR_REGISTRY.getNearby(worldKey, center, 48);
        if (!nearby.isEmpty()) {
            player.sendMessage(Text.literal(
                    "§b[Terminal] Nearby (" + nearby.size() + "):"), false);
            for (var entry : nearby) {
                BlockPos p = entry.getPos();
                int dist = (int) Math.sqrt(center.getSquaredDistance(p));
                String entryShip = entry.hasShipBinding()
                        ? " §8[§b" + entry.getShipId() + "§8]" : " §8[unbound]";
                player.sendMessage(Text.literal(
                        "  §f" + entry.getType().getDisplayName()
                                + " §7@ " + p.toShortString()
                                + " (" + dist + "m)" + entryShip), false);
            }
        }
    }

    private TerminalDesignatorType readType(ItemStack stack) {
        NbtCompound nbt = getOrCreateNbt(stack);
        if (!nbt.contains(NBT_TYPE)) return TerminalDesignatorType.OPS_TERMINAL;
        try {
            return TerminalDesignatorType.valueOf(nbt.getString(NBT_TYPE));
        } catch (IllegalArgumentException e) {
            return TerminalDesignatorType.OPS_TERMINAL;
        }
    }

    private String readShip(ItemStack stack) {
        NbtCompound nbt = getOrCreateNbt(stack);
        String id = nbt.getString(NBT_SHIP);
        return id.isEmpty() ? null : id;
    }

    private void writeType(ItemStack stack, TerminalDesignatorType type) {
        NbtCompound nbt = getOrCreateNbt(stack);
        nbt.putString(NBT_TYPE, type.name());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static NbtCompound getOrCreateNbt(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        return component == null ? new NbtCompound() : component.copyNbt();
    }

    private TerminalDesignatorType cycleNext(TerminalDesignatorType current) {
        TerminalDesignatorType[] values = TerminalDesignatorType.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private boolean hasPermission(ServerPlayerEntity player) {
        return SecondDawnRP.PERMISSION_SERVICE.canUseTerminalDesignatorTool(player);
    }
}