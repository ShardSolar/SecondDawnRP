package net.shard.seconddawnrp.gmevent.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.data.*;

import java.util.ArrayList;
import java.util.List;

public record OpenEnvConfigS2CPacket(
        String entryId,
        List<String> vanillaEffects,
        String medicalConditionId,
        String medicalConditionSeverity,
        int radiusBlocks,
        LingerMode lingerMode,
        int lingerDurationTicks,
        EnvFireMode fireMode,
        int onEntryCooldownTicks,
        EnvVisibility visibility,
        boolean active
) implements CustomPayload {

    public static final Id<OpenEnvConfigS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "open_env_config"));

    public static final PacketCodec<RegistryByteBuf, OpenEnvConfigS2CPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeString(value.entryId());
                        buf.writeInt(value.vanillaEffects().size());
                        value.vanillaEffects().forEach(buf::writeString);
                        buf.writeString(value.medicalConditionId() != null ? value.medicalConditionId() : "");
                        buf.writeString(value.medicalConditionSeverity());
                        buf.writeInt(value.radiusBlocks());
                        buf.writeString(value.lingerMode().name());
                        buf.writeInt(value.lingerDurationTicks());
                        buf.writeString(value.fireMode().name());
                        buf.writeInt(value.onEntryCooldownTicks());
                        buf.writeString(value.visibility().name());
                        buf.writeBoolean(value.active());
                    },
                    buf -> {
                        String entryId = buf.readString();
                        int fxCount = buf.readInt();
                        List<String> fx = new ArrayList<>();
                        for (int i = 0; i < fxCount; i++) fx.add(buf.readString());
                        String condId = buf.readString();
                        String condSev = buf.readString();
                        int radius = buf.readInt();
                        LingerMode linger = LingerMode.valueOf(buf.readString());
                        int lingerTicks = buf.readInt();
                        EnvFireMode fire = EnvFireMode.valueOf(buf.readString());
                        int cooldown = buf.readInt();
                        EnvVisibility vis = EnvVisibility.valueOf(buf.readString());
                        boolean active = buf.readBoolean();
                        return new OpenEnvConfigS2CPacket(
                                entryId, fx,
                                condId.isEmpty() ? null : condId,
                                condSev, radius, linger, lingerTicks,
                                fire, cooldown, vis, active);
                    }
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    public static OpenEnvConfigS2CPacket from(EnvironmentalEffectEntry e) {
        return new OpenEnvConfigS2CPacket(
                e.getEntryId(), e.getVanillaEffects(),
                e.getMedicalConditionId(), e.getMedicalConditionSeverity(),
                e.getRadiusBlocks(), e.getLingerMode(), e.getLingerDurationTicks(),
                e.getFireMode(), e.getOnEntryCooldownTicks(),
                e.getVisibility(), e.isActive());
    }
}