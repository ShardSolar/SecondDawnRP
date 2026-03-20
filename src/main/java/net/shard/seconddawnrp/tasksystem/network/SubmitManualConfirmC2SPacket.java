package net.shard.seconddawnrp.tasksystem.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;

public record SubmitManualConfirmC2SPacket(String taskId) implements CustomPayload {

    public static final CustomPayload.Id<SubmitManualConfirmC2SPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("submit_manual_confirm"));

    public static final PacketCodec<RegistryByteBuf, SubmitManualConfirmC2SPacket> CODEC =
            PacketCodec.of(SubmitManualConfirmC2SPacket::write, SubmitManualConfirmC2SPacket::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(taskId);
    }

    private static SubmitManualConfirmC2SPacket read(RegistryByteBuf buf) {
        return new SubmitManualConfirmC2SPacket(buf.readString());
    }
}