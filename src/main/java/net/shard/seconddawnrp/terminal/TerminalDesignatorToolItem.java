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
 * Terminal Designator Tool — right-click any block to assign a TerminalDesignatorType to it.
 *
 * Controls:
 *   Right-click air            → cycle terminal type
 *   Right-click block          → designate block at current type
 *   Sneak + right-click air    → list all nearby designations (within 48 blocks)
 *   Sneak + right-click block  → remove designation from block
 *
 * Permission: requires op level 2 (admin). Same check as TaskTerminalToolItem.
 */
public class TerminalDesignatorToolItem extends Item {

    private static final String NBT_TYPE = "DesignatorType";

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

        // Right-click block = designate
        TerminalDesignatorType type = readType(context.getStack());
        SecondDawnRP.TERMINAL_DESIGNATOR_REGISTRY.register(worldKey, pos, type);

        player.sendMessage(Text.literal(
                "§b[Terminal] Designated §f" + type.getDisplayName()
                        + "§b at " + pos.toShortString()
        ), false);

        // Refresh glow immediately so the newly placed terminal lights up
        if (world instanceof ServerWorld sw) {
            SecondDawnRP.TERMINAL_DESIGNATOR_SERVICE.refreshGlowForPlayer(player, sw);
        }

        return ActionResult.SUCCESS;
    }

    // ── Right-click in air ────────────────────────────────────────────────────

    @Override
    public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity playerEntity, Hand hand) {
        ItemStack stack = playerEntity.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        if (!(playerEntity instanceof ServerPlayerEntity player)) return TypedActionResult.pass(stack);

        if (!hasPermission(player)) {
            player.sendMessage(Text.literal("[Terminal] No permission."), false);
            return TypedActionResult.fail(stack);
        }

        if (player.isSneaking()) {
            // Sneak + right-click air = list nearby designations
            listNearby(player, world);
            return TypedActionResult.success(stack);
        }

        // Right-click air = cycle type
        TerminalDesignatorType current = readType(stack);
        TerminalDesignatorType next = cycleNext(current);
        writeType(stack, next);

        player.sendMessage(Text.literal(
                "§b[Terminal] Type set to: §f" + next.getDisplayName()
                        + (next.isImplemented() ? "" : " §7(not yet implemented)")
        ), false);

        return TypedActionResult.success(stack);
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        TerminalDesignatorType current = readType(stack);
        tooltip.add(Text.literal("Type: " + current.getDisplayName())
                .withColor(current.getGlowColor()));
        if (!current.isImplemented()) {
            tooltip.add(Text.literal("  (screen not yet built)").withColor(0x888888));
        }
        tooltip.add(Text.literal("Right-click air: cycle type").withColor(0x888888));
        tooltip.add(Text.literal("Sneak+right-click air: list nearby").withColor(0x888888));
        tooltip.add(Text.literal("Right-click block: designate").withColor(0x888888));
        tooltip.add(Text.literal("Sneak+right-click block: remove").withColor(0x888888));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void listNearby(ServerPlayerEntity player, World world) {
        String worldKey = world.getRegistryKey().getValue().toString();
        BlockPos center = player.getBlockPos();
        var nearby = SecondDawnRP.TERMINAL_DESIGNATOR_REGISTRY.getNearby(worldKey, center, 48);

        if (nearby.isEmpty()) {
            player.sendMessage(Text.literal("§7[Terminal] No designations within 48 blocks."), false);
            return;
        }

        player.sendMessage(Text.literal("§b[Terminal] Nearby designations (" + nearby.size() + "):"), false);
        for (var entry : nearby) {
            BlockPos p = entry.getPos();
            int dist = (int) Math.sqrt(center.getSquaredDistance(p));
            player.sendMessage(Text.literal(
                    "  §f" + entry.getType().getDisplayName()
                            + " §7@ " + p.toShortString()
                            + " (" + dist + "m)"
            ), false);
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

    private void writeType(ItemStack stack, TerminalDesignatorType type) {
        NbtCompound nbt = getOrCreateNbt(stack);
        nbt.putString(NBT_TYPE, type.name());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private NbtCompound getOrCreateNbt(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        return component == null ? new NbtCompound() : component.copyNbt();
    }

    private TerminalDesignatorType cycleNext(TerminalDesignatorType current) {
        TerminalDesignatorType[] values = TerminalDesignatorType.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private boolean hasPermission(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2);
    }
}