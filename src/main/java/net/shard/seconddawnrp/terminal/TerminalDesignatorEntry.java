package net.shard.seconddawnrp.terminal;

import net.minecraft.util.math.BlockPos;

import java.util.Objects;

/**
 * One registered terminal designation: a world position mapped to a terminal type.
 *
 * V15 adds shipId (nullable) — binds the terminal to a specific registered ship.
 * Used by tactical stations to know which ship's encounter data to display,
 * and by the Engineering console to filter components to the correct ship.
 * Terminals without a ship binding behave as before (null = unbound).
 *
 * Immutable value object — replace rather than mutate.
 */
public final class TerminalDesignatorEntry {

    private final String worldKey;   // e.g. "minecraft:overworld"
    private final long   packedPos;  // BlockPos.asLong()
    private final TerminalDesignatorType type;

    /**
     * Optional ship binding. Null = no ship assigned (legacy / not applicable).
     * Set via: /terminal ship assign <shipId>  (while holding Terminal Designator Tool)
     */
    private final String shipId;

    // ── Constructors ──────────────────────────────────────────────────────────

    public TerminalDesignatorEntry(String worldKey, BlockPos pos,
                                   TerminalDesignatorType type, String shipId) {
        this.worldKey  = Objects.requireNonNull(worldKey, "worldKey");
        this.packedPos = pos.asLong();
        this.type      = Objects.requireNonNull(type, "type");
        this.shipId    = shipId; // nullable
    }

    /** Convenience constructor without shipId — shipId defaults to null. */
    public TerminalDesignatorEntry(String worldKey, BlockPos pos, TerminalDesignatorType type) {
        this(worldKey, pos, type, null);
    }

    /** Deserialization constructor (packed pos already known). */
    public TerminalDesignatorEntry(String worldKey, long packedPos,
                                   TerminalDesignatorType type, String shipId) {
        this.worldKey  = Objects.requireNonNull(worldKey, "worldKey");
        this.packedPos = packedPos;
        this.type      = Objects.requireNonNull(type, "type");
        this.shipId    = shipId;
    }

    /** Deserialization constructor without shipId. */
    public TerminalDesignatorEntry(String worldKey, long packedPos, TerminalDesignatorType type) {
        this(worldKey, packedPos, type, null);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getWorldKey()             { return worldKey; }
    public long   getPackedPos()            { return packedPos; }
    public BlockPos getPos()                { return BlockPos.fromLong(packedPos); }
    public TerminalDesignatorType getType() { return type; }

    /** The ship this terminal is bound to, or null if unbound. */
    public String getShipId()               { return shipId; }

    /** True if this terminal has a ship binding. */
    public boolean hasShipBinding()         { return shipId != null; }

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
                + ", type=" + type
                + ", ship=" + (shipId != null ? shipId : "unbound") + "}";
    }
}