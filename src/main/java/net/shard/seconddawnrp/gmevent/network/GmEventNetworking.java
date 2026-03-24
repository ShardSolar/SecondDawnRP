package net.shard.seconddawnrp.gmevent.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.data.EnvironmentalEffectEntry;
import net.shard.seconddawnrp.gmevent.data.TriggerEntry;
import net.shard.seconddawnrp.gmevent.network.EnvRegistryS2CPacket;
import net.shard.seconddawnrp.gmevent.network.OpenTriggerConfigS2CPacket;
import net.shard.seconddawnrp.gmevent.network.SaveTriggerConfigC2SPacket;

/**
 * Registers all Phase 4.5 networking payloads and server-side receivers.
 * Called from SecondDawnRP.onInitialize().
 */
public final class GmEventNetworking {

    private GmEventNetworking() {}

    public static void registerPayloads() {
        // S2C
        PayloadTypeRegistry.playS2C().register(OpenEnvConfigS2CPacket.ID, OpenEnvConfigS2CPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(EnvRegistryS2CPacket.ID, EnvRegistryS2CPacket.CODEC);

        // Trigger packets S2C + C2S
        PayloadTypeRegistry.playS2C().register(OpenTriggerConfigS2CPacket.ID, OpenTriggerConfigS2CPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveTriggerConfigC2SPacket.ID, SaveTriggerConfigC2SPacket.CODEC);

        // Env C2S
        PayloadTypeRegistry.playC2S().register(SaveEnvConfigC2SPacket.ID, SaveEnvConfigC2SPacket.CODEC);

        PayloadTypeRegistry.playS2C().register(ToolVisibilityS2CPacket.ID,
                ToolVisibilityS2CPacket.CODEC);
    }

    public static void registerServerReceivers() {
        // Trigger save receiver
        ServerPlayNetworking.registerGlobalReceiver(
                SaveTriggerConfigC2SPacket.ID,
                (payload, context) -> context.server().execute(() -> {
                    var opt = SecondDawnRP.TRIGGER_SERVICE.getById(payload.entryId());
                    if (opt.isEmpty()) return;
                    TriggerEntry entry = opt.get();
                    entry.setTriggerMode(payload.triggerMode());
                    entry.setFireMode(payload.fireMode());
                    entry.setRadiusBlocks(payload.radiusBlocks());
                    entry.setCooldownTicks(payload.cooldownTicks());
                    entry.setArmed(payload.armed());
                    entry.setActions(payload.actions());
                    SecondDawnRP.TRIGGER_SERVICE.saveEntry(entry);
                })
        );

        // Env save receiver
        ServerPlayNetworking.registerGlobalReceiver(
                SaveEnvConfigC2SPacket.ID,
                (payload, context) -> context.server().execute(() -> {
                    var opt = SecondDawnRP.ENV_EFFECT_SERVICE.getById(payload.entryId());
                    if (opt.isEmpty()) return;

                    EnvironmentalEffectEntry entry = opt.get();
                    entry.setVanillaEffects(payload.vanillaEffects());
                    entry.setMedicalConditionId(payload.medicalConditionId());
                    entry.setMedicalConditionSeverity(payload.medicalConditionSeverity());
                    entry.setRadiusBlocks(payload.radiusBlocks());
                    entry.setLingerMode(payload.lingerMode());
                    entry.setLingerDurationTicks(payload.lingerDurationTicks());
                    entry.setFireMode(payload.fireMode());
                    entry.setOnEntryCooldownTicks(payload.onEntryCooldownTicks());
                    entry.setVisibility(payload.visibility());
                    entry.setActive(payload.active());
                    SecondDawnRP.ENV_EFFECT_SERVICE.saveEntry(entry);
                })
        );



    }
}