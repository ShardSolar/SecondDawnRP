package net.shard.seconddawnrp.degradation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
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
import net.shard.seconddawnrp.degradation.item.ComponentRegistrationTool;
import net.shard.seconddawnrp.degradation.network.LocateComponentS2CPacket;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class EngineeringCommands {

    private EngineeringCommands() {}

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_SHIPS =
            (ctx, builder) -> {
                if (SecondDawnRP.ENCOUNTER_SERVICE != null)
                    SecondDawnRP.ENCOUNTER_SERVICE.getShipRegistry().keySet().forEach(builder::suggest);
                builder.suggest("clear");
                return builder.buildFuture();
            };

    public static void register(
            CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess,
            CommandManager.RegistrationEnvironment environment
    ) {
        dispatcher.register(CommandManager.literal("engineering")
                .requires(source -> source.hasPermissionLevel(2))

                // /engineering locate <componentId>
                .then(CommandManager.literal("locate")
                        .then(CommandManager.argument("componentId", StringArgumentType.word())
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) { ctx.getSource().sendError(Text.literal("Only players can use this.")); return 0; }
                                    String id = StringArgumentType.getString(ctx, "componentId");
                                    Optional<ComponentEntry> opt = SecondDawnRP.DEGRADATION_SERVICE.getById(id);
                                    if (opt.isEmpty()) { ctx.getSource().sendError(Text.literal("No component found with ID '" + id + "'.")); return 0; }
                                    ComponentEntry e = opt.get();
                                    Vec3d c = Vec3d.ofCenter(BlockPos.fromLong(e.getBlockPosLong()));
                                    ServerPlayNetworking.send(player, new LocateComponentS2CPacket(e.getComponentId(), e.getStatus(), c.x, c.y, c.z));
                                    ctx.getSource().sendFeedback(() -> Text.literal("Locator sent for '" + e.getDisplayName() + "' [" + e.getStatus().name() + "] at " + formatPos(e.getBlockPosLong()) + ".").formatted(Formatting.AQUA), false);
                                    return 1;
                                })))

                // /engineering locatenearest
                .then(CommandManager.literal("locatenearest")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) { ctx.getSource().sendError(Text.literal("Only players can use this.")); return 0; }
                            Vec3d pp = player.getPos();
                            Optional<ComponentEntry> nearest = SecondDawnRP.DEGRADATION_SERVICE.getAllComponents().stream()
                                    .filter(e -> e.getWorldKey().equals(player.getWorld().getRegistryKey().getValue().toString()))
                                    .min(Comparator.comparingDouble(e -> { BlockPos p = BlockPos.fromLong(e.getBlockPosLong()); return pp.squaredDistanceTo(p.getX(), p.getY(), p.getZ()); }));
                            if (nearest.isEmpty()) { ctx.getSource().sendError(Text.literal("No registered components found in this dimension.")); return 0; }
                            ComponentEntry e = nearest.get();
                            Vec3d c = Vec3d.ofCenter(BlockPos.fromLong(e.getBlockPosLong()));
                            ServerPlayNetworking.send(player, new LocateComponentS2CPacket(e.getComponentId(), e.getStatus(), c.x, c.y, c.z));
                            ctx.getSource().sendFeedback(() -> Text.literal("Nearest: '" + e.getDisplayName() + "' [" + e.getStatus().name() + "] at " + formatPos(e.getBlockPosLong()) + ".").formatted(Formatting.AQUA), false);
                            return 1;
                        }))

                // /engineering list
                .then(CommandManager.literal("list")
                        .executes(ctx -> {
                            Collection<ComponentEntry> all = SecondDawnRP.DEGRADATION_SERVICE.getAllComponents();
                            if (all.isEmpty()) { ctx.getSource().sendFeedback(() -> Text.literal("No components registered.").formatted(Formatting.GRAY), false); return 1; }
                            ctx.getSource().sendFeedback(() -> Text.literal("=== Registered Components (" + all.size() + ") ===").formatted(Formatting.GOLD), false);
                            all.stream().sorted(Comparator.comparing(ComponentEntry::getStatus).reversed().thenComparing(ComponentEntry::getDisplayName)).forEach(e -> {
                                String ship = e.getShipId() != null ? " §8[" + e.getShipId() + "]" : "";
                                String miss = e.isMissingBlock() ? " [MISSING]" : "";
                                ctx.getSource().sendFeedback(() -> Text.literal("  [" + e.getStatus().name() + "] " + e.getDisplayName() + " (" + e.getHealth() + "/100)" + miss + ship + " — " + e.getComponentId()).formatted(colorForStatus(e.getStatus())), false);
                            });
                            return 1;
                        }))

                // /engineering status <componentId>
                .then(CommandManager.literal("status")
                        .then(CommandManager.argument("componentId", StringArgumentType.word())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "componentId");
                                    Optional<ComponentEntry> opt = SecondDawnRP.DEGRADATION_SERVICE.getById(id);
                                    if (opt.isEmpty()) { ctx.getSource().sendError(Text.literal("No component found with ID '" + id + "'.")); return 0; }
                                    ComponentEntry e = opt.get();
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                    "Component: " + e.getDisplayName() + "\n"
                                                            + "  ID:      " + e.getComponentId() + "\n"
                                                            + "  Status:  " + e.getStatus().name() + "\n"
                                                            + "  Health:  " + e.getHealth() + "/100\n"
                                                            + "  Missing: " + e.isMissingBlock() + "\n"
                                                            + "  Ship:    " + (e.getShipId() != null ? e.getShipId() : "unassigned") + "\n"
                                                            + "  Pos:     " + formatPos(e.getBlockPosLong()) + " in " + e.getWorldKey())
                                            .formatted(colorForStatus(e.getStatus())), false);
                                    return 1;
                                })))

                // /engineering sethealth <componentId> <health>
                .then(CommandManager.literal("sethealth")
                        .then(CommandManager.argument("componentId", StringArgumentType.word())
                                .then(CommandManager.argument("health", IntegerArgumentType.integer(0, 100))
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "componentId");
                                            int health = IntegerArgumentType.getInteger(ctx, "health");
                                            Optional<ComponentEntry> opt = SecondDawnRP.DEGRADATION_SERVICE.getById(id);
                                            if (opt.isEmpty()) { ctx.getSource().sendError(Text.literal("No component found with ID '" + id + "'.")); return 0; }
                                            ComponentEntry entry = opt.get();
                                            entry.setHealth(health); entry.normalizeState();
                                            SecondDawnRP.DEGRADATION_SERVICE.forceSave(entry);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Set health of '" + entry.getDisplayName() + "' to " + health + "/100 [" + entry.getStatus().name() + "].").formatted(Formatting.GREEN), true);
                                            return 1;
                                        }))))

                // /engineering setrepair <componentId> <itemId> [count]
                .then(CommandManager.literal("setrepair")
                        .then(CommandManager.argument("componentId", StringArgumentType.word())
                                .then(CommandManager.argument("itemId", StringArgumentType.word())
                                        .executes(ctx -> setRepairItem(ctx, 1))
                                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> setRepairItem(ctx, IntegerArgumentType.getInteger(ctx, "count")))))))

                // /engineering register <displayName>
                .then(CommandManager.literal("register")
                        .then(CommandManager.argument("displayName", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) { ctx.getSource().sendError(Text.literal("Only players can use this.")); return 0; }
                                    String displayName = StringArgumentType.getString(ctx, "displayName");
                                    BlockPos pos = player.getBlockPos().down();
                                    String worldKey = player.getWorld().getRegistryKey().getValue().toString();
                                    try {
                                        ComponentEntry entry = SecondDawnRP.DEGRADATION_SERVICE.register(worldKey, pos.asLong(), player.getWorld().getBlockState(pos).getBlock().toString(), displayName, player.getUuid());
                                        ctx.getSource().sendFeedback(() -> Text.literal("Registered '" + entry.getDisplayName() + "' at " + formatPos(pos.asLong()) + " ID: " + entry.getComponentId()).formatted(Formatting.GREEN), true);
                                        return 1;
                                    } catch (IllegalStateException ex) { ctx.getSource().sendError(Text.literal(ex.getMessage())); return 0; }
                                })))

                // /engineering remove <componentId>
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("componentId", StringArgumentType.word())
                                .executes(ctx -> {
                                    String id = StringArgumentType.getString(ctx, "componentId");
                                    Optional<ComponentEntry> opt = SecondDawnRP.DEGRADATION_SERVICE.getById(id);
                                    if (opt.isEmpty()) { ctx.getSource().sendError(Text.literal("No component found with ID '" + id + "'.")); return 0; }
                                    ComponentEntry entry = opt.get();
                                    SecondDawnRP.DEGRADATION_SERVICE.unregister(entry.getWorldKey(), entry.getBlockPosLong());
                                    ctx.getSource().sendFeedback(() -> Text.literal("Removed '" + entry.getDisplayName() + "' (" + id + ").").formatted(Formatting.YELLOW), true);
                                    return 1;
                                })))

                // /engineering save
                .then(CommandManager.literal("save")
                        .executes(ctx -> {
                            SecondDawnRP.DEGRADATION_SERVICE.saveAll();
                            ctx.getSource().sendFeedback(() -> Text.literal("Degradation component data saved.").formatted(Formatting.GREEN), true);
                            return 1;
                        }))

                // /engineering degradation disable|enable|status
                .then(CommandManager.literal("degradation")
                        .then(CommandManager.literal("disable")
                                .executes(ctx -> {
                                    if (SecondDawnRP.DEGRADATION_SERVICE.isDegradationGloballyDisabled()) { ctx.getSource().sendFeedback(() -> Text.literal("Degradation is already disabled.").formatted(Formatting.GRAY), false); return 0; }
                                    SecondDawnRP.DEGRADATION_SERVICE.setDegradationGloballyDisabled(true);
                                    ctx.getSource().sendFeedback(() -> Text.literal("⚠ Degradation DISABLED globally.").formatted(Formatting.YELLOW), true);
                                    return 1;
                                }))
                        .then(CommandManager.literal("enable")
                                .executes(ctx -> {
                                    if (!SecondDawnRP.DEGRADATION_SERVICE.isDegradationGloballyDisabled()) { ctx.getSource().sendFeedback(() -> Text.literal("Degradation is already enabled.").formatted(Formatting.GRAY), false); return 0; }
                                    SecondDawnRP.DEGRADATION_SERVICE.setDegradationGloballyDisabled(false);
                                    ctx.getSource().sendFeedback(() -> Text.literal("✔ Degradation ENABLED.").formatted(Formatting.GREEN), true);
                                    return 1;
                                }))
                        .then(CommandManager.literal("status")
                                .executes(ctx -> {
                                    boolean dis = SecondDawnRP.DEGRADATION_SERVICE.isDegradationGloballyDisabled();
                                    long total = SecondDawnRP.DEGRADATION_SERVICE.getAllComponents().size();
                                    long offline  = SecondDawnRP.DEGRADATION_SERVICE.getAllComponents().stream().filter(e -> e.getStatus() == ComponentStatus.OFFLINE).count();
                                    long critical = SecondDawnRP.DEGRADATION_SERVICE.getAllComponents().stream().filter(e -> e.getStatus() == ComponentStatus.CRITICAL).count();
                                    long degraded = SecondDawnRP.DEGRADATION_SERVICE.getAllComponents().stream().filter(e -> e.getStatus() == ComponentStatus.DEGRADED).count();
                                    long missing  = SecondDawnRP.DEGRADATION_SERVICE.getAllComponents().stream().filter(ComponentEntry::isMissingBlock).count();
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                    "=== Degradation Status ===\n"
                                                            + "  State:    " + (dis ? "DISABLED" : "ENABLED") + "\n"
                                                            + "  Total:    " + total + "\n"
                                                            + "  Offline:  " + offline + "\n"
                                                            + "  Critical: " + critical + "\n"
                                                            + "  Degraded: " + degraded + "\n"
                                                            + "  Missing:  " + missing)
                                            .formatted(dis ? Formatting.YELLOW : Formatting.GREEN), false);
                                    return 1;
                                })))

                // /engineering ship settarget|assign|assignall|unassign|list
                .then(CommandManager.literal("ship")

                        // /engineering ship settarget <shipId|clear>
                        // Sets ship context on Component Registration Tool in main hand.
                        .then(CommandManager.literal("settarget")
                                .then(CommandManager.argument("shipId", StringArgumentType.word())
                                        .suggests(SUGGEST_SHIPS)
                                        .executes(ctx -> {
                                            if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) { ctx.getSource().sendError(Text.literal("Only players can use this.")); return 0; }
                                            String shipId = StringArgumentType.getString(ctx, "shipId");
                                            boolean clearing = shipId.equalsIgnoreCase("clear");
                                            if (!clearing && SecondDawnRP.ENCOUNTER_SERVICE != null
                                                    && !SecondDawnRP.ENCOUNTER_SERVICE.getShipRegistry().containsKey(shipId)) {
                                                ctx.getSource().sendError(Text.literal("Ship '" + shipId + "' not found in registry."));
                                                return 0;
                                            }
                                            ComponentRegistrationTool.setShipContext(player, clearing ? null : shipId);
                                            return 1;
                                        })))

                        // /engineering ship assign <componentId> <shipId>
                        // Assign a single existing component to a ship.
                        .then(CommandManager.literal("assign")
                                .then(CommandManager.argument("componentId", StringArgumentType.word())
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .suggests(SUGGEST_SHIPS)
                                                .executes(ctx -> {
                                                    String id = StringArgumentType.getString(ctx, "componentId");
                                                    String shipId = StringArgumentType.getString(ctx, "shipId");
                                                    Optional<ComponentEntry> opt = SecondDawnRP.DEGRADATION_SERVICE.getById(id);
                                                    if (opt.isEmpty()) { ctx.getSource().sendError(Text.literal("No component found with ID '" + id + "'.")); return 0; }
                                                    ComponentEntry entry = opt.get();
                                                    entry.setShipId(shipId);
                                                    SecondDawnRP.DEGRADATION_SERVICE.forceSave(entry);
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Component '" + entry.getDisplayName() + "' assigned to ship '" + shipId + "'.").formatted(Formatting.GREEN), true);
                                                    return 1;
                                                }))))

                        // /engineering ship assignall <worldKey> <shipId>
                        // Assign ALL currently unassigned components in a world to a ship.
                        // Primary migration tool for components registered before V15.
                        // Will NOT overwrite components already assigned to another ship.
                        .then(CommandManager.literal("assignall")
                                .then(CommandManager.argument("worldKey", StringArgumentType.word())
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .suggests(SUGGEST_SHIPS)
                                                .executes(ctx -> {
                                                    String worldKey = StringArgumentType.getString(ctx, "worldKey");
                                                    String shipId = StringArgumentType.getString(ctx, "shipId");
                                                    if (SecondDawnRP.ENCOUNTER_SERVICE != null
                                                            && !SecondDawnRP.ENCOUNTER_SERVICE.getShipRegistry().containsKey(shipId)) {
                                                        ctx.getSource().sendError(Text.literal("Ship '" + shipId + "' not found in registry."));
                                                        return 0;
                                                    }
                                                    // Accept shorthand e.g. "overworld" → "minecraft:overworld"
                                                    String key = worldKey.contains(":") ? worldKey : "minecraft:" + worldKey;
                                                    List<ComponentEntry> unassigned = SecondDawnRP.DEGRADATION_SERVICE.getAllComponents().stream()
                                                            .filter(e -> e.getShipId() == null && e.getWorldKey().equals(key))
                                                            .toList();
                                                    if (unassigned.isEmpty()) {
                                                        ctx.getSource().sendFeedback(() -> Text.literal("No unassigned components found in world '" + key + "'.").formatted(Formatting.GRAY), false);
                                                        return 0;
                                                    }
                                                    for (ComponentEntry entry : unassigned) { entry.setShipId(shipId); SecondDawnRP.DEGRADATION_SERVICE.forceSave(entry); }
                                                    final int count = unassigned.size();
                                                    ctx.getSource().sendFeedback(() -> Text.literal("Assigned " + count + " component(s) in '" + key + "' to ship '" + shipId + "'.").formatted(Formatting.GREEN), true);
                                                    return count;
                                                }))))

                        // /engineering ship unassign <componentId>
                        .then(CommandManager.literal("unassign")
                                .then(CommandManager.argument("componentId", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "componentId");
                                            Optional<ComponentEntry> opt = SecondDawnRP.DEGRADATION_SERVICE.getById(id);
                                            if (opt.isEmpty()) { ctx.getSource().sendError(Text.literal("No component found with ID '" + id + "'.")); return 0; }
                                            ComponentEntry entry = opt.get();
                                            entry.setShipId(null);
                                            SecondDawnRP.DEGRADATION_SERVICE.forceSave(entry);
                                            ctx.getSource().sendFeedback(() -> Text.literal("Ship binding removed from '" + entry.getDisplayName() + "'.").formatted(Formatting.YELLOW), true);
                                            return 1;
                                        })))

                        // /engineering ship list [shipId]
                        .then(CommandManager.literal("list")
                                .executes(ctx -> {
                                    Collection<ComponentEntry> all = SecondDawnRP.DEGRADATION_SERVICE.getAllComponents();
                                    long bound = all.stream().filter(ComponentEntry::hasShipBinding).count();
                                    ctx.getSource().sendFeedback(() -> Text.literal("Components: " + all.size() + " total | " + bound + " ship-bound | " + (all.size() - bound) + " unassigned").formatted(Formatting.GOLD), false);
                                    return 1;
                                })
                                .then(CommandManager.argument("shipId", StringArgumentType.word())
                                        .suggests(SUGGEST_SHIPS)
                                        .executes(ctx -> {
                                            String shipId = StringArgumentType.getString(ctx, "shipId");
                                            List<ComponentEntry> components = SecondDawnRP.DEGRADATION_SERVICE.getComponentsForShip(shipId);
                                            if (components.isEmpty()) { ctx.getSource().sendFeedback(() -> Text.literal("No components assigned to ship '" + shipId + "'.").formatted(Formatting.GRAY), false); return 0; }
                                            ctx.getSource().sendFeedback(() -> Text.literal("── Components on " + shipId + " (" + components.size() + ") ──").formatted(Formatting.AQUA), false);
                                            components.stream().sorted(Comparator.comparing(ComponentEntry::getDisplayName)).forEach(e ->
                                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                                    "  [" + e.getStatus().name() + "] " + e.getDisplayName()
                                                                            + " (" + e.getHealth() + "/100)"
                                                                            + (e.isMissingBlock() ? " [MISSING]" : "")
                                                                            + " — " + e.getComponentId())
                                                            .formatted(colorForStatus(e.getStatus())), false));
                                            return components.size();
                                        })))
                ) // end /engineering ship
        );
    }

    private static int setRepairItem(
            com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx, int count) {
        String id = StringArgumentType.getString(ctx, "componentId");
        String itemId = StringArgumentType.getString(ctx, "itemId");
        Optional<ComponentEntry> opt = SecondDawnRP.DEGRADATION_SERVICE.getById(id);
        if (opt.isEmpty()) { ctx.getSource().sendError(Text.literal("No component found with ID '" + id + "'.")); return 0; }
        ComponentEntry entry = opt.get();
        entry.setRepairItemId(itemId); entry.setRepairItemCount(count);
        SecondDawnRP.DEGRADATION_SERVICE.forceSave(entry);
        final int fc = count;
        ctx.getSource().sendFeedback(() -> Text.literal("Repair item for '" + entry.getDisplayName() + "' set to " + itemId + " x" + fc + ".").formatted(Formatting.GREEN), true);
        return 1;
    }

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