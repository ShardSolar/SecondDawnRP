package net.shard.seconddawnrp.tactical.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;

/**
 * S→C packet that triggers locator particles around a registered zone block.
 *
 * Sent by DamageZoneToolItem when registering or removing a block, and by
 * /admin hardpoint zone locate to highlight all blocks in a zone at once.
 *
 * mode: "MODEL" or "REAL" — determines particle color set on the client.
 */
public record LocateZoneBlockS2CPacket(
        String zoneId,
        String mode,   // "MODEL" or "REAL"
        double x,
        double y,
        double z
) implements CustomPayload {

    public static final Id<LocateZoneBlockS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "locate_zone_block"));

    public static final PacketCodec<PacketByteBuf, LocateZoneBlockS2CPacket> CODEC =
            PacketCodec.of(LocateZoneBlockS2CPacket::write, LocateZoneBlockS2CPacket::read);

    private static LocateZoneBlockS2CPacket read(PacketByteBuf buf) {
        return new LocateZoneBlockS2CPacket(
                buf.readString(),
                buf.readString(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble()
        );
    }

    private void write(PacketByteBuf buf) {
        buf.writeString(zoneId);
        buf.writeString(mode);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}