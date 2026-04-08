package net.shard.seconddawnrp.tactical.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.shard.seconddawnrp.tactical.network.LocateZoneBlockS2CPacket;

/**
 * Client-side handler for zone block locator particles.
 *
 * MODEL blocks — END_ROD (blue/white) + ENCHANT (blue sparkles)
 *   Visual metaphor: schematic/blueprint — these are the indicator blocks
 *
 * REAL blocks — ELECTRIC_SPARK (orange) + FLAME (warm fire)
 *   Visual metaphor: live ship systems — these are what actually gets destroyed
 */
public final class DamageZoneClientHandler {

    private DamageZoneClientHandler() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                LocateZoneBlockS2CPacket.ID,
                (payload, context) -> context.client().execute(
                        () -> spawnLocatorParticles(payload))
        );
    }

    private static void spawnLocatorParticles(LocateZoneBlockS2CPacket payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        double x = payload.x();
        double y = payload.y();
        double z = payload.z();
        boolean isModel = "MODEL".equals(payload.mode());

        // MODEL: blue/white schematic feel
        // REAL:  orange/yellow live-system feel
        ParticleEffect mainParticle   = isModel ? ParticleTypes.END_ROD       : ParticleTypes.ELECTRIC_SPARK;
        ParticleEffect accentParticle = isModel ? ParticleTypes.ENCHANT        : ParticleTypes.FLAME;

        double faceOffset     = 0.34;
        int    faceBurstCount = 3;
        double outwardSpeed   = 0.022;
        double jitter         = 0.014;
        double riseMin        = 0.010;
        double riseMax        = 0.024;

        // Six face pluems
        double[][] faces = {
                { 0, faceOffset, 0,  0, 1, 0 },   // top
                { 0,-faceOffset, 0,  0,-1, 0 },   // bottom
                { 0, 0,-faceOffset,  0, 0,-1 },   // north
                { 0, 0, faceOffset,  0, 0, 1 },   // south
                {-faceOffset, 0, 0, -1, 0, 0 },   // west
                { faceOffset, 0, 0,  1, 0, 0 }    // east
        };

        for (double[] f : faces) {
            spawnFacePlume(client, x, y, z,
                    f[0], f[1], f[2],
                    f[3], f[4], f[5],
                    faceBurstCount, mainParticle, accentParticle,
                    outwardSpeed, jitter, riseMin, riseMax);
        }

        // Center shimmer
        for (int i = 0; i < 3; i++) {
            double ox = (client.world.random.nextDouble() - 0.5) * 0.18;
            double oy = (client.world.random.nextDouble() - 0.5) * 0.18;
            double oz = (client.world.random.nextDouble() - 0.5) * 0.18;
            double vx = (client.world.random.nextDouble() - 0.5) * 0.010;
            double vy = riseMin + client.world.random.nextDouble() * (riseMax - riseMin);
            double vz = (client.world.random.nextDouble() - 0.5) * 0.010;
            client.world.addParticle(accentParticle, x + ox, y + oy, z + oz, vx, vy, vz);
        }
    }

    private static void spawnFacePlume(MinecraftClient client,
                                       double x, double y, double z,
                                       double offsetX, double offsetY, double offsetZ,
                                       double normalX, double normalY, double normalZ,
                                       int count,
                                       ParticleEffect mainParticle,
                                       ParticleEffect accentParticle,
                                       double outwardSpeed, double jitter,
                                       double riseMin, double riseMax) {
        for (int i = 0; i < count; i++) {
            double px = x + offsetX + (client.world.random.nextDouble() - 0.5) * 0.08;
            double py = y + offsetY + (client.world.random.nextDouble() - 0.5) * 0.08;
            double pz = z + offsetZ + (client.world.random.nextDouble() - 0.5) * 0.08;

            double vx = normalX * outwardSpeed + (client.world.random.nextDouble() - 0.5) * jitter;
            double vy = normalY * outwardSpeed + (client.world.random.nextDouble() - 0.5) * jitter;
            double vz = normalZ * outwardSpeed + (client.world.random.nextDouble() - 0.5) * jitter;

            vy += riseMin + client.world.random.nextDouble() * (riseMax - riseMin);

            client.world.addParticle(mainParticle, px, py, pz, vx, vy, vz);

            if (client.world.random.nextFloat() < 0.30f) {
                client.world.addParticle(accentParticle, px, py, pz,
                        vx * 0.65, vy * 0.75, vz * 0.65);
            }
        }
    }
}