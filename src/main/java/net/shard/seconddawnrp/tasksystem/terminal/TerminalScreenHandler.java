package net.shard.seconddawnrp.tasksystem.terminal;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.shard.seconddawnrp.registry.ModScreenHandlers;
import net.shard.seconddawnrp.tasksystem.terminal.TerminalScreenOpenData.TerminalTaskEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TerminalScreenHandler extends ScreenHandler {

    private final List<TerminalTaskEntry> tasks;
    private int selectedIndex;

    // Client-side constructor — called by ExtendedScreenHandlerType via packet codec
    public TerminalScreenHandler(int syncId, PlayerInventory playerInventory, TerminalScreenOpenData data) {
        super(ModScreenHandlers.TERMINAL_SCREEN, syncId);
        this.tasks = new ArrayList<>(data.tasks());
        this.selectedIndex = tasks.isEmpty() ? -1 : 0;
    }

    public List<TerminalTaskEntry> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int index) {
        if (index >= 0 && index < tasks.size()) {
            this.selectedIndex = index;
        }
    }

    public TerminalTaskEntry getSelectedTask() {
        if (selectedIndex < 0 || selectedIndex >= tasks.size()) return null;
        return tasks.get(selectedIndex);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }
}