package net.shard.seconddawnrp.tasksystem.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.divison.Division;
import net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType;

public record CreateTaskC2SPacket(
        String taskId,
        String displayName,
        String description,
        String divisionName,
        String objectiveTypeName,
        String targetId,
        int requiredAmount,
        int rewardPoints,
        boolean officerConfirmationRequired
) implements CustomPayload {

    public static final CustomPayload.Id<CreateTaskC2SPacket> ID =
            new CustomPayload.Id<>(SecondDawnRP.id("create_task"));

    public static final PacketCodec<RegistryByteBuf, CreateTaskC2SPacket> CODEC =
            PacketCodec.of(CreateTaskC2SPacket::write, CreateTaskC2SPacket::read);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    private void write(RegistryByteBuf buf) {
        buf.writeString(taskId);
        buf.writeString(displayName);
        buf.writeString(description);
        buf.writeString(divisionName);
        buf.writeString(objectiveTypeName);
        buf.writeString(targetId);
        buf.writeInt(requiredAmount);
        buf.writeInt(rewardPoints);
        buf.writeBoolean(officerConfirmationRequired);
    }

    private static CreateTaskC2SPacket read(RegistryByteBuf buf) {
        return new CreateTaskC2SPacket(
                buf.readString(),
                buf.readString(),
                buf.readString(),
                buf.readString(),
                buf.readString(),
                buf.readString(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean()
        );
    }

    public Division getDivision() {
        return Division.valueOf(divisionName);
    }

    public TaskObjectiveType getObjectiveType() {
        return TaskObjectiveType.valueOf(objectiveTypeName);
    }
}