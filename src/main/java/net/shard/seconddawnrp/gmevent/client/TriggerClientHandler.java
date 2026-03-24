package net.shard.seconddawnrp.gmevent.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.shard.seconddawnrp.gmevent.network.OpenTriggerConfigS2CPacket;
import net.shard.seconddawnrp.gmevent.screen.TriggerConfigScreen;

public final class TriggerClientHandler {

    private TriggerClientHandler() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                OpenTriggerConfigS2CPacket.ID,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreen(new TriggerConfigScreen(
                                payload.entryId(), payload.triggerMode(), payload.fireMode(),
                                payload.radiusBlocks(), payload.cooldownTicks(),
                                payload.armed(), payload.actions()))
                )
        );
    }
}