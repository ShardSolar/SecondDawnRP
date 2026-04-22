package net.shard.seconddawnrp.degradation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;
import net.shard.seconddawnrp.degradation.network.LocateComponentS2CPacket;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class EngineeringCommands {

    private EngineeringCommands() {}

    public static void register(
            CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess,
            CommandManager.RegistrationEnvironment environment
    ) {
        dispatcher.register(CommandManager.literal("engineering")
                .requires(source -> source.hasPermissionLevel(2))

                // ── /engineering locate <componentId> ─────────────────────────
                .then(CommandManager.literal("locate")
                        .then(CommandManager.argument("componentId", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerCommandSource source = ctx.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
                                        source.sendError(Text.literal("Only players can use this command."));
                                        return 0;
                                    }

                                    String componentId = StringArgumentType.getString(ctx, "componentId");
                                    Optional<ComponentEntry> opt =
                                            SecondDawnRP.DEGRADATION_SERVICE.getById(componentId);

                                    if (opt.isEmpty()) {
                                        source.sendError(Text.literal(
                                                "No component found with ID '" + componentId + "'."));
                                        return 0;
                                    }

                                    ComponentEntry entry = opt.get();
                                    Vec3d center = Vec3d.ofCenter(
                                            BlockPos.fromLong(entry.getBlockPosLong()));

                                    ServerPlayNetworking.send(player,
                                            new LocateComponentS2CPacket(
                                                    entry.getComponentId(),
                                                    entry.getStatus(),
                                                    center.x, center.y, center.z));

                                    source.sendFeedback(() -> Text.literal(
                                                    "Locator sent for component '" + entry.getDisplayName()
                                                            + "' [" + entry.getStatus().name() + "] at "
                                                            + formatPos(entry.getBlockPosLong()) + ".")
                                            .formatted(Formatting.AQUA), false);
                                    return 1;
                                })))

                // ── /engineering locatenearest ────────────────────────────────
                .then(CommandManager.literal("locatenearest")
                        .executes(ctx -> {
                            ServerCommandSource source = ctx.getSource();
                            if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
                                source.sendError(Text.literal("Only players can use this command."));
                                return 0;
                            }

                            Vec3d playerPos = player.getPos();
                            Collection<ComponentEntry> all =
                                    SecondDawnRP.DEGRADATION_SERVICE.getAllComponents();

                            Optional<ComponentEntry> nearest = all.stream()
                                    .filter(e -> e.getWorldKey().equals(
                                            player.getWorld().getRegistryKey()
                                                    .getValue().toString()))
                                    .min(Comparator.comparingDouble(e -> {
                                        BlockPos pos = BlockPos.fromLong(e.getBlockPosLong());
                                        return playerPos.squaredDistanceTo(
                                                pos.getX(), pos.getY(), pos.getZ());
                                    }));

                            if (nearest.isEmpty()) {
                                source.sendError(Text.literal(
                                        "No registered components found in this dimension."));
                                return 0;
                            }

                            ComponentEntry entry = nearest.get();
                            Vec3d center = Vec3d.ofCenter(
                                    BlockPos.fromLong(entry.getBlockPosLong()));

                            ServerPlayNetworking.send(player,
                                    new LocateComponentS2CPacket(
                                            entry.getComponentId(),
                                            entry.getStatus(),
                                            center.x, center.y, center.z));

                            source.sendFeedback(() -> Text.literal(
                                            "Nearest component: '" + entry.getDisplayName()
                                                    + "' [" + entry.getStatus().name() + "] at "
                                                    + formatPos(entry.getBlockPosLong()) + ".")
                                    .formatted(Formatting.AQUA), false);
                            return 1;
                        }))

                // ── /engineering list ─────────────────────────────────────────
                .then(CommandManager.literal("list")
                        .executes(ctx -> {
                            ServerCommandSource source = ctx.getSource();
                            Collection<ComponentEntry> all =
                                    SecondDawnRP.DEGRADATION_SERVICE.getAllComponents();

                            if (all.isEmpty()) {
                                source.sendFeedback(() -> Text.literal(
                                                "No components registered.").formatted(Formatting.GRAY),
                                        false);
                                return 1;
                            }

                            source.sendFeedback(() -> Text.literal(
                                            "=== Registered Components (" + all.size() + ") ===")
                                    .formatted(Formatting.GOLD), false);

                            all.stream()
                                    .sorted(Comparator.comparing(ComponentEntry::getStatus)
                                            .reversed()
                                            .thenComparing(ComponentEntry::getDisplayName))
                                    .forEach(e -> {
                                        Formatting color = colorForStatus(e.getStatus());
                                        String missing = e.isMissingBlock() ? " [MISSING]" : "";
                                        source.sendFeedback(() -> Text.literal(
                                                        "  [" + e.getStatus().name() + "] "
                                                                + e.getDisplayName()
                                                                + " (" + e.getHealth() + "/100)"
                                                                + missing
                                                                + " — " + e.getComponentId())
                                                .formatted(color), false);
                                    });
                            return 1;
                        }))

                // ── /engineering status <componentId> ─────────────────────────
                .then(CommandManager.literal("status")
                        .then(CommandManager.argument("componentId", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerCommandSource source = ctx.getSource();
                                    String componentId = StringArgumentType.getString(
                                            ctx, "componentId");
                                    Optional<ComponentEntry> opt =
                                            SecondDawnRP.DEGRADATION_SERVICE.getById(componentId);

                                    if (opt.isEmpty()) {
                                        source.sendError(Text.literal(
                                                "No component found with ID '" + componentId + "'."));
                                        return 0;
                                    }

                                    ComponentEntry e = opt.get();
                                    Formatting color = colorForStatus(e.getStatus());
                                    source.sendFeedback(() -> Text.literal(
                                                    "Component: " + e.getDisplayName() + "\n"
                                                            + "  ID:      " + e.getComponentId() + "\n"
                                                            + "  Status:  " + e.getStatus().name() + "\n"
                                                            + "  Health:  " + e.getHealth() + "/100\n"
                                                            + "  Missing: " + e.isMissingBlock() + "\n"
                                                            + "  Ship:    " + (e.getShipId() != null
                                                            ? e.getShipId() : "unassigned") + "\n"
                                                            + "  Pos:     " + formatPos(e.getBlockPosLong())
                                                            + " in " + e.getWorldKey())
                                            .formatted(color), false);
                                    return 1;
                                })))

                // ── /engineering sethealth <componentId> <health> ─────────────
                .then(CommandManager.literal("sethealth")
                        .then(CommandManager.argument("componentId", StringArgumentType.word())
                                .then(CommandManager.argument("health",
                                                IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> {
                                            ServerCommandSource source = ctx.getSource();
                                            String componentId = StringArgumentType.getString(
                                                    ctx, "componentId");
                                            int health = IntegerArgumentType.getInteger(
                                                    ctx, "health");

                                            Optional<ComponentEntry> opt =
                                                    SecondDawnRP.DEGRADATION_SERVICE
                                                            .getById(componentId);
                                            if (opt.isEmpty()) {
                                                source.sendError(Text.literal(
                                                        "No component found with ID '"
                                                                + componentId + "'."));
                                                return 0;
                                            }

                                            ComponentEntry entry = opt.get();
                                            entry.setHealth(health);
                                            entry.normalizeState();
                                            SecondDawnRP.DEGRADATION_SERVICE.forceSave(entry);

                                            source.sendFeedback(() -> Text.literal(
                                                            "Set health of '" + entry.getDisplayName()
                                                                    + "' to " + health + "/100 ["
                                                                    + entry.getStatus().name() + "].")
                                                    .formatted(Formatting.GREEN), true);
                                            return 1;
                                        }))))

                // ── /engineering setrepair <componentId> <itemId> [count] ──────
                .then(CommandManager.literal("setrepair")
                        .then(CommandManager.argument("componentId", StringArgumentType.word())
                                .then(CommandManager.argument("itemId", StringArgumentType.word())
                                        .executes(ctx -> setRepairItem(ctx, 1))
                                        .then(CommandManager.argument("count",
                                                        IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> setRepairItem(ctx,
                                                        IntegerArgumentType.getInteger(
                                                                ctx, "count")))))))

                // ── /engineering register <displayName> ───────────────────────
                // Registers the block the player is looking at (or standing on).
                // Admin sneak+right-click with Component Registration Tool is the
                // primary workflow; this command is a fallback for automation.
                .then(CommandManager.literal("register")
                        .then(CommandManager.argument("displayName",
                                        StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    ServerCommandSource source = ctx.getSource();
                                    if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
                                        source.sendError(Text.literal(
                                                "Only players can use this command."));
                                        return 0;
                                    }

                                    String displayName = StringArgumentType.getString(
                                            ctx, "displayName");
                                    BlockPos pos = player.getBlockPos().down();
                                    String worldKey = player.getWorld().getRegistryKey()
                                            .getValue().toString();

                                    try {
                                        ComponentEntry entry =
                                                SecondDawnRP.DEGRADATION_SERVICE.register(
                                                        worldKey, pos.asLong(),
                                                        player.getWorld().getBlockState(pos)
                                                                .getBlock().toString(),
                                                        displayName, player.getUuid());
                                        source.sendFeedback(() -> Text.literal(
                                                        "Registered component '" + entry.getDisplayName()
                                                                + "' at " + formatPos(pos.asLong())
                                                                + " with ID: " + entry.getComponentId())
                                                .formatted(Formatting.GREEN), true);
                                        return 1;
                                    } catch (IllegalStateException ex) {
                                        source.sendError(Text.literal(ex.getMessage()));
                                        return 0;
                                    }
                                })))

                // ── /engineering remove <componentId> ─────────────────────────
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("componentId", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerCommandSource source = ctx.getSource();
                                    String componentId = StringArgumentType.getString(
                                            ctx, "componentId");

                                    Optional<ComponentEntry> opt =
                                            SecondDawnRP.DEGRADATION_SERVICE.getById(componentId);
                                    if (opt.isEmpty()) {
                                        source.sendError(Text.literal(
                                                "No component found with ID '" + componentId + "'."));
                                        return 0;
                                    }

                                    ComponentEntry entry = opt.get();
                                    SecondDawnRP.DEGRADATION_SERVICE.unregister(
                                            entry.getWorldKey(), entry.getBlockPosLong());
                                    source.sendFeedback(() -> Text.literal(
                                                    "Removed component '" + entry.getDisplayName()
                                                            + "' (" + componentId + ").")
                                            .formatted(Formatting.YELLOW), true);
                                    return 1;
                                })))

                // ── /engineering save ─────────────────────────────────────────
                .then(CommandManager.literal("save")
                        .executes(ctx -> {
                            SecondDawnRP.DEGRADATION_SERVICE.saveAll();
                            ctx.getSource().sendFeedback(() -> Text.literal(
                                            "Degradation component data saved.")
                                    .formatted(Formatting.GREEN), true);
                            return 1;
                        }))

                // ── /engineering degradation <disable|enable|status> ──────────
                .then(CommandManager.literal("degradation")
                        .then(CommandManager.literal("disable")
                                .executes(ctx -> {
                                    ServerCommandSource source = ctx.getSource();
                                    if (SecondDawnRP.DEGRADATION_SERVICE
                                            .isDegradationGloballyDisabled()) {
                                        source.sendFeedback(() -> Text.literal(
                                                        "Degradation is already disabled.")
                                                .formatted(Formatting.GRAY), false);
                                        return 0;
                                    }
                                    SecondDawnRP.DEGRADATION_SERVICE
                                            .setDegradationGloballyDisabled(true);
                                    source.sendFeedback(() -> Text.literal(
                                                    "⚠ Degradation DISABLED globally. "
                                                            + "Components will not degrade until re-enabled.")
                                            .formatted(Formatting.YELLOW), true);
                                    return 1;
                                }))
                        .then(CommandManager.literal("enable")
                                .executes(ctx -> {
                                    ServerCommandSource source = ctx.getSource();
                                    if (!SecondDawnRP.DEGRADATION_SERVICE
                                            .isDegradationGloballyDisabled()) {
                                        source.sendFeedback(() -> Text.literal(
                                                        "Degradation is already enabled.")
                                                .formatted(Formatting.GRAY), false);
                                        return 0;
                                    }
                                    SecondDawnRP.DEGRADATION_SERVICE
                                            .setDegradationGloballyDisabled(false);
                                    source.sendFeedback(() -> Text.literal(
                                                    "✔ Degradation ENABLED. "
                                                            + "All components will resume normal drain.")
                                            .formatted(Formatting.GREEN), true);
                                    return 1;
                                }))
                        .then(CommandManager.literal("status")
                                .executes(ctx -> {
                                    boolean disabled = SecondDawnRP.DEGRADATION_SERVICE
                                            .isDegradationGloballyDisabled();
                                    long total = SecondDawnRP.DEGRADATION_SERVICE
                                            .getAllComponents().size();
                                    long offline = SecondDawnRP.DEGRADATION_SERVICE
                                            .getAllComponents().stream()
                                            .filter(e -> e.getStatus() == ComponentStatus.OFFLINE)
                                            .count();
                                    long critical = SecondDawnRP.DEGRADATION_SERVICE
                                            .getAllComponents().stream()
                                            .filter(e -> e.getStatus() == ComponentStatus.CRITICAL)
                                            .count();
                                    long degraded = SecondDawnRP.DEGRADATION_SERVICE
                                            .getAllComponents().stream()
                                            .filter(e -> e.getStatus() == ComponentStatus.DEGRADED)
                                            .count();
                                    long missing = SecondDawnRP.DEGRADATION_SERVICE
                                            .getAllComponents().stream()
                                            .filter(ComponentEntry::isMissingBlock)
                                            .count();

                                    Formatting stateColor = disabled
                                            ? Formatting.YELLOW : Formatting.GREEN;
                                    String stateLabel = disabled ? "DISABLED" : "ENABLED";

                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                    "=== Degradation Status ===\n"
                                                            + "  State:    " + stateLabel + "\n"
                                                            + "  Total:    " + total + " components\n"
                                                            + "  Offline:  " + offline + "\n"
                                                            + "  Critical: " + critical + "\n"
                                                            + "  Degraded: " + degraded + "\n"
                                                            + "  Missing:  " + missing)
                                            .formatted(stateColor), false);
                                    return 1;
                                })))
        );
    }

    // ── Shared subcommand helpers ──────────────────────────────────────────────

    private static int setRepairItem(
            com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx,
            int count) {
        ServerCommandSource source = ctx.getSource();
        String componentId = StringArgumentType.getString(ctx, "componentId");
        String itemId = StringArgumentType.getString(ctx, "itemId");

        Optional<ComponentEntry> opt =
                SecondDawnRP.DEGRADATION_SERVICE.getById(componentId);
        if (opt.isEmpty()) {
            source.sendError(Text.literal(
                    "No component found with ID '" + componentId + "'."));
            return 0;
        }

        ComponentEntry entry = opt.get();
        entry.setRepairItemId(itemId);
        entry.setRepairItemCount(count);
        SecondDawnRP.DEGRADATION_SERVICE.forceSave(entry);

        final int finalCount = count;
        source.sendFeedback(() -> Text.literal(
                        "Repair item for '" + entry.getDisplayName()
                                + "' set to " + itemId + " x" + finalCount + ".")
                .formatted(Formatting.GREEN), true);
        return 1;
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    private static String formatPos(long blockPosLong) {
        BlockPos pos = BlockPos.fromLong(blockPosLong);
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static Formatting colorForStatus(ComponentStatus status) {
        return switch (status) {
            case NOMINAL  -> Formatting.GREEN;
            case DEGRADED -> Formatting.YELLOW;
            case CRITICAL -> Formatting.RED;
            case OFFLINE  -> Formatting.DARK_RED;
        };
    }
}