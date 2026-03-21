package net.shard.seconddawnrp.tasksystem.terminal;

import net.minecraft.util.math.BlockPos;
import java.util.Objects;

public class TaskTerminalEntry {

    private String worldKey;
    private long blockPosLong;

    public TaskTerminalEntry() {
    }

    public TaskTerminalEntry(String worldKey, BlockPos pos) {
        this.worldKey = Objects.requireNonNull(worldKey, "worldKey");
        this.blockPosLong = pos.asLong();
    }

    public String getWorldKey() {
        return worldKey;
    }

    public BlockPos getBlockPos() {
        return BlockPos.fromLong(blockPosLong);
    }

    public long getBlockPosLong() {
        return blockPosLong;
    }

    public boolean matches(String worldKey, BlockPos pos) {
        return this.worldKey.equals(worldKey) && this.blockPosLong == pos.asLong();
    }
}