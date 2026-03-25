package net.shard.seconddawnrp.character;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GM commands for the character lifecycle.
 *
 * <p>Commands:
 * <pre>
 *  /gm character kill [player] transfer:[percent]
 *      Two-step: first call issues a confirmation prompt, second (confirm) executes.
 *      Requires op level 3.
 *
 *  /gm character set name [player] [name]
 *  /gm character set bio  [player] [bio]
 *  /gm character set species [player] [speciesId]
 *  /gm character set permadeath [player] [true|false]
 *      Direct GM overrides for character fields. Audited.
 *
 *  /gm injury modify [player] [days]
 *      Adjust the expiry of a player's active long-term injury by N days.
 *      Positive = extend, negative = reduce. Audited.
 * </pre>
 */
public final class GmCharacterCommands {

    /**
     * Pending death confirmations. Key = GM UUID, Value = target player UUID.
     * Entries expire after 30 seconds (checked on each command invocation).
     */
    private static final Map<UUID, PendingKill> PENDING_KILLS = new HashMap<>();
    private static final long CONFIRMATION_WINDOW_MS = 30_000;

    private GmCharacterCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("gm")
                        .requires(src -> src.hasPermissionLevel(3))
                        .then(CommandManager.literal("character")

                                // /gm character kill <player> <transferPercent>
                                .then(CommandManager.literal("kill")
                                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                                .then(CommandManager.argument("transfer", FloatArgumentType.floatArg(0f, 100f))
                                                        .executes(ctx -> killStep1(ctx)))))

                                // /gm character kill confirm
                                .then(CommandManager.literal("killconfirm")
                                        .executes(ctx -> killStep2(ctx)))

