package net.shard.seconddawnrp.tactical.data;

import net.minecraft.server.MinecraftServer;
import net.shard.seconddawnrp.SecondDawnRP;

import java.util.Optional;

/**
 * Manages the home ship's persistent position outside of combat encounters.
 *
 * Passive tick runs every 600 server ticks (30 seconds) via ServerTickEvents.
 * During an active encounter the combat tick owns home ship movement — passive
 * tick skips position update but still runs proximity checks and broadcasts.
 */
public class PassiveShipMovementService {

    private final SqlShipPositionRepository repository;

    private ShipState homeShipState = null;
    private ShipRegistryEntry homeShipEntry = null;

    public PassiveShipMovementService(SqlShipPositionRepository repository) {
        this.repository = repository;
    }

    // ── Startup ───────────────────────────────────────────────────────────────

    public void loadHomeShip() {
        if (SecondDawnRP.TACTICAL_SERVICE == null) return;

        // getShipRegistry() returns Map<String, ShipRegistryEntry> — use .values()
        homeShipEntry = SecondDawnRP.TACTICAL_SERVICE.getEncounterService()
                .getShipRegistry().values().stream()
                .filter(e -> e.isHomeShip())
                .findFirst()
                .orElse(null);

        if (homeShipEntry == null) {
            System.out.println("[SecondDawnRP] ShipPositionService: No home ship registered." +
                    " Set isHomeShip: true on a ship_registry entry to enable passive movement.");
            return;
        }

        // ShipClassDefinition uses a static registry
        Optional<ShipClassDefinition> classOpt =
                ShipClassDefinition.get(homeShipEntry.getShipClass());
        if (classOpt.isEmpty()) {
            System.err.println("[SecondDawnRP] ShipPositionService: Ship class '"
                    + homeShipEntry.getShipClass() + "' not found for home ship.");
            return;
        }
        ShipClassDefinition classDef = classOpt.get();

        // ShipState constructor: shipId, registryName, shipClass, encounterId,
        //                        faction, posX, posZ, heading, hullMax, shieldMax, powerBudget
        homeShipState = new ShipState(
                homeShipEntry.getShipId(),
                homeShipEntry.getRegistryName(),
                homeShipEntry.getShipClass(),
                "PASSIVE",
                homeShipEntry.getFaction(),
                homeShipEntry.getDefaultPosX(),
                homeShipEntry.getDefaultPosZ(),
                homeShipEntry.getDefaultHeading(),
                classDef.getHullMax(),
                classDef.getShieldMax(),
                classDef.getPowerCapacity());
        // combatId must not be null — used in ShipSnapshot serialization
        homeShipState.setCombatId(homeShipEntry.getShipId());

        Optional<SqlShipPositionRepository.ShipPositionSnapshot> saved =
                repository.load(homeShipEntry.getShipId());

        if (saved.isPresent()) {
            var snap = saved.get();
            homeShipState.setPosX(snap.posX());
            homeShipState.setPosZ(snap.posZ());
            homeShipState.setHeading(snap.heading());
            homeShipState.setSpeed(snap.speed());
            homeShipState.setWarpSpeed(snap.warpSpeed());
            homeShipState.setWarpCapable(snap.warpCapable());
            homeShipState.setTargetHeading(snap.targetHeading());
            homeShipState.setTargetSpeed(snap.targetSpeed());
            System.out.println("[SecondDawnRP] ShipPositionService: Restored home ship '"
                    + homeShipEntry.getRegistryName() + "' at ("
                    + String.format("%.1f", snap.posX()) + ", "
                    + String.format("%.1f", snap.posZ()) + ")"
                    + " warp " + snap.warpSpeed());
        } else {
            System.out.println("[SecondDawnRP] ShipPositionService: No saved position for '"
                    + homeShipEntry.getRegistryName() + "' — using default spawn position.");
            savePosition();
        }
    }

    // ── Shutdown ──────────────────────────────────────────────────────────────

    public void savePosition() {
        if (homeShipState != null) {
            repository.save(homeShipState);
        }
    }

    // ── Passive tick ──────────────────────────────────────────────────────────

    public void onPassiveTick(MinecraftServer server) {
        if (homeShipState == null || server == null) return;

        boolean encounterActive = isEncounterActiveForHomeShip();

        if (!encounterActive) {
            applyMovement();
            repository.save(homeShipState);
        }

        updateProximity();
        broadcastStellarNav(server);
    }

    // ── Movement ──────────────────────────────────────────────────────────────

    private void applyMovement() {
        float current  = homeShipState.getHeading();
        float target   = homeShipState.getTargetHeading();
        float diff     = ((target - current + 540) % 360) - 180;
        float turnRate = 5.0f;
        float newHeading = current + Math.max(-turnRate, Math.min(turnRate, diff));
        homeShipState.setHeading(((newHeading % 360) + 360) % 360);

        float currentSpeed = homeShipState.getSpeed();
        float targetSpeed  = homeShipState.getTargetSpeed();
        float speedDelta   = targetSpeed - currentSpeed;
        float maxAccel     = 20.0f;
        homeShipState.setSpeed(currentSpeed + Math.max(-maxAccel,
                Math.min(maxAccel, speedDelta)));

        double rad = Math.toRadians(homeShipState.getHeading());
        double spd = homeShipState.getSpeed();
        homeShipState.setPosX(homeShipState.getPosX() + Math.cos(rad) * spd);
        homeShipState.setPosZ(homeShipState.getPosZ() + Math.sin(rad) * spd);
    }

    // ── Proximity ─────────────────────────────────────────────────────────────

    private void updateProximity() {
        if (SecondDawnRP.LOCATION_SERVICE == null) return;
        double posX = homeShipState.getPosX();
        double posZ = homeShipState.getPosZ();
        int warpSpeed = homeShipState.getWarpSpeed();
        SecondDawnRP.LOCATION_SERVICE.getAllDimensions().forEach(def -> {
            if (!def.hasOrbitalZone()) return;
            SecondDawnRP.LOCATION_SERVICE.isReachable(
                    def.dimensionId(), posX, posZ, warpSpeed);
        });
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    private void broadcastStellarNav(MinecraftServer server) {
        for (var player : server.getPlayerManager().getPlayerList()) {
            net.shard.seconddawnrp.tactical.network.TacticalNetworking
                    .sendStellarNavUpdate(player, homeShipState);
        }
    }

    // ── Helm input ────────────────────────────────────────────────────────────

    public void applyHelmInput(float targetHeading, float targetSpeed) {
        if (homeShipState == null) return;
        homeShipState.setTargetHeading(targetHeading);
        homeShipState.setTargetSpeed(Math.max(0, targetSpeed));
    }

    public void applyWarpSpeed(int warpSpeed) {
        if (homeShipState == null) return;
        homeShipState.setWarpSpeed(Math.max(0, Math.min(9, warpSpeed)));
        homeShipState.setWarpCapable(warpSpeed > 0);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public ShipState getHomeShipState()         { return homeShipState; }
    public ShipRegistryEntry getHomeShipEntry() { return homeShipEntry; }
    public boolean hasHomeShip()                { return homeShipState != null; }

    // ── Internal ──────────────────────────────────────────────────────────────

    private boolean isEncounterActiveForHomeShip() {
        if (SecondDawnRP.TACTICAL_SERVICE == null || homeShipState == null) return false;
        return SecondDawnRP.TACTICAL_SERVICE.getEncounterService()
                .getAllEncounters().stream()
                .anyMatch(e -> e.getStatus() == EncounterState.Status.ACTIVE
                        && e.getShip(homeShipState.getShipId()).isPresent());
    }
}