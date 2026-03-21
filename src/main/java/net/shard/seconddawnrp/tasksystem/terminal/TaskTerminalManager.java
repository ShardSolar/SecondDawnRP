package net.shard.seconddawnrp.tasksystem.terminal;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TaskTerminalManager {

    private final TaskTerminalRepository repository;
    private final List<TaskTerminalEntry> entries = new ArrayList<>();

    public TaskTerminalManager(TaskTerminalRepository repository) {
        this.repository = repository;
        this.entries.addAll(repository.loadAll());
    }

    public boolean isTerminal(World world, BlockPos pos) {
        String worldKey = world.getRegistryKey().getValue().toString();
        return entries.stream().anyMatch(entry -> entry.matches(worldKey, pos));
    }

    public Optional<TaskTerminalEntry> getTerminal(World world, BlockPos pos) {
        String worldKey = world.getRegistryKey().getValue().toString();
        return entries.stream().filter(entry -> entry.matches(worldKey, pos)).findFirst();
    }

    public boolean addTerminal(World world, BlockPos pos) {
        if (isTerminal(world, pos)) {
            return false;
        }

        entries.add(new TaskTerminalEntry(world.getRegistryKey().getValue().toString(), pos));
        save();
        return true;
    }

    public boolean removeTerminal(World world, BlockPos pos) {
        String worldKey = world.getRegistryKey().getValue().toString();
        boolean removed = entries.removeIf(entry -> entry.matches(worldKey, pos));

        if (removed) {
            save();
        }

        return removed;
    }

    public boolean toggleTerminal(World world, BlockPos pos) {
        if (isTerminal(world, pos)) {
            return removeTerminal(world, pos);
        }
        return addTerminal(world, pos);
    }

    public List<TaskTerminalEntry> getAll() {
        return List.copyOf(entries);
    }

    public void save() {
        repository.saveAll(entries);
    }
}