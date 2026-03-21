package net.shard.seconddawnrp.tasksystem.terminal;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class TerminalScreenHandlerFactory implements ExtendedScreenHandlerFactory<TerminalScreenOpenData> {

    private final TerminalScreenOpenData data;

    public TerminalScreenHandlerFactory(TerminalScreenOpenData data) {
        this.data = data;
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("Task Terminal");
    }

    @Override
    public TerminalScreenOpenData getScreenOpeningData(ServerPlayerEntity player) {
        return data;
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new TerminalScreenHandler(syncId, playerInventory, data);
    }
}