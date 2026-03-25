package net.shard.seconddawnrp.character;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.SecondDawnRP;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Player-facing character commands.
 *
 * <pre>
 *  /character info            — show own character profile
 *  /character info [player]   — show another player's profile (op 2+)
 * </pre>
 */
public final class CharacterCommand {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private CharacterCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("character")

                        // /character info — self
                        .then(CommandManager.literal("info")
                                .executes(ctx -> showSelf(ctx))

                                // /character info <player> — GM/admin only
                                .then(CommandManager.argument("player", EntityArgumentType.player())
                                        .requires(src -> src.hasPermissionLevel(2))
                                        .executes(ctx -> showOther(ctx))))
        );
    }

    // ── /character info (self) ────────────────────────────────────────────────

    private static int showSelf(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            Optional<CharacterProfile> profileOpt =
                    SecondDawnRP.CHARACTER_SERVICE.get(player.getUuid());

            if (profileOpt.isEmpty()) {
                ctx.getSource().sendError(
                        Text.literal("No character profile found. Visit the Character Creation Terminal."));
                return 0;
            }

            sendProfileDisplay(ctx.getSource(), profileOpt.get(), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ── /character info <player> (GM view) ───────────────────────────────────

    private static int showOther(CommandContext<ServerCommandSource> ctx) {
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
            Optional<CharacterProfile> profileOpt =
                    SecondDawnRP.CHARACTER_SERVICE.get(target.getUuid());

            if (profileOpt.isEmpty()) {
                ctx.getSource().sendError(
                        Text.literal("No active character for " + target.getName().getString()));
                return 0;
            }

            sendProfileDisplay(ctx.getSource(), profileOpt.get(), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ── Shared display ────────────────────────────────────────────────────────

    private static void sendProfileDisplay(ServerCommandSource source,
                                           CharacterProfile p, boolean gmView) {
        source.sendFeedback(() -> Text.literal(
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").formatted(Formatting.DARK_GRAY), false);

        source.sendFeedback(() -> Text.literal("Character Profile")
                .formatted(Formatting.GOLD).append(
                        Text.literal(p.getStatus() == CharacterStatus.DECEASED
                                        ? " [DECEASED]" : "")
                                .formatted(Formatting.DARK_RED)), false);

        source.sendFeedback(() -> row("Name",
                p.getCharacterName() != null ? p.getCharacterName() : "(not set)"), false);

        source.sendFeedback(() -> row("Species",
                p.getSpecies() != null ? p.getSpecies() : "(not set)"), false);

        // Bio — wrap long bios
        String bio = p.getBio() != null && !p.getBio().isBlank() ? p.getBio() : "(no biography)";
        source.sendFeedback(() -> row("Bio", bio.length() > 80 ? bio.substring(0, 77) + "…" : bio), false);

        // Languages
        String langs = p.getKnownLanguages().isEmpty()
                ? "(none)" : String.join(", ", p.getKnownLanguages());
        source.sendFeedback(() -> row("Languages", langs), false);

        // LTI
        String lti = p.getActiveLongTermInjuryId() != null
                ? "Active (" + p.getActiveLongTermInjuryId() + ")" : "None";
        source.sendFeedback(() -> row("Injury", lti), false);

        if (p.getProgressionTransfer() > 0) {
            source.sendFeedback(() -> row("Transferred Points",
                    String.valueOf(p.getProgressionTransfer())), false);
        }

        // GM-only fields
        if (gmView) {
            source.sendFeedback(() -> Text.literal("— GM Info —")
                    .formatted(Formatting.DARK_GRAY), false);
            source.sendFeedback(() -> row("Character ID", p.getCharacterId()), false);
            source.sendFeedback(() -> row("Player UUID",  p.getPlayerUuid().toString()), false);
            source.sendFeedback(() -> row("Permadeath",   String.valueOf(p.isPermadeathConsent())), false);
            source.sendFeedback(() -> row("Universal Translator",
                    String.valueOf(p.hasUniversalTranslator())), false);
            source.sendFeedback(() -> row("Created",
                    DATE_FMT.format(Instant.ofEpochMilli(p.getCreatedAt()))), false);
            if (p.getDeceasedAt() != null) {
                source.sendFeedback(() -> row("Died",
                        DATE_FMT.format(Instant.ofEpochMilli(p.getDeceasedAt()))), false);
            }
        }

        source.sendFeedback(() -> Text.literal(
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━").formatted(Formatting.DARK_GRAY), false);
    }

    private static Text row(String label, String value) {
        return Text.literal(label + ": ").formatted(Formatting.GOLD)
                .append(Text.literal(value).formatted(Formatting.WHITE));
    }
}