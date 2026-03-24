package net.shard.seconddawnrp.gmevent.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;

import java.util.ArrayList;
import java.util.List;

/**
 * Sent server → client when a GM equips or unequips a registration tool.
 *
 * <p>Carries a list of block positions (as longs) and a colour (ARGB int)
 * for the particle column to render. An empty positions list means
 * clear all active columns (tool was unequipped).
 *
 * <p>The client renders a particle column at each position while the
 * packet is active. A new packet from the server replaces the previous one.
 */
public record ToolVisibilityS2CPacket(
        List<Long> blockPositions,
        int particleColour,     // ARGB — used for dust particles
        String worldKey         // only render in this world
) implements CustomPayload {

    public static final Id<ToolVisibilityS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "tool_visibility"));

    public static final PacketCodec<RegistryByteBuf, ToolVisibilityS2CPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeInt(value.blockPositions().size());
                        for (long pos : value.blockPositions()) buf.writeLong(pos);
                        buf.writeInt(value.particleColour());
                        buf.writeString(value.worldKey());
                    },
                    buf -> {
                        int count = buf.readInt();
                        List<Long> positions = new ArrayList<>(count);
                        for (int i = 0; i < count; i++) positions.add(buf.readLong());
                        int colour = buf.readInt();
                        String worldKey = buf.readString();
                        return new ToolVisibilityS2CPacket(positions, colour, worldKey);
                    }
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    /** Empty packet — clears all columns on the client. */
    public static ToolVisibilityS2CPacket clear() {
        return new ToolVisibilityS2CPacket(List.of(), 0, "");
    }
}