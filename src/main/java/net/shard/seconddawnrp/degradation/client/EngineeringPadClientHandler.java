package net.shard.seconddawnrp.degradation.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;
import net.shard.seconddawnrp.degradation.network.LocateComponentS2CPacket;
import net.shard.seconddawnrp.degradation.network.OpenEngineeringPadS2CPacket;
import net.shard.seconddawnrp.degradation.screen.EngineeringPadScreen;

public final class EngineeringPadClientHandler {

    private EngineeringPadClientHandler() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                LocateComponentS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> spawnLocatorParticles(payload))
        );

        ClientPlayNetworking.registerGlobalReceiver(
                OpenEngineeringPadS2CPacket.ID,
                (payload, context) -> context.client().execute(() -> openEngineeringPad(payload))
        );
    }

    private static void openEngineeringPad(OpenEngineeringPadS2CPacket payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        client.setScreen(new EngineeringPadScreen(
                payload.components(),
                payload.warpCores(),
                payload.focusedCoreId(),
                payload.warpCoreState(),
                payload.warpCoreFuel(),
                payload.warpCoreMaxFuel(),
                payload.warpCorePower()
        ));
    }

    private static void spawnLocatorParticles(LocateComponentS2CPacket payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        double x = payload.x();
        double y = payload.y();
        double z = payload.z();
        ComponentStatus status = payload.status();

        int count = switch (status) {
            case NOMINAL -> 5;
            case DEGRADED -> 7;
            case CRITICAL -> 10;
            case OFFLINE -> 12;
        };

        double spread = switch (status) {
            case NOMINAL -> 0.28;
            case DEGRADED -> 0.34;
            case CRITICAL -> 0.42;
            case OFFLINE -> 0.50;
        };

        double verticalRange = switch (status) {
            case NOMINAL -> 0.18;
            case DEGRADED -> 0.26;
            case CRITICAL -> 0.36;
            case OFFLINE -> 0.44;
        };

        double riseMin = switch (status) {
            case NOMINAL -> 0.010;
            case DEGRADED -> 0.014;
            case CRITICAL -> 0.020;
            case OFFLINE -> 0.024;
        };

        double riseMax = switch (status) {
            case NOMINAL -> 0.018;
            case DEGRADED -> 0.024;
            case CRITICAL -> 0.032;
            case OFFLINE -> 0.040;
        };

        ParticleEffect primaryParticle = switch (status) {
            case NOMINAL -> ParticleTypes.END_ROD;
            case DEGRADED -> ParticleTypes.END_ROD;
            case CRITICAL -> ParticleTypes.ELECTRIC_SPARK;
            case OFFLINE -> ParticleTypes.LARGE_SMOKE;
        };

        for (int i = 0; i < count; i++) {
            double ox = (client.world.random.nextDouble() - 0.5) * spread;
            double oz = (client.world.random.nextDouble() - 0.5) * spread;
            double oy = client.world.random.nextDouble() * verticalRange;

            double vx = (client.world.random.nextDouble() - 0.5) * 0.018;
            double vz = (client.world.random.nextDouble() - 0.5) * 0.018;
            double vy = riseMin + client.world.random.nextDouble() * (riseMax - riseMin);

            client.world.addParticle(
                    primaryParticle,
                    x + ox,
                    y + 0.10 + oy,
                    z + oz,
                    vx, vy, vz
            );

            if (status == ComponentStatus.CRITICAL && client.world.random.nextFloat() < 0.45f) {
                client.world.addParticle(
                        ParticleTypes.END_ROD,
                        x + ox,
                        y + 0.10 + oy,
                        z + oz,
                        vx * 0.5, vy * 0.8, vz * 0.5
                );
            }

            if (status == ComponentStatus.OFFLINE && client.world.random.nextFloat() < 0.35f) {
                client.world.addParticle(
                        ParticleTypes.SMOKE,
                        x + ox,
                        y + 0.10 + oy,
                        z + oz,
                        vx * 0.35, vy * 0.6, vz * 0.35
                );
            }
        }
    }
}