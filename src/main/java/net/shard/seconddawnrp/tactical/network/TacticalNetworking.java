package net.shard.seconddawnrp.tactical.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tactical.data.EncounterState;
import net.shard.seconddawnrp.tactical.data.ShipState;

import java.util.ArrayList;
import java.util.List;

/**
 * All Tactical packet types in one file.
 *
 * S→C: EncounterUpdatePayload    — full encounter state delta sent every tick
 *      StandbyUpdatePayload      — anomaly map pushed every 10s
 *      OpenTacticalPayload       — open screen with full state
 *      StellarNavUpdatePayload   — passive ship navigation data
 * C→S: WeaponFirePayload         — player fires weapon (includes targetFacing)
 *      HelmInputPayload          — helm heading/speed change
 *      PowerReroutePayload       — Engineering power allocation
 *      ShieldDistributePayload   — shield facing distribution
 *      RequestTacticalUpdatePayload — client requests fresh state
 */
public class TacticalNetworking {

    // ── Shared ship snapshot ──────────────────────────────────────────────────

    public record ShipSnapshot(
            String shipId, String registryName, String combatId, String faction,
            double posX, double posZ, float heading, float speed,
            int hullIntegrity, int hullMax,
            int shieldFore, int shieldAft, int shieldPort, int shieldStarboard,
            int powerBudget, int weaponsPower, int shieldsPower, int enginesPower, int sensorsPower,
            int torpedoCount, int warpSpeed, boolean warpCapable,
            String hullState, boolean destroyed, String controlMode
    ) {
        static ShipSnapshot of(ShipState s) {
            return new ShipSnapshot(
                    s.getShipId(), s.getRegistryName(), s.getCombatId(), s.getFaction(),
                    s.getPosX(), s.getPosZ(), s.getHeading(), s.getSpeed(),
                    s.getHullIntegrity(), s.getHullMax(),
                    s.getShield(ShipState.ShieldFacing.FORE),
                    s.getShield(ShipState.ShieldFacing.AFT),
                    s.getShield(ShipState.ShieldFacing.PORT),
                    s.getShield(ShipState.ShieldFacing.STARBOARD),
                    s.getPowerBudget(), s.getWeaponsPower(), s.getShieldsPower(),
                    s.getEnginesPower(), s.getSensorsPower(),
                    s.getTorpedoCount(), s.getWarpSpeed(), s.isWarpCapable(),
                    s.getHullState().name(), s.isDestroyed(),
                    s.getControlMode().name()
            );
        }

        static void write(PacketByteBuf buf, ShipSnapshot s) {
            buf.writeString(s.shipId()       != null ? s.shipId()       : "");
            buf.writeString(s.registryName() != null ? s.registryName() : "");
            buf.writeString(s.combatId()     != null ? s.combatId()     : "");
            buf.writeString(s.faction()      != null ? s.faction()      : "FRIENDLY");
            buf.writeDouble(s.posX()); buf.writeDouble(s.posZ());
            buf.writeFloat(s.heading()); buf.writeFloat(s.speed());
            buf.writeInt(s.hullIntegrity()); buf.writeInt(s.hullMax());
            buf.writeInt(s.shieldFore()); buf.writeInt(s.shieldAft());
            buf.writeInt(s.shieldPort()); buf.writeInt(s.shieldStarboard());
            buf.writeInt(s.powerBudget()); buf.writeInt(s.weaponsPower());
            buf.writeInt(s.shieldsPower()); buf.writeInt(s.enginesPower());
            buf.writeInt(s.sensorsPower());
            buf.writeInt(s.torpedoCount()); buf.writeInt(s.warpSpeed());
            buf.writeBoolean(s.warpCapable());
            buf.writeString(s.hullState()   != null ? s.hullState()   : "NOMINAL");
            buf.writeBoolean(s.destroyed());
            buf.writeString(s.controlMode() != null ? s.controlMode() : "GM_MANUAL");
        }

        static ShipSnapshot read(PacketByteBuf buf) {
            return new ShipSnapshot(
                    buf.readString(), buf.readString(), buf.readString(), buf.readString(),
                    buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
                    buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readBoolean(),
                    buf.readString(), buf.readBoolean(), buf.readString()
            );
        }
    }

    // ── S→C: Encounter update ─────────────────────────────────────────────────

