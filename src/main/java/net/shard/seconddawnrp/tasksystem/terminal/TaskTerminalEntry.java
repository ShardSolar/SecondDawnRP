package net.shard.seconddawnrp.tasksystem.terminal;

import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.divison.Division;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TaskTerminalEntry {

    private String worldKey;
    private long blockPosLong;
    private TerminalType type;
    private List<Division> allowedDivisions;

    public TaskTerminalEntry() {}

    public TaskTerminalEntry(String worldKey, BlockPos pos, TerminalType type, List<Division> allowedDivisions) {
        this.worldKey = Objects.requireNonNull(worldKey, "worldKey");
        this.blockPosLong = pos.asLong();
        this.type = type != null ? type : TerminalType.PUBLIC_BOARD;
        this.allowedDivisions = allowedDivisions != null ? new ArrayList<>(allowedDivisions) : new ArrayList<>();
    }

    // Keep your existing constructor for backward compat
    public TaskTerminalEntry(String worldKey, BlockPos pos) {
        this(worldKey, pos, TerminalType.PUBLIC_BOARD, List.of());
    }

    public String getWorldKey() { return worldKey; }
    public BlockPos getBlockPos() { return BlockPos.fromLong(blockPosLong); }
    public long getBlockPosLong() { return blockPosLong; }

    public TerminalType getType() {
        return type != null ? type : TerminalType.PUBLIC_BOARD;
    }

    public void setType(TerminalType type) {
        this.type = Objects.requireNonNull(type);
    }

    public List<Division> getAllowedDivisions() {
        return allowedDivisions != null ? allowedDivisions : new ArrayList<>();
    }

    public void setAllowedDivisions(List<Division> divisions) {
        this.allowedDivisions = divisions != null ? new ArrayList<>(divisions) : new ArrayList<>();
    }

    public boolean matches(String worldKey, BlockPos pos) {
        return this.worldKey.equals(worldKey) && this.blockPosLong == pos.asLong();
    }
}