package net.shard.seconddawnrp.tactical.service;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.tactical.damage.DamageModelMapper;
import net.shard.seconddawnrp.tactical.data.*;
import net.shard.seconddawnrp.tactical.data.ShipState.ShieldFacing;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns all DamageZone instances and zone penalty state for all active ships.
 *
 * Persistence:
 *   loadFromDatabase() — called at server start, populates pre-loaded block data
 *   from damage_zone_model_blocks / damage_zone_real_blocks tables.
 *   initZonesForShip() merges persisted blocks into freshly created DamageZone objects.
 *   All block registration/removal methods write through to TacticalRepository immediately.
 *
 * Ship scoping (V15):
 *   Effects and announcements are routed only to players inside the damaged ship's
 *   registered bounding box via TacticalService.getPlayersOnShip(). Ships with no
 *   bounds configured silently skip player effects — they are not broadcast globally.
 */
public class HullDamageService {

    // shipId → zoneId → DamageZone
    private final Map<String, Map<String, DamageZone>> zonesByShip = new ConcurrentHashMap<>();

    // shipId → set of destroyed zone IDs — drives per-tick stat penalties
    private final Map<String, Set<String>> destroyedZones = new ConcurrentHashMap<>();

    // Pre-loaded block data from DB: shipId → zoneId → "MODEL"|"REAL" → List<blockPosLong>
    // Populated by loadFromDatabase(), consumed by initZonesForShip(), then cleared.
    private Map<String, Map<String, Map<String, List<Long>>>> preloadedBlocks = new HashMap<>();

    private TacticalRepository repository;

    /**
     * Back-reference to TacticalService for ship-scoped player resolution.
     * Set in TacticalService constructor via setTacticalService().
     * Never null after initialization completes.
     */
    private TacticalService tacticalService;

    private static final float VULNERABILITY_THRESHOLD = 0.25f;

    private static final Map<ShieldFacing, List<String>> FACING_ZONES = Map.of(
            ShieldFacing.FORE,      List.of("zone.bridge", "zone.weapons_fore",
                    "zone.torpedo_bay", "zone.sensors"),
            ShieldFacing.AFT,       List.of("zone.engines", "zone.engineering"),
            ShieldFacing.PORT,      List.of("zone.shield_emit", "zone.weapons_aft",
                    "zone.life_support"),
            ShieldFacing.STARBOARD, List.of("zone.shield_emit", "zone.weapons_aft",
                    "zone.life_support")
    );

    // ── Wiring ────────────────────────────────────────────────────────────────

    public void setRepository(TacticalRepository repository) {
        this.repository = repository;
    }

