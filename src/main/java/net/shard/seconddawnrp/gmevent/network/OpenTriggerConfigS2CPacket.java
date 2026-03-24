package net.shard.seconddawnrp.gmevent.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.data.*;

import java.util.ArrayList;
import java.util.List;

public record OpenTriggerConfigS2CPacket(
        String entryId,
        TriggerMode triggerMode,
        TriggerFireMode fireMode,
        int radiusBlocks,
        int cooldownTicks,
        boolean armed,
        List<TriggerAction> actions
) implements CustomPayload {

    public static final Id<OpenTriggerConfigS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "open_trigger_config"));

    public static final PacketCodec<RegistryByteBuf, OpenTriggerConfigS2CPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeString(value.entryId());
                        buf.writeString(value.triggerMode().name());
                        buf.writeString(value.fireMode().name());
                        buf.writeInt(value.radiusBlocks());
                        buf.writeInt(value.cooldownTicks());
                        buf.writeBoolean(value.armed());
                        buf.writeInt(value.actions().size());
                        for (TriggerAction a : value.actions()) {
                            buf.writeString(a.getType().name());
                            buf.writeString(a.getPayload());
                        }
                    },
                    buf -> {
                        String entryId = buf.readString();
                        TriggerMode tm = TriggerMode.valueOf(buf.readString());
                        TriggerFireMode fm = TriggerFireMode.valueOf(buf.readString());
                        int radius = buf.readInt();
                        int cooldown = buf.readInt();
                        boolean armed = buf.readBoolean();
                        int count = buf.readInt();
                        List<TriggerAction> actions = new ArrayList<>();
                        for (int i = 0; i < count; i++) {
                            actions.add(new TriggerAction(
                                    TriggerActionType.valueOf(buf.readString()),
                                    buf.readString()));
                        }
                        return new OpenTriggerConfigS2CPacket(entryId, tm, fm, radius, cooldown, armed, actions);
                    }
            );

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    public static OpenTriggerConfigS2CPacket from(TriggerEntry e) {
        return new OpenTriggerConfigS2CPacket(
                e.getEntryId(), e.getTriggerMode(), e.getFireMode(),
                e.getRadiusBlocks(), e.getCooldownTicks(), e.isArmed(),
                new ArrayList<>(e.getActions()));
    }
}