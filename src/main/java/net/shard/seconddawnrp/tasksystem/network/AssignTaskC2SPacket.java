package net.shard.seconddawnrp.tasksystem.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;

public record AssignTaskC2SPacket(
        String taskId,
        String assignModeName,
        String playerName,
        String divisionName
) implements CustomPayload {

    public static final CustomPayload.Id<AssignTaskC2SPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("assign_task"));

    public static final PacketCodec<RegistryByteBuf, AssignTaskC2SPacket> CODEC =
            PacketCodec.of(AssignTaskC2SPacket::write, AssignTaskC2SPacket::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(taskId);
        buf.writeString(assignModeName);
        buf.writeString(playerName);
        buf.writeString(divisionName);
    }

    private static AssignTaskC2SPacket read(RegistryByteBuf buf) {
        return new AssignTaskC2SPacket(
                buf.readString(),
                buf.readString(),
                buf.readString(),
                buf.readString()
        );
    }
}