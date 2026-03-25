package net.shard.seconddawnrp.degradation.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentEntry;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;
import net.shard.seconddawnrp.degradation.service.DegradationService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Sent server -> client when the player right-clicks with the Engineering PAD
 * in air. Carries a snapshot of all registered components so the client
 * screen can render them without a round-trip.
 */
public record OpenEngineeringPadS2CPacket(
        List<ComponentSnapshot> components,
        List<WarpCoreSnapshot> warpCores,
        String focusedCoreId,
        String warpCoreState,
        int warpCoreFuel,
        int warpCoreMaxFuel,
        int warpCorePower
) implements CustomPayload {

    public static final Id<OpenEngineeringPadS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "open_engineering_pad"));

    /**
     * Lightweight view of a component for screen rendering.
     */
    public record ComponentSnapshot(
            String componentId,
            String displayName,
            String worldKey,
            long blockPosLong,
            int health,
            ComponentStatus status,
            String repairItemId,
            int repairItemCount
    ) {}

    /** Lightweight view of a warp core for Engineering Pad display. */
    public record WarpCoreSnapshot(
            String entryId,
            String state,
            int fuel,
            int maxFuel,
            int power,
            int coilHealth,
            int coilCount
    ) {}

    // ── Codecs ────────────────────────────────────────────────────────────────

    private static final PacketCodec<RegistryByteBuf, ComponentSnapshot> SNAPSHOT_CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeString(value.componentId());
                        buf.writeString(value.displayName());
                        buf.writeString(value.worldKey());
                        buf.writeLong(value.blockPosLong());
                        buf.writeInt(value.health());
                        buf.writeString(value.status().name());
                        buf.writeString(value.repairItemId() != null ? value.repairItemId() : "");
                        buf.writeInt(value.repairItemCount());
                    },
                    buf -> new ComponentSnapshot(
                            buf.readString(),
                            buf.readString(),
                            buf.readString(),
                            buf.readLong(),
                            buf.readInt(),
                            ComponentStatus.valueOf(buf.readString()),
                            buf.readString(),
                            buf.readInt()
                    )
            );

    private static final PacketCodec<RegistryByteBuf, WarpCoreSnapshot> WC_CODEC =
            PacketCodec.of(
                    (v, buf) -> {
                        buf.writeString(v.entryId());
                        buf.writeString(v.state());
                        buf.writeInt(v.fuel());
                        buf.writeInt(v.maxFuel());
                        buf.writeInt(v.power());
                        buf.writeInt(v.coilHealth());
                        buf.writeInt(v.coilCount());
                    },
                    buf -> new WarpCoreSnapshot(
                            buf.readString(), buf.readString(), buf.readInt(),
                            buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt())
            );

    public static final PacketCodec<RegistryByteBuf, OpenEngineeringPadS2CPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeInt(value.components().size());
                        for (ComponentSnapshot snap : value.components()) {
                            SNAPSHOT_CODEC.encode(buf, snap);
                        }
                        buf.writeInt(value.warpCores().size());
                        for (WarpCoreSnapshot wc : value.warpCores()) {
                            WC_CODEC.encode(buf, wc);
                        }
                        buf.writeString(value.focusedCoreId() != null ? value.focusedCoreId() : "");
                        buf.writeString(value.warpCoreState());
                        buf.writeInt(value.warpCoreFuel());
                        buf.writeInt(value.warpCoreMaxFuel());
                        buf.writeInt(value.warpCorePower());
                    },
                    buf -> {
                        int size = buf.readInt();
                        List<ComponentSnapshot> list = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            list.add(SNAPSHOT_CODEC.decode(buf));
                        }
                        int wcSize = buf.readInt();
                        List<WarpCoreSnapshot> wcList = new ArrayList<>(wcSize);
                        for (int i = 0; i < wcSize; i++) wcList.add(WC_CODEC.decode(buf));
                        String focusedId = buf.readString();
                        return new OpenEngineeringPadS2CPacket(
                                list,
                                wcList,
                                focusedId,
                                buf.readString(),
                                buf.readInt(),
                                buf.readInt(),
                                buf.readInt()
                        );
                    }
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Build a packet from the live service, sorted worst-first.
     */
    public static OpenEngineeringPadS2CPacket fromService(DegradationService service) {
        List<ComponentSnapshot> snapshots = new ArrayList<>();
        for (ComponentEntry entry : service.getAllComponents()) {
            // Resolve effective repair item — fall back to config default
            String repairId = entry.getRepairItemId() != null && !entry.getRepairItemId().isEmpty()
                    ? entry.getRepairItemId()
                    : service.getConfig().getDefaultRepairItemId();
            int repairCount = entry.getRepairItemCount() > 0
                    ? entry.getRepairItemCount()
                    : service.getConfig().getDefaultRepairItemCount();
            snapshots.add(new ComponentSnapshot(
                    entry.getComponentId(),
                    entry.getDisplayName(),
                    entry.getWorldKey(),
                    entry.getBlockPosLong(),
                    entry.getHealth(),
                    entry.getStatus(),
                    repairId,
                    repairCount
            ));
        }
        snapshots.sort(Comparator
                .<ComponentSnapshot>comparingInt(s -> statusSortKey(s.status()))
                .thenComparingInt(ComponentSnapshot::health));
        // Build warp core snapshots for ALL registered cores
        List<WarpCoreSnapshot> wcSnapshots = new ArrayList<>();
        var wcService = net.shard.seconddawnrp.SecondDawnRP.WARP_CORE_SERVICE;
        String wcState = ""; int wcFuel = 0, wcMaxFuel = 64, wcPower = 0;
        if (wcService != null && wcService.isRegistered()) {
            for (var wcEntry : wcService.getAll()) {
                int coilHealth = wcService.getCoilHealth(wcEntry);
                int coilCount  = wcEntry.getResonanceCoilIds().size();
                wcSnapshots.add(new WarpCoreSnapshot(
                        wcEntry.getEntryId(), wcEntry.getState().name(),
                        wcEntry.getFuelRods(), wcService.getConfig().getMaxFuelRods(),
                        wcEntry.getCurrentPowerOutput(), coilHealth, coilCount));
            }
            // Legacy single-core fields — use first core for backward compat
            var first = wcService.getAll().iterator().next();
            wcState  = first.getState().name();
            wcFuel   = first.getFuelRods();
            wcMaxFuel = wcService.getConfig().getMaxFuelRods();
            wcPower  = first.getCurrentPowerOutput();
        }
        return new OpenEngineeringPadS2CPacket(snapshots, wcSnapshots, null, wcState, wcFuel, wcMaxFuel, wcPower);
    }

    /** Build packet focused on a specific warp core (opened via controller block). */
    public static OpenEngineeringPadS2CPacket fromServiceWithCore(
            DegradationService service, String focusedCoreId) {
        OpenEngineeringPadS2CPacket base = fromService(service);
        // Filter warpCores to only the focused one
        List<WarpCoreSnapshot> focused = base.warpCores().stream()
                .filter(wc -> wc.entryId().equals(focusedCoreId))
                .toList();
        // Use focused core legacy fields
        var wcService = net.shard.seconddawnrp.SecondDawnRP.WARP_CORE_SERVICE;
        String wcState = ""; int wcFuel = 0, wcMaxFuel = 64, wcPower = 0;
        if (wcService != null) {
            var entry = wcService.getById(focusedCoreId);
            if (entry.isPresent()) {
                wcState = entry.get().getState().name();
                wcFuel = entry.get().getFuelRods();
                wcMaxFuel = wcService.getConfig().getMaxFuelRods();
                wcPower = entry.get().getCurrentPowerOutput();
            }
        }
        return new OpenEngineeringPadS2CPacket(
                base.components(), focused, focusedCoreId,
                wcState, wcFuel, wcMaxFuel, wcPower);
    }

    private static int statusSortKey(ComponentStatus status) {
        return switch (status) {
            case OFFLINE  -> 0;
            case CRITICAL -> 1;
            case DEGRADED -> 2;
            case NOMINAL  -> 3;
        };
    }
}