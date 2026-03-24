package net.shard.seconddawnrp.gmevent.data;

public enum LingerMode {
    /** Effect clears the moment the player exits the radius. */
    IMMEDIATE,
    /** Effect continues for a configurable duration after exit. */
    LINGER,
    /** Effect stays until the source block is deactivated. */
    PERSISTENT
}