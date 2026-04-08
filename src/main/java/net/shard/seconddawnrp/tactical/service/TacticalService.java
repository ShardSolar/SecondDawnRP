package net.shard.seconddawnrp.tactical.service;

import net.minecraft.server.MinecraftServer;
import net.shard.seconddawnrp.tactical.data.*;
import net.shard.seconddawnrp.tactical.network.TacticalNetworking;

import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Encounter tick orchestrator. Runs on END_SERVER_TICK every 100 ticks (5 seconds).
 *
 * Tick order:
 *   1. Power      — warp core output → power budget distribution
 *   2. Penalties  — destroyed zone effects clamped onto live stats
 *   3. Movement   — heading/speed/position interpolation
 *   4. Shields    — suppression tick + regen
 *   5. Weapons    — hardpoint cooldown ticks
 *   6. Fire       — queued weapon fire resolved
 *   7. Warp       — warp state update
 *   8. Hull check — destroyed ships handled
 *   9. Broadcast  — encounter state pushed to all open tactical screens
 */
public class TacticalService {

    private static final int TICK_INTERVAL = 100; // 5 seconds at 20 TPS

    private final EncounterService    encounterService;
    private final ShipMovementService movementService;
    private final ShieldService       shieldService;
    private final WeaponService       weaponService;
    private final PowerService        powerService;
    private final HullDamageService   hullDamageService;
    private final WarpService         warpService;

    private MinecraftServer server;

    private final ConcurrentLinkedQueue<PendingFireAction> pendingFire =
            new ConcurrentLinkedQueue<>();

    public TacticalService(EncounterService encounterService,
                           TacticalRepository repository) {
        this.encounterService  = encounterService;
        this.hullDamageService = new HullDamageService();
        this.hullDamageService.setRepository(repository);
        this.shieldService     = new ShieldService();
        this.shieldService.setHullDamageService(hullDamageService);
        this.movementService   = new ShipMovementService();
        this.powerService      = new PowerService();
        this.weaponService     = new WeaponService(shieldService, hullDamageService, encounterService);
        this.warpService       = new WarpService();
    }

    public void setServer(MinecraftServer server) { this.server = server; }

    // ── Database load ─────────────────────────────────────────────────────────

    /**
     * Load zone block registrations from the database.
     * Called at server start after EncounterService.loadFromDatabase().
     */
    public void loadFromDatabase() {
        hullDamageService.loadFromDatabase();
    }

    // ── addShip wrapper ───────────────────────────────────────────────────────

    public String addShip(String encounterId, String shipId, String shipClass,
                          String faction, String controlMode) {
        String result = encounterService.addShip(
                encounterId, shipId, shipClass, faction, controlMode);

        if (result.startsWith("Added")) {
            encounterService.getEncounter(encounterId)
                    .flatMap(e -> e.getShip(shipId))
                    .ifPresent(hullDamageService::initZonesForShip);
        }

        return result;
    }

    // ── Main tick ─────────────────────────────────────────────────────────────

    public void tick(MinecraftServer server, int currentTick) {
        if (currentTick % TICK_INTERVAL != 0) return;

        for (EncounterState encounter : encounterService.getAllEncounters()) {
            if (!encounter.isActive()) continue;
            tickEncounter(encounter);
        }
    }

    private void tickEncounter(EncounterState encounter) {
        // 1. Power
        powerService.tick(encounter);

        // 2. Zone penalties
        for (ShipState ship : encounter.getAllShips()) {
            if (ship.isDestroyed()) continue;
            ShipClassDefinition def = ShipClassDefinition.get(ship.getShipClass()).orElse(null);
            if (def != null) hullDamageService.applyZonePenalties(ship, def);
        }

        // 3. Movement
        movementService.tick(encounter);

        // 4. Shields
        shieldService.tick(encounter);

        // 5. Weapon cooldowns
        weaponService.tick(encounter);

        // 6. Queued fire
        resolvePendingFire(encounter);

        // 7. Warp
        warpService.tick(encounter, server);

        // 8. Hull check
        for (ShipState ship : encounter.getAllShips()) {
            if (!ship.isDestroyed() && ship.getHullIntegrity() <= 0) {
                encounterService.handleShipDestroyed(encounter, ship);
                hullDamageService.clearZonesForShip(ship.getShipId());
            }
        }

        // 9. Broadcast
        TacticalNetworking.broadcastEncounterUpdate(encounter, server);
    }

