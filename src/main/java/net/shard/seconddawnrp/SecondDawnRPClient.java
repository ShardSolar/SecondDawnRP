package net.shard.seconddawnrp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.shard.seconddawnrp.degradation.client.ComponentWarningClientHandler;
import net.shard.seconddawnrp.gmevent.client.GmKeybindings;
import net.shard.seconddawnrp.gmevent.client.GmKeyInputHandler;
import net.shard.seconddawnrp.gmevent.network.GmToolRefreshS2CPacket;
import net.shard.seconddawnrp.gmevent.screen.SpawnConfigScreen;
import net.shard.seconddawnrp.gmevent.screen.SpawnItemScreen;
import net.shard.seconddawnrp.registry.ModScreenHandlers;
import net.shard.seconddawnrp.tasksystem.network.OpsPadRefreshS2CPacket;
import net.shard.seconddawnrp.tasksystem.pad.AdminTaskScreenHandler;
import net.shard.seconddawnrp.tasksystem.pad.AdminTaskViewModel;
import net.shard.seconddawnrp.tasksystem.pad.OperationsPadScreen;
import net.shard.seconddawnrp.tasksystem.pad.TaskPadScreen;
import net.shard.seconddawnrp.tasksystem.terminal.TerminalScreen;
import net.shard.seconddawnrp.warpcore.client.WarpCoreClientHandler;

import java.util.List;

public class SecondDawnRPClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Screen registrations
        HandledScreens.register(ModScreenHandlers.TASK_PAD_SCREEN, TaskPadScreen::new);
        HandledScreens.register(ModScreenHandlers.ADMIN_TASK_SCREEN, OperationsPadScreen::new);
        HandledScreens.register(ModScreenHandlers.TERMINAL_SCREEN, TerminalScreen::new);
        HandledScreens.register(ModScreenHandlers.SPAWN_CONFIG_SCREEN, SpawnConfigScreen::new);
        HandledScreens.register(ModScreenHandlers.SPAWN_ITEM_SCREEN, SpawnItemScreen::new);
        ComponentWarningClientHandler.register();
        WarpCoreClientHandler.register();
        net.shard.seconddawnrp.gmevent.client.EnvEffectClientHandler.register();
        net.shard.seconddawnrp.gmevent.client.TriggerClientHandler.register();

        // GM keybindings
        GmKeybindings.register();
        GmKeyInputHandler.register();

        // Ops PADD refresh
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

        // GM tool refresh — updates open GM screens when a template is saved
        ClientPlayNetworking.registerGlobalReceiver(
                GmToolRefreshS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> {
                    var screen = context.client().currentScreen;
                    if (screen instanceof SpawnConfigScreen s) {
                        s.getScreenHandler().replaceTemplates(payload.templates());
                    } else if (screen instanceof SpawnItemScreen s) {
                        s.getScreenHandler().replaceTemplates(payload.templates());
                    }
                })
        );
    }

    private static AdminTaskViewModel toViewModel(OpsPadRefreshS2CPacket.TaskEntry entry) {
        return new AdminTaskViewModel(
                entry.taskId(), entry.title(), entry.status(),
                entry.assigneeLabel(), entry.divisionLabel(),
                entry.progressLabel(), entry.detailLines()
        );
    }
}