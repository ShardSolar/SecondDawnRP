package net.shard.seconddawnrp.gmevent.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.data.AnomalyEntry;
import net.shard.seconddawnrp.gmevent.data.AnomalyType;
import net.shard.seconddawnrp.tactical.network.TacticalNetworking;

/**
 * GM commands for Anomaly Marker Blocks.
 *
 * <pre>
 * /gm anomaly list                          — list all registered markers
 * /gm anomaly contacts                      — list active session contacts
 * /gm anomaly activate <id>                 — activate contact + broadcast alert
 * /gm anomaly deactivate <id>               — deactivate contact
 * /gm anomaly setname <id> <name>           — set display name
 * /gm anomaly settype <id> <type>           — set anomaly type
 * /gm anomaly setdesc <id> <description>    — set narrative description
 * /gm anomaly info <id>                     — show full entry info
 * /gm anomaly remove <id>                   — remove registration
 * </pre>
 */
public final class GmAnomalyCommands {

    private GmAnomalyCommands() {}

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource>
            ANOMALY_ID = (ctx, builder) -> {
        SecondDawnRP.ANOMALY_SERVICE.getAll()
                .forEach(e -> builder.suggest(e.getEntryId(),
                        Text.literal((e.isActive() ? "[ACTIVE] " : "[INACTIVE] ")
                                + e.getName() + " — " + e.getType().getDisplayName())));
        return builder.buildFuture();
    };

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource>
            ANOMALY_TYPE = (ctx, builder) -> {
        for (AnomalyType t : AnomalyType.values())
            builder.suggest(t.name(), Text.literal(t.getDisplayName()));
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess access,
                                CommandManager.RegistrationEnvironment env) {
        var gmAnomaly = CommandManager.literal("anomaly").requires(src -> isGM(src));

        gmAnomaly.then(CommandManager.literal("list")
                .executes(ctx -> executeList(ctx.getSource())));

        gmAnomaly.then(CommandManager.literal("contacts")
                .executes(ctx -> executeContacts(ctx.getSource())));

        gmAnomaly.then(CommandManager.literal("activate")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(ANOMALY_ID)
                        .executes(ctx -> executeActivate(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        gmAnomaly.then(CommandManager.literal("deactivate")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(ANOMALY_ID)
                        .executes(ctx -> executeDeactivate(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        gmAnomaly.then(CommandManager.literal("setname")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(ANOMALY_ID)
                        .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeSetName(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        StringArgumentType.getString(ctx, "name"))))));

        gmAnomaly.then(CommandManager.literal("settype")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(ANOMALY_ID)
                        .then(CommandManager.argument("type", StringArgumentType.word())
                                .suggests(ANOMALY_TYPE)
                                .executes(ctx -> executeSetType(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        StringArgumentType.getString(ctx, "type"))))));

        gmAnomaly.then(CommandManager.literal("setdesc")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(ANOMALY_ID)
                        .then(CommandManager.argument("description", StringArgumentType.greedyString())
                                .executes(ctx -> executeSetDesc(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        StringArgumentType.getString(ctx, "description"))))));

        gmAnomaly.then(CommandManager.literal("info")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(ANOMALY_ID)
                        .executes(ctx -> executeInfo(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        gmAnomaly.then(CommandManager.literal("remove")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(ANOMALY_ID)
                        .executes(ctx -> executeRemove(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        dispatcher.register(CommandManager.literal("gm").then(gmAnomaly));
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private static int executeList(ServerCommandSource src) {
        var all = SecondDawnRP.ANOMALY_SERVICE.getAll();
        if (all.isEmpty()) {
            src.sendFeedback(() -> Text.literal("No anomaly markers registered.")
                    .formatted(Formatting.GRAY), false);
            return 1;
        }
        src.sendFeedback(() -> Text.literal("── Anomaly Markers ──")
                .formatted(Formatting.LIGHT_PURPLE), false);
        all.forEach(e -> src.sendFeedback(() ->
                        Text.literal("  " + e.getEntryId() + " — " + e.getName()
                                        + " [" + e.getType().getDisplayName() + "] "
                                        + (e.isActive() ? "ACTIVE" : "inactive"))
                                .formatted(e.isActive()
                                        ? (e.getType().isCriticalAlert() ? Formatting.RED : Formatting.YELLOW)
                                        : Formatting.GRAY),
                false));
        return 1;
    }

    private static int executeContacts(ServerCommandSource src) {
        var active = SecondDawnRP.ANOMALY_SERVICE.getActiveContacts();
        if (active.isEmpty()) {
            src.sendFeedback(() -> Text.literal("No active anomaly contacts this session.")
                    .formatted(Formatting.GRAY), false);
            return 1;
        }
        src.sendFeedback(() -> Text.literal("── Active Sensor Contacts ──")
                .formatted(Formatting.LIGHT_PURPLE), false);
        active.forEach(e -> src.sendFeedback(() ->
                        Text.literal("  " + e.getName() + " — " + e.getType().getDisplayName()
                                        + " [" + e.getEntryId() + "]")
                                .formatted(e.getType().isCriticalAlert() ? Formatting.RED : Formatting.YELLOW),
                false));
        return 1;
    }

    private static int executeActivate(ServerCommandSource src, String id) {
        var opt = SecondDawnRP.ANOMALY_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown anomaly id: " + id)); return 0; }
        if (opt.get().isActive()) {
            src.sendFeedback(() -> Text.literal("'" + opt.get().getName()
                    + "' is already active.").formatted(Formatting.YELLOW), false);
            return 1;
        }
        SecondDawnRP.ANOMALY_SERVICE.activate(id, src.getServer());

        // Push live update to all open tactical screens
        TacticalNetworking.pushAnomalyUpdate(src.getServer(), opt.get().getWorldKey());

        src.sendFeedback(() ->
                Text.literal("Anomaly contact activated: " + opt.get().getName())
                        .formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int executeDeactivate(ServerCommandSource src, String id) {
        var opt = SecondDawnRP.ANOMALY_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown anomaly id: " + id)); return 0; }
        SecondDawnRP.ANOMALY_SERVICE.deactivate(id);

        // Push live update to all open tactical screens
        TacticalNetworking.pushAnomalyUpdate(src.getServer(), opt.get().getWorldKey());

        src.sendFeedback(() ->
                Text.literal("Anomaly contact deactivated: " + opt.get().getName())
                        .formatted(Formatting.YELLOW), true);
        return 1;
    }

    private static int executeSetName(ServerCommandSource src, String id, String name) {
        var opt = SecondDawnRP.ANOMALY_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown anomaly id: " + id)); return 0; }
        opt.get().setName(name);
        SecondDawnRP.ANOMALY_SERVICE.saveEntry(opt.get());
        src.sendFeedback(() -> Text.literal("Name set to '" + name + "'.")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeSetType(ServerCommandSource src, String id, String typeName) {
        var opt = SecondDawnRP.ANOMALY_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown anomaly id: " + id)); return 0; }
        AnomalyType type;
        try { type = AnomalyType.valueOf(typeName.toUpperCase()); }
        catch (IllegalArgumentException e) {
            src.sendError(Text.literal("Unknown type: " + typeName
                    + ". Valid: ENERGY, BIOLOGICAL, GRAVITATIONAL, UNKNOWN"));
            return 0;
        }
        opt.get().setType(type);
        SecondDawnRP.ANOMALY_SERVICE.saveEntry(opt.get());
        src.sendFeedback(() -> Text.literal("Type set to " + type.getDisplayName())
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeSetDesc(ServerCommandSource src, String id, String desc) {
        var opt = SecondDawnRP.ANOMALY_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown anomaly id: " + id)); return 0; }
        opt.get().setDescription(desc);
        SecondDawnRP.ANOMALY_SERVICE.saveEntry(opt.get());
        src.sendFeedback(() -> Text.literal("Description set.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeInfo(ServerCommandSource src, String id) {
        var opt = SecondDawnRP.ANOMALY_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown anomaly id: " + id)); return 0; }
        AnomalyEntry e = opt.get();
        src.sendFeedback(() -> Text.literal("── Anomaly: " + e.getEntryId() + " ──")
                .formatted(Formatting.LIGHT_PURPLE), false);
        src.sendFeedback(() -> Text.literal("Name: " + e.getName()).formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("Type: " + e.getType().getDisplayName()
                        + " [" + (e.getType().isCriticalAlert() ? "RED" : "YELLOW") + "]")
                .formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("Active: " + e.isActive()).formatted(Formatting.GRAY), false);
        if (!e.getDescription().isBlank())
            src.sendFeedback(() -> Text.literal("Desc: " + e.getDescription())
                    .formatted(Formatting.DARK_GRAY), false);
        return 1;
    }

    private static int executeRemove(ServerCommandSource src, String id) {
        var opt = SecondDawnRP.ANOMALY_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown anomaly id: " + id)); return 0; }
        String name = opt.get().getName();
        SecondDawnRP.ANOMALY_SERVICE.unregister(opt.get().getWorldKey(),
                opt.get().getBlockPosLong());
        src.sendFeedback(() -> Text.literal("Anomaly marker '" + name + "' removed.")
                .formatted(Formatting.YELLOW), true);
        return 1;
    }

    private static boolean isGM(ServerCommandSource src) {
        var p = src.getPlayer();
        if (p == null) return src.hasPermissionLevel(2);
        return p.hasPermissionLevel(2)
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(p, "st.gm.use");
    }
}