package net.shard.seconddawnrp.tasksystem.terminal;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.divison.Division;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

import java.util.List;

public class TaskTerminalToolItem extends Item {

    private static final String NBT_TYPE     = "TerminalType";
    private static final String NBT_DIVISION = "TerminalDivision";

    public TaskTerminalToolItem(Settings settings) {
        super(settings);
    }

    // ── Right-click on a block ────────────────────────────────────────────────

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        BlockPos pos = context.getBlockPos();

        if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.SUCCESS;
        }

        if (!hasPermission(serverPlayer)) {
            serverPlayer.sendMessage(Text.literal("[Terminal] No permission to configure terminals."), false);
            return ActionResult.FAIL;
        }

        // Sneak + right-click block = remove
        if (player.isSneaking()) {
            boolean removed = SecondDawnRP.TERMINAL_MANAGER.removeTerminal(world, pos);
            serverPlayer.sendMessage(Text.literal(
                    removed
                            ? "[Terminal] Removed at " + pos.toShortString()
                            : "[Terminal] No terminal found at " + pos.toShortString()
            ), false);
            return ActionResult.SUCCESS;
        }

        // Right-click block = register with current config
        ItemStack stack = context.getStack();
        TerminalType type = readType(stack);
        List<Division> divisions = buildDivisionFilter(stack, type);

        SecondDawnRP.TERMINAL_MANAGER.registerTerminal(world, pos, type, divisions);

        String divLabel = type == TerminalType.DIVISION_BOARD
                ? " [" + readDivision(stack).name() + "]"
                : "";
        serverPlayer.sendMessage(Text.literal(
                "[Terminal] Registered " + type.name() + divLabel + " at " + pos.toShortString()
        ), false);

        return ActionResult.SUCCESS;
    }

    // ── Right-click in air ────────────────────────────────────────────────────

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        if (world.isClient) {
            return TypedActionResult.success(stack);
        }

        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return TypedActionResult.pass(stack);
        }

        if (!hasPermission(serverPlayer)) {
            serverPlayer.sendMessage(Text.literal("[Terminal] No permission to configure terminals."), false);
            return TypedActionResult.fail(stack);
        }

        if (player.isSneaking()) {
            // Sneak + right-click air = cycle Division
            Division next = cycleNextDivision(readDivision(stack));
            writeDivision(stack, next);
            serverPlayer.sendMessage(Text.literal(
                    "[Terminal] Division filter set to: " + next.name()
                            + " (applies when type is DIVISION_BOARD)"
            ), false);
        } else {
            // Right-click air = cycle TerminalType
            TerminalType next = cycleNextType(readType(stack));
            writeType(stack, next);
            serverPlayer.sendMessage(Text.literal(
                    "[Terminal] Type set to: " + next.name()
            ), false);
        }

        return TypedActionResult.success(stack);
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        TerminalType termType = readType(stack);
        tooltip.add(Text.literal("Type: " + termType.name()).withColor(0x8FD7E8));

        if (termType == TerminalType.DIVISION_BOARD) {
            tooltip.add(Text.literal("Division: " + readDivision(stack).name()).withColor(0xFFB24A));
        }

        tooltip.add(Text.literal("Right-click air: cycle type").withColor(0x888888));
        tooltip.add(Text.literal("Sneak + right-click air: cycle division").withColor(0x888888));
        tooltip.add(Text.literal("Right-click block: place terminal").withColor(0x888888));
        tooltip.add(Text.literal("Sneak + right-click block: remove").withColor(0x888888));
    }

    // ── NBT helpers ───────────────────────────────────────────────────────────

    private TerminalType readType(ItemStack stack) {
        NbtCompound nbt = getOrCreateNbt(stack);
        if (!nbt.contains(NBT_TYPE)) return TerminalType.PUBLIC_BOARD;
        try {
            return TerminalType.valueOf(nbt.getString(NBT_TYPE));
        } catch (IllegalArgumentException e) {
            return TerminalType.PUBLIC_BOARD;
        }
    }

    private void writeType(ItemStack stack, TerminalType type) {
        NbtCompound nbt = getOrCreateNbt(stack);
        nbt.putString(NBT_TYPE, type.name());
        saveNbt(stack, nbt);
    }

    private Division readDivision(ItemStack stack) {
        NbtCompound nbt = getOrCreateNbt(stack);
        if (!nbt.contains(NBT_DIVISION)) return Division.values()[0];
        try {
            return Division.valueOf(nbt.getString(NBT_DIVISION));
        } catch (IllegalArgumentException e) {
            return Division.values()[0];
        }
    }

    private void writeDivision(ItemStack stack, Division division) {
        NbtCompound nbt = getOrCreateNbt(stack);
        nbt.putString(NBT_DIVISION, division.name());
        saveNbt(stack, nbt);
    }

    private NbtCompound getOrCreateNbt(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (component == null) return new NbtCompound();
        return component.copyNbt();
    }

    private void saveNbt(ItemStack stack, NbtCompound nbt) {
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    // ── Cycle helpers ─────────────────────────────────────────────────────────

    private TerminalType cycleNextType(TerminalType current) {
        TerminalType[] values = TerminalType.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private Division cycleNextDivision(Division current) {
        Division[] values = Division.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private List<Division> buildDivisionFilter(ItemStack stack, TerminalType type) {
        if (type == TerminalType.PUBLIC_BOARD) {
            return List.of(); // public board ignores division filter
        }
        return List.of(readDivision(stack));
    }

    // ── Permission helper ─────────────────────────────────────────────────────

    private boolean hasPermission(ServerPlayerEntity player) {
        PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
        if (profile == null) return false;
        return SecondDawnRP.TASK_PERMISSION_SERVICE.canViewOpsPad(player, profile);
    }
}