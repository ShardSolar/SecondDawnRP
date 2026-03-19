package net.shard.seconddawnrp;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.shard.seconddawnrp.registry.ModScreenHandlers;
import net.shard.seconddawnrp.tasksystem.pad.OperationsPadScreen;
import net.shard.seconddawnrp.tasksystem.pad.TaskPadScreen;

public class SecondDawnRPClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HandledScreens.register(ModScreenHandlers.TASK_PAD_SCREEN, TaskPadScreen::new);
        HandledScreens.register(ModScreenHandlers.ADMIN_TASK_SCREEN, OperationsPadScreen::new);
    }
}