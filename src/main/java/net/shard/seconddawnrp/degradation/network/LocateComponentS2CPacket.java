package net.shard.seconddawnrp.degradation.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;

public record LocateComponentS2CPacket(
        String componentId,
        ComponentStatus status,
        double x,
        double y,
        double z
) implements CustomPayload {

    public static final Id<LocateComponentS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "locate_component"));

    public static final PacketCodec<PacketByteBuf, LocateComponentS2CPacket> CODEC =
            PacketCodec.of(
                    LocateComponentS2CPacket::write,
                    LocateComponentS2CPacket::read
            );

    private static LocateComponentS2CPacket read(PacketByteBuf buf) {
        String componentId = buf.readString();
        ComponentStatus status = buf.readEnumConstant(ComponentStatus.class);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        return new LocateComponentS2CPacket(componentId, status, x, y, z);
    }

    private void write(PacketByteBuf buf) {
        buf.writeString(componentId);
        buf.writeEnumConstant(status);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}