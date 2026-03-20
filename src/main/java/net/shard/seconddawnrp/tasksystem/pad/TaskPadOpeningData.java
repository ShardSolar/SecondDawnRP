package net.shard.seconddawnrp.tasksystem.pad;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;

import java.util.List;

public record TaskPadOpeningData(
        List<String> activeLines,
        List<String> completedLines,
        List<String> activeTaskIds
) {
    public static final PacketCodec<RegistryByteBuf, TaskPadOpeningData> PACKET_CODEC =
            PacketCodec.of(TaskPadOpeningData::write, TaskPadOpeningData::read);

    private void write(RegistryByteBuf buf) {
        buf.writeCollection(activeLines, PacketByteBuf::writeString);
        buf.writeCollection(completedLines, PacketByteBuf::writeString);
        buf.writeCollection(activeTaskIds, PacketByteBuf::writeString);
    }

    private static TaskPadOpeningData read(RegistryByteBuf buf) {
        List<String> activeLines = buf.readList(PacketByteBuf::readString);
        List<String> completedLines = buf.readList(PacketByteBuf::readString);
        List<String> activeTaskIds = buf.readList(PacketByteBuf::readString);
        return new TaskPadOpeningData(activeLines, completedLines, activeTaskIds);
    }
}