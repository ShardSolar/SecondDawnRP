package net.shard.shipyardsrp.registry;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.shard.shipyardsrp.ShipyardsRP;
import net.shard.shipyardsrp.tasksystem.padd.TaskPaddOpeningData;
import net.shard.shipyardsrp.tasksystem.padd.TaskPaddScreenHandler;

public final class ModScreenHandlers {

    public static final ScreenHandlerType<TaskPaddScreenHandler> TASK_PADD_SCREEN =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    Identifier.of(ShipyardsRP.MOD_ID, "task_padd"),
                    new ExtendedScreenHandlerType<>(
                            TaskPaddScreenHandler::new,
                            PacketCodec.of(TaskPaddOpeningData::write, TaskPaddOpeningData::read)
                    )
            );

    private ModScreenHandlers() {
    }

    public static void register() {
        // static init hook
    }
}