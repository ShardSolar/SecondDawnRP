package net.shard.seconddawnrp.gmevent.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.data.TriggerAction;
import net.shard.seconddawnrp.gmevent.data.TriggerActionType;
import net.shard.seconddawnrp.gmevent.data.TriggerEntry;

import java.util.ArrayList;

/**
 * /gm trigger commands.
 *
 * <pre>
 * /gm trigger list
 * /gm trigger arm <id>
 * /gm trigger disarm <id>
 * /gm trigger reset <id>         — reset FIRST_ENTRY flag
 * /gm trigger addaction <id> <type> <payload>
 * /gm trigger removeaction <id> <index>
 * /gm trigger info <id>
 * /gm trigger setradius <id> <blocks>
 * /gm trigger setcooldown <id> <ticks>
 * </pre>
 */
public final class GmTriggerCommands {

    private GmTriggerCommands() {}

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource>
            TRIGGER_ID = (ctx, builder) -> {
        SecondDawnRP.TRIGGER_SERVICE.getAll()
                .forEach(e -> builder.suggest(e.getEntryId(),
                        Text.literal((e.isArmed() ? "[ARMED] " : "[UNARMED] ") + e.getEntryId())));
        return builder.buildFuture();
    };

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource>
            ACTION_TYPES = (ctx, builder) -> {
        for (TriggerActionType t : TriggerActionType.values()) builder.suggest(t.name());
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess access,
                                CommandManager.RegistrationEnvironment env) {
        var gmTrigger = CommandManager.literal("trigger").requires(src -> isGM(src));

        gmTrigger.then(CommandManager.literal("list")
                .executes(ctx -> executeList(ctx.getSource())));

        gmTrigger.then(CommandManager.literal("arm")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(TRIGGER_ID)
                        .executes(ctx -> executeArm(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"), true))));

        gmTrigger.then(CommandManager.literal("disarm")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(TRIGGER_ID)
                        .executes(ctx -> executeArm(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"), false))));

        gmTrigger.then(CommandManager.literal("reset")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(TRIGGER_ID)
                        .executes(ctx -> executeReset(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        gmTrigger.then(CommandManager.literal("addaction")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(TRIGGER_ID)
                        .then(CommandManager.argument("type", StringArgumentType.word()).suggests(ACTION_TYPES)
                                .then(CommandManager.argument("payload", StringArgumentType.greedyString())
                                        .executes(ctx -> executeAddAction(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "type"),
                                                StringArgumentType.getString(ctx, "payload")))))));

        gmTrigger.then(CommandManager.literal("removeaction")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(TRIGGER_ID)
                        .then(CommandManager.argument("index", IntegerArgumentType.integer(0))
                                .executes(ctx -> executeRemoveAction(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "index"))))));

        gmTrigger.then(CommandManager.literal("setradius")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(TRIGGER_ID)
                        .then(CommandManager.argument("blocks", IntegerArgumentType.integer(1, 32))
                                .executes(ctx -> executeSetRadius(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "blocks"))))));

        gmTrigger.then(CommandManager.literal("setcooldown")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(TRIGGER_ID)
                        .then(CommandManager.argument("ticks", IntegerArgumentType.integer(0))
                                .executes(ctx -> executeSetCooldown(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "ticks"))))));

        gmTrigger.then(CommandManager.literal("info")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(TRIGGER_ID)
                        .executes(ctx -> executeInfo(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        dispatcher.register(CommandManager.literal("gm").then(gmTrigger));
    }

    private static int executeList(ServerCommandSource src) {
        var all = SecondDawnRP.TRIGGER_SERVICE.getAll();
        if (all.isEmpty()) {
            src.sendFeedback(() -> Text.literal("No trigger blocks registered.").formatted(Formatting.GRAY), false);
            return 1;
        }
        src.sendFeedback(() -> Text.literal("── Trigger Blocks ──").formatted(Formatting.YELLOW), false);
        all.forEach(e -> src.sendFeedback(() ->
                Text.literal("  " + e.getEntryId() + " — " + e.getTriggerMode()
                                + " | " + e.getFireMode()
                                + " | " + (e.isArmed() ? "ARMED" : "UNARMED"))
                        .formatted(e.isArmed() ? Formatting.GREEN : Formatting.RED), false));
        return 1;
    }

    private static int executeArm(ServerCommandSource src, String id, boolean arm) {
        if (!SecondDawnRP.TRIGGER_SERVICE.setArmed(id, arm)) {
            src.sendError(Text.literal("Unknown trigger id: " + id));
            return 0;
        }
        src.sendFeedback(() -> Text.literal("Trigger '" + id + "' " + (arm ? "armed." : "disarmed."))
                .formatted(arm ? Formatting.GREEN : Formatting.YELLOW), true);
        return 1;
    }

    private static int executeReset(ServerCommandSource src, String id) {
        if (!SecondDawnRP.TRIGGER_SERVICE.resetFirstEntry(id)) {
            src.sendError(Text.literal("Unknown trigger id: " + id));
            return 0;
        }
        src.sendFeedback(() -> Text.literal("First-entry flag reset for '" + id + "'.").formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int executeAddAction(ServerCommandSource src, String id, String typeStr, String payload) {
        var opt = SecondDawnRP.TRIGGER_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown id: " + id)); return 0; }
        TriggerActionType type;
        try { type = TriggerActionType.valueOf(typeStr.toUpperCase()); }
        catch (IllegalArgumentException e) { src.sendError(Text.literal("Unknown action type: " + typeStr)); return 0; }
        TriggerEntry entry = opt.get();
        var actions = new ArrayList<>(entry.getActions());
        actions.add(new TriggerAction(type, payload));
        entry.setActions(actions);
        SecondDawnRP.TRIGGER_SERVICE.saveEntry(entry);
        src.sendFeedback(() -> Text.literal("Added action " + type + " to '" + id + "'.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeRemoveAction(ServerCommandSource src, String id, int index) {
        var opt = SecondDawnRP.TRIGGER_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown id: " + id)); return 0; }
        TriggerEntry entry = opt.get();
        if (index >= entry.getActions().size()) {
            src.sendError(Text.literal("Index " + index + " out of range (0-" + (entry.getActions().size()-1) + ")."));
            return 0;
        }
        var actions = new ArrayList<>(entry.getActions());
        actions.remove(index);
        entry.setActions(actions);
        SecondDawnRP.TRIGGER_SERVICE.saveEntry(entry);
        src.sendFeedback(() -> Text.literal("Removed action at index " + index + ".").formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int executeSetRadius(ServerCommandSource src, String id, int blocks) {
        var opt = SecondDawnRP.TRIGGER_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown id: " + id)); return 0; }
        opt.get().setRadiusBlocks(blocks);
        SecondDawnRP.TRIGGER_SERVICE.saveEntry(opt.get());
        src.sendFeedback(() -> Text.literal("Radius set to " + blocks).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeSetCooldown(ServerCommandSource src, String id, int ticks) {
        var opt = SecondDawnRP.TRIGGER_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown id: " + id)); return 0; }
        opt.get().setCooldownTicks(ticks);
        SecondDawnRP.TRIGGER_SERVICE.saveEntry(opt.get());
        src.sendFeedback(() -> Text.literal("Cooldown set to " + ticks + " ticks.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeInfo(ServerCommandSource src, String id) {
        var opt = SecondDawnRP.TRIGGER_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown id: " + id)); return 0; }
        TriggerEntry e = opt.get();
        src.sendFeedback(() -> Text.literal("── Trigger: " + id + " ──").formatted(Formatting.YELLOW), false);
        src.sendFeedback(() -> Text.literal("Armed: " + e.isArmed() + " | Mode: " + e.getTriggerMode()
                + " | Fire: " + e.getFireMode()).formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("Radius: " + e.getRadiusBlocks()
                + " | Cooldown: " + e.getCooldownTicks() + " ticks").formatted(Formatting.GRAY), false);
        for (int i = 0; i < e.getActions().size(); i++) {
            TriggerAction a = e.getActions().get(i);
            final int idx = i;
            src.sendFeedback(() -> Text.literal("  [" + idx + "] " + a.getType() + ": " + a.getPayload())
                    .formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    private static boolean isGM(ServerCommandSource src) {
        var p = src.getPlayer();
        if (p == null) return src.hasPermissionLevel(2);
        return p.hasPermissionLevel(2) || SecondDawnRP.PERMISSION_SERVICE.hasPermission(p, "st.gm.use");
    }
}