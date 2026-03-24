package net.shard.seconddawnrp.gmevent.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.shard.seconddawnrp.gmevent.network.ToolVisibilityS2CPacket;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;

/**
 * Client-side handler for tool visibility particle columns.
 *
 * <p>When {@link ToolVisibilityS2CPacket} is received, stores the position list
 * and colour. Every 10 client ticks, spawns dust particles in a column above
 * each registered block position, visible only to the GM holding the tool.
 *
 * <p>No entities, no persistent state on the server — purely client-side visual.
 */
public final class ToolVisibilityClientHandler {

    private ToolVisibilityClientHandler() {}

    private static List<Long> activePositions = Collections.emptyList();
    private static int activeColour = 0xFFFFAA00;
    private static String activeWorldKey = "";
    private static int tickCounter = 0;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                ToolVisibilityS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> {
                    activePositions = payload.blockPositions();
                    activeColour    = payload.particleColour();
                    activeWorldKey  = payload.worldKey();
                    tickCounter     = 0; // trigger immediate render on next tick
                })
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (activePositions.isEmpty()) return;
            tickCounter++;
            if (tickCounter < 10) return;
            tickCounter = 0;

            ClientWorld world = client.world;
            if (world == null) return;

            // Only render in the correct world
            String currentWorld = world.getRegistryKey().getValue().toString();
            if (!currentWorld.equals(activeWorldKey)) return;

            // Extract RGB from ARGB colour
            float r = ((activeColour >> 16) & 0xFF) / 255f;
            float g = ((activeColour >> 8)  & 0xFF) / 255f;
            float b = (activeColour         & 0xFF) / 255f;

            DustParticleEffect dust = new DustParticleEffect(new Vector3f(r, g, b), 1.0f);

            for (long posLong : activePositions) {
                BlockPos pos = BlockPos.fromLong(posLong);
                // Spawn a column of 4 particles above the block
                for (int i = 1; i <= 4; i++) {
                    double px = pos.getX() + 0.5 + (Math.random() - 0.5) * 0.3;
                    double py = pos.getY() + i;
                    double pz = pos.getZ() + 0.5 + (Math.random() - 0.5) * 0.3;
                    world.addParticle(dust, px, py, pz, 0, 0.05, 0);
                }
                // Pulse at block level too
                world.addParticle(dust,
                        pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5,
                        0, 0, 0);
            }
        });
    }

    /** Called on world unload / disconnect to clear state. */
    public static void clear() {
        activePositions = Collections.emptyList();
        activeWorldKey  = "";
    }
}