package net.shard.seconddawnrp.tasksystem.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;

public record OpsPadRefreshS2CPacket() implements CustomPayload {

    public static final CustomPayload.Id<OpsPadRefreshS2CPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("ops_pad_refresh"));

    public static final PacketCodec<RegistryByteBuf, OpsPadRefreshS2CPacket> CODEC =
            PacketCodec.of((value, buf) -> {
            }, buf -> new OpsPadRefreshS2CPacket());

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}