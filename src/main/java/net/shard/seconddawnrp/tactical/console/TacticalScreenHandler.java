package net.shard.seconddawnrp.tactical.console;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.shard.seconddawnrp.registry.ModScreenHandlers;
import net.shard.seconddawnrp.tactical.network.TacticalNetworking.ShipSnapshot;
import net.shard.seconddawnrp.tactical.network.TacticalNetworking.EncounterUpdatePayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen handler for the Tactical Console.
 * Holds the current encounter snapshot and the station filter
 * which controls which panels are interactive.
 */
public class TacticalScreenHandler extends ScreenHandler {

    /**
     * Which station this screen was opened from.
     * Controls which panels are interactive and which are read-only.
     *
     * FULL      — Command/GM: all panels interactive
     * HELM      — Navigation interactive, Weapons/Shields locked
     * WEAPONS   — Weapons interactive, Navigation/Shields locked
     * SHIELDS   — Shields interactive, Navigation/Weapons locked
     * SENSORS   — Navigation read-only, all others locked
     */
    public enum StationFilter { FULL, HELM, WEAPONS, SHIELDS, SENSORS }

    private StationFilter stationFilter = StationFilter.FULL;

    private String encounterId    = "";
    private String encounterStatus = "STANDBY";
    private List<ShipSnapshot> ships = new ArrayList<>();
    private List<String> combatLog   = new ArrayList<>();

    private String selectedShipId   = null;
    private String selectedTargetId = null;
    private ShipSnapshot playerShip = null;
    private long lastUpdateMs = 0;

    // Standby map data
    private double shipOriginX = 0;
    private double shipOriginZ = 0;
    private List<net.shard.seconddawnrp.tactical.network.TacticalNetworking.AnomalyMarker>
            anomalies = new java.util.ArrayList<>();

    // Stellar navigation state — home ship position outside combat
    private double  stellarPosX          = 0;
    private double  stellarPosZ          = 0;
    private float   stellarHeading       = 0;
    private float   stellarSpeed         = 0;
    private float   stellarTargetHeading = 0;
    private float   stellarTargetSpeed   = 0;
    private int     stellarWarpSpeed     = 0;
    private boolean stellarWarpCapable   = false;
    private List<net.shard.seconddawnrp.tactical.network.TacticalNetworking.OrbitalZoneData>
            orbitalZones = new java.util.ArrayList<>();
    private boolean hasStellarData       = false;

    // Home ship snapshot for standby weapons/shields panels
    private net.shard.seconddawnrp.tactical.network.TacticalNetworking.ShipSnapshot
            homeShipSnapshot = null;

    // Client-side constructor (no station — defaults to FULL)
    public TacticalScreenHandler(int syncId, PlayerInventory inventory) {
        super(ModScreenHandlers.TACTICAL_SCREEN, syncId);
    }

    // Client-side constructor with station filter
    public TacticalScreenHandler(int syncId, PlayerInventory inventory,
                                 StationFilter stationFilter) {
        super(ModScreenHandlers.TACTICAL_SCREEN, syncId);
        this.stationFilter = stationFilter;
    }

    public void applyUpdate(EncounterUpdatePayload payload) {
        this.encounterId     = payload.encounterId();
        this.encounterStatus = payload.status();
        this.ships           = new ArrayList<>(payload.ships());
        this.combatLog       = new ArrayList<>(payload.recentLog());
        this.lastUpdateMs    = System.currentTimeMillis();

        this.playerShip = ships.stream()
                .filter(s -> "FRIENDLY".equals(s.faction()) && !s.destroyed())
                .findFirst()
                .orElse(null);

        if (selectedShipId == null && playerShip != null)
            selectedShipId = playerShip.shipId();
    }

    /** Apply extra data from the open packet (anomalies, ship origin). */
    public void applyOpenData(
            net.shard.seconddawnrp.tactical.network.TacticalNetworking.OpenTacticalPayload payload) {
        this.shipOriginX = payload.shipOriginX();
        this.shipOriginZ = payload.shipOriginZ();
        this.anomalies   = new java.util.ArrayList<>(payload.anomalies());
    }

    /**
     * Live update pushed every ~10s and on GM anomaly activate/deactivate.
     * Replaces the anomaly list and ship origin in place without reopening the screen.
     */
    public void applyStandbyUpdate(double originX, double originZ,
                                   java.util.List<net.shard.seconddawnrp.tactical.network.TacticalNetworking.AnomalyMarker>
                                           updatedAnomalies) {
        this.shipOriginX = originX;
        this.shipOriginZ = originZ;
        this.anomalies   = new java.util.ArrayList<>(updatedAnomalies);
    }

