package net.shard.seconddawnrp.gmevent.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A registered Environmental Effect Block entry.
 * All configuration for one block instance.
 */
public class EnvironmentalEffectEntry {

    private final String entryId;
    private final String worldKey;
    private final long blockPosLong;
    private final UUID registeredByUuid;

    private List<String> vanillaEffects = new ArrayList<>();
    private String medicalConditionId = null;
    private String medicalConditionSeverity = "Moderate";
    private int radiusBlocks = 8;
    private LingerMode lingerMode = LingerMode.IMMEDIATE;
    private int lingerDurationTicks = 100;
    private EnvFireMode fireMode = EnvFireMode.CONTINUOUS;
    private int onEntryCooldownTicks = 200;
    private EnvVisibility visibility = EnvVisibility.VISIBLE;
    private boolean active = false;

    public EnvironmentalEffectEntry(String entryId, String worldKey,
                                    long blockPosLong, UUID registeredByUuid) {
        this.entryId = entryId;
        this.worldKey = worldKey;
        this.blockPosLong = blockPosLong;
        this.registeredByUuid = registeredByUuid;
    }

    public void setVanillaEffects(List<String> e)      { this.vanillaEffects = new ArrayList<>(e); }
    public void setMedicalConditionId(String id)        { this.medicalConditionId = id; }
    public void setMedicalConditionSeverity(String s)   { this.medicalConditionSeverity = s; }
    public void setRadiusBlocks(int r)                  { this.radiusBlocks = r; }
    public void setLingerMode(LingerMode m)             { this.lingerMode = m; }
    public void setLingerDurationTicks(int t)           { this.lingerDurationTicks = t; }
    public void setFireMode(EnvFireMode m)              { this.fireMode = m; }
    public void setOnEntryCooldownTicks(int t)          { this.onEntryCooldownTicks = t; }
    public void setVisibility(EnvVisibility v)          { this.visibility = v; }
    public void setActive(boolean a)                    { this.active = a; }

    public String getEntryId()                  { return entryId; }
    public String getWorldKey()                 { return worldKey; }
    public long getBlockPosLong()               { return blockPosLong; }
    public UUID getRegisteredByUuid()           { return registeredByUuid; }
    public List<String> getVanillaEffects()     { return vanillaEffects; }
    public String getMedicalConditionId()       { return medicalConditionId; }
    public String getMedicalConditionSeverity() { return medicalConditionSeverity; }
    public int getRadiusBlocks()                { return radiusBlocks; }
    public LingerMode getLingerMode()           { return lingerMode; }
    public int getLingerDurationTicks()         { return lingerDurationTicks; }
    public EnvFireMode getFireMode()            { return fireMode; }
    public int getOnEntryCooldownTicks()        { return onEntryCooldownTicks; }
    public EnvVisibility getVisibility()        { return visibility; }
    public boolean isActive()                   { return active; }
}