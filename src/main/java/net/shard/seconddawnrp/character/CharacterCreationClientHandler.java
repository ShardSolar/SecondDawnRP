package net.shard.seconddawnrp.character;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Client-side receiver for {@link OpenCharacterCreationS2CPacket}.
 *
 * <p>Registered from {@code SecondDawnRPClient.onInitializeClient()}.
 */
public final class CharacterCreationClientHandler {

    private CharacterCreationClientHandler() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                OpenCharacterCreationS2CPacket.ID,
                (packet, context) -> context.client().execute(() -> {
                    MinecraftClient.getInstance().setScreen(
                            new CharacterCreationScreen(
                                    packet.species(),
                                    packet.currentCharacterName(),
                                    packet.currentSpeciesId(),
                                    packet.currentBio(),
                                    packet.speciesLocked()
                            )
                    );
                })
        );
    }
}