    /**
     * Full stellar navigation update from ShipPositionService passive tick.
     * Contains home ship position, heading, speed, warp state, and orbital zones.
     */
    public void applyStellarNavUpdate(
            net.shard.seconddawnrp.tactical.network.TacticalNetworking.StellarNavUpdatePayload p) {
        this.stellarPosX          = p.posX();
        this.stellarPosZ          = p.posZ();
        this.stellarHeading       = p.heading();
        this.stellarSpeed         = p.speed();
        this.stellarTargetHeading = p.targetHeading();
        this.stellarTargetSpeed   = p.targetSpeed();
        this.stellarWarpSpeed     = p.warpSpeed();
        this.stellarWarpCapable   = p.warpCapable();
        this.orbitalZones         = new java.util.ArrayList<>(p.orbitalZones());
        this.anomalies            = new java.util.ArrayList<>(p.anomalies());
        this.shipOriginX          = p.shipOriginX();
        this.shipOriginZ          = p.shipOriginZ();
        this.homeShipSnapshot     = p.homeShipSnapshot();
        this.hasStellarData       = true;
    }

    // ── Station permission helpers ────────────────────────────────────────────

    /** Navigation panel is interactive (helm input, evasive) */
    public boolean canUseHelm() {
        return stationFilter == StationFilter.FULL || stationFilter == StationFilter.HELM;
    }

    /** Weapons panel is interactive (fire phasers/torpedoes) */
    public boolean canUseWeapons() {
        return stationFilter == StationFilter.FULL || stationFilter == StationFilter.WEAPONS;
    }

    /** Shields panel is interactive (redistribute) */
    public boolean canUseShields() {
        return stationFilter == StationFilter.FULL || stationFilter == StationFilter.SHIELDS;
    }

    /** Navigation panel visible but read-only (sensors officer sees the map) */
    public boolean canSeeNavigation() {
        return stationFilter != StationFilter.WEAPONS && stationFilter != StationFilter.SHIELDS;
    }

    /** Status panel always visible to all stations */
    public boolean canSeeStatus() { return true; }

    // ── Getters ───────────────────────────────────────────────────────────────

    public StationFilter getStationFilter()  { return stationFilter; }
    public String getEncounterId()           { return encounterId; }
    public String getEncounterStatus()       { return encounterStatus; }
    public double getShipOriginX()           { return shipOriginX; }
    public double getShipOriginZ()           { return shipOriginZ; }
    public java.util.List<net.shard.seconddawnrp.tactical.network.TacticalNetworking.AnomalyMarker>
    getAnomalies()                   { return anomalies; }

    // Stellar navigation getters
    public double  getStellarPosX()          { return stellarPosX; }
    public double  getStellarPosZ()          { return stellarPosZ; }
    public float   getStellarHeading()       { return stellarHeading; }
    public float   getStellarSpeed()         { return stellarSpeed; }
    public float   getStellarTargetHeading() { return stellarTargetHeading; }
    public float   getStellarTargetSpeed()   { return stellarTargetSpeed; }
    public int     getStellarWarpSpeed()     { return stellarWarpSpeed; }
    public boolean isStellarWarpCapable()    { return stellarWarpCapable; }
    public boolean hasStellarData()          { return hasStellarData; }
    public java.util.List<net.shard.seconddawnrp.tactical.network.TacticalNetworking.OrbitalZoneData>
    getOrbitalZones()                { return orbitalZones; }

    public net.shard.seconddawnrp.tactical.network.TacticalNetworking.ShipSnapshot
    getHomeShipSnapshot()            { return homeShipSnapshot; }
    public List<ShipSnapshot> getShips()     { return ships; }
    public List<String> getCombatLog()       { return combatLog; }
    public ShipSnapshot getPlayerShip()      { return playerShip; }
    public String getSelectedShipId()        { return selectedShipId; }
    public String getSelectedTargetId()      { return selectedTargetId; }
    public long getLastUpdateMs()            { return lastUpdateMs; }
    public boolean isStandby()               { return "STANDBY".equals(encounterStatus) || ships.isEmpty(); }

    public void setSelectedShipId(String id)   { this.selectedShipId = id; }
    public void setSelectedTargetId(String id) { this.selectedTargetId = id; }

    public List<ShipSnapshot> getFriendlyShips() {
        return ships.stream().filter(s -> "FRIENDLY".equals(s.faction())).toList();
    }

    public List<ShipSnapshot> getHostileShips() {
        return ships.stream().filter(s -> "HOSTILE".equals(s.faction())).toList();
    }

    public ShipSnapshot getShip(String shipId) {
        return ships.stream().filter(s -> s.shipId().equals(shipId)).findFirst().orElse(null);
    }

    @Override public boolean canUse(PlayerEntity player) { return true; }
    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
}