    private void resolvePendingFire(EncounterState encounter) {
        PendingFireAction action;
        while ((action = pendingFire.poll()) != null) {
            if (!action.encounterId().equals(encounter.getEncounterId())) {
                pendingFire.offer(action);
                continue;
            }

            Optional<ShipState> attacker = encounter.getShip(action.attackerShipId());
            Optional<ShipState> target   = encounter.getShip(action.targetShipId());
            if (attacker.isEmpty() || target.isEmpty()) continue;

            ShipState.ShieldFacing facing = null;
            if (action.targetFacing() != null && !"AUTO".equals(action.targetFacing())) {
                try {
                    facing = ShipState.ShieldFacing.valueOf(action.targetFacing());
                } catch (IllegalArgumentException ignored) {}
            }

            String result = switch (action.weaponType()) {
                case "PHASER"  -> weaponService.firePhasers(
                        encounter, attacker.get(), target.get(), facing, server);
                case "TORPEDO" -> weaponService.fireTorpedo(
                        encounter, attacker.get(), target.get(), facing, server);
                default        -> "Unknown weapon type.";
            };
            encounter.log("[FIRE] " + result);
        }
    }

    // ── Action queuing ────────────────────────────────────────────────────────

    public void queueWeaponFire(String encounterId, String attackerShipId,
                                String targetShipId, String weaponType,
                                String targetFacing) {
        pendingFire.offer(new PendingFireAction(
                encounterId, attackerShipId, targetShipId,
                weaponType, targetFacing != null ? targetFacing : "AUTO"));
    }

    public void queueWeaponFire(String encounterId, String attackerShipId,
                                String targetShipId, String weaponType) {
        queueWeaponFire(encounterId, attackerShipId, targetShipId, weaponType, "AUTO");
    }

    public void applyHelmInput(String encounterId, String shipId,
                               float targetHeading, float targetSpeed) {
        encounterService.getEncounter(encounterId)
                .flatMap(e -> e.getShip(shipId))
                .ifPresent(ship -> {
                    ship.setTargetHeading(targetHeading);
                    ship.setTargetSpeed(targetSpeed);
                });
    }

    public void applyPowerReroute(String encounterId, String shipId,
                                  int weapons, int shields, int engines, int sensors) {
        encounterService.getEncounter(encounterId)
                .flatMap(e -> e.getShip(shipId))
                .ifPresent(ship -> powerService.setManualAllocation(
                        ship, weapons, shields, engines, sensors));
    }

    public void applyShieldDistribution(String encounterId, String shipId,
                                        int fore, int aft, int port, int starboard) {
        encounterService.getEncounter(encounterId)
                .flatMap(e -> e.getShip(shipId))
                .ifPresent(ship -> shieldService.redistributeShields(
                        ship, fore, aft, port, starboard));
    }

    public void applyEvasiveManeuver(String encounterId, String shipId) {
        encounterService.getEncounter(encounterId)
                .flatMap(e -> e.getShip(shipId))
                .ifPresent(movementService::applyEvasiveManeuver);
    }

    public String engageWarp(String encounterId, String shipId, int warpFactor) {
        return encounterService.getEncounter(encounterId)
                .flatMap(e -> e.getShip(shipId))
                .map(ship -> warpService.engageWarp(ship, warpFactor))
                .orElse("Ship or encounter not found.");
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public EncounterService  getEncounterService()  { return encounterService; }
    public HullDamageService getHullDamageService() { return hullDamageService; }
    public WarpService       getWarpService()       { return warpService; }

    private record PendingFireAction(
            String encounterId,
            String attackerShipId,
            String targetShipId,
            String weaponType,
            String targetFacing
    ) {}
}