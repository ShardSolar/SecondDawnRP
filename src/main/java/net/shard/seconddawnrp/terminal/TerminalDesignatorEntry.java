package net.shard.seconddawnrp.terminal;

import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/**
 * One registered terminal designation: a world position mapped to a terminal type.
 * Immutable value object — replace rather than mutate.
 */
public final class TerminalDesignatorEntry {

    private final String worldKey;  // e.g. "minecraft:overworld"
    private final long   packedPos; // BlockPos.asLong()
    private final TerminalDesignatorType type;

    public TerminalDesignatorEntry(String worldKey, BlockPos pos, TerminalDesignatorType type) {
        this.worldKey  = Objects.requireNonNull(worldKey,  "worldKey");
        this.packedPos = pos.asLong();
        this.type      = Objects.requireNonNull(type, "type");
    }

    // For deserialization from JSON (packed pos already known)
    public TerminalDesignatorEntry(String worldKey, long packedPos, TerminalDesignatorType type) {
        this.worldKey  = Objects.requireNonNull(worldKey,  "worldKey");
        this.packedPos = packedPos;
        this.type      = Objects.requireNonNull(type, "type");
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getWorldKey()          { return worldKey; }
    public long   getPackedPos()         { return packedPos; }
    public BlockPos getPos()             { return BlockPos.fromLong(packedPos); }
    public TerminalDesignatorType getType() { return type; }

    // ── Matching ──────────────────────────────────────────────────────────────

    public boolean matches(String worldKey, BlockPos pos) {
        return this.worldKey.equals(worldKey) && this.packedPos == pos.asLong();
    }

    public boolean matches(String worldKey, long packedPos) {
        return this.worldKey.equals(worldKey) && this.packedPos == packedPos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TerminalDesignatorEntry e)) return false;
        return packedPos == e.packedPos && worldKey.equals(e.worldKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldKey, packedPos);
    }

    @Override
    public String toString() {
        return "TerminalDesignatorEntry{world=" + worldKey
                + ", pos=" + BlockPos.fromLong(packedPos).toShortString()
                + ", type=" + type + "}";
    }
}