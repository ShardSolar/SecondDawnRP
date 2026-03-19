package net.shard.seconddawnrp.playerdata;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.divison.Division;
import net.shard.seconddawnrp.divison.Rank;

public class PlayerProfileCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("profile")
                .requires(source -> true)

                .then(CommandManager.literal("view")
                        .executes(context -> {
                            ensureReady();

                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            PlayerProfile profile = SecondDawnRP.PROFILE_SERVICE.getOrLoad(player);

                            context.getSource().sendFeedback(() -> Text.literal(
                                    "Service Name: " + profile.getServiceName() + "\n" +
                                            "Division: " + profile.getDivision() + "\n" +
                                            "Path: " + profile.getProgressionPath() + "\n" +
                                            "Rank: " + profile.getRank() + "\n" +
                                            "Billets: " + profile.getBillets() + "\n" +
                                            "Points: " + profile.getRankPoints() + "\n" +
                                            "Duty: " + profile.getDutyStatus() + "\n" +
                                            "Certs: " + profile.getCertifications()
                            ), false);

                            return 1;
                        }))

                .then(CommandManager.literal("setdivision")
                        .then(CommandManager.argument("division", StringArgumentType.word())
                                .executes(context -> {
                                    ensureReady();

                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                    Division division = Division.valueOf(
                                            StringArgumentType.getString(context, "division").toUpperCase()
                                    );

                                    SecondDawnRP.PROFILE_SERVICE.setDivision(player, division);
                                    context.getSource().sendFeedback(() -> Text.literal("Division set to " + division), false);
                                    return 1;
                                })))

                .then(CommandManager.literal("setrank")
                        .then(CommandManager.argument("rank", StringArgumentType.word())
                                .executes(context -> {
                                    ensureReady();

                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                    Rank rank = Rank.valueOf(
                                            StringArgumentType.getString(context, "rank").toUpperCase()
                                    );

                                    SecondDawnRP.PROFILE_SERVICE.setRank(player, rank);
                                    context.getSource().sendFeedback(() -> Text.literal("Rank set to " + rank), false);
                                    return 1;
                                })))

                .then(CommandManager.literal("addbillet")
                        .then(CommandManager.argument("billet", StringArgumentType.word())
                                .executes(context -> {
                                    ensureReady();

                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                    Billet billet = Billet.valueOf(
                                            StringArgumentType.getString(context, "billet").toUpperCase()
                                    );

                                    SecondDawnRP.PROFILE_SERVICE.addBillet(player, billet);
                                    context.getSource().sendFeedback(() -> Text.literal("Added billet " + billet), false);
                                    return 1;
                                })))

                .then(CommandManager.literal("removebillet")
                        .then(CommandManager.argument("billet", StringArgumentType.word())
                                .executes(context -> {
                                    ensureReady();

                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                    Billet billet = Billet.valueOf(
                                            StringArgumentType.getString(context, "billet").toUpperCase()
                                    );

                                    SecondDawnRP.PROFILE_SERVICE.removeBillet(player, billet);
                                    context.getSource().sendFeedback(() -> Text.literal("Removed billet " + billet), false);
                                    return 1;
                                })))

                .then(CommandManager.literal("addpoints")
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer())
                                .executes(context -> {
                                    ensureReady();

                                    ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                                    int amount = IntegerArgumentType.getInteger(context, "amount");

                                    SecondDawnRP.PROFILE_SERVICE.addRankPoints(player, amount);
                                    context.getSource().sendFeedback(() -> Text.literal("Added " + amount + " rank points"), false);
                                    return 1;
                                }))));
    }

    private static void ensureReady() {
        if (SecondDawnRP.PROFILE_SERVICE == null) {
            throw new IllegalStateException("SecondDawnRP profile services are not initialized yet.");
        }
    }
}