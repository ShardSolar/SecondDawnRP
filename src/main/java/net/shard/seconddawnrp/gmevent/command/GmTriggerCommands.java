package net.shard.seconddawnrp.gmevent.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.division.Division;
import net.shard.seconddawnrp.gmevent.data.EnvironmentalEffectEntry;
import net.shard.seconddawnrp.gmevent.data.TriggerAction;
import net.shard.seconddawnrp.gmevent.data.TriggerActionType;
import net.shard.seconddawnrp.gmevent.data.TriggerEntry;
import net.shard.seconddawnrp.medical.MedicalConditionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

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
 * /gm trigger help
 * </pre>
 */
public final class GmTriggerCommands {

    private GmTriggerCommands() {}

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource>
            TRIGGER_ID = (ctx, builder) -> {
        SecondDawnRP.TRIGGER_SERVICE.getAll()
                .forEach(e -> builder.suggest(
                        e.getEntryId(),
                        Text.literal((e.isArmed() ? "[ARMED] " : "[UNARMED] ") + e.getEntryId())
                ));
        return builder.buildFuture();
    };

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource>
            ACTION_TYPES = (ctx, builder) -> {
        for (TriggerActionType t : TriggerActionType.values()) {
            builder.suggest(t.name());
        }
        return builder.buildFuture();
    };

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource>
            ACTION_PAYLOADS = (ctx, builder) -> {
        String typeStr;
        try {
            typeStr = StringArgumentType.getString(ctx, "type");
        } catch (Exception e) {
            return builder.buildFuture();
        }

        TriggerActionType type;
        try {
            type = TriggerActionType.valueOf(typeStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return builder.buildFuture();
        }

        return switch (type) {
            case BROADCAST -> suggestBroadcastPayloads(builder);
            case ACTIVATE_LINKED, DEACTIVATE_LINKED, TOGGLE_LINKED -> suggestLinkedPayloads(builder);
            case GENERATE_TASK -> suggestGenerateTaskPayloads(builder);
            case NOTIFY_GM -> suggestNotifyPayloads(builder);
            case PLAY_SOUND -> suggestPlaySoundPayloads(builder);
            case APPLY_MEDICAL_CONDITION -> suggestMedicalConditionPayloads(builder);
        };
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess access,
                                CommandManager.RegistrationEnvironment env) {
        var gmTrigger = CommandManager.literal("trigger").requires(GmTriggerCommands::isGM);

        gmTrigger.then(CommandManager.literal("list")
                .executes(ctx -> executeList(ctx.getSource())));

        gmTrigger.then(CommandManager.literal("arm")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(TRIGGER_ID)
                        .executes(ctx -> executeArm(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"),
                                true
                        ))));

        gmTrigger.then(CommandManager.literal("disarm")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(TRIGGER_ID)
                        .executes(ctx -> executeArm(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "id"),
                                false
                        ))));

        gmTrigger.then(CommandManager.literal("reset")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(TRIGGER_ID)
                        .executes(ctx -> executeReset(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")
                        ))));

        gmTrigger.then(CommandManager.literal("addaction")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(TRIGGER_ID)
                        .then(CommandManager.argument("type", StringArgumentType.word()).suggests(ACTION_TYPES)
                                .then(CommandManager.argument("payload", StringArgumentType.greedyString())
                                        .suggests(ACTION_PAYLOADS)
                                        .executes(ctx -> executeAddAction(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "type"),
                                                StringArgumentType.getString(ctx, "payload")
                                        ))))));

        gmTrigger.then(CommandManager.literal("removeaction")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(TRIGGER_ID)
                        .then(CommandManager.argument("index", IntegerArgumentType.integer(0))
                                .executes(ctx -> executeRemoveAction(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "index")
                                )))));

        gmTrigger.then(CommandManager.literal("setradius")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(TRIGGER_ID)
                        .then(CommandManager.argument("blocks", IntegerArgumentType.integer(1, 32))
                                .executes(ctx -> executeSetRadius(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "blocks")
                                )))));

        gmTrigger.then(CommandManager.literal("setcooldown")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(TRIGGER_ID)
                        .then(CommandManager.argument("ticks", IntegerArgumentType.integer(0))
                                .executes(ctx -> executeSetCooldown(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "ticks")
                                )))));

        gmTrigger.then(CommandManager.literal("info")
                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(TRIGGER_ID)
                        .executes(ctx -> executeInfo(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")
                        ))));

        gmTrigger.then(CommandManager.literal("help")
                .executes(ctx -> executeHelp(ctx.getSource())));

        dispatcher.register(CommandManager.literal("gm").then(gmTrigger));
    }

    private static int executeList(ServerCommandSource src) {
        var all = SecondDawnRP.TRIGGER_SERVICE.getAll();
        if (all.isEmpty()) {
            src.sendFeedback(() -> Text.literal("No trigger blocks registered.")
                    .formatted(Formatting.GRAY), false);
            return 1;
        }

        src.sendFeedback(() -> Text.literal("── Trigger Blocks ──")
                .formatted(Formatting.YELLOW), false);

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

        src.sendFeedback(() -> Text.literal("First-entry flag reset for '" + id + "'.")
                .formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int executeAddAction(ServerCommandSource src, String id, String typeStr, String payload) {
        var opt = SecondDawnRP.TRIGGER_SERVICE.getById(id);
        if (opt.isEmpty()) {
            src.sendError(Text.literal("Unknown id: " + id));
            return 0;
        }

        TriggerActionType type;
        try {
            type = TriggerActionType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            src.sendError(Text.literal("Unknown action type: " + typeStr)
                    .formatted(Formatting.RED));
            src.sendFeedback(() -> Text.literal("Run /gm trigger help for valid types and payload formats.")
                    .formatted(Formatting.GRAY), false);
            return 0;
        }

        if (payload == null || payload.isBlank()) {
            src.sendError(Text.literal("Missing payload for action type: " + typeStr)
                    .formatted(Formatting.RED));
            src.sendFeedback(() -> Text.literal("Run /gm trigger help for usage examples.")
                    .formatted(Formatting.GRAY), false);
            return 0;
        }

        String validationError = validatePayload(type, payload);
        if (validationError != null) {
            src.sendError(Text.literal(validationError).formatted(Formatting.RED));
            src.sendFeedback(() -> Text.literal("Run /gm trigger help for payload examples.")
                    .formatted(Formatting.GRAY), false);
            return 0;
        }

        TriggerEntry entry = opt.get();
        var actions = new ArrayList<>(entry.getActions());
        actions.add(new TriggerAction(type, payload));
        entry.setActions(actions);
        SecondDawnRP.TRIGGER_SERVICE.saveEntry(entry);

        src.sendFeedback(() -> Text.literal("Added action " + type + " to '" + id + "'.")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static String validatePayload(TriggerActionType type, String payload) {
        return switch (type) {
            case BROADCAST -> validateBroadcastPayload(payload);
            case ACTIVATE_LINKED, DEACTIVATE_LINKED, TOGGLE_LINKED -> validateLinkedPayload(payload);
            case GENERATE_TASK -> validateGenerateTaskPayload(payload);
            case NOTIFY_GM -> validateNotifyPayload(payload);
            case PLAY_SOUND -> validatePlaySoundPayload(payload);
            case APPLY_MEDICAL_CONDITION -> validateMedicalConditionPayload(payload);
        };
    }

    private static String validateBroadcastPayload(String payload) {
        int sep = payload.indexOf(':');
        if (sep < 0) {
            return "BROADCAST payload must be CHANNEL:message.";
        }

        String channel = payload.substring(0, sep).trim();
        String message = payload.substring(sep + 1).trim();

        if (message.isBlank()) {
            return "BROADCAST message cannot be blank.";
        }

        if (channel.equalsIgnoreCase("ALL") || channel.equalsIgnoreCase("GM_ONLY")) {
            return null;
        }

        if (channel.regionMatches(true, 0, "DIVISION:", 0, 9)) {
            String divisionName = channel.substring(9).trim();
            if (divisionName.isBlank()) {
                return "BROADCAST DIVISION channel must be DIVISION:<NAME>.";
            }
            try {
                Division.valueOf(divisionName.toUpperCase());
                return null;
            } catch (IllegalArgumentException e) {
                return "Unknown division in BROADCAST payload: " + divisionName;
            }
        }

        return "Unknown BROADCAST channel. Use ALL, GM_ONLY, or DIVISION:<NAME>.";
    }

    private static String validateLinkedPayload(String payload) {
        String[] ids = payload.split(",");
        List<String> missing = new ArrayList<>();

        for (String raw : ids) {
            String id = raw.trim();
            if (id.isEmpty()) continue;
            if (SecondDawnRP.ENV_EFFECT_SERVICE.getById(id).isEmpty()) {
                missing.add(id);
            }
        }

        if (missing.isEmpty()) {
            return null;
        }

        return "Unknown linked env effect ID(s): " + String.join(", ", missing);
    }

    private static String validateGenerateTaskPayload(String payload) {
        String[] parts = payload.split("\\|", 3);
        if (parts.length < 3) {
            return "GENERATE_TASK payload must be displayName|description|division.";
        }

        String displayName = parts[0].trim();
        String description = parts[1].trim();
        String divisionName = parts[2].trim();

        if (displayName.isBlank()) {
            return "GENERATE_TASK displayName cannot be blank.";
        }
        if (description.isBlank()) {
            return "GENERATE_TASK description cannot be blank.";
        }
        if (divisionName.isBlank()) {
            return "GENERATE_TASK division cannot be blank.";
        }

        try {
            Division.valueOf(divisionName.toUpperCase());
            return null;
        } catch (IllegalArgumentException e) {
            return "Unknown division in GENERATE_TASK payload: " + divisionName;
        }
    }

    private static String validateNotifyPayload(String payload) {
        if (payload.isBlank()) {
            return "NOTIFY_GM payload cannot be blank.";
        }
        return null;
    }

    private static String validatePlaySoundPayload(String payload) {
        String[] parts = payload.split(":");
        if (parts.length < 2) {
            return "PLAY_SOUND payload must be namespace:path[:volume[:pitch]].";
        }

        String namespace = parts[0].trim();
        String path = parts[1].trim();
        if (namespace.isBlank() || path.isBlank()) {
            return "PLAY_SOUND namespace and path cannot be blank.";
        }

        Identifier id;
        try {
            id = Identifier.of(namespace, path);
        } catch (Exception e) {
            return "Invalid sound identifier: " + namespace + ":" + path;
        }

        if (!Registries.SOUND_EVENT.containsId(id)) {
            return "Unknown sound event: " + id;
        }

        if (parts.length > 2) {
            try {
                Float.parseFloat(parts[2]);
            } catch (NumberFormatException e) {
                return "PLAY_SOUND volume must be a number.";
            }
        }

        if (parts.length > 3) {
            try {
                Float.parseFloat(parts[3]);
            } catch (NumberFormatException e) {
                return "PLAY_SOUND pitch must be a number.";
            }
        }

        return null;
    }

    private static String validateMedicalConditionPayload(String payload) {
        String[] parts = payload.split("\\|", 2);
        String conditionKey = parts[0].trim();

        if (conditionKey.isBlank()) {
            return "APPLY_MEDICAL_CONDITION requires a condition key.";
        }

        if (SecondDawnRP.MEDICAL_CONDITION_REGISTRY == null) {
            return "Medical condition registry is not available.";
        }

        if (!SecondDawnRP.MEDICAL_CONDITION_REGISTRY.exists(conditionKey)) {
            return "Unknown medical condition key: " + conditionKey;
        }

        return null;
    }

    private static int executeRemoveAction(ServerCommandSource src, String id, int index) {
        var opt = SecondDawnRP.TRIGGER_SERVICE.getById(id);
        if (opt.isEmpty()) {
            src.sendError(Text.literal("Unknown id: " + id));
            return 0;
        }

        TriggerEntry entry = opt.get();
        if (index >= entry.getActions().size()) {
            src.sendError(Text.literal("Index " + index + " out of range (0-" + (entry.getActions().size() - 1) + ")."));
            return 0;
        }

        var actions = new ArrayList<>(entry.getActions());
        actions.remove(index);
        entry.setActions(actions);
        SecondDawnRP.TRIGGER_SERVICE.saveEntry(entry);

        src.sendFeedback(() -> Text.literal("Removed action at index " + index + ".")
                .formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int executeSetRadius(ServerCommandSource src, String id, int blocks) {
        var opt = SecondDawnRP.TRIGGER_SERVICE.getById(id);
        if (opt.isEmpty()) {
            src.sendError(Text.literal("Unknown id: " + id));
            return 0;
        }

        opt.get().setRadiusBlocks(blocks);
        SecondDawnRP.TRIGGER_SERVICE.saveEntry(opt.get());

        src.sendFeedback(() -> Text.literal("Radius set to " + blocks)
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeSetCooldown(ServerCommandSource src, String id, int ticks) {
        var opt = SecondDawnRP.TRIGGER_SERVICE.getById(id);
        if (opt.isEmpty()) {
            src.sendError(Text.literal("Unknown id: " + id));
            return 0;
        }

        opt.get().setCooldownTicks(ticks);
        SecondDawnRP.TRIGGER_SERVICE.saveEntry(opt.get());

        src.sendFeedback(() -> Text.literal("Cooldown set to " + ticks + " ticks.")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeInfo(ServerCommandSource src, String id) {
        var opt = SecondDawnRP.TRIGGER_SERVICE.getById(id);
        if (opt.isEmpty()) {
            src.sendError(Text.literal("Unknown id: " + id));
            return 0;
        }

        TriggerEntry e = opt.get();
        src.sendFeedback(() -> Text.literal("── Trigger: " + id + " ──")
                .formatted(Formatting.YELLOW), false);
        src.sendFeedback(() -> Text.literal("Armed: " + e.isArmed() + " | Mode: " + e.getTriggerMode()
                        + " | Fire: " + e.getFireMode())
                .formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("Radius: " + e.getRadiusBlocks()
                        + " | Cooldown: " + e.getCooldownTicks() + " ticks")
                .formatted(Formatting.GRAY), false);

        for (int i = 0; i < e.getActions().size(); i++) {
            TriggerAction a = e.getActions().get(i);
            final int idx = i;
            src.sendFeedback(() -> Text.literal("  [" + idx + "] " + a.getType() + ": " + a.getPayload())
                    .formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    private static int executeHelp(ServerCommandSource src) {
        src.sendFeedback(() -> Text.literal("── Trigger Actions Help ──")
                .formatted(Formatting.YELLOW), false);

        src.sendFeedback(() -> Text.literal("BROADCAST")
                .formatted(Formatting.AQUA), false);
        src.sendFeedback(() -> Text.literal("  Payload: CHANNEL:message")
                .formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("  Channels: ALL, GM_ONLY, DIVISION:<NAME>")
                .formatted(Formatting.DARK_GRAY), false);
        src.sendFeedback(() -> Text.literal("  Example: ALL:Hull breach detected")
                .formatted(Formatting.DARK_GRAY), false);
        src.sendFeedback(() -> Text.literal("  Example: DIVISION:ENGINEERING:Report to deck 3")
                .formatted(Formatting.DARK_GRAY), false);

        src.sendFeedback(() -> Text.literal("ACTIVATE_LINKED / DEACTIVATE_LINKED / TOGGLE_LINKED")
                .formatted(Formatting.AQUA), false);
        src.sendFeedback(() -> Text.literal("  Payload: env_id1,env_id2,...")
                .formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("  Example: env_radiation_lab,env_engine_fire")
                .formatted(Formatting.DARK_GRAY), false);

        src.sendFeedback(() -> Text.literal("GENERATE_TASK")
                .formatted(Formatting.AQUA), false);
        src.sendFeedback(() -> Text.literal("  Payload: displayName|description|division")
                .formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("  Example: Repair Hull|Fix outer hull breach|ENGINEERING")
                .formatted(Formatting.DARK_GRAY), false);

        src.sendFeedback(() -> Text.literal("NOTIFY_GM")
                .formatted(Formatting.AQUA), false);
        src.sendFeedback(() -> Text.literal("  Payload: message")
                .formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("  Example: Sensor contact detected in maintenance junction")
                .formatted(Formatting.DARK_GRAY), false);

        src.sendFeedback(() -> Text.literal("PLAY_SOUND")
                .formatted(Formatting.AQUA), false);
        src.sendFeedback(() -> Text.literal("  Payload: namespace:path:volume:pitch")
                .formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("  Example: minecraft:block.note_block.bell:1:1")
                .formatted(Formatting.DARK_GRAY), false);

        src.sendFeedback(() -> Text.literal("APPLY_MEDICAL_CONDITION")
                .formatted(Formatting.AQUA), false);
        src.sendFeedback(() -> Text.literal("  Payload: conditionKey OR conditionKey|note")
                .formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("  Example: radiation_sickness")
                .formatted(Formatting.DARK_GRAY), false);
        src.sendFeedback(() -> Text.literal("  Example: neural_toxin|Triggered by gas leak")
                .formatted(Formatting.DARK_GRAY), false);

        src.sendFeedback(() -> Text.literal("Usage")
                .formatted(Formatting.AQUA), false);
        src.sendFeedback(() -> Text.literal("  /gm trigger addaction <id> <type> <payload>")
                .formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("  /gm trigger info <id>")
                .formatted(Formatting.GRAY), false);
        return 1;
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestBroadcastPayloads(
            SuggestionsBuilder builder) {
        suggestIfMatches(builder, "ALL:");
        suggestIfMatches(builder, "GM_ONLY:");
        for (Division division : Division.values()) {
            suggestIfMatches(builder, "DIVISION:" + division.name() + ":");
        }
        suggestIfMatches(builder, "ALL:Hull breach detected");
        suggestIfMatches(builder, "GM_ONLY:Trigger fired in restricted section");
        return builder.buildFuture();
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestLinkedPayloads(
            SuggestionsBuilder builder) {
        for (EnvironmentalEffectEntry entry : SecondDawnRP.ENV_EFFECT_SERVICE.getAll()) {
            suggestIfMatches(builder, entry.getEntryId());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestGenerateTaskPayloads(
            SuggestionsBuilder builder) {
        for (Division division : Division.values()) {
            suggestIfMatches(builder, "Repair Hull|Fix outer hull breach|" + division.name());
        }
        suggestIfMatches(builder, "Investigate Sensor Readings|Check anomalous readings|SCIENCE");
        return builder.buildFuture();
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestNotifyPayloads(
            SuggestionsBuilder builder) {
        suggestIfMatches(builder, "Sensor contact detected in maintenance junction");
        suggestIfMatches(builder, "Unauthorized access detected");
        suggestIfMatches(builder, "Trigger fired in cargo bay");
        return builder.buildFuture();
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestPlaySoundPayloads(
            SuggestionsBuilder builder) {
        suggestIfMatches(builder, "minecraft:block.note_block.bell:1:1");
        suggestIfMatches(builder, "minecraft:block.note_block.pling:1:1");
        suggestIfMatches(builder, "minecraft:entity.ender_dragon.growl:1:1");
        return builder.buildFuture();
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestMedicalConditionPayloads(
            SuggestionsBuilder builder) {
        if (SecondDawnRP.MEDICAL_CONDITION_REGISTRY == null) {
            return builder.buildFuture();
        }

        for (MedicalConditionTemplate template : SecondDawnRP.MEDICAL_CONDITION_REGISTRY.getAll()) {
            suggestIfMatches(builder, template.key());
            suggestIfMatches(builder, template.key() + "|Triggered by hazard");
        }
        return builder.buildFuture();
    }

    private static void suggestIfMatches(SuggestionsBuilder builder, String suggestion) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        if (suggestion.toLowerCase(Locale.ROOT).startsWith(remaining)) {
            builder.suggest(suggestion);
        }
    }

    private static boolean isGM(ServerCommandSource src) {
        var p = src.getPlayer();
        if (p == null) return src.hasPermissionLevel(2);
        return p.hasPermissionLevel(2)
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(p, "st.gm.use");
    }
}