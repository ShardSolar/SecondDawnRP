package net.shard.seconddawnrp.gmevent.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.shard.seconddawnrp.gmevent.network.EnvRegistryS2CPacket;
import net.shard.seconddawnrp.gmevent.network.OpenEnvConfigS2CPacket;
import net.shard.seconddawnrp.gmevent.screen.EnvEffectConfigScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side handlers for Environmental Effect Block networking.
 * Caches the registry locally so the config screen can render dropdowns.
 */
public final class EnvEffectClientHandler {

    private EnvEffectClientHandler() {}

    // Cached registry — set when EnvRegistryS2CPacket is received
    private static List<net.shard.seconddawnrp.gmevent.data.VanillaEffectDefinition>
            cachedEffects = new ArrayList<>();
    private static List<net.shard.seconddawnrp.gmevent.data.MedicalConditionDefinition>
            cachedConditions = new ArrayList<>();

    public static void register() {
        // Registry arrives first, then the config screen packet
        ClientPlayNetworking.registerGlobalReceiver(
                EnvRegistryS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> {
                    cachedEffects    = payload.vanillaEffects();
                    cachedConditions = payload.medicalConditions();
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(
                OpenEnvConfigS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> {
                    EnvEffectConfigScreen screen = new EnvEffectConfigScreen(
                            payload.entryId(),
                            payload.vanillaEffects(),
                            payload.medicalConditionId(),
                            payload.medicalConditionSeverity(),
                            payload.radiusBlocks(),
                            payload.lingerMode(),
                            payload.lingerDurationTicks(),
                            payload.fireMode(),
                            payload.onEntryCooldownTicks(),
                            payload.visibility(),
                            payload.active()
                    );
                    screen.setRegistry(cachedEffects, cachedConditions);
                    context.client().setScreen(screen);
                })
        );
    }
}