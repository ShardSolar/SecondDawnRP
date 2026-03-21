package net.shard.seconddawnrp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.shard.seconddawnrp.registry.ModScreenHandlers;
import net.shard.seconddawnrp.tasksystem.network.OpsPadRefreshS2CPacket;
import net.shard.seconddawnrp.tasksystem.pad.AdminTaskScreenHandler;
import net.shard.seconddawnrp.tasksystem.pad.AdminTaskViewModel;
import net.shard.seconddawnrp.tasksystem.pad.OperationsPadScreen;
import net.shard.seconddawnrp.tasksystem.pad.TaskPadScreen;
import net.shard.seconddawnrp.tasksystem.terminal.TerminalScreen;

import java.util.List;

public class SecondDawnRPClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HandledScreens.register(ModScreenHandlers.TASK_PAD_SCREEN, TaskPadScreen::new);
        HandledScreens.register(ModScreenHandlers.ADMIN_TASK_SCREEN, OperationsPadScreen::new);
        HandledScreens.register(ModScreenHandlers.TERMINAL_SCREEN, TerminalScreen::new);

        ClientPlayNetworking.registerGlobalReceiver(
                OpsPadRefreshS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> {
                    if (context.client().currentScreen instanceof OperationsPadScreen screen) {
                        List<AdminTaskViewModel> views = payload.tasks().stream()
                                .map(SecondDawnRPClient::toViewModel)
                                .toList();

                        AdminTaskScreenHandler handler = screen.getScreenHandler();
                        if (handler != null) {
                            handler.replaceTasks(views);
                            screen.handleRefreshApplied();
                        }
                    }
                })
        );
    }

    private static AdminTaskViewModel toViewModel(OpsPadRefreshS2CPacket.TaskEntry entry) {
        return new AdminTaskViewModel(
                entry.taskId(),
                entry.title(),
                entry.status(),
                entry.assigneeLabel(),
                entry.divisionLabel(),
                entry.progressLabel(),
                entry.detailLines()
        );
    }
}