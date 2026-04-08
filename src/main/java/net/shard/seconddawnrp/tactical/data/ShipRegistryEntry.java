package net.shard.seconddawnrp.tactical.data;

import net.minecraft.util.math.BlockPos;

/**
 * A named vessel registered in the ship registry.
 * Links a ship name to a ShipClassDefinition and damage model location.
 */
public class ShipRegistryEntry {

    private final String shipId;
    private String registryName;
    private String shipClass;
    private String faction;        // FRIENDLY or HOSTILE default

    // Damage model — scale model location
    private String modelWorldKey;
    private long   modelOriginLong;

    // Real ship — block positions for physical damage
    private String realShipWorldKey;
    private long   realShipOriginLong;

    // Default crew spawn on this vessel
    private long   defaultSpawnLong;
    private String defaultSpawnWorldKey;

    // Starting position on Tactical map
    private double defaultPosX;
    private double defaultPosZ;
    private float  defaultHeading;

    // Home ship flag — exactly one ship per server has this set.
    // Its ShipState is always loaded and position is persisted to ship_position table.
    private boolean isHomeShip = false;

    public ShipRegistryEntry(String shipId, String registryName, String shipClass,
                             String faction) {
        this.shipId       = shipId;
        this.registryName = registryName;
        this.shipClass    = shipClass;
        this.faction      = faction;
    }

    public String getShipId()        { return shipId; }
    public String getRegistryName()  { return registryName; }
    public String getShipClass()     { return shipClass; }
    public String getFaction()       { return faction; }
    public String getModelWorldKey() { return modelWorldKey; }
    public String getRealShipWorldKey() { return realShipWorldKey; }
    public String getDefaultSpawnWorldKey() { return defaultSpawnWorldKey; }
    public double getDefaultPosX()   { return defaultPosX; }
    public double getDefaultPosZ()   { return defaultPosZ; }
    public float  getDefaultHeading() { return defaultHeading; }

    public BlockPos getModelOrigin() {
        return modelOriginLong != 0 ? BlockPos.fromLong(modelOriginLong) : BlockPos.ORIGIN;
    }

    public BlockPos getRealShipOrigin() {
        return realShipOriginLong != 0 ? BlockPos.fromLong(realShipOriginLong) : BlockPos.ORIGIN;
    }

    public BlockPos getDefaultSpawn() {
        return defaultSpawnLong != 0 ? BlockPos.fromLong(defaultSpawnLong) : BlockPos.ORIGIN;
    }

    public void setRegistryName(String n)     { this.registryName = n; }
    public void setFaction(String f)          { this.faction = f; }
    public void setShipClass(String c)        { this.shipClass = c; }
    public void setModelWorldKey(String k)    { this.modelWorldKey = k; }
    public void setModelOrigin(BlockPos p)    { this.modelOriginLong = p.asLong(); }
    public void setRealShipWorldKey(String k) { this.realShipWorldKey = k; }
    public void setRealShipOrigin(BlockPos p) { this.realShipOriginLong = p.asLong(); }
    public void setDefaultSpawn(BlockPos p, String worldKey) {
        this.defaultSpawnLong    = p.asLong();
        this.defaultSpawnWorldKey = worldKey;
    }
    public void setDefaultPosition(double x, double z, float heading) {
        this.defaultPosX    = x;
        this.defaultPosZ    = z;
        this.defaultHeading = heading;
    }

    public boolean isHomeShip()          { return isHomeShip; }
    public void setHomeShip(boolean h)   { this.isHomeShip = h; }
}