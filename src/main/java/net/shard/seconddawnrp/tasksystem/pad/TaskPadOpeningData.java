package net.shard.seconddawnrp.tasksystem.pad;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

import java.util.ArrayList;
import java.util.List;

public record TaskPadOpeningData(List<String> activeLines, List<String> completedLines) {

    public static final PacketCodec<RegistryByteBuf, TaskPadOpeningData> PACKET_CODEC =
            PacketCodec.of(TaskPadOpeningData::write, TaskPadOpeningData::read);

    public static TaskPadOpeningData read(RegistryByteBuf buf) {
        return new TaskPadOpeningData(readLines(buf), readLines(buf));
    }

    public void write(RegistryByteBuf buf) {
        writeLines(buf, activeLines);
        writeLines(buf, completedLines);
    }

    private static void writeLines(RegistryByteBuf buf, List<String> lines) {
        buf.writeVarInt(lines.size());
        for (String line : lines) {
            buf.writeString(line);
        }
    }

    private static List<String> readLines(RegistryByteBuf buf) {
        int size = buf.readVarInt();
        List<String> lines = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            lines.add(buf.readString());
        }

        return lines;
    }
}