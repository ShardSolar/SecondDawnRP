package net.shard.seconddawnrp.gmevent.data;

public enum EnvFireMode {
    /** Reapplies the effect each tick while in radius. */
    CONTINUOUS,
    /** Applies once per player entry, with configurable cooldown before re-trigger. */
    ON_ENTRY
}