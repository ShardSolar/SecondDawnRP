package net.shard.seconddawnrp.degradation.data;

import java.util.UUID;

/**
 * A registered, maintainable block in the world.
 *
 * Identity key: worldKey + blockPos.
 * Component IDs are auto-generated server-side from the block type and position.
 *
 * V15 adds shipId — links a component to a specific registered vessel.
 * null = unowned / global (legacy components registered before V15).
 * Non-null = component belongs to that shipId and will only appear in the
 * Engineering Pad when the viewing player is inside that ship's bounds.
 */
public class ComponentEntry {

    private final String componentId;
    private final String worldKey;
    private final long blockPosLong;
    private final String blockTypeId;
    private final String displayName;

    private int health;
    private ComponentStatus status;
    private long lastDrainTickMs;
    private long lastTaskGeneratedMs;
    private UUID registeredByUuid;

    private String repairItemId;   // null = use global default
    private int repairItemCount;   // 0 = use global default

    /**
     * True when the registered block no longer exists at the registered position.
     * The component remains registered so engineering can restore it.
     */
    private boolean missingBlock;

    /**
     * The ship this component belongs to. Null = unowned / global.
     * Set at registration time by the admin via the Component Registration Tool.
     * Used to filter the Engineering Pad per-ship and to cross-reference
     * zone damage events against affected components.
     */
    private String shipId;

    // ── Full constructor (used by repository on load) ──────────────────────────

    public ComponentEntry(
            String componentId,
            String worldKey,
            long blockPosLong,
            String blockTypeId,
            String displayName,
            int health,
            ComponentStatus status,
            long lastDrainTickMs,
            long lastTaskGeneratedMs,
            UUID registeredByUuid,
            String repairItemId,
            int repairItemCount,
            boolean missingBlock,
            String shipId
    ) {
        this.componentId = componentId;
        this.worldKey = worldKey;
        this.blockPosLong = blockPosLong;
        this.blockTypeId = blockTypeId;
        this.displayName = displayName;
        this.health = Math.max(0, Math.min(100, health));
        this.status = status != null ? status : ComponentStatus.fromHealth(this.health);
        this.lastDrainTickMs = lastDrainTickMs;
        this.lastTaskGeneratedMs = lastTaskGeneratedMs;
        this.registeredByUuid = registeredByUuid;
        this.repairItemId = repairItemId;
        this.repairItemCount = repairItemCount;
        this.missingBlock = missingBlock;
        this.shipId = shipId;

        normalizeState();
    }

    /**
     * Legacy constructor without shipId — shipId defaults to null.
     * Keeps existing call sites compiling without changes while the
     * ecosystem migrates to the full constructor.
     */
    public ComponentEntry(
            String componentId,
            String worldKey,
            long blockPosLong,
            String blockTypeId,
            String displayName,
            int health,
            ComponentStatus status,
            long lastDrainTickMs,
            long lastTaskGeneratedMs,
            UUID registeredByUuid,
            String repairItemId,
            int repairItemCount,
            boolean missingBlock
    ) {
        this(componentId, worldKey, blockPosLong, blockTypeId, displayName,
                health, status, lastDrainTickMs, lastTaskGeneratedMs,
                registeredByUuid, repairItemId, repairItemCount, missingBlock,
                null /* shipId — unowned */);
    }

    // ── Mutators ─────────────────────────────────────────────────────────────

    public void setHealth(int health) {
        this.health = Math.max(0, Math.min(100, health));
        this.status = ComponentStatus.fromHealth(this.health);
        if (this.health > 0) {
            if (this.missingBlock) {
                this.status = ComponentStatus.OFFLINE;
            }
        }
    }

    public void setLastDrainTickMs(long lastDrainTickMs) {
        this.lastDrainTickMs = lastDrainTickMs;
    }

    public void setLastTaskGeneratedMs(long lastTaskGeneratedMs) {
        this.lastTaskGeneratedMs = lastTaskGeneratedMs;
    }

    public void setStatus(ComponentStatus status) {
        this.status = status != null ? status : ComponentStatus.fromHealth(this.health);
        normalizeState();
    }

    public void setRepairItemId(String repairItemId) {
        this.repairItemId = repairItemId;
    }

    public void setRepairItemCount(int repairItemCount) {
        this.repairItemCount = repairItemCount;
    }

    public void setMissingBlock(boolean missingBlock) {
        this.missingBlock = missingBlock;
        normalizeState();
    }

    /**
     * Assign or reassign this component to a ship.
     * Pass null to clear the ship binding (make it global/unowned).
     */
    public void setShipId(String shipId) {
        this.shipId = shipId;
    }

    /**
     * Forces consistency between health, status, and missingBlock.
     */
    public void normalizeState() {
        this.health = Math.max(0, Math.min(100, this.health));

        if (this.missingBlock) {
            this.health = 0;
            this.status = ComponentStatus.OFFLINE;
            return;
        }

        this.status = ComponentStatus.fromHealth(this.health);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getComponentId()       { return componentId; }
    public String getWorldKey()          { return worldKey; }
    public long getBlockPosLong()        { return blockPosLong; }
    public String getBlockTypeId()       { return blockTypeId; }
    public String getDisplayName()       { return displayName; }
    public int getHealth()               { return health; }
    public ComponentStatus getStatus()   { return status; }
    public long getLastDrainTickMs()     { return lastDrainTickMs; }
    public long getLastTaskGeneratedMs() { return lastTaskGeneratedMs; }
    public UUID getRegisteredByUuid()    { return registeredByUuid; }
    public String getRepairItemId()      { return repairItemId; }
    public int getRepairItemCount()      { return repairItemCount; }
    public boolean isMissingBlock()      { return missingBlock; }

    /**
     * The ship this component is registered to, or null if unowned/global.
     * Global components appear in ALL Engineering Pad views regardless of ship context.
     */
    public String getShipId()            { return shipId; }

    /**
     * True if this component is bound to a specific ship.
     * False for legacy / global components registered before V15.
     */
    public boolean hasShipBinding()      { return shipId != null; }

    @Override
    public String toString() {
        return "ComponentEntry{id='" + componentId
                + "', health=" + health
                + ", status=" + status
                + ", missingBlock=" + missingBlock
                + ", shipId=" + (shipId != null ? shipId : "none")
                + ", world='" + worldKey
                + "', pos=" + blockPosLong + "'}";
    }
}