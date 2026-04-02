package net.shard.seconddawnrp.medical;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.character.LongTermInjury;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GM commands for the Phase 8 Medical system.
 *
 * <pre>
 *  /gm medical apply  [player] [conditionKey] [note?]   — apply a registry condition
 *  /gm medical clear  [player] [conditionId|conditionKey] — force-resolve an active condition
 *  /gm medical list   [player]                          — list active conditions with IDs
 *  /gm medical conditions                               — list all registry condition keys
 *  /gm medical reload                                   — hot-reload condition registry
 * </pre>
 */
public final class GmMedicalCommands {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /** Tab-completion provider for registry condition keys. */
    private static final SuggestionProvider<ServerCommandSource> CONDITION_KEY_SUGGESTIONS =
            (ctx, builder) -> {
                SecondDawnRP.MEDICAL_CONDITION_REGISTRY.getAll().stream()
                        .map(MedicalConditionTemplate::key)
                        .filter(k -> k.startsWith(builder.getRemaining()))
                        .forEach(builder::suggest);
                return builder.buildFuture();
            };

    /**
     * Tab-completion provider for active condition IDs and active condition keys
     * on the selected target player.
     */
    private static final SuggestionProvider<ServerCommandSource> ACTIVE_CONDITION_SUGGESTIONS =
            (ctx, builder) -> {
                try {
                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                    List<MedicalService.ConditionDetail> details =
                            SecondDawnRP.MEDICAL_SERVICE.getActiveConditions(target.getUuid());

                    String remaining = builder.getRemaining();

                    for (MedicalService.ConditionDetail detail : details) {
                        String id = detail.condition().getInjuryId();
                        if (id != null && id.startsWith(remaining)) {
                            builder.suggest(id);
                        }

                        String key = detail.condition().getConditionKey();
                        if (key != null && key.startsWith(remaining)) {
                            builder.suggest(key);
                        }
                    }
                } catch (Exception ignored) {
                }
                return builder.buildFuture();
            };

    private GmMedicalCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("gm")
                        .requires(src -> src.hasPermissionLevel(3))
                        .then(CommandManager.literal("medical")

                                // /gm medical apply <player> <conditionKey> [note]
                                .then(CommandManager.literal("apply")
                                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                                .then(CommandManager.argument("conditionKey", StringArgumentType.word())
                                                        .suggests(CONDITION_KEY_SUGGESTIONS)
                                                        .then(CommandManager.argument("note", StringArgumentType.greedyString())
                                                                .executes(ctx -> applyCondition(ctx, true)))
                                                        .executes(ctx -> applyCondition(ctx, false)))))

