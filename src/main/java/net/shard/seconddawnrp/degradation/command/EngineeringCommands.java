package net.shard.seconddawnrp.degradation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.shard.seconddawnrp.degradation.network.LocateComponentS2CPacket;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;

public final class EngineeringCommands {

    private EngineeringCommands() {
    }

    public static void register(
            CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess,
            CommandManager.RegistrationEnvironment environment
    ) {
        dispatcher.register(CommandManager.literal("engineering")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("locate")
                        .then(CommandManager.argument("componentId", StringArgumentType.word())
                                .executes(context -> {
                                    ServerCommandSource source = context.getSource();

                                    if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
                                        source.sendError(Text.literal("Only players can use this command."));
                                        return 0;
                                    }

                                    String componentId = StringArgumentType.getString(context, "componentId");
                                    BlockPos pos = player.getBlockPos().add(0, 0, 5);
                                    Vec3d center = Vec3d.ofCenter(pos);

                                    ServerPlayNetworking.send(
                                            player,
                                            new LocateComponentS2CPacket(
                                                    componentId,
                                                    getFallbackStatus(),
                                                    center.x,
                                                    center.y,
                                                    center.z
                                            )
                                    );

                                    source.sendFeedback(
                                            () -> Text.literal("Sent locator for component '" + componentId + "'."),
                                            false
                                    );
                                    return 1;
                                })))
                .then(CommandManager.literal("locatenearest")
                        .executes(context -> {
                            ServerCommandSource source = context.getSource();

                            if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
                                source.sendError(Text.literal("Only players can use this command."));
                                return 0;
                            }

                            BlockPos pos = player.getBlockPos().add(0, 0, 5);
                            Vec3d center = Vec3d.ofCenter(pos);

                            ServerPlayNetworking.send(
                                    player,
                                    new LocateComponentS2CPacket(
                                            "example_component",
                                            getFallbackStatus(),
                                            center.x,
                                            center.y,
                                            center.z
                                    )
                            );

                            source.sendFeedback(
                                    () -> Text.literal("Sent locator for nearest component."),
                                    false
                            );
                            return 1;
                        }))
        );
    }

    private static ComponentStatus getFallbackStatus() {
        ComponentStatus[] values = ComponentStatus.values();
        if (values.length == 0) {
            throw new IllegalStateException("ComponentStatus enum has no values.");
        }
        return values[0];
    }
}