package net.shard.seconddawnrp.tactical.data;

/**
 * The logical representation of a single ship in an encounter.
 * Source of truth for all combat resolution. Never tied to block coordinates.
 */
public class ShipState {

    private final String shipId;
    private String registryName;
    private String combatId;
    private String shipClass;
    private String encounterId;
    private String faction; // FRIENDLY, HOSTILE

    // Position (logical units, not block coords)
    private double posX;
    private double posZ;
    private double posY; // Phase 14 hook — inactive

    // Movement
    private float heading;       // 0-360 degrees
    private float targetHeading;
    private float speed;
    private float targetSpeed;

    // Warp
    private int warpSpeed;       // 0 = sublight
    private boolean warpCapable;

    // Hull
    private int hullIntegrity;
    private int hullMax;

    // Shields (four facings)
    private int shieldFore;
    private int shieldAft;
    private int shieldPort;
    private int shieldStarboard;
    private int shieldForeSuppression;   // ticks remaining in regen suppression
    private int shieldAftSuppression;
    private int shieldPortSuppression;
    private int shieldStarboardSuppression;

    // Power
    private int powerOutput;     // read from WarpCoreService each tick
    private int powerBudget;     // after warp state penalties
    private boolean manualPowerAllocation;
    private boolean manualPowerOverride; // GM override — skips warp core read
    private int weaponsPower;
    private int shieldsPower;
    private int enginesPower;
    private int sensorsPower;

    // Weapons
    private int torpedoCount;

    // Control
    private ControlMode controlMode;
    private boolean destroyed;

    public enum ControlMode { PLAYER_CREW, GM_MANUAL }

    public ShipState(String shipId, String registryName, String shipClass,
                     String encounterId, String faction,
                     double posX, double posZ, float heading,
                     int hullMax, int shieldMax, int powerBudget) {
        this.shipId         = shipId;
        this.registryName   = registryName;
        this.shipClass      = shipClass;
        this.encounterId    = encounterId;
        this.faction        = faction;
        this.posX           = posX;
        this.posZ           = posZ;
        this.heading        = heading;
        this.targetHeading  = heading;
        this.speed          = 0;
        this.targetSpeed    = 0;
        this.warpSpeed      = 0;
        this.warpCapable    = false;
        this.hullMax        = hullMax;
        this.hullIntegrity  = hullMax;
        this.shieldFore     = shieldMax;
        this.shieldAft      = shieldMax;
        this.shieldPort     = shieldMax;
        this.shieldStarboard = shieldMax;
        this.powerBudget    = powerBudget;
        this.powerOutput    = powerBudget;
        this.weaponsPower   = powerBudget / 4;
        this.shieldsPower   = powerBudget / 4;
        this.enginesPower   = powerBudget / 4;
        this.sensorsPower   = powerBudget / 4;
        this.manualPowerAllocation = false;
        this.controlMode    = ControlMode.GM_MANUAL;
        this.destroyed      = false;
    }

    // ── Hull state ────────────────────────────────────────────────────────────

    public enum HullState { NOMINAL, DAMAGED, CRITICAL, FAILING, DESTROYED }

    public HullState getHullState() {
        if (destroyed || hullIntegrity <= 0) return HullState.DESTROYED;
        int pct = hullIntegrity * 100 / Math.max(1, hullMax);
        if (pct >= 76) return HullState.NOMINAL;
        if (pct >= 51) return HullState.DAMAGED;
        if (pct >= 26) return HullState.CRITICAL;
        return HullState.FAILING;
    }

    public float getHullPercent() {
        return (float) hullIntegrity / Math.max(1, hullMax);
    }

    // ── Shield helpers ────────────────────────────────────────────────────────

    public enum ShieldFacing { FORE, AFT, PORT, STARBOARD }

    public int getShield(ShieldFacing facing) {
        return switch (facing) {
            case FORE      -> shieldFore;
            case AFT       -> shieldAft;
            case PORT      -> shieldPort;
            case STARBOARD -> shieldStarboard;
        };
    }

    public void setShield(ShieldFacing facing, int value) {
        int v = Math.max(0, value);
        switch (facing) {
            case FORE      -> shieldFore = v;
            case AFT       -> shieldAft = v;
            case PORT      -> shieldPort = v;
            case STARBOARD -> shieldStarboard = v;
        }
    }

    public int getSuppression(ShieldFacing facing) {
        return switch (facing) {
            case FORE      -> shieldForeSuppression;
            case AFT       -> shieldAftSuppression;
            case PORT      -> shieldPortSuppression;
            case STARBOARD -> shieldStarboardSuppression;
        };
    }

