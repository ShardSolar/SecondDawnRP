package net.shard.seconddawnrp.gmevent.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.data.MedicalConditionDefinition;
import net.shard.seconddawnrp.gmevent.data.VanillaEffectDefinition;

import java.util.List;

/**
 * Sent server → client when a GM opens the env effect config GUI.
 * Carries the full vanilla effects and medical conditions registries
 * so the client can render the dropdown lists without a server round-trip.
 */
public record EnvRegistryS2CPacket(
        List<VanillaEffectDefinition> vanillaEffects,
        List<MedicalConditionDefinition> medicalConditions
) implements CustomPayload {

    public static final Id<EnvRegistryS2CPacket> ID =
            new Id<>(Identifier.of(SecondDawnRP.MOD_ID, "env_registry"));

    public static final PacketCodec<RegistryByteBuf, EnvRegistryS2CPacket> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        // Vanilla effects
                        buf.writeInt(value.vanillaEffects().size());
                        for (VanillaEffectDefinition e : value.vanillaEffects()) {
                            buf.writeString(e.getEffectId());
                            buf.writeString(e.getDisplayName());
                            buf.writeInt(e.getDefaultAmplitude());
                            buf.writeInt(e.getDefaultDurationTicks());
                        }
                        // Medical conditions
                        buf.writeInt(value.medicalConditions().size());
                        for (MedicalConditionDefinition c : value.medicalConditions()) {
                            buf.writeString(c.getConditionId());
                            buf.writeString(c.getDisplayName());
                            buf.writeString(c.getDefaultSeverity());
                            buf.writeString(c.getDescription());
                        }
                    },
                    buf -> {
                        int efCount = buf.readInt();
                        List<VanillaEffectDefinition> effects = new java.util.ArrayList<>();
                        for (int i = 0; i < efCount; i++) {
                            effects.add(new VanillaEffectDefinition(
                                    buf.readString(), buf.readString(),
                                    buf.readInt(), buf.readInt()));
                        }
                        int cdCount = buf.readInt();
                        List<MedicalConditionDefinition> conditions = new java.util.ArrayList<>();
                        for (int i = 0; i < cdCount; i++) {
                            conditions.add(new MedicalConditionDefinition(
                                    buf.readString(), buf.readString(),
                                    buf.readString(), buf.readString()));
                        }
                        return new EnvRegistryS2CPacket(effects, conditions);
                    }
            );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }

    public static EnvRegistryS2CPacket fromService() {
        return new EnvRegistryS2CPacket(
                SecondDawnRP.GM_REGISTRY_SERVICE.getVanillaEffects(),
                SecondDawnRP.GM_REGISTRY_SERVICE.getMedicalConditions());
    }
}