                                // /gm character set ...
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.literal("name")
                                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                                        .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                                                .executes(ctx -> setName(ctx)))))
                                        .then(CommandManager.literal("bio")
                                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                                        .then(CommandManager.argument("bio", StringArgumentType.greedyString())
                                                                .executes(ctx -> setBio(ctx)))))
                                        .then(CommandManager.literal("species")
                                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                                        .then(CommandManager.argument("speciesId", StringArgumentType.word())
                                                                .executes(ctx -> setSpecies(ctx)))))
                                        .then(CommandManager.literal("permadeath")
                                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                                        .then(CommandManager.argument("value", StringArgumentType.word())
                                                                .executes(ctx -> setPermadeath(ctx)))))))

                        // /gm injury modify <player> <days>
                        .then(CommandManager.literal("injury")
                                .then(CommandManager.literal("modify")
                                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                                .then(CommandManager.argument("days", IntegerArgumentType.integer(-365, 365))
                                                        .executes(ctx -> injuryModify(ctx))))))
        );
    }

    // ── /gm character kill — step 1: request confirmation ────────────────────

    private static int killStep1(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity gmPlayer = ctx.getSource().getPlayerOrThrow();
            ServerPlayerEntity target   = EntityArgumentType.getPlayer(ctx, "player");
            float transferPct           = FloatArgumentType.getFloat(ctx, "transfer");

            // Clean up stale pending kills first
            PENDING_KILLS.entrySet().removeIf(
                    e -> System.currentTimeMillis() - e.getValue().createdAtMs > CONFIRMATION_WINDOW_MS);

            PENDING_KILLS.put(gmPlayer.getUuid(),
                    new PendingKill(target.getUuid(), transferPct / 100f));

            gmPlayer.sendMessage(
                    Text.literal("[Character Death] You are about to kill ")
                            .formatted(Formatting.RED)
                            .append(Text.literal(target.getName().getString()).formatted(Formatting.YELLOW))
                            .append(Text.literal(" with a " + (int) transferPct + "% point transfer. ")
                                    .formatted(Formatting.RED))
                            .append(Text.literal("Run /gm character killconfirm within 30 seconds to confirm.")
                                    .formatted(Formatting.GOLD)),
                    false
            );
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ── /gm character killconfirm — step 2: execute death ────────────────────

    private static int killStep2(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity gmPlayer = ctx.getSource().getPlayerOrThrow();
            UUID gmUuid = gmPlayer.getUuid();

            PendingKill pending = PENDING_KILLS.remove(gmUuid);
            if (pending == null) {
                gmPlayer.sendMessage(
                        Text.literal("[Character] No pending kill to confirm, or confirmation expired.")
                                .formatted(Formatting.RED), false);
                return 0;
            }
            if (System.currentTimeMillis() - pending.createdAtMs > CONFIRMATION_WINDOW_MS) {
                gmPlayer.sendMessage(
                        Text.literal("[Character] Confirmation window expired. Re-run the kill command.")
                                .formatted(Formatting.RED), false);
                return 0;
            }

            UUID targetUuid = pending.targetUuid;
            ServerPlayerEntity targetPlayer =
                    ctx.getSource().getServer().getPlayerManager().getPlayer(targetUuid);

            // Check consent if player is online and doesn't have permadeath consent
            CharacterProfile profile = SecondDawnRP.CHARACTER_SERVICE.get(targetUuid).orElse(null);
            if (profile != null && !profile.isPermadeathConsent() && targetPlayer == null) {
                gmPlayer.sendMessage(
                        Text.literal("[Character] Target player is offline. "
                                        + "Cannot confirm consent. Ask them to be online for character death.")
                                .formatted(Formatting.RED), false);
                return 0;
            }

            int currentPoints = 0;
            if (targetPlayer != null) {
                var playerProfile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(targetUuid);
                if (playerProfile != null) currentPoints = playerProfile.getRankPoints();
            }

            CharacterProfile newChar = SecondDawnRP.CHARACTER_SERVICE.executeCharacterDeath(
                    targetUuid, pending.transferPercent, currentPoints, targetPlayer);

            String targetName = profile != null ? profile.getCharacterName() : targetUuid.toString();
            gmPlayer.sendMessage(
                    Text.literal("[Character] ")
                            .formatted(Formatting.GREEN)
                            .append(Text.literal(targetName).formatted(Formatting.YELLOW))
                            .append(Text.literal(" has died. New blank character created. "
                                            + "New character ID: " + newChar.getCharacterId())
                                    .formatted(Formatting.GREEN)),
                    false
            );

            // TODO Phase 9.5: PROFILE_SERVICE → set division to UNASSIGNED
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ── /gm character set name ────────────────────────────────────────────────

    private static int setName(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            String name = StringArgumentType.getString(ctx, "name");

            CharacterProfile profile = SecondDawnRP.CHARACTER_SERVICE.get(target.getUuid())
                    .orElse(null);
            if (profile == null) {
                ctx.getSource().sendError(Text.literal("No active character found for " + target.getName().getString()));
                return 0;
            }
            profile.setCharacterName(name);
            SecondDawnRP.CHARACTER_SERVICE.onPlayerLeave(target.getUuid()); // triggers save
            SecondDawnRP.CHARACTER_SERVICE.onPlayerJoin(target);            // reload

            ctx.getSource().sendFeedback(
                    () -> Text.literal("[Character] Name updated to: " + name)
                            .formatted(Formatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ── /gm character set bio ─────────────────────────────────────────────────

    private static int setBio(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            String bio = StringArgumentType.getString(ctx, "bio");

            CharacterProfile profile = SecondDawnRP.CHARACTER_SERVICE.get(target.getUuid())
                    .orElse(null);
            if (profile == null) {
                ctx.getSource().sendError(Text.literal("No active character."));
                return 0;
            }
            profile.setBio(bio);
            ctx.getSource().sendFeedback(
                    () -> Text.literal("[Character] Bio updated.").formatted(Formatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ── /gm character set species ─────────────────────────────────────────────

    private static int setSpecies(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            String speciesId = StringArgumentType.getString(ctx, "speciesId");

            CharacterProfile profile = SecondDawnRP.CHARACTER_SERVICE.get(target.getUuid())
                    .orElse(null);
            if (profile == null) {
                ctx.getSource().sendError(Text.literal("No active character."));
                return 0;
            }
            profile.setSpecies(speciesId);
            ctx.getSource().sendFeedback(
                    () -> Text.literal("[Character] Species set to: " + speciesId)
                            .formatted(Formatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ── /gm character set permadeath ──────────────────────────────────────────

    private static int setPermadeath(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            String valueStr = StringArgumentType.getString(ctx, "value");
            boolean value   = valueStr.equalsIgnoreCase("true");

            CharacterProfile profile = SecondDawnRP.CHARACTER_SERVICE.get(target.getUuid())
                    .orElse(null);
            if (profile == null) {
                ctx.getSource().sendError(Text.literal("No active character."));
                return 0;
            }
            profile.setPermadeathConsent(value);
            ctx.getSource().sendFeedback(
                    () -> Text.literal("[Character] Permadeath consent set to: " + value)
                            .formatted(Formatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ── /gm injury modify ────────────────────────────────────────────────────

    private static int injuryModify(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            int days = IntegerArgumentType.getInteger(ctx, "days");

            SecondDawnRP.LONG_TERM_INJURY_SERVICE.adjustExpiry(target.getUuid(), days);

            String sign = days >= 0 ? "+" : "";
            ctx.getSource().sendFeedback(
                    () -> Text.literal("[Injury] Expiry adjusted by " + sign + days
                                    + " day(s) for " + target.getName().getString() + ".")
                            .formatted(Formatting.GREEN),
                    true // broadcast to ops log
            );
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private record PendingKill(UUID targetUuid, float transferPercent, long createdAtMs) {
        PendingKill(UUID targetUuid, float transferPercent) {
            this(targetUuid, transferPercent, System.currentTimeMillis());
        }
    }
}