package net.shard.seconddawnrp.tactical.service;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.particle.ParticleTypes;
import net.shard.seconddawnrp.tactical.data.*;
import net.shard.seconddawnrp.tactical.data.ShipState.ShieldFacing;

import java.util.*;

/**
 * Validates and resolves weapon fire actions.
 *
 * Zone penalty integration:
 *   zone.bridge     — hit chance reduced by 15% (checked via HullDamageService)
 *   zone.weapons_aft — aft-arc hardpoints treated as unavailable
 *
 * Both firePhasers() and fireTorpedo() accept a nullable ShieldFacing playerFacing:
 *   non-null → use player-selected facing
 *   null     → auto-compute from attacker/target positions
 */
public class WeaponService {

    private final ShieldService     shieldService;
    private final HullDamageService hullDamageService;
    private final EncounterService  encounterService;

    public WeaponService(ShieldService shieldService,
                         HullDamageService hullDamageService,
                         EncounterService encounterService) {
        this.shieldService     = shieldService;
        this.hullDamageService = hullDamageService;
        this.encounterService  = encounterService;
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public void tick(EncounterState encounter) {
        for (ShipState ship : encounter.getAllShips()) {
            if (ship.isDestroyed()) continue;
            List<HardpointEntry> hps = encounterService.getHardpoints(ship.getShipId());
            hps.forEach(HardpointEntry::tickCooldown);
        }
    }

    // ── Facing resolution ─────────────────────────────────────────────────────

    private ShieldFacing resolveImpactFacing(ShipState attacker, ShipState target,
                                             ShieldFacing playerFacing) {
        if (playerFacing != null) return playerFacing;
        return target.getImpactFacing(attacker.getPosX(), attacker.getPosZ());
    }

    // ── Phasers ───────────────────────────────────────────────────────────────

    public String firePhasers(EncounterState encounter, ShipState attacker,
                              ShipState target, ShieldFacing playerFacing,
                              MinecraftServer server) {
        ShipClassDefinition def = ShipClassDefinition.get(attacker.getShipClass()).orElse(null);
        if (def == null)            return "No ship class definition found.";
        if (attacker.isDestroyed()) return "Your ship is destroyed.";
        if (target.isDestroyed())   return target.getRegistryName() + " is already destroyed.";

        if (attacker.getWeaponsPower() < 50)
            return "Insufficient weapons power. Reroute power to weapons.";

        ShieldFacing impactFacing = resolveImpactFacing(attacker, target, playerFacing);

        // Find available phaser hardpoint — skip aft hardpoints if weapons_aft destroyed
        List<HardpointEntry> hps = encounterService.getHardpoints(attacker.getShipId());
        boolean aftDestroyed = hullDamageService.isWeaponsAftDestroyed(attacker.getShipId());

        HardpointEntry selectedHp = null;
        for (HardpointEntry hp : hps) {
            if (hp.getWeaponType() != HardpointEntry.WeaponType.PHASER_ARRAY) continue;
            if (!hp.isAvailable()) continue;
            // Skip aft arc hardpoints if zone.weapons_aft is destroyed
            if (aftDestroyed && hp.getArc() == HardpointEntry.Arc.AFT) continue;
            selectedHp = hp;
            break;
        }

        boolean usedHardpoint = selectedHp != null;
        if (selectedHp != null) {
            if (attacker.getWeaponsPower() < selectedHp.getPowerDraw())
                return "Insufficient weapons power for this hardpoint.";
            selectedHp.fireCooldown();
        }

        float powerScale = (float)attacker.getWeaponsPower()
                / Math.max(1, attacker.getPowerBudget());
        int baseDamage = usedHardpoint
                ? (int)(def.getPhaserDamage() * powerScale)
                : (int)(def.getPhaserDamage() * powerScale * 0.8f);

        float hitChance = calculateHitChance(attacker, target);
        if (Math.random() > hitChance) {
            encounter.log("[WEAPONS] " + attacker.getCombatId() + " phaser burst: MISS");
            spawnPhaserParticles(attacker, target, server, false);
            return "Phaser burst: MISS — target evaded.";
        }

        int excess = shieldService.applyShieldDamage(target, impactFacing, baseDamage);
        String result;
        if (excess > 0) {
            hullDamageService.applyHullDamage(encounter, target, excess, impactFacing, server);
            result = String.format(
                    "Phaser HIT — shield %s depleted, %d hull damage. Shield: %d/%d",
                    impactFacing.name(), excess,
                    target.getShield(impactFacing), def.getShieldMax());
        } else {
            result = String.format(
                    "Phaser HIT — %d damage to %s shield. Shield: %d",
                    baseDamage, impactFacing.name(), target.getShield(impactFacing));
        }

        encounter.log("[WEAPONS] " + attacker.getCombatId()
                + " → " + target.getCombatId() + " | " + result);
        spawnPhaserParticles(attacker, target, server, true);
        return result;
    }

    public String firePhasers(EncounterState encounter, ShipState attacker,
                              ShipState target, MinecraftServer server) {
        return firePhasers(encounter, attacker, target, null, server);
    }

    // ── Torpedoes ─────────────────────────────────────────────────────────────

    public String fireTorpedo(EncounterState encounter, ShipState attacker,
                              ShipState target, ShieldFacing playerFacing,
                              MinecraftServer server) {
        // Torpedo bay zone penalty — already enforces count=0 each tick,
        // but check explicitly for a clear feedback message
        if (hullDamageService.isTorpedoBayDestroyed(attacker.getShipId()))
            return "Torpedo bay destroyed — reloading impossible.";
        if (attacker.getTorpedoCount() <= 0)
            return "No torpedoes loaded. Physical loading required in torpedo bay.";
        if (attacker.isDestroyed()) return "Your ship is destroyed.";
        if (target.isDestroyed())   return target.getRegistryName() + " is already destroyed.";

        ShipClassDefinition def = ShipClassDefinition.get(attacker.getShipClass()).orElse(null);
        if (def == null) return "No class definition.";

        attacker.setTorpedoCount(attacker.getTorpedoCount() - 1);

        ShieldFacing impactFacing = resolveImpactFacing(attacker, target, playerFacing);
        int shield = target.getShield(impactFacing);
        int damage = def.getTorpedoDamage();

        int excess;
        if (shield < def.getShieldMax() * 0.25f) {
            excess = (int)(damage * 0.75f);
            shieldService.applyShieldDamage(target, impactFacing, (int)(damage * 0.25f));
        } else {
            excess = shieldService.applyShieldDamage(target, impactFacing, damage);
        }

        if (excess > 0) {
            hullDamageService.applyHullDamage(encounter, target, excess, impactFacing, server);
        }

        String result = String.format(
                "TORPEDO IMPACT — %d hull damage. %d torpedoes remaining.",
                excess, attacker.getTorpedoCount());
        encounter.log("[WEAPONS] " + attacker.getCombatId()
                + " torpedo → " + target.getCombatId() + " | " + result);
        return result;
    }

    public String fireTorpedo(EncounterState encounter, ShipState attacker,
                              ShipState target, MinecraftServer server) {
        return fireTorpedo(encounter, attacker, target, null, server);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private float calculateHitChance(ShipState attacker, ShipState target) {
        float base         = 0.85f;
        float sensorBonus  = (float)attacker.getSensorsPower()
                / Math.max(1, attacker.getPowerBudget()) * 0.1f;
        float evasionPenalty = target.getSpeed() * 0.02f;

        // Bridge destroyed — 15% accuracy penalty on the attacker
        float bridgePenalty = hullDamageService.isBridgeDestroyed(attacker.getShipId())
                ? 0.15f : 0f;

        return Math.min(0.98f, Math.max(0.30f,
                base + sensorBonus - evasionPenalty - bridgePenalty));
    }

    private void spawnPhaserParticles(ShipState attacker, ShipState target,
                                      MinecraftServer server, boolean hit) {
        if (server == null) return;
        ServerWorld world = server.getOverworld();
        double fromX = attacker.getPosX(), fromZ = attacker.getPosZ();
        double toX   = target.getPosX(),   toZ   = target.getPosZ();
        double y     = 64;

        for (int i = 0; i <= 10; i++) {
            double t = i / 10.0;
            double x = fromX + (toX - fromX) * t;
            double z = fromZ + (toZ - fromZ) * t;
            world.spawnParticles(
                    hit ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SMOKE,
                    x, y, z, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }
}