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
import net.minecraft.text.Text;
import net.shard.seconddawnrp.gmevent.data.EnvironmentalEffectEntry;

import java.util.Arrays;

/**
 * GM commands for Environmental Effect Blocks.
 *
 * <pre>
 * /gm env list                              — list all registered env blocks
 * /gm env toggle <id> <on|off>             — activate or deactivate
 * /gm env addeffect <id> <effectString>    — add a vanilla effect
 * /gm env removeeffect <id> <effectString> — remove a vanilla effect
 * /gm env setcondition <id> <condId> [severity] — set medical condition
 * /gm env clearcondition <id>             — clear medical condition
 * /gm env setradius <id> <blocks>         — set radius
 * /gm env info <id>                       — show full config
 * </pre>
 */
public final class GmEnvCommands {

    private GmEnvCommands() {}

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource>
            EFFECT_SUGGESTIONS = (ctx, builder) -> {
        if (SecondDawnRP.GM_REGISTRY_SERVICE != null) {
            SecondDawnRP.GM_REGISTRY_SERVICE.getVanillaEffects()
                    .forEach(e -> builder.suggest(
                            e.getEffectId() + ":0:200",
                            Text.literal(e.getDisplayName())));
        }
        return builder.buildFuture();
    };

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource>
            CONDITION_SUGGESTIONS = (ctx, builder) -> {
        if (SecondDawnRP.GM_REGISTRY_SERVICE != null) {
            SecondDawnRP.GM_REGISTRY_SERVICE.getMedicalConditions()
                    .forEach(c -> builder.suggest(
                            c.getConditionId(),
                            Text.literal(c.getDisplayName())));
        }
        return builder.buildFuture();
    };