                                // /gm medical clear <player> <conditionId|conditionKey>
                                .then(CommandManager.literal("clear")
                                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                                .then(CommandManager.argument("condition", StringArgumentType.word())
                                                        .suggests(ACTIVE_CONDITION_SUGGESTIONS)
                                                        .executes(GmMedicalCommands::clearCondition))))

                                // /gm medical list <player>
                                .then(CommandManager.literal("list")
                                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                                .executes(GmMedicalCommands::listConditions)))

                                // /gm medical conditions
                                .then(CommandManager.literal("conditions")
                                        .executes(GmMedicalCommands::listRegistry))

                                // /gm medical reload
                                .then(CommandManager.literal("reload")
                                        .executes(GmMedicalCommands::reloadRegistry))
                        )
        );
    }

    // ── Apply ─────────────────────────────────────────────────────────────────

    private static int applyCondition(CommandContext<ServerCommandSource> ctx, boolean hasNote) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            String conditionKey = StringArgumentType.getString(ctx, "conditionKey");
            String note = hasNote ? StringArgumentType.getString(ctx, "note") : null;

            if (!SecondDawnRP.MEDICAL_CONDITION_REGISTRY.exists(conditionKey)) {
                ctx.getSource().sendError(Text.literal(
                        "[Medical] Unknown condition key: '" + conditionKey
                                + "'. Run /gm medical conditions to see all keys."));
                return 0;
            }

            String appliedByUuid = ctx.getSource().getEntity() instanceof ServerPlayerEntity gm
                    ? gm.getUuidAsString()
                    : "server";

            MedicalService.ApplyResult result = SecondDawnRP.MEDICAL_SERVICE.applyCondition(
                    target.getUuid(), conditionKey, null, null, note, appliedByUuid);

            if (result == MedicalService.ApplyResult.SUCCESS) {
                String templateName = SecondDawnRP.MEDICAL_CONDITION_REGISTRY
                        .get(conditionKey)
                        .map(MedicalConditionTemplate::displayName)
                        .orElse(conditionKey);

                ctx.getSource().sendFeedback(
                        () -> Text.literal("[Medical] Applied '")
                                .formatted(Formatting.GREEN)
                                .append(Text.literal(templateName).formatted(Formatting.YELLOW))
                                .append(Text.literal("' to ").formatted(Formatting.GREEN))
                                .append(Text.literal(target.getName().getString()).formatted(Formatting.YELLOW))
                                .append(Text.literal(".").formatted(Formatting.GREEN)),
                        true);
                return 1;
            } else {
                ctx.getSource().sendError(Text.literal("[Medical] Failed: " + result.name()));
                return 0;
            }
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ── Clear ─────────────────────────────────────────────────────────────────

    private static int clearCondition(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            String input = StringArgumentType.getString(ctx, "condition");

            List<MedicalService.ConditionDetail> details =
                    SecondDawnRP.MEDICAL_SERVICE.getActiveConditions(target.getUuid());

            if (details.isEmpty()) {
                ctx.getSource().sendError(Text.literal(
                        "[Medical] No active conditions found on " + target.getName().getString() + "."));
                return 0;
            }

            // First: exact match on active condition ID
            MedicalService.ConditionDetail exactIdMatch = null;
            for (MedicalService.ConditionDetail detail : details) {
                LongTermInjury condition = detail.condition();
                if (condition.getInjuryId() != null && condition.getInjuryId().equals(input)) {
                    exactIdMatch = detail;
                    break;
                }
            }

            if (exactIdMatch != null) {
                return resolveMatchedCondition(ctx, target, exactIdMatch);
            }

            // Second: match by condition key
            List<MedicalService.ConditionDetail> keyMatches = new ArrayList<>();
            for (MedicalService.ConditionDetail detail : details) {
                LongTermInjury condition = detail.condition();
                if (condition.getConditionKey() != null && condition.getConditionKey().equals(input)) {
                    keyMatches.add(detail);
                }
            }

            if (keyMatches.isEmpty()) {
                ctx.getSource().sendError(Text.literal(
                        "[Medical] No active condition matching '" + input + "' found on "
                                + target.getName().getString()
                                + ". Use /gm medical list to check IDs/keys."));
                return 0;
            }

            if (keyMatches.size() > 1) {
                ctx.getSource().sendError(Text.literal(
                        "[Medical] Multiple active conditions match key '" + input
                                + "' on " + target.getName().getString()
                                + ". Use the condition ID instead:"));

                for (MedicalService.ConditionDetail detail : keyMatches) {
                    LongTermInjury c = detail.condition();
                    String line = "  - " + c.getInjuryId() + " §7(" + c.getConditionKey() + " / "
                            + detail.displayName() + "§7)";
                    ctx.getSource().sendFeedback(() -> Text.literal(line), false);
                }
                return 0;
            }

            return resolveMatchedCondition(ctx, target, keyMatches.get(0));

        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int resolveMatchedCondition(CommandContext<ServerCommandSource> ctx,
                                               ServerPlayerEntity target,
                                               MedicalService.ConditionDetail detail) {
        String gmUuid = ctx.getSource().getEntity() instanceof ServerPlayerEntity gm
                ? gm.getUuidAsString()
                : "server";

        LongTermInjury condition = detail.condition();
        SecondDawnRP.MEDICAL_SERVICE.forceResolve(condition.getInjuryId(), gmUuid);

        ctx.getSource().sendFeedback(
                () -> Text.literal("[Medical] Cleared '")
                        .formatted(Formatting.GREEN)
                        .append(Text.literal(detail.displayName()).formatted(Formatting.YELLOW))
                        .append(Text.literal("' from ").formatted(Formatting.GREEN))
                        .append(Text.literal(target.getName().getString()).formatted(Formatting.YELLOW))
                        .append(Text.literal(" §7(ID: " + condition.getInjuryId() + ")").formatted(Formatting.GRAY)),
                true);
        return 1;
    }

    // ── List active conditions on a player ────────────────────────────────────

    private static int listConditions(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            List<MedicalService.ConditionDetail> details =
                    SecondDawnRP.MEDICAL_SERVICE.getActiveConditions(target.getUuid());

            ctx.getSource().sendFeedback(
                    () -> Text.literal("═══ Active conditions: "
                                    + target.getName().getString()
                                    + " (" + details.size() + ") ═══")
                            .formatted(Formatting.AQUA), false);

            if (details.isEmpty()) {
                ctx.getSource().sendFeedback(
                        () -> Text.literal("  None.").formatted(Formatting.GRAY), false);
                return 1;
            }

            for (MedicalService.ConditionDetail detail : details) {
                LongTermInjury c = detail.condition();
                String name = detail.displayName();
                String colour = detail.severityColour();
                String id = c.getInjuryId();
                String key = c.getConditionKey();
                String applied = DATE_FMT.format(Instant.ofEpochMilli(c.getAppliedAtMs()));

                int totalSteps = detail.template() != null
                        ? detail.template().treatmentPlan().size()
                        : 0;
                int doneSteps = totalSteps - detail.pendingSteps().size();
                String steps = totalSteps > 0
                        ? " [" + doneSteps + "/" + totalSteps + " steps]"
                        : "";
                String readyTag = detail.isReadyToResolve() ? " §a✔ READY TO RESOLVE§r" : "";

                String appliedByDisplay = resolvePlayerName(ctx, c.getAppliedBy());
                final String appliedByFinal = appliedByDisplay;

                ctx.getSource().sendFeedback(
                        () -> Text.literal(
                                "  " + colour + name + "§r" + steps + readyTag + "\n"
                                        + "  §7ID: §f" + id + "\n"
                                        + "  §7Key: §f" + key + "\n"
                                        + "  §7Applied: §f" + applied
                                        + (appliedByFinal != null ? " §7by §f" + appliedByFinal : "")
                                        + (c.getNotes() != null ? "\n  §7Note: §f" + c.getNotes() : "")),
                        false);
            }
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ── List all registry conditions ──────────────────────────────────────────

    private static int listRegistry(CommandContext<ServerCommandSource> ctx) {
        try {
            List<MedicalConditionTemplate> all =
                    SecondDawnRP.MEDICAL_CONDITION_REGISTRY.getAll();

            ctx.getSource().sendFeedback(
                    () -> Text.literal("═══ Medical Condition Registry ("
                                    + all.size() + " conditions) ═══")
                            .formatted(Formatting.AQUA), false);

            for (MedicalConditionTemplate.Severity severity :
                    MedicalConditionTemplate.Severity.values()) {
                List<MedicalConditionTemplate> group =
                        SecondDawnRP.MEDICAL_CONDITION_REGISTRY.getBySeverity(severity);
                if (group.isEmpty()) continue;

                ctx.getSource().sendFeedback(
                        () -> Text.literal("\n  " + severity.colour() + severity.label() + "§r")
                                .formatted(Formatting.WHITE), false);

                for (MedicalConditionTemplate t : group) {
                    int stepCount = t.treatmentPlan().size();
                    boolean hasTiming = t.treatmentPlan().stream()
                            .anyMatch(MedicalConditionTemplate.TreatmentStep::hasTiming);
                    String timedTag = hasTiming ? " §b[timed]§r" : "";
                    String surgTag = t.requiresSurgery() ? " §d[surgeon]§r" : "";

                    ctx.getSource().sendFeedback(
                            () -> Text.literal(
                                    "    §f" + t.key() + " §7— " + t.displayName()
                                            + " §8(" + stepCount + " steps)"
                                            + timedTag + surgTag),
                            false);
                }
            }

            ctx.getSource().sendFeedback(
                    () -> Text.literal("\n§7Use: /gm medical apply <player> <key>")
                            .formatted(Formatting.GRAY), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ── Reload ────────────────────────────────────────────────────────────────

    private static int reloadRegistry(CommandContext<ServerCommandSource> ctx) {
        try {
            SecondDawnRP.MEDICAL_CONDITION_REGISTRY.reload();
            int count = SecondDawnRP.MEDICAL_CONDITION_REGISTRY.getAll().size();
            ctx.getSource().sendFeedback(
                    () -> Text.literal("[Medical] Registry reloaded — " + count + " conditions.")
                            .formatted(Formatting.GREEN),
                    true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error reloading registry: " + e.getMessage()));
            return 0;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String resolvePlayerName(CommandContext<ServerCommandSource> ctx,
                                            String uuidStr) {
        if (uuidStr == null) return null;
        try {
            UUID uuid = UUID.fromString(uuidStr);
            ServerPlayerEntity p = ctx.getSource().getServer()
                    .getPlayerManager().getPlayer(uuid);
            return p != null ? p.getName().getString() : uuidStr.substring(0, 8) + "…";
        } catch (IllegalArgumentException e) {
            return uuidStr;
        }
    }
}