package net.shard.seconddawnrp.tasksystem.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;

public record AcceptTerminalTaskC2SPacket(String taskId) implements CustomPayload {

    public static final CustomPayload.Id<AcceptTerminalTaskC2SPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("accept_terminal_task"));

    public static final PacketCodec<RegistryByteBuf, AcceptTerminalTaskC2SPacket> CODEC =
            PacketCodec.of(AcceptTerminalTaskC2SPacket::write, AcceptTerminalTaskC2SPacket::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(taskId);
    }

    private static AcceptTerminalTaskC2SPacket read(RegistryByteBuf buf) {
        return new AcceptTerminalTaskC2SPacket(buf.readString());
    }
}