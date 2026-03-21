package net.shard.seconddawnrp.tasksystem.terminal;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;

import java.util.ArrayList;
import java.util.List;

public record TerminalScreenOpenData(List<TerminalTaskEntry> tasks) {

    public record TerminalTaskEntry(
            String taskId,
            String displayName,
            String divisionName,
            String objectiveType,
            String target,
            int requiredAmount,
            int rewardPoints,
            boolean officerConfirmationRequired
    ) {
        public static final PacketCodec<RegistryByteBuf, TerminalTaskEntry> CODEC =
                PacketCodec.of(TerminalTaskEntry::write, TerminalTaskEntry::read);

        private void write(RegistryByteBuf buf) {
            buf.writeString(taskId);
            buf.writeString(displayName);
            buf.writeString(divisionName);
            buf.writeString(objectiveType);
            buf.writeString(target);
            buf.writeInt(requiredAmount);
            buf.writeInt(rewardPoints);
            buf.writeBoolean(officerConfirmationRequired);
        }

        private static TerminalTaskEntry read(RegistryByteBuf buf) {
            return new TerminalTaskEntry(
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
    }

    public static final PacketCodec<RegistryByteBuf, TerminalScreenOpenData> PACKET_CODEC =
            PacketCodec.of(TerminalScreenOpenData::write, TerminalScreenOpenData::read);

    private void write(RegistryByteBuf buf) {
        buf.writeInt(tasks.size());
        for (TerminalTaskEntry entry : tasks) {
            TerminalTaskEntry.CODEC.encode(buf, entry);
        }
    }

    private static TerminalScreenOpenData read(RegistryByteBuf buf) {
        int count = buf.readInt();
        List<TerminalTaskEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(TerminalTaskEntry.CODEC.decode(buf));
        }
        return new TerminalScreenOpenData(entries);
    }
}