package net.shard.seconddawnrp.tactical.service;

import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tactical.data.EncounterState;
import net.shard.seconddawnrp.tactical.data.ShipState;

/**
 * Computes power budgets from the warp core output and distributes power
 * across systems. Manual mode takes precedence; auto-distribution kicks in
 * when no player has touched power allocation within the last 300 ticks.
 */
public class PowerService {

    private static final long MANUAL_TIMEOUT_MS = 30_000L; // 30 seconds
    private final java.util.Map<String, Long> lastManualAdjustment = new java.util.concurrent.ConcurrentHashMap<>();

    public void tick(EncounterState encounter) {
        for (ShipState ship : encounter.getAllShips()) {
            if (ship.isDestroyed()) continue;
            updatePowerBudget(ship);
            if (!isManualActive(ship)) {
                autoDistribute(ship);
                ship.setManualPower(false);
            }
        }
    }

    private void updatePowerBudget(ShipState ship) {
        // If a GM has manually overridden power, skip the warp core read
        if (ship.isManualPowerOverride()) return;

        // Read live power from WarpCoreService
        int rawOutput = 0;
        if (SecondDawnRP.WARP_CORE_SERVICE != null) {
            // Find a warp core on this ship — for MVP use first available
            // Full implementation would link per-ship to specific core IDs
            rawOutput = SecondDawnRP.WARP_CORE_SERVICE.getTotalPowerOutput();
        }
        ship.setPowerOutput(rawOutput);

        // Apply warp core state penalty
        int budget = applyStatePenalty(ship, rawOutput);
        ship.setPowerBudget(budget);
    }

    private int applyStatePenalty(ShipState ship, int rawOutput) {
        // Query warp core state if available
        if (SecondDawnRP.WARP_CORE_SERVICE == null) return rawOutput;
        String state = SecondDawnRP.WARP_CORE_SERVICE.getOverallState();
        return switch (state) {
            case "ONLINE"    -> rawOutput;
            case "UNSTABLE"  -> (int)(rawOutput * 0.6);
            case "CRITICAL"  -> (int)(rawOutput * 0.25);
            case "FAILED"    -> 0;
            default          -> rawOutput;
        };
    }

    /**
     * Auto-distribution: shields get largest share, then weapons, engines, sensors.
     * Minimum 10% to each system.
     */
    private void autoDistribute(ShipState ship) {
        int budget = ship.getPowerBudget();
        if (budget <= 0) {
            ship.setShieldsPower(0);
            ship.setWeaponsPower(0);
            ship.setEnginesPower(0);
            ship.setSensorsPower(0);
            return;
        }
        // Shields 35%, Weapons 30%, Engines 25%, Sensors 10%
        ship.setShieldsPower((int)(budget * 0.35));
        ship.setWeaponsPower((int)(budget * 0.30));
        ship.setEnginesPower((int)(budget * 0.25));
        ship.setSensorsPower(budget - ship.getShieldsPower()
                - ship.getWeaponsPower() - ship.getEnginesPower());
    }

    /**
     * Player manually sets power allocation.
     * Clamps total to power budget — if over, scales proportionally.
     */
    public void setManualAllocation(ShipState ship, int weapons, int shields,
                                    int engines, int sensors) {
        int budget = ship.getPowerBudget();
        int total  = weapons + shields + engines + sensors;
        if (total <= 0) return;

        if (total > budget) {
            float scale = (float) budget / total;
            weapons = (int)(weapons * scale);
            shields = (int)(shields * scale);
            engines = (int)(engines * scale);
            sensors = budget - weapons - shields - engines;
        }

        ship.setWeaponsPower(weapons);
        ship.setShieldsPower(shields);
        ship.setEnginesPower(engines);
        ship.setSensorsPower(sensors);
        ship.setManualPower(true);
        lastManualAdjustment.put(ship.getShipId(), System.currentTimeMillis());
    }

    private boolean isManualActive(ShipState ship) {
        if (!ship.isManualPower()) return false;
        Long last = lastManualAdjustment.get(ship.getShipId());
        if (last == null) return false;
        return System.currentTimeMillis() - last < MANUAL_TIMEOUT_MS;
    }
}