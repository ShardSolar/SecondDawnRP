package net.shard.seconddawnrp.gmevent.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TriggerEntry {
    private final String entryId;
    private final String worldKey;
    private final long blockPosLong;
    private final UUID registeredByUuid;

    private TriggerMode triggerMode     = TriggerMode.RADIUS;
    private TriggerFireMode fireMode    = TriggerFireMode.PER_PLAYER;
    private int radiusBlocks            = 5;
    private int cooldownTicks           = 200;
    private boolean armed               = false;
    private boolean firstEntryFired     = false;
    private List<TriggerAction> actions = new ArrayList<>();

    public TriggerEntry(String entryId, String worldKey, long blockPosLong, UUID registeredByUuid) {
        this.entryId = entryId; this.worldKey = worldKey;
        this.blockPosLong = blockPosLong; this.registeredByUuid = registeredByUuid;
    }

    public void setTriggerMode(TriggerMode m)    { this.triggerMode = m; }
    public void setFireMode(TriggerFireMode m)   { this.fireMode = m; }
    public void setRadiusBlocks(int r)           { this.radiusBlocks = r; }
    public void setCooldownTicks(int t)          { this.cooldownTicks = t; }
    public void setArmed(boolean a)              { this.armed = a; }
    public void setFirstEntryFired(boolean f)    { this.firstEntryFired = f; }
    public void setActions(List<TriggerAction> a){ this.actions = new ArrayList<>(a); }

    public String getEntryId()              { return entryId; }
    public String getWorldKey()             { return worldKey; }
    public long getBlockPosLong()           { return blockPosLong; }
    public UUID getRegisteredByUuid()       { return registeredByUuid; }
    public TriggerMode getTriggerMode()     { return triggerMode; }
    public TriggerFireMode getFireMode()    { return fireMode; }
    public int getRadiusBlocks()            { return radiusBlocks; }
    public int getCooldownTicks()           { return cooldownTicks; }
    public boolean isArmed()                { return armed; }
    public boolean isFirstEntryFired()      { return firstEntryFired; }
    public List<TriggerAction> getActions() { return actions; }
}