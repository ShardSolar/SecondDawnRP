package net.shard.seconddawnrp.tasksystem.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;

public record ReviewTaskActionC2SPacket(
        String taskId,
        String actionName
) implements CustomPayload {

    public static final CustomPayload.Id<ReviewTaskActionC2SPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("review_task_action"));

    public static final PacketCodec<RegistryByteBuf, ReviewTaskActionC2SPacket> CODEC =
            PacketCodec.of(ReviewTaskActionC2SPacket::write, ReviewTaskActionC2SPacket::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(taskId);
        buf.writeString(actionName);
    }

    private static ReviewTaskActionC2SPacket read(RegistryByteBuf buf) {
        return new ReviewTaskActionC2SPacket(
                buf.readString(),
                buf.readString()
        );
    }
}