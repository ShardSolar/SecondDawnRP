package net.shard.seconddawnrp.degradation.data;

public enum ComponentStatus {

    /** Health 76–100. No drain acceleration. Fully functional. */
    NOMINAL,

    /** Health 51–75. Warning state. Still functional. */
    DEGRADED,

    /** Health 26–50. Functionally locked, repair required, auto-task generated. */
    CRITICAL,

    /** Health 0–25. Fully failed/offline, same lock plus severe failure effects. */
    OFFLINE;

    public static ComponentStatus fromHealth(int health) {
        if (health > 75) return NOMINAL;
        if (health > 50) return DEGRADED;
        if (health > 25) return CRITICAL;
        return OFFLINE;
    }
}