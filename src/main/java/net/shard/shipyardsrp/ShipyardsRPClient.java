package net.shard.shipyardsrp;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.shard.shipyardsrp.registry.ModScreenHandlers;
import net.shard.shipyardsrp.tasksystem.padd.TaskPaddScreen;

public class ShipyardsRPClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HandledScreens.register(ModScreenHandlers.TASK_PADD_SCREEN, TaskPaddScreen::new);
    }
}