    public void setTacticalService(TacticalService tacticalService) {
        this.tacticalService = tacticalService;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void loadFromDatabase() {
        if (repository == null) return;
        preloadedBlocks = repository.loadAllZoneBlocks();
        int shipCount = preloadedBlocks.size();
        int blockCount = preloadedBlocks.values().stream()
                .flatMap(z -> z.values().stream())
                .flatMap(t -> t.values().stream())
                .mapToInt(List::size)
                .sum();
        System.out.println("[Tactical] Loaded zone block registrations: "
                + blockCount + " block(s) across " + shipCount + " ship(s).");
    }

    // ── Zone initialisation ───────────────────────────────────────────────────

    public void initZonesForShip(ShipState ship) {
        ShipClassDefinition def = ShipClassDefinition.get(ship.getShipClass()).orElse(null);
        if (def == null || def.getDamageZones().isEmpty()) return;

        Map<String, DamageZone> zones = new ConcurrentHashMap<>();
        List<String> zoneIds = def.getDamageZones();
        int zoneHp = Math.max(1, ship.getHullMax() / zoneIds.size());

        for (String zoneId : zoneIds) {
            DamageZone zone = new DamageZone(zoneId, ship.getShipId(), zoneHp);

            Map<String, Map<String, List<Long>>> shipData =
                    preloadedBlocks.get(ship.getShipId());
            if (shipData != null) {
                Map<String, List<Long>> zoneData = shipData.get(zoneId);
                if (zoneData != null) {
                    List<Long> modelBlocks = zoneData.getOrDefault("MODEL", List.of());
                    List<Long> realBlocks  = zoneData.getOrDefault("REAL",  List.of());
                    for (long pos : modelBlocks)
                        zone.addModelBlock(net.minecraft.util.math.BlockPos.fromLong(pos));
                    for (long pos : realBlocks)
                        zone.addRealShipBlock(net.minecraft.util.math.BlockPos.fromLong(pos));
                }
            }

            zones.put(zoneId, zone);
        }

        zonesByShip.put(ship.getShipId(), zones);
        destroyedZones.remove(ship.getShipId());

        int totalBlocks = zones.values().stream()
                .mapToInt(z -> z.getModelBlocks().size() + z.getRealShipBlocks().size())
                .sum();
        System.out.println("[Tactical] Initialised " + zones.size()
                + " zone(s) for ship " + ship.getShipId()
                + " (" + totalBlocks + " block(s) restored).");
    }

    public void clearZonesForShip(String shipId) {
        zonesByShip.remove(shipId);
        destroyedZones.remove(shipId);
    }

    // ── Block registration ────────────────────────────────────────────────────

    public void registerModelBlock(String shipId, String zoneId,
                                   net.minecraft.util.math.BlockPos pos) {
        DamageZone zone = getOrCreateZone(shipId, zoneId);
        zone.addModelBlock(pos);
        if (repository != null)
            repository.saveZoneModelBlock(shipId, zoneId, pos.asLong());
    }

    public void registerRealBlock(String shipId, String zoneId,
                                  net.minecraft.util.math.BlockPos pos) {
        DamageZone zone = getOrCreateZone(shipId, zoneId);
        zone.addRealShipBlock(pos);
        if (repository != null)
            repository.saveZoneRealBlock(shipId, zoneId, pos.asLong());
    }

    private DamageZone getOrCreateZone(String shipId, String zoneId) {
        return zonesByShip
                .computeIfAbsent(shipId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(zoneId, k -> new DamageZone(zoneId, shipId, 100));
    }

    // ── Per-tick penalty application ──────────────────────────────────────────

    public void applyZonePenalties(ShipState ship, ShipClassDefinition def) {
        Set<String> destroyed = destroyedZones.get(ship.getShipId());
        if (destroyed == null || destroyed.isEmpty()) return;

        for (String zoneId : destroyed) {
            switch (zoneId) {
                case "zone.engines" -> {
                    float cap = def.getMaxSpeed() * 0.5f;
                    if (ship.getTargetSpeed() > cap) ship.setTargetSpeed(cap);
                    if (ship.getSpeed() > cap)       ship.setSpeed(cap);
                }
                case "zone.weapons_fore" -> {
                    int reduced = (int)(ship.getWeaponsPower() * 0.70f);
                    ship.setWeaponsPower(reduced);
                }
                case "zone.torpedo_bay" -> {
                    ship.setTorpedoCount(0);
                }
                case "zone.engineering" -> {
                    int budget  = ship.getPowerBudget();
                    int reduced = (int)(budget * 0.75f);
                    if (budget > 0) {
                        float scale = (float)reduced / budget;
                        ship.setPowerBudget(reduced);
                        ship.setWeaponsPower((int)(ship.getWeaponsPower() * scale));
                        ship.setShieldsPower((int)(ship.getShieldsPower() * scale));
                        ship.setEnginesPower((int)(ship.getEnginesPower() * scale));
                        ship.setSensorsPower(reduced - ship.getWeaponsPower()
                                - ship.getShieldsPower() - ship.getEnginesPower());
                    }
                }
                case "zone.sensors" -> {
                    ship.setSensorsPower(0);
                }
            }
        }
    }

    // ── Zone penalty queries ──────────────────────────────────────────────────

    public boolean isBridgeDestroyed(String shipId) {
        Set<String> d = destroyedZones.get(shipId);
        return d != null && d.contains("zone.bridge");
    }

    public boolean isShieldEmitDestroyed(String shipId) {
        Set<String> d = destroyedZones.get(shipId);
        return d != null && d.contains("zone.shield_emit");
    }

    public boolean isWeaponsAftDestroyed(String shipId) {
        Set<String> d = destroyedZones.get(shipId);
        return d != null && d.contains("zone.weapons_aft");
    }

    public boolean isTorpedoBayDestroyed(String shipId) {
        Set<String> d = destroyedZones.get(shipId);
        return d != null && d.contains("zone.torpedo_bay");
    }

    // ── Main damage entry point ───────────────────────────────────────────────

    public void applyHullDamage(EncounterState encounter, ShipState ship,
                                int damage, ShieldFacing facing,
                                MinecraftServer server) {
        ShipState.HullState stateBefore = ship.getHullState();
        ship.setHullIntegrity(ship.getHullIntegrity() - damage);
        ShipState.HullState stateAfter = ship.getHullState();

        if (stateAfter == ShipState.HullState.DESTROYED && !ship.isDestroyed()) {
            ship.setDestroyed(true);
            encounter.log("[HULL] " + ship.getRegistryName()
                    + " hull integrity at zero — DESTROYED");
            return;
        }

        if (stateBefore != stateAfter)
            onThresholdCrossed(encounter, ship, stateAfter, server);

        if (facing != null && isVulnerable(ship, facing))
            applyZoneDamage(encounter, ship, damage, facing, server);
    }

    public void applyHullDamage(EncounterState encounter, ShipState ship,
                                int damage, MinecraftServer server) {
        applyHullDamage(encounter, ship, damage, null, server);
    }

    // ── Vulnerability ─────────────────────────────────────────────────────────

    private boolean isVulnerable(ShipState ship, ShieldFacing facing) {
        ShipClassDefinition def = ShipClassDefinition.get(ship.getShipClass()).orElse(null);
        if (def == null) return false;
        int current = ship.getShield(facing);
        float maxPerFacing = def.getShieldMax() / 4f;
        return current < maxPerFacing * VULNERABILITY_THRESHOLD;
    }

    // ── Zone damage ───────────────────────────────────────────────────────────

    private void applyZoneDamage(EncounterState encounter, ShipState ship,
                                 int damage, ShieldFacing facing,
                                 MinecraftServer server) {
        Map<String, DamageZone> shipZones = zonesByShip.get(ship.getShipId());
        if (shipZones == null || shipZones.isEmpty()) return;

        List<String> candidateIds = FACING_ZONES.getOrDefault(facing, List.of());
        if (candidateIds.isEmpty()) return;

        List<DamageZone> candidates = candidateIds.stream()
                .map(shipZones::get)
                .filter(z -> z != null && !z.isDestroyed())
                .toList();
        if (candidates.isEmpty()) return;

        DamageZone zone = candidates.get((int)(Math.random() * candidates.size()));
        zone.applyDamage(damage);
        zone.setDamaged(true);

        encounter.log("[HULL] Zone damaged: " + zone.getZoneId()
                + " on " + ship.getRegistryName()
                + " (" + zone.getCurrentHp() + "/" + zone.getMaxHp() + " HP)");

        if (zone.isDestroyed())
            onZoneDestroyed(encounter, ship, zone, server);
    }

    private void onZoneDestroyed(EncounterState encounter, ShipState ship,
                                 DamageZone zone, MinecraftServer server) {
        encounter.log("[HULL] Zone DESTROYED: " + zone.getZoneId()
                + " on " + ship.getRegistryName());

        destroyedZones
                .computeIfAbsent(ship.getShipId(), k -> ConcurrentHashMap.newKeySet())
                .add(zone.getZoneId());

        applyZoneSystemEffect(encounter, ship, zone, server);

        ServerWorld realWorld = resolveShipWorld(ship, server);
        DamageModelMapper.destroyZone(zone, null, realWorld);
    }

    // ── Force destroy (GM command / testing) ─────────────────────────────────

    public void forceDestroyZone(EncounterState encounter, ShipState ship,
                                 DamageZone zone, MinecraftServer server) {
        zone.applyDamage(zone.getMaxHp() + 1);
        zone.setDamaged(true);
        onZoneDestroyed(encounter, ship, zone, server);
    }

    // ── Zone system effects ───────────────────────────────────────────────────

    private void applyZoneSystemEffect(EncounterState encounter, ShipState ship,
                                       DamageZone zone, MinecraftServer server) {
        String zoneId = zone.getZoneId();
        String effectMsg = switch (zoneId) {
            case "zone.bridge"       -> "Tactical console accuracy penalty — bridge damaged.";
            case "zone.weapons_fore" -> "Forward phaser damage reduced — weapons systems offline.";
            case "zone.weapons_aft"  -> "Aft hardpoints disabled — weapons systems offline.";
            case "zone.torpedo_bay"  -> "Torpedo bay destroyed — reloading impossible.";
            case "zone.shield_emit"  -> "Shield emitter damaged — regeneration halved.";
            case "zone.engines"      -> "Engine room hit — maximum speed reduced by 50%.";
            case "zone.sensors"      -> "Sensors offline — targeting accuracy degraded.";
            case "zone.engineering"  -> "Engineering hit — total power output reduced by 25%.";
            case "zone.life_support" -> "Life support compromised — crew effects incoming.";
            default                  -> "System damage reported.";
        };

        // Broadcast only to crew on the damaged ship
        broadcastToShip(ship.getShipId(), server,
                "[DAMAGE] " + effectMsg, Formatting.RED);
        encounter.log("[ZONE] " + zoneId + ": " + effectMsg);
        if (server == null) return;

        List<ServerPlayerEntity> shipCrew = resolveShipCrew(ship.getShipId(), server);

        switch (zoneId) {
            case "zone.life_support" -> {
                applyEffectToZone(server, zone, shipCrew,
                        net.minecraft.entity.effect.StatusEffects.NAUSEA,   100, 1);
                applyEffectToZone(server, zone, shipCrew,
                        net.minecraft.entity.effect.StatusEffects.BLINDNESS, 40, 0);
            }
            case "zone.engineering" ->
                    applyEffectToZone(server, zone, shipCrew,
                            net.minecraft.entity.effect.StatusEffects.NAUSEA, 60, 0);
            case "zone.bridge" ->
                    applyEffectToZone(server, zone, shipCrew,
                            net.minecraft.entity.effect.StatusEffects.SLOWNESS, 60, 0);
            case "zone.engines" ->
                    applyEffectToZone(server, zone, shipCrew,
                            net.minecraft.entity.effect.StatusEffects.NAUSEA, 40, 0);
        }
    }

    // ── Hull threshold events ─────────────────────────────────────────────────

    private void onThresholdCrossed(EncounterState encounter, ShipState ship,
                                    ShipState.HullState newState,
                                    MinecraftServer server) {
        encounter.log("[HULL] " + ship.getRegistryName() + " hull state: " + newState.name());
        if (server == null) return;

        switch (newState) {
            case DAMAGED -> {
                broadcastToShip(ship.getShipId(), server,
                        "[TACTICAL] Hull integrity below 75%. Damage reported in multiple sections.",
                        Formatting.YELLOW);
                generateEngineeringTasks(encounter, ship, 1);
            }
            case CRITICAL -> {
                broadcastToShip(ship.getShipId(), server,
                        "[TACTICAL] ⚠ CRITICAL HULL DAMAGE — weapons efficiency reduced, shield regen halved!",
                        Formatting.RED);
                applyEffectToShip(ship.getShipId(), server,
                        net.minecraft.entity.effect.StatusEffects.NAUSEA, 60, 0);
                generateEngineeringTasks(encounter, ship, 2);
            }
            case FAILING -> {
                broadcastToShip(ship.getShipId(), server,
                        "[TACTICAL] ⚠⚠ HULL FAILING — multiple systems offline! EVACUATE NON-ESSENTIAL PERSONNEL!",
                        Formatting.DARK_RED);
                applyEffectToShip(ship.getShipId(), server,
                        net.minecraft.entity.effect.StatusEffects.NAUSEA,   100, 1);
                applyEffectToShip(ship.getShipId(), server,
                        net.minecraft.entity.effect.StatusEffects.BLINDNESS,  40, 0);
                generateEngineeringTasks(encounter, ship, 3);
            }
        }
    }

    // ── Repair ────────────────────────────────────────────────────────────────

    public void repairZone(ShipState ship, String zoneId, ServerWorld realWorld) {
        Map<String, DamageZone> shipZones = zonesByShip.get(ship.getShipId());
        if (shipZones != null) {
            DamageZone zone = shipZones.get(zoneId);
            if (zone != null) {
                zone.repair();
                DamageModelMapper.restoreZone(zone, realWorld);
            }
        }
        Set<String> destroyed = destroyedZones.get(ship.getShipId());
        if (destroyed != null) destroyed.remove(zoneId);
        System.out.println("[Tactical] Zone " + zoneId + " repaired on " + ship.getShipId());
    }

    public void repairZone(ShipState ship, String zoneId) {
        repairZone(ship, zoneId, null);
    }

    // ── Remove zone ───────────────────────────────────────────────────────────

    public boolean removeZone(String shipId, String zoneId) {
        Map<String, DamageZone> shipZones = zonesByShip.get(shipId);
        boolean removed = shipZones != null && shipZones.remove(zoneId) != null;
        Set<String> destroyed = destroyedZones.get(shipId);
        if (destroyed != null) destroyed.remove(zoneId);
        if (repository != null)
            repository.deleteAllZoneBlocksForShipZone(shipId, zoneId);
        return removed;
    }

    // ── DamageZone block mutation with persistence ────────────────────────────

    public void persistRemoveModelBlock(String shipId, String zoneId, long blockPosLong) {
        if (repository != null)
            repository.deleteZoneModelBlock(shipId, zoneId, blockPosLong);
    }

    public void persistRemoveRealBlock(String shipId, String zoneId, long blockPosLong) {
        if (repository != null)
            repository.deleteZoneRealBlock(shipId, zoneId, blockPosLong);
    }

    public void persistClearModelBlocks(String shipId, String zoneId) {
        if (repository != null)
            repository.deleteAllZoneModelBlocks(shipId, zoneId);
    }

    public void persistClearRealBlocks(String shipId, String zoneId) {
        if (repository != null)
            repository.deleteAllZoneRealBlocks(shipId, zoneId);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public boolean isZoneDamaged(String shipId, String zoneId) {
        Map<String, DamageZone> shipZones = zonesByShip.get(shipId);
        if (shipZones == null) return false;
        DamageZone zone = shipZones.get(zoneId);
        return zone != null && zone.isDamaged();
    }

    public boolean isZoneDestroyed(String shipId, String zoneId) {
        Set<String> destroyed = destroyedZones.get(shipId);
        return destroyed != null && destroyed.contains(zoneId);
    }

    public Set<String> getDamagedZones(String shipId) {
        Map<String, DamageZone> shipZones = zonesByShip.get(shipId);
        if (shipZones == null) return Set.of();
        Set<String> result = new HashSet<>();
        for (var entry : shipZones.entrySet()) {
            if (entry.getValue().isDamaged()) result.add(entry.getKey());
        }
        return result;
    }

    public Set<String> getDestroyedZoneIds(String shipId) {
        return destroyedZones.getOrDefault(shipId, Set.of());
    }

    public Optional<DamageZone> getZone(String shipId, String zoneId) {
        Map<String, DamageZone> shipZones = zonesByShip.get(shipId);
        if (shipZones == null) return Optional.empty();
        return Optional.ofNullable(shipZones.get(zoneId));
    }

    public Map<String, DamageZone> getZonesForShip(String shipId) {
        return zonesByShip.getOrDefault(shipId, Map.of());
    }

    // ── World resolution ──────────────────────────────────────────────────────

    private ServerWorld resolveShipWorld(ShipState ship, MinecraftServer server) {
        if (server == null) return null;
        if (tacticalService != null) {
            return tacticalService.getShipRegistryEntry(ship.getShipId())
                    .map(entry -> entry.getRealShipWorldKey())
                    .map(key -> {
                        for (ServerWorld w : server.getWorlds()) {
                            if (w.getRegistryKey().getValue().toString().equals(key)) return w;
                        }
                        return null;
                    })
                    .orElse(server.getOverworld());
        }
        return server.getOverworld();
    }

    // ── Ship-scoped helpers ───────────────────────────────────────────────────

    /**
     * Resolve the crew list for a ship. Falls back to an empty list if the ship
     * has no bounds configured — effects are then silently skipped.
     */
    private List<ServerPlayerEntity> resolveShipCrew(String shipId, MinecraftServer server) {
        if (server == null || tacticalService == null) return List.of();
        return tacticalService.getPlayersOnShip(shipId, server);
    }

    /**
     * Apply a status effect only to players inside the given damage zone's
     * real-block bounding box, AND who are also on the ship (double-filtered).
     * Zone bounds give local precision; ship bounds guard against cross-ship leakage.
     */
    private void applyEffectToZone(MinecraftServer server,
                                   DamageZone zone,
                                   List<ServerPlayerEntity> shipCrew,
                                   net.minecraft.registry.entry.RegistryEntry<
                                           net.minecraft.entity.effect.StatusEffect> effect,
                                   int durationTicks, int amplifier) {
        for (ServerPlayerEntity player : shipCrew) {
            if (zone.containsPlayer(player)) {
                player.addStatusEffect(
                        new StatusEffectInstance(effect, durationTicks, amplifier));
            }
        }
    }

    /**
     * Apply a status effect to all crew on the ship (hull threshold events).
     * Scoped to the ship's bounds — does NOT affect players on other ships.
     */
    private void applyEffectToShip(String shipId, MinecraftServer server,
                                   net.minecraft.registry.entry.RegistryEntry<
                                           net.minecraft.entity.effect.StatusEffect> effect,
                                   int durationTicks, int amplifier) {
        for (ServerPlayerEntity player : resolveShipCrew(shipId, server)) {
            player.addStatusEffect(
                    new StatusEffectInstance(effect, durationTicks, amplifier));
        }
    }

    /**
     * Broadcast a message only to crew on the specified ship.
     * If the ship has no bounds configured, the message is not sent to anyone —
     * it is not broadcast globally as a fallback. This is intentional: a ship
     * without bounds is not configured for live play.
     */
    private void broadcastToShip(String shipId, MinecraftServer server,
                                 String message, Formatting color) {
        if (server == null) return;
        List<ServerPlayerEntity> crew = resolveShipCrew(shipId, server);
        if (crew.isEmpty()) return;
        Text text = Text.literal(message).formatted(color);
        crew.forEach(p -> p.sendMessage(text, false));
    }

    // ── Task stubs ────────────────────────────────────────────────────────────

    private void generateEngineeringTasks(EncounterState encounter,
                                          ShipState ship, int count) {
        encounter.log("[HULL] " + count + " repair task(s) generated for Engineering.");
    }
}