    public void setSuppression(ShieldFacing facing, int ticks) {
        switch (facing) {
            case FORE      -> shieldForeSuppression = ticks;
            case AFT       -> shieldAftSuppression = ticks;
            case PORT      -> shieldPortSuppression = ticks;
            case STARBOARD -> shieldStarboardSuppression = ticks;
        }
    }

    public void tickSuppression() {
        if (shieldForeSuppression > 0)       shieldForeSuppression--;
        if (shieldAftSuppression > 0)        shieldAftSuppression--;
        if (shieldPortSuppression > 0)       shieldPortSuppression--;
        if (shieldStarboardSuppression > 0)  shieldStarboardSuppression--;
    }

    /** Determine which shield facing an attacker hits based on relative angle. */
    public ShieldFacing getImpactFacing(double attackerX, double attackerZ) {
        double dx = attackerX - posX;
        double dz = attackerZ - posZ;
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        // Normalize to ship's reference frame
        double relative = ((angle - heading) % 360 + 360) % 360;
        if (relative < 45 || relative >= 315) return ShieldFacing.FORE;
        if (relative < 135) return ShieldFacing.PORT;
        if (relative < 225) return ShieldFacing.AFT;
        return ShieldFacing.STARBOARD;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String getShipId()          { return shipId; }
    public String getRegistryName()    { return registryName; }
    public String getCombatId()        { return combatId; }
    public String getShipClass()       { return shipClass; }
    public String getEncounterId()     { return encounterId; }
    public String getFaction()         { return faction; }
    public double getPosX()            { return posX; }
    public double getPosZ()            { return posZ; }
    public float  getHeading()         { return heading; }
    public float  getTargetHeading()   { return targetHeading; }
    public float  getSpeed()           { return speed; }
    public float  getTargetSpeed()     { return targetSpeed; }
    public int    getWarpSpeed()       { return warpSpeed; }
    public boolean isWarpCapable()     { return warpCapable; }
    public int    getHullIntegrity()   { return hullIntegrity; }
    public int    getHullMax()         { return hullMax; }
    public int    getPowerOutput()     { return powerOutput; }
    public int    getPowerBudget()     { return powerBudget; }
    public boolean isManualPower()     { return manualPowerAllocation; }
    public boolean isManualPowerOverride() { return manualPowerOverride; }
    public int    getWeaponsPower()    { return weaponsPower; }
    public int    getShieldsPower()    { return shieldsPower; }
    public int    getEnginesPower()    { return enginesPower; }
    public int    getSensorsPower()    { return sensorsPower; }
    public int    getTorpedoCount()    { return torpedoCount; }
    public ControlMode getControlMode() { return controlMode; }
    public boolean isDestroyed()       { return destroyed; }

    public void setCombatId(String id)             { this.combatId = id; }
    public void setFaction(String f)               { this.faction = f; }
    public void setPosX(double x)                  { this.posX = x; }
    public void setPosZ(double z)                  { this.posZ = z; }
    public void setHeading(float h)                { this.heading = ((h % 360) + 360) % 360; }
    public void setTargetHeading(float h)          { this.targetHeading = ((h % 360) + 360) % 360; }
    public void setSpeed(float s)                  { this.speed = s; }
    public void setTargetSpeed(float s)            { this.targetSpeed = s; }
    public void setWarpSpeed(int w)                { this.warpSpeed = w; }
    public void setWarpCapable(boolean b)          { this.warpCapable = b; }
    public void setHullIntegrity(int h)            { this.hullIntegrity = Math.max(0, h); }
    public void setPowerOutput(int p)              { this.powerOutput = p; }
    public void setPowerBudget(int p)              { this.powerBudget = p; }
    public void setManualPower(boolean b)          { this.manualPowerAllocation = b; }
    public void setManualPowerOverride(boolean b)  { this.manualPowerOverride = b; }
    public void setWeaponsPower(int p)             { this.weaponsPower = p; }
    public void setShieldsPower(int p)             { this.shieldsPower = p; }
    public void setEnginesPower(int p)             { this.enginesPower = p; }
    public void setSensorsPower(int p)             { this.sensorsPower = p; }
    public void setTorpedoCount(int t)             { this.torpedoCount = Math.max(0, t); }
    public void setControlMode(ControlMode m)      { this.controlMode = m; }
    public void setDestroyed(boolean b)            { this.destroyed = b; }
}