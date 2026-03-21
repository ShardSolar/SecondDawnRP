package net.shard.seconddawnrp.registry;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tasksystem.pad.AdminTaskScreenHandler;
import net.shard.seconddawnrp.tasksystem.pad.TaskPadOpeningData;
import net.shard.seconddawnrp.tasksystem.pad.TaskPadScreenHandler;
import net.shard.seconddawnrp.tasksystem.terminal.TerminalScreenHandler;
import net.shard.seconddawnrp.tasksystem.terminal.TerminalScreenOpenData;

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

    public static void register() {
        // no-op
    }
}