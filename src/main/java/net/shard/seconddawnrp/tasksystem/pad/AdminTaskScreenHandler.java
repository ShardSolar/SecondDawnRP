package net.shard.seconddawnrp.tasksystem.pad;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.shard.seconddawnrp.registry.ModScreenHandlers;

import java.util.ArrayList;
import java.util.List;

public class AdminTaskScreenHandler extends ScreenHandler {

    private final List<AdminTaskViewModel> tasks = new ArrayList<>();
    private int selectedIndex = -1;

    public AdminTaskScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ModScreenHandlers.ADMIN_TASK_SCREEN, syncId);

        tasks.add(new AdminTaskViewModel(
                "calibrate_eps_grid",
                "Calibrate EPS Grid",
                "ACTIVE",
                "Lt. T'Vel",
                "Engineering",
                "2 / 5",
                List.of(
                        "Task ID: calibrate_eps_grid",
                        "Description: Recalibrate relay nodes on deck 4.",
                        "Objective: Break malfunctioning relay blocks",
                        "Target: seconddawnrp:eps_relay_fault",
                        "Division: Engineering",
                        "Reward: 15 rank points",
                        "Status: ACTIVE"
                )
        ));

        tasks.add(new AdminTaskViewModel(
                "cargo_cleanup",
                "Cargo Bay Cleanup",
                "AWAITING REVIEW",
                "Crewman Hale",
                "Operations",
                "12 / 12",
                List.of(
                        "Task ID: cargo_cleanup",
                        "Description: Remove debris from cargo bay 2.",
                        "Objective: Break spawned trash blocks",
                        "Target: seconddawnrp:trash_block",
                        "Division: Operations",
                        "Reward: 10 rank points",
                        "Status: AWAITING REVIEW"
                )
        ));

        if (!tasks.isEmpty()) {
            selectedIndex = 0;
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    public List<AdminTaskViewModel> getTasks() {
        return tasks;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int selectedIndex) {
        if (selectedIndex >= 0 && selectedIndex < tasks.size()) {
            this.selectedIndex = selectedIndex;
        }
    }

    public AdminTaskViewModel getSelectedTask() {
        if (selectedIndex < 0 || selectedIndex >= tasks.size()) {
            return null;
        }
        return tasks.get(selectedIndex);
    }
}