    public record EncounterUpdatePayload(
            String encounterId,
            String status,
            List<ShipSnapshot> ships,
            List<String> recentLog
    ) implements CustomPayload {
        public static final Id<EncounterUpdatePayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "tactical_update"));
        public static final PacketCodec<PacketByteBuf, EncounterUpdatePayload> CODEC =
                PacketCodec.of(EncounterUpdatePayload::write, EncounterUpdatePayload::read);

        @Override public Id<EncounterUpdatePayload> getId() { return ID; }

        void write(PacketByteBuf buf) {
            buf.writeString(encounterId);
            buf.writeString(status);
            buf.writeInt(ships.size());
            ships.forEach(s -> ShipSnapshot.write(buf, s));
            buf.writeInt(recentLog.size());
            recentLog.forEach(buf::writeString);
        }

        static EncounterUpdatePayload read(PacketByteBuf buf) {
            String eid    = buf.readString();
            String status = buf.readString();
            int count     = buf.readInt();
            List<ShipSnapshot> ships = new ArrayList<>(count);
            for (int i = 0; i < count; i++) ships.add(ShipSnapshot.read(buf));
            int logCount = buf.readInt();
            List<String> log = new ArrayList<>(logCount);
            for (int i = 0; i < logCount; i++) log.add(buf.readString());
            return new EncounterUpdatePayload(eid, status, ships, log);
        }
    }

    // ── S→C: Standby map update ───────────────────────────────────────────────

    public record StandbyUpdatePayload(
            double shipOriginX,
            double shipOriginZ,
            List<AnomalyMarker> anomalies
    ) implements CustomPayload {
        public static final Id<StandbyUpdatePayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "tactical_standby_update"));
        public static final PacketCodec<PacketByteBuf, StandbyUpdatePayload> CODEC =
                PacketCodec.of(StandbyUpdatePayload::write, StandbyUpdatePayload::read);

        @Override public Id<StandbyUpdatePayload> getId() { return ID; }

        void write(PacketByteBuf buf) {
            buf.writeDouble(shipOriginX);
            buf.writeDouble(shipOriginZ);
            buf.writeInt(anomalies.size());
            anomalies.forEach(a -> AnomalyMarker.write(buf, a));
        }

        static StandbyUpdatePayload read(PacketByteBuf buf) {
            double ox = buf.readDouble();
            double oz = buf.readDouble();
            int ac = buf.readInt();
            List<AnomalyMarker> anomalies = new ArrayList<>(ac);
            for (int i = 0; i < ac; i++) anomalies.add(AnomalyMarker.read(buf));
            return new StandbyUpdatePayload(ox, oz, anomalies);
        }
    }

    public static void sendStandbyUpdate(ServerPlayerEntity player) {
        double originX = 0, originZ = 0;
        if (SecondDawnRP.TERMINAL_DESIGNATOR_SERVICE != null
                && player.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
            net.minecraft.util.math.BlockPos origin =
                    SecondDawnRP.TERMINAL_DESIGNATOR_SERVICE.getShipOriginNear(player, sw);
            if (origin != null) { originX = origin.getX(); originZ = origin.getZ(); }
        }

        List<AnomalyMarker> anomalies = new ArrayList<>();
        if (SecondDawnRP.ANOMALY_SERVICE != null) {
            String worldKey = player.getWorld().getRegistryKey().getValue().toString();
            SecondDawnRP.ANOMALY_SERVICE.getAll().stream()
                    .filter(a -> a.isActive() && worldKey.equals(a.getWorldKey()))
                    .forEach(a -> {
                        net.minecraft.util.math.BlockPos pos =
                                net.minecraft.util.math.BlockPos.fromLong(a.getBlockPosLong());
                        int color = switch (a.getType().name()) {
                            case "UNKNOWN"    -> 0xFF3333;
                            case "BIOLOGICAL" -> 0x33FF88;
                            default           -> 0xFFAA00;
                        };
                        anomalies.add(new AnomalyMarker(
                                a.getEntryId(),
                                a.getName() != null ? a.getName() : a.getType().name(),
                                pos.getX(), pos.getZ(), color));
                    });
        }

        ServerPlayNetworking.send(player, new StandbyUpdatePayload(originX, originZ, anomalies));
    }

    public static void pushAnomalyUpdate(MinecraftServer server, String worldKey) {
        if (server == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (worldKey.equals(player.getWorld().getRegistryKey().getValue().toString())) {
                sendStandbyUpdate(player);
            }
        }
    }

    // ── C→S: Weapon fire ──────────────────────────────────────────────────────
    //
    // targetFacing: "FORE", "AFT", "PORT", "STARBOARD", or "AUTO"
    // "AUTO" means the server computes facing from attacker/target positions.

    public record WeaponFirePayload(
            String encounterId,
            String attackerShipId,
            String targetShipId,
            String weaponType,
            String targetFacing
    ) implements CustomPayload {
        public static final Id<WeaponFirePayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "tactical_weapon_fire"));
        public static final PacketCodec<PacketByteBuf, WeaponFirePayload> CODEC =
                PacketCodec.of(WeaponFirePayload::write, WeaponFirePayload::read);

        @Override public Id<WeaponFirePayload> getId() { return ID; }

        void write(PacketByteBuf buf) {
            buf.writeString(encounterId);
            buf.writeString(attackerShipId);
            buf.writeString(targetShipId);
            buf.writeString(weaponType);
            buf.writeString(targetFacing != null ? targetFacing : "AUTO");
        }

        static WeaponFirePayload read(PacketByteBuf buf) {
            return new WeaponFirePayload(
                    buf.readString(), buf.readString(),
                    buf.readString(), buf.readString(), buf.readString());
        }
    }

    // ── C→S: Helm input ───────────────────────────────────────────────────────

    public record HelmInputPayload(
            String encounterId,
            String shipId,
            float targetHeading,
            float targetSpeed,
            boolean evasive
    ) implements CustomPayload {
        public static final Id<HelmInputPayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "tactical_helm"));
        public static final PacketCodec<PacketByteBuf, HelmInputPayload> CODEC =
                PacketCodec.of(HelmInputPayload::write, HelmInputPayload::read);

        @Override public Id<HelmInputPayload> getId() { return ID; }

        void write(PacketByteBuf buf) {
            buf.writeString(encounterId); buf.writeString(shipId);
            buf.writeFloat(targetHeading); buf.writeFloat(targetSpeed);
            buf.writeBoolean(evasive);
        }

        static HelmInputPayload read(PacketByteBuf buf) {
            return new HelmInputPayload(buf.readString(), buf.readString(),
                    buf.readFloat(), buf.readFloat(), buf.readBoolean());
        }
    }

    // ── C→S: Power reroute ────────────────────────────────────────────────────

    public record PowerReroutePayload(
            String encounterId,
            String shipId,
            int weapons, int shields, int engines, int sensors
    ) implements CustomPayload {
        public static final Id<PowerReroutePayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "tactical_power"));
        public static final PacketCodec<PacketByteBuf, PowerReroutePayload> CODEC =
                PacketCodec.of(PowerReroutePayload::write, PowerReroutePayload::read);

        @Override public Id<PowerReroutePayload> getId() { return ID; }

        void write(PacketByteBuf buf) {
            buf.writeString(encounterId); buf.writeString(shipId);
            buf.writeInt(weapons); buf.writeInt(shields);
            buf.writeInt(engines); buf.writeInt(sensors);
        }

        static PowerReroutePayload read(PacketByteBuf buf) {
            return new PowerReroutePayload(buf.readString(), buf.readString(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }
    }

    // ── C→S: Shield distribution ──────────────────────────────────────────────

    public record ShieldDistributePayload(
            String encounterId,
            String shipId,
            int fore, int aft, int port, int starboard
    ) implements CustomPayload {
        public static final Id<ShieldDistributePayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "tactical_shields"));
        public static final PacketCodec<PacketByteBuf, ShieldDistributePayload> CODEC =
                PacketCodec.of(ShieldDistributePayload::write, ShieldDistributePayload::read);

        @Override public Id<ShieldDistributePayload> getId() { return ID; }

        void write(PacketByteBuf buf) {
            buf.writeString(encounterId); buf.writeString(shipId);
            buf.writeInt(fore); buf.writeInt(aft);
            buf.writeInt(port); buf.writeInt(starboard);
        }

        static ShieldDistributePayload read(PacketByteBuf buf) {
            return new ShieldDistributePayload(buf.readString(), buf.readString(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }
    }

    // ── C→S: Request fresh update ────────────────────────────────────────────

    public record RequestTacticalUpdatePayload(String stationFilter) implements CustomPayload {
        public static final Id<RequestTacticalUpdatePayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "tactical_request_update"));
        public static final PacketCodec<PacketByteBuf, RequestTacticalUpdatePayload> CODEC =
                PacketCodec.of(RequestTacticalUpdatePayload::write,
                        RequestTacticalUpdatePayload::read);

        @Override public Id<RequestTacticalUpdatePayload> getId() { return ID; }

        void write(PacketByteBuf buf) { buf.writeString(stationFilter); }

        static RequestTacticalUpdatePayload read(PacketByteBuf buf) {
            return new RequestTacticalUpdatePayload(buf.readString());
        }
    }

    // ── Anomaly marker ────────────────────────────────────────────────────────

    public record AnomalyMarker(
            String id, String label, double posX, double posZ, int color
    ) {
        static void write(PacketByteBuf buf, AnomalyMarker m) {
            buf.writeString(m.id()); buf.writeString(m.label());
            buf.writeDouble(m.posX()); buf.writeDouble(m.posZ());
            buf.writeInt(m.color());
        }

        static AnomalyMarker read(PacketByteBuf buf) {
            return new AnomalyMarker(buf.readString(), buf.readString(),
                    buf.readDouble(), buf.readDouble(), buf.readInt());
        }
    }

    // ── S→C: Open screen ─────────────────────────────────────────────────────

    public record OpenTacticalPayload(
            String encounterId,
            String status,
            List<ShipSnapshot> ships,
            List<String> combatLog,
            boolean gmMode,
            String station,
            double shipOriginX,
            double shipOriginZ,
            List<AnomalyMarker> anomalies
    ) implements CustomPayload {
        public static final Id<OpenTacticalPayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "tactical_open"));
        public static final PacketCodec<PacketByteBuf, OpenTacticalPayload> CODEC =
                PacketCodec.of(OpenTacticalPayload::write, OpenTacticalPayload::read);

        @Override public Id<OpenTacticalPayload> getId() { return ID; }

        void write(PacketByteBuf buf) {
            buf.writeString(encounterId); buf.writeString(status);
            buf.writeInt(ships.size());
            ships.forEach(s -> ShipSnapshot.write(buf, s));
            buf.writeInt(combatLog.size());
            combatLog.forEach(buf::writeString);
            buf.writeBoolean(gmMode);
            buf.writeString(station);
            buf.writeDouble(shipOriginX); buf.writeDouble(shipOriginZ);
            buf.writeInt(anomalies.size());
            anomalies.forEach(a -> AnomalyMarker.write(buf, a));
        }

        static OpenTacticalPayload read(PacketByteBuf buf) {
            String eid = buf.readString(); String st = buf.readString();
            int count = buf.readInt();
            List<ShipSnapshot> ships = new ArrayList<>(count);
            for (int i = 0; i < count; i++) ships.add(ShipSnapshot.read(buf));
            int lc = buf.readInt();
            List<String> log = new ArrayList<>(lc);
            for (int i = 0; i < lc; i++) log.add(buf.readString());
            boolean gm     = buf.readBoolean();
            String station = buf.readString();
            double ox = buf.readDouble(); double oz = buf.readDouble();
            int ac = buf.readInt();
            List<AnomalyMarker> anomalies = new ArrayList<>(ac);
            for (int i = 0; i < ac; i++) anomalies.add(AnomalyMarker.read(buf));
            return new OpenTacticalPayload(eid, st, ships, log, gm, station, ox, oz, anomalies);
        }
    }

    // ── S→C: Stellar navigation update ───────────────────────────────────────

    public record OrbitalZoneData(
            String dimensionId,
            String displayName,
            double centerX,
            double centerZ,
            double radius,
            int    minimumWarpSpeed,
            String reachability
    ) {
        static void write(PacketByteBuf buf, OrbitalZoneData z) {
            buf.writeString(z.dimensionId());
            buf.writeString(z.displayName());
            buf.writeDouble(z.centerX());
            buf.writeDouble(z.centerZ());
            buf.writeDouble(z.radius());
            buf.writeInt(z.minimumWarpSpeed());
            buf.writeString(z.reachability());
        }

        static OrbitalZoneData read(PacketByteBuf buf) {
            return new OrbitalZoneData(buf.readString(), buf.readString(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readInt(), buf.readString());
        }
    }

    public record StellarNavUpdatePayload(
            double posX,
            double posZ,
            float  heading,
            float  speed,
            float  targetHeading,
            float  targetSpeed,
            int    warpSpeed,
            boolean warpCapable,
            List<OrbitalZoneData> orbitalZones,
            List<AnomalyMarker> anomalies,
            double shipOriginX,
            double shipOriginZ,
            @org.jetbrains.annotations.Nullable ShipSnapshot homeShipSnapshot
    ) implements CustomPayload {
        public static final Id<StellarNavUpdatePayload> ID =
                new Id<>(Identifier.of("seconddawnrp", "tactical_stellar_nav"));
        public static final PacketCodec<PacketByteBuf, StellarNavUpdatePayload> CODEC =
                PacketCodec.of(StellarNavUpdatePayload::write, StellarNavUpdatePayload::read);

        @Override public Id<StellarNavUpdatePayload> getId() { return ID; }

        void write(PacketByteBuf buf) {
            buf.writeDouble(posX); buf.writeDouble(posZ);
            buf.writeFloat(heading); buf.writeFloat(speed);
            buf.writeFloat(targetHeading); buf.writeFloat(targetSpeed);
            buf.writeInt(warpSpeed); buf.writeBoolean(warpCapable);
            buf.writeInt(orbitalZones.size());
            orbitalZones.forEach(z -> OrbitalZoneData.write(buf, z));
            buf.writeInt(anomalies.size());
            anomalies.forEach(a -> AnomalyMarker.write(buf, a));
            buf.writeDouble(shipOriginX); buf.writeDouble(shipOriginZ);
            buf.writeBoolean(homeShipSnapshot != null);
            if (homeShipSnapshot != null) ShipSnapshot.write(buf, homeShipSnapshot);
        }

        static StellarNavUpdatePayload read(PacketByteBuf buf) {
            double posX = buf.readDouble(); double posZ = buf.readDouble();
            float heading = buf.readFloat(); float speed = buf.readFloat();
            float tHdg = buf.readFloat(); float tSpd = buf.readFloat();
            int warp = buf.readInt(); boolean capable = buf.readBoolean();
            int zc = buf.readInt();
            List<OrbitalZoneData> zones = new ArrayList<>(zc);
            for (int i = 0; i < zc; i++) zones.add(OrbitalZoneData.read(buf));
            int ac = buf.readInt();
            List<AnomalyMarker> anomalies = new ArrayList<>(ac);
            for (int i = 0; i < ac; i++) anomalies.add(AnomalyMarker.read(buf));
            double ox = buf.readDouble(); double oz = buf.readDouble();
            ShipSnapshot snap = buf.readBoolean() ? ShipSnapshot.read(buf) : null;
            return new StellarNavUpdatePayload(posX, posZ, heading, speed,
                    tHdg, tSpd, warp, capable, zones, anomalies, ox, oz, snap);
        }
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(EncounterUpdatePayload.ID,    EncounterUpdatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StandbyUpdatePayload.ID,      StandbyUpdatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(WeaponFirePayload.ID,         WeaponFirePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HelmInputPayload.ID,          HelmInputPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PowerReroutePayload.ID,       PowerReroutePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ShieldDistributePayload.ID,   ShieldDistributePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenTacticalPayload.ID,       OpenTacticalPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RequestTacticalUpdatePayload.ID, RequestTacticalUpdatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StellarNavUpdatePayload.ID,   StellarNavUpdatePayload.CODEC);
    }

    public static void registerServerReceivers() {
        // Weapon fire — pass targetFacing through to TacticalService
        ServerPlayNetworking.registerGlobalReceiver(WeaponFirePayload.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    if (SecondDawnRP.TACTICAL_SERVICE == null) return;
                    SecondDawnRP.TACTICAL_SERVICE.queueWeaponFire(
                            payload.encounterId(), payload.attackerShipId(),
                            payload.targetShipId(), payload.weaponType(),
                            payload.targetFacing());
                }));

        ServerPlayNetworking.registerGlobalReceiver(HelmInputPayload.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    if (payload.evasive()) {
                        if (SecondDawnRP.TACTICAL_SERVICE != null) {
                            SecondDawnRP.TACTICAL_SERVICE.applyEvasiveManeuver(
                                    payload.encounterId(), payload.shipId());
                        }
                    } else {
                        net.shard.seconddawnrp.tactical.command.HelmInputRouter.route(
                                payload, ctx.player());
                    }
                }));

        ServerPlayNetworking.registerGlobalReceiver(PowerReroutePayload.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    if (SecondDawnRP.TACTICAL_SERVICE == null) return;
                    SecondDawnRP.TACTICAL_SERVICE.applyPowerReroute(
                            payload.encounterId(), payload.shipId(),
                            payload.weapons(), payload.shields(),
                            payload.engines(), payload.sensors());
                }));

        ServerPlayNetworking.registerGlobalReceiver(ShieldDistributePayload.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    if (SecondDawnRP.TACTICAL_SERVICE == null) return;
                    SecondDawnRP.TACTICAL_SERVICE.applyShieldDistribution(
                            payload.encounterId(), payload.shipId(),
                            payload.fore(), payload.aft(),
                            payload.port(), payload.starboard());
                }));

        ServerPlayNetworking.registerGlobalReceiver(RequestTacticalUpdatePayload.ID, (payload, ctx) ->
                ctx.server().execute(() -> {
                    if (SecondDawnRP.ENCOUNTER_SERVICE == null) return;
                    var encounters = SecondDawnRP.ENCOUNTER_SERVICE.getAllEncounters();
                    var encounter  = encounters.stream()
                            .filter(e -> e.getStatus() == EncounterState.Status.ACTIVE
                                    || e.getStatus() == EncounterState.Status.PAUSED)
                            .findFirst()
                            .orElse(encounters.isEmpty() ? null : encounters.iterator().next());
                    boolean gmMode = ctx.player().hasPermissionLevel(2)
                            && "ALL".equals(payload.stationFilter());
                    sendOpenPacket(ctx.player(), encounter, payload.stationFilter(), gmMode);
                }));
    }

    // ── S→C helpers ──────────────────────────────────────────────────────────

    public static void broadcastEncounterUpdate(EncounterState encounter,
                                                MinecraftServer server) {
        if (server == null) return;
        List<ShipSnapshot> snapshots = encounter.getAllShips().stream()
                .map(ShipSnapshot::of).toList();
        EncounterUpdatePayload payload = new EncounterUpdatePayload(
                encounter.getEncounterId(),
                encounter.getStatus().name(),
                snapshots,
                encounter.getRecentLog(20));
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static void sendEncounterUpdate(ServerPlayerEntity player,
                                           EncounterState encounter) {
        List<ShipSnapshot> snapshots = encounter.getAllShips().stream()
                .map(ShipSnapshot::of).toList();
        ServerPlayNetworking.send(player, new EncounterUpdatePayload(
                encounter.getEncounterId(),
                encounter.getStatus().name(),
                snapshots,
                encounter.getRecentLog(50)));
    }

    public static void sendOpenPacket(ServerPlayerEntity player,
                                      net.shard.seconddawnrp.tactical.data.EncounterState encounter) {
        sendOpenPacket(player, encounter, "FULL", player.hasPermissionLevel(2));
    }

    public static void sendOpenPacket(ServerPlayerEntity player,
                                      net.shard.seconddawnrp.tactical.data.EncounterState encounter,
                                      String station, boolean gmMode) {
        String encId  = encounter != null ? encounter.getEncounterId() : "STANDBY";
        String status = encounter != null ? encounter.getStatus().name() : "STANDBY";
        List<ShipSnapshot> ships = encounter != null
                ? encounter.getAllShips().stream().map(ShipSnapshot::of).toList()
                : List.of();
        List<String> log = encounter != null ? encounter.getRecentLog(20) : List.of();

        double originX = 0, originZ = 0;
        if (SecondDawnRP.TERMINAL_DESIGNATOR_SERVICE != null
                && player.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
            net.minecraft.util.math.BlockPos origin =
                    SecondDawnRP.TERMINAL_DESIGNATOR_SERVICE.getShipOriginNear(player, sw);
            if (origin != null) { originX = origin.getX(); originZ = origin.getZ(); }
        }

        List<AnomalyMarker> anomalies = new ArrayList<>();
        if (SecondDawnRP.ANOMALY_SERVICE != null) {
            String worldKey = player.getWorld().getRegistryKey().getValue().toString();
            SecondDawnRP.ANOMALY_SERVICE.getAll().stream()
                    .filter(a -> a.isActive() && worldKey.equals(a.getWorldKey()))
                    .forEach(a -> {
                        net.minecraft.util.math.BlockPos pos =
                                net.minecraft.util.math.BlockPos.fromLong(a.getBlockPosLong());
                        int color = switch (a.getType().name()) {
                            case "UNKNOWN"    -> 0xFF3333;
                            case "BIOLOGICAL" -> 0x33FF88;
                            default           -> 0xFFAA00;
                        };
                        anomalies.add(new AnomalyMarker(
                                a.getEntryId(),
                                a.getName() != null ? a.getName() : a.getType().name(),
                                pos.getX(), pos.getZ(), color));
                    });
        }

        ServerPlayNetworking.send(player,
                new OpenTacticalPayload(encId, status, ships, log, gmMode, station,
                        originX, originZ, anomalies));
    }

    public static void sendOpenPacket(ServerPlayerEntity player,
                                      net.shard.seconddawnrp.tactical.data.EncounterState encounter,
                                      String station) {
        sendOpenPacket(player, encounter, station, player.hasPermissionLevel(2));
    }

    public static void sendStellarNavUpdate(ServerPlayerEntity player,
                                            net.shard.seconddawnrp.tactical.data.ShipState homeShip) {
        if (homeShip == null) return;

        double originX = 0, originZ = 0;
        if (SecondDawnRP.TERMINAL_DESIGNATOR_SERVICE != null
                && player.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
            net.minecraft.util.math.BlockPos origin =
                    SecondDawnRP.TERMINAL_DESIGNATOR_SERVICE.getShipOriginNear(player, sw);
            if (origin != null) { originX = origin.getX(); originZ = origin.getZ(); }
        }

        List<OrbitalZoneData> zones = new ArrayList<>();
        if (SecondDawnRP.LOCATION_SERVICE != null) {
            SecondDawnRP.LOCATION_SERVICE.getAllDimensions().stream()
                    .filter(def -> def.orbitalZone() != null)
                    .forEach(def -> {
                        var result = SecondDawnRP.LOCATION_SERVICE.isReachable(
                                def.dimensionId(),
                                homeShip.getPosX(), homeShip.getPosZ(),
                                homeShip.getWarpSpeed());
                        zones.add(new OrbitalZoneData(
                                def.dimensionId(), def.displayName(),
                                def.orbitalZone().centerX(), def.orbitalZone().centerZ(),
                                def.orbitalZone().radius(),
                                def.orbitalZone().minimumWarpSpeed(),
                                result.name()));
                    });
        }

        List<AnomalyMarker> anomalies = new ArrayList<>();
        if (SecondDawnRP.ANOMALY_SERVICE != null) {
            String worldKey = player.getWorld().getRegistryKey().getValue().toString();
            SecondDawnRP.ANOMALY_SERVICE.getAll().stream()
                    .filter(a -> a.isActive() && worldKey.equals(a.getWorldKey()))
                    .forEach(a -> {
                        net.minecraft.util.math.BlockPos pos =
                                net.minecraft.util.math.BlockPos.fromLong(a.getBlockPosLong());
                        int color = switch (a.getType().name()) {
                            case "UNKNOWN"    -> 0xFF3333;
                            case "BIOLOGICAL" -> 0x33FF88;
                            default           -> 0xFFAA00;
                        };
                        anomalies.add(new AnomalyMarker(
                                a.getEntryId(),
                                a.getName() != null ? a.getName() : a.getType().name(),
                                pos.getX(), pos.getZ(), color));
                    });
        }

        ServerPlayNetworking.send(player, new StellarNavUpdatePayload(
                homeShip.getPosX(), homeShip.getPosZ(),
                homeShip.getHeading(), homeShip.getSpeed(),
                homeShip.getTargetHeading(), homeShip.getTargetSpeed(),
                homeShip.getWarpSpeed(), homeShip.isWarpCapable(),
                zones, anomalies, originX, originZ,
                ShipSnapshot.of(homeShip)));
    }
}