    private static final com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource>
            ENV_ID_SUGGESTIONS = (ctx, builder) -> {
        SecondDawnRP.ENV_EFFECT_SERVICE.getAll()
                .forEach(e -> builder.suggest(e.getEntryId(),
                        Text.literal((e.isActive() ? "[ON] " : "[OFF] ") + e.getEntryId())));
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess access,
                                CommandManager.RegistrationEnvironment env) {
        var gmEnv = CommandManager.literal("env")
                .requires(src -> isGM(src));

        gmEnv.then(CommandManager.literal("list")
                .executes(ctx -> executeList(ctx.getSource())));

        gmEnv.then(CommandManager.literal("toggle")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(ENV_ID_SUGGESTIONS)
                        .then(CommandManager.literal("on")
                                .executes(ctx -> executeToggle(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"), true)))
                        .then(CommandManager.literal("off")
                                .executes(ctx -> executeToggle(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"), false)))));

        gmEnv.then(CommandManager.literal("addeffect")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(ENV_ID_SUGGESTIONS)
                        .then(CommandManager.argument("effect", StringArgumentType.greedyString())
                                .suggests(EFFECT_SUGGESTIONS)
                                .executes(ctx -> executeAddEffect(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        StringArgumentType.getString(ctx, "effect"))))));

        gmEnv.then(CommandManager.literal("removeeffect")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(ENV_ID_SUGGESTIONS)
                        .then(CommandManager.argument("effect", StringArgumentType.greedyString())
                                .executes(ctx -> executeRemoveEffect(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        StringArgumentType.getString(ctx, "effect"))))));

        gmEnv.then(CommandManager.literal("setcondition")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(ENV_ID_SUGGESTIONS)
                        .then(CommandManager.argument("conditionId", StringArgumentType.word())
                                .suggests(CONDITION_SUGGESTIONS).then(CommandManager.argument("severity", StringArgumentType.word())
                                        .executes(ctx -> executeSetCondition(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"),
                                                StringArgumentType.getString(ctx, "conditionId"),
                                                StringArgumentType.getString(ctx, "severity"))))
                                .executes(ctx -> executeSetCondition(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        StringArgumentType.getString(ctx, "conditionId"),
                                        "Moderate")))));

        gmEnv.then(CommandManager.literal("clearcondition")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(ENV_ID_SUGGESTIONS)
                        .executes(ctx -> executeClearCondition(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        gmEnv.then(CommandManager.literal("setradius")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(ENV_ID_SUGGESTIONS)
                        .then(CommandManager.argument("blocks", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> executeSetRadius(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "id"),
                                        IntegerArgumentType.getInteger(ctx, "blocks"))))));

        gmEnv.then(CommandManager.literal("info")
                .then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests(ENV_ID_SUGGESTIONS)
                        .executes(ctx -> executeInfo(ctx.getSource(),
                                StringArgumentType.getString(ctx, "id")))));

        dispatcher.register(CommandManager.literal("gm").then(gmEnv));
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private static int executeList(ServerCommandSource src) {
        var all = SecondDawnRP.ENV_EFFECT_SERVICE.getAll();
        if (all.isEmpty()) {
            src.sendFeedback(() -> Text.literal("No environmental effect blocks registered.")
                    .formatted(Formatting.GRAY), false);
            return 1;
        }
        src.sendFeedback(() -> Text.literal("── Environmental Effect Blocks ──")
                .formatted(Formatting.GREEN), false);
        all.forEach(e -> src.sendFeedback(() ->
                        Text.literal("  " + e.getEntryId() + " — ")
                                .formatted(Formatting.GRAY)
                                .append(Text.literal(e.isActive() ? "ACTIVE" : "INACTIVE")
                                        .formatted(e.isActive() ? Formatting.GREEN : Formatting.RED))
                                .append(Text.literal(" r=" + e.getRadiusBlocks()).formatted(Formatting.GRAY)),
                false));
        return 1;
    }

    private static int executeToggle(ServerCommandSource src, String id, boolean active) {
        if (!SecondDawnRP.ENV_EFFECT_SERVICE.toggle(id, active)) {
            src.sendError(Text.literal("No env effect block with id '" + id + "'."));
            return 0;
        }
        src.sendFeedback(() -> Text.literal("Env block '" + id + "' set to "
                        + (active ? "ACTIVE" : "INACTIVE") + ".")
                .formatted(active ? Formatting.GREEN : Formatting.YELLOW), true);
        return 1;
    }

    private static int executeAddEffect(ServerCommandSource src, String id, String effect) {
        var opt = SecondDawnRP.ENV_EFFECT_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown id: " + id)); return 0; }
        EnvironmentalEffectEntry e = opt.get();
        var list = new java.util.ArrayList<>(e.getVanillaEffects());
        list.add(effect);
        e.setVanillaEffects(list);
        SecondDawnRP.ENV_EFFECT_SERVICE.saveEntry(e);
        src.sendFeedback(() -> Text.literal("Added effect: " + effect).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeRemoveEffect(ServerCommandSource src, String id, String effect) {
        var opt = SecondDawnRP.ENV_EFFECT_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown id: " + id)); return 0; }
        EnvironmentalEffectEntry e = opt.get();
        var list = new java.util.ArrayList<>(e.getVanillaEffects());
        list.remove(effect);
        e.setVanillaEffects(list);
        SecondDawnRP.ENV_EFFECT_SERVICE.saveEntry(e);
        src.sendFeedback(() -> Text.literal("Removed effect: " + effect).formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int executeSetCondition(ServerCommandSource src, String id,
                                           String condId, String severity) {
        var opt = SecondDawnRP.ENV_EFFECT_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown id: " + id)); return 0; }
        EnvironmentalEffectEntry e = opt.get();
        e.setMedicalConditionId(condId);
        e.setMedicalConditionSeverity(severity);
        SecondDawnRP.ENV_EFFECT_SERVICE.saveEntry(e);
        src.sendFeedback(() -> Text.literal("Set condition: " + condId + " (" + severity + ")")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeClearCondition(ServerCommandSource src, String id) {
        var opt = SecondDawnRP.ENV_EFFECT_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown id: " + id)); return 0; }
        EnvironmentalEffectEntry e = opt.get();
        e.setMedicalConditionId(null);
        SecondDawnRP.ENV_EFFECT_SERVICE.saveEntry(e);
        src.sendFeedback(() -> Text.literal("Cleared medical condition from " + id)
                .formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int executeSetRadius(ServerCommandSource src, String id, int blocks) {
        var opt = SecondDawnRP.ENV_EFFECT_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown id: " + id)); return 0; }
        EnvironmentalEffectEntry e = opt.get();
        e.setRadiusBlocks(blocks);
        SecondDawnRP.ENV_EFFECT_SERVICE.saveEntry(e);
        src.sendFeedback(() -> Text.literal("Radius set to " + blocks + " blocks.")
                .formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeInfo(ServerCommandSource src, String id) {
        var opt = SecondDawnRP.ENV_EFFECT_SERVICE.getById(id);
        if (opt.isEmpty()) { src.sendError(Text.literal("Unknown id: " + id)); return 0; }
        EnvironmentalEffectEntry e = opt.get();
        src.sendFeedback(() -> Text.literal("── Env Block: " + id + " ──").formatted(Formatting.GREEN), false);
        src.sendFeedback(() -> Text.literal("Active: " + e.isActive()).formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("Radius: " + e.getRadiusBlocks()).formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("Fire: " + e.getFireMode() + " | Linger: " + e.getLingerMode()
                + " | Vis: " + e.getVisibility()).formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("Effects: " + e.getVanillaEffects()).formatted(Formatting.GRAY), false);
        String cond = e.getMedicalConditionId() != null
                ? e.getMedicalConditionId() + " (" + e.getMedicalConditionSeverity() + ")" : "None";
        src.sendFeedback(() -> Text.literal("Medical condition: " + cond).formatted(Formatting.GRAY), false);
        return 1;
    }

    private static boolean isGM(ServerCommandSource src) {
        var p = src.getPlayer();
        if (p == null) return src.hasPermissionLevel(2);
        return p.hasPermissionLevel(2) || SecondDawnRP.PERMISSION_SERVICE.hasPermission(p, "st.gm.use");
    }
}