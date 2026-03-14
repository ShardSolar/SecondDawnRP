package net.shard.shipyardsrp.tasksystem.padd;

import net.minecraft.network.RegistryByteBuf;

import java.util.ArrayList;
import java.util.List;

public record TaskPaddOpeningData(List<String> activeLines, List<String> completedLines) {

    public static TaskPaddOpeningData read(RegistryByteBuf buf) {
        return new TaskPaddOpeningData(readLines(buf), readLines(buf));
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