package net.shard.seconddawnrp.registry;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.screen.SpawnConfigScreenHandler;
import net.shard.seconddawnrp.gmevent.screen.SpawnConfigScreenOpenData;
import net.shard.seconddawnrp.gmevent.screen.SpawnItemScreenHandler;
import net.shard.seconddawnrp.gmevent.screen.SpawnItemScreenOpenData;
import net.shard.seconddawnrp.roster.data.RosterOpenData;
import net.shard.seconddawnrp.roster.screen.RosterScreenHandler;
import net.shard.seconddawnrp.tasksystem.pad.AdminTaskScreenHandler;
import net.shard.seconddawnrp.tasksystem.pad.TaskPadOpeningData;
import net.shard.seconddawnrp.tasksystem.pad.TaskPadScreenHandler;
import net.shard.seconddawnrp.tasksystem.terminal.TerminalScreenHandler;
import net.shard.seconddawnrp.tasksystem.terminal.TerminalScreenOpenData;
import net.shard.seconddawnrp.transporter.client.TransporterScreenHandler;

public class ModScreenHandlers {

    public static final ScreenHandlerType<TaskPadScreenHandler> TASK_PAD_SCREEN =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    SecondDawnRP.id("task_pad"),
                    new ExtendedScreenHandlerType<>(TaskPadScreenHandler::new, TaskPadOpeningData.PACKET_CODEC)
            );

    public static final ScreenHandlerType<AdminTaskScreenHandler> ADMIN_TASK_SCREEN =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    SecondDawnRP.id("operations_pad"),
                    new ScreenHandlerType<>(AdminTaskScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
            );

    public static final ScreenHandlerType<TerminalScreenHandler> TERMINAL_SCREEN =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    SecondDawnRP.id("terminal"),
                    new ExtendedScreenHandlerType<>(TerminalScreenHandler::new, TerminalScreenOpenData.PACKET_CODEC)
            );

    public static final ScreenHandlerType<SpawnConfigScreenHandler> SPAWN_CONFIG_SCREEN =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    SecondDawnRP.id("spawn_config"),
                    new ExtendedScreenHandlerType<>(SpawnConfigScreenHandler::new,
                            SpawnConfigScreenOpenData.PACKET_CODEC)
            );

    public static final ScreenHandlerType<SpawnItemScreenHandler> SPAWN_ITEM_SCREEN =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    SecondDawnRP.id("spawn_item"),
                    new ExtendedScreenHandlerType<>(SpawnItemScreenHandler::new,
                            SpawnItemScreenOpenData.PACKET_CODEC)
            );

    public static final ScreenHandlerType<RosterScreenHandler> ROSTER_SCREEN =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    SecondDawnRP.id("roster"),
                    new ExtendedScreenHandlerType<>(
                            (syncId, inv, data) -> new RosterScreenHandler(syncId, inv, data),
                            RosterOpenData.PACKET_CODEC)
            );

    public static final ScreenHandlerType<TransporterScreenHandler> TRANSPORTER_SCREEN =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    SecondDawnRP.id("transporter_controller"),
                    new ScreenHandlerType<>(
                            (syncId, inv) -> new TransporterScreenHandler(syncId, inv),
                            FeatureFlags.VANILLA_FEATURES)
            );

    public static final ScreenHandlerType<net.shard.seconddawnrp.tactical.console.TacticalScreenHandler>
            TACTICAL_SCREEN = Registry.register(
            Registries.SCREEN_HANDLER,
            SecondDawnRP.id("tactical_console"),
            new ScreenHandlerType<>(
                    (syncId, inv) -> new net.shard.seconddawnrp.tactical.console.TacticalScreenHandler(syncId, inv),
                    FeatureFlags.VANILLA_FEATURES)
    );
    public static void register() {
        // no-op
    }
}