package net.shard.seconddawnrp.tactical.data;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * A named vessel registered in the ship registry.
 * Links a ship name to a ShipClassDefinition and damage model location.
 *
 * V15 adds realBoundsMin / realBoundsMax — the 3D bounding box of the
 * real ship build in world space. Used by TacticalService.getPlayersOnShip()
 * to scope damage effects and the Engineering Pad component filter.
 * Set via: /tactical ship bounds <shipId> <pos1> <pos2>
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

    // ── V15: Real ship bounding box ───────────────────────────────────────────
    // Packed BlockPos longs for the min and max corners of the real build volume.
    // Both are 0 (BlockPos.ORIGIN) when not yet configured — hasBounds() checks this.
    // World key is assumed to be the same as realShipWorldKey.
    private long realBoundsMinLong = 0L;
    private long realBoundsMaxLong = 0L;

    public ShipRegistryEntry(String shipId, String registryName, String shipClass,
                             String faction) {
        this.shipId       = shipId;
        this.registryName = registryName;
        this.shipClass    = shipClass;
        this.faction      = faction;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

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
    public boolean isHomeShip()      { return isHomeShip; }

    public BlockPos getModelOrigin() {
        return modelOriginLong != 0 ? BlockPos.fromLong(modelOriginLong) : BlockPos.ORIGIN;
    }

    public BlockPos getRealShipOrigin() {
        return realShipOriginLong != 0 ? BlockPos.fromLong(realShipOriginLong) : BlockPos.ORIGIN;
    }

    public BlockPos getDefaultSpawn() {
        return defaultSpawnLong != 0 ? BlockPos.fromLong(defaultSpawnLong) : BlockPos.ORIGIN;
    }

    // ── V15: Bounds accessors ─────────────────────────────────────────────────

    /** Raw packed long for the minimum corner. Stored directly in DB. */
    public long getRealBoundsMinLong() { return realBoundsMinLong; }

    /** Raw packed long for the maximum corner. Stored directly in DB. */
    public long getRealBoundsMaxLong() { return realBoundsMaxLong; }

    /** Decoded minimum corner BlockPos. Returns ORIGIN if not configured. */
    public BlockPos getRealBoundsMin() {
        return realBoundsMinLong != 0 ? BlockPos.fromLong(realBoundsMinLong) : BlockPos.ORIGIN;
    }

    /** Decoded maximum corner BlockPos. Returns ORIGIN if not configured. */
    public BlockPos getRealBoundsMax() {
        return realBoundsMaxLong != 0 ? BlockPos.fromLong(realBoundsMaxLong) : BlockPos.ORIGIN;
    }

    /**
     * True when both bounding box corners have been set by an admin.
     * Bounds of 0,0,0 / 0,0,0 is the sentinel for "not configured yet".
     */
    public boolean hasBounds() {
        return realBoundsMinLong != 0L || realBoundsMaxLong != 0L;
    }

    /**
     * Returns a Minecraft Box for this ship's real build volume.
     * The box is expanded by 0.5 on all axes so players standing exactly
     * on the boundary are included. Returns null if bounds not configured.
     *
     * Note: Box works in world-space doubles; BlockPos corners are treated
     * as inclusive min and exclusive max (pos + 1 on each axis covers the block).
     */
    public Box getRealBoundsBox() {
        if (!hasBounds()) return null;
        BlockPos min = getRealBoundsMin();
        BlockPos max = getRealBoundsMax();
        // Ensure min ≤ max on each axis regardless of registration order
        int minX = Math.min(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxX = Math.max(min.getX(), max.getX()) + 1; // +1 to cover the max block
        int maxY = Math.max(min.getY(), max.getY()) + 1;
        int maxZ = Math.max(min.getZ(), max.getZ()) + 1;
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Returns true if the given world-space position is inside this ship's
     * registered bounding box. Always returns false if bounds are not configured.
     *
     * Used by TacticalService.getPlayersOnShip() and the Engineering Pad filter.
     */
    public boolean containsPosition(double x, double y, double z) {
        Box box = getRealBoundsBox();
        if (box == null) return false;
        return box.contains(x, y, z);
    }

    // ── Mutators ──────────────────────────────────────────────────────────────

    public void setRegistryName(String n)     { this.registryName = n; }
    public void setFaction(String f)          { this.faction = f; }
    public void setShipClass(String c)        { this.shipClass = c; }
    public void setModelWorldKey(String k)    { this.modelWorldKey = k; }
    public void setModelOrigin(BlockPos p)    { this.modelOriginLong = p.asLong(); }
    public void setRealShipWorldKey(String k) { this.realShipWorldKey = k; }
    public void setRealShipOrigin(BlockPos p) { this.realShipOriginLong = p.asLong(); }
    public void setDefaultSpawn(BlockPos p, String worldKey) {
        this.defaultSpawnLong     = p.asLong();
        this.defaultSpawnWorldKey = worldKey;
    }
    public void setDefaultPosition(double x, double z, float heading) {
        this.defaultPosX    = x;
        this.defaultPosZ    = z;
        this.defaultHeading = heading;
    }
    public void setHomeShip(boolean h) { this.isHomeShip = h; }

    /**
     * Set the real ship bounding box from two corner BlockPos values.
     * Order does not matter — getRealBoundsBox() normalises min/max.
     * Admin sets this via: /tactical ship bounds <shipId> <pos1> <pos2>
     */
    public void setRealBounds(BlockPos corner1, BlockPos corner2) {
        this.realBoundsMinLong = corner1.asLong();
        this.realBoundsMaxLong = corner2.asLong();
    }

    /** Direct setter used by TacticalRepository when loading from DB. */
    public void setRealBoundsMinLong(long minLong) { this.realBoundsMinLong = minLong; }

    /** Direct setter used by TacticalRepository when loading from DB. */
    public void setRealBoundsMaxLong(long maxLong) { this.realBoundsMaxLong = maxLong; }
}