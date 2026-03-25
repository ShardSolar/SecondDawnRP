package net.shard.seconddawnrp.character;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Registers all network payloads for the character creation system.
 *
 * <p>Called from {@code SecondDawnRP.onInitialize()} before any other
 * networking setup, following the same pattern as
 * {@code GmEventNetworking.registerPayloads()}.
 */
public final class CharacterCreationNetworking {

    private CharacterCreationNetworking() {}

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(
                OpenCharacterCreationS2CPacket.ID,
                OpenCharacterCreationS2CPacket.CODEC);

        PayloadTypeRegistry.playC2S().register(
                SaveCharacterCreationC2SPacket.ID,
                SaveCharacterCreationC2SPacket.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(
                SaveCharacterCreationC2SPacket.ID,
                (packet, context) -> context.server().execute(() ->
                        SaveCharacterCreationC2SPacket.handle(packet, context.player())));
    }
}