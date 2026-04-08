package net.shard.seconddawnrp.terminal;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.network.OpenEngineeringPadS2CPacket;
import net.shard.seconddawnrp.roster.network.RosterNetworking;
import net.shard.seconddawnrp.tasksystem.pad.AdminTaskScreenHandler;
import net.shard.seconddawnrp.tasksystem.util.TaskPermissionUtil;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for terminal designations.
 *
 * Responsibilities:
 *   1. Dispatch player right-click interactions to the correct existing screen.
 *   2. Render colored block-outline particles when the Terminal Designator Tool is held.
 *   3. Show an action bar prompt when a player is looking at a designated terminal.
 */
public class TerminalDesignatorService {

    private static final float PARTICLE_SIZE = 0.8f;
    private static final int PARTICLES_PER_EDGE = 8;
    private static final double REACH = 5.0;

    // ── Interact dispatch ─────────────────────────────────────────────────────

    public boolean handleInteract(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        String worldKey = world.getRegistryKey().getValue().toString();
        var optional = SecondDawnRP.TERMINAL_DESIGNATOR_REGISTRY.get(worldKey, pos);
        if (optional.isEmpty()) return false;

        TerminalDesignatorEntry entry = optional.get();
        TerminalDesignatorType type = entry.getType();

        if (!type.isImplemented()) {
            player.sendMessage(Text.literal(
                    "§7[" + type.getDisplayName() + "] This terminal type is not yet active."
            ), false);
            return true;
        }

        openScreen(player, type);
        return true;
    }

    private void openScreen(ServerPlayerEntity player, TerminalDesignatorType type) {
        switch (type) {
            case OPS_TERMINAL -> {
                if (!TaskPermissionUtil.canOpenOperationsPad(player)) {
                    player.sendMessage(Text.literal(
                            "§cYou do not have permission to use the Operations PAD."
                    ), false);
                    return;
                }
                player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                        (syncId, inventory, p) -> new AdminTaskScreenHandler(syncId, inventory),
                        Text.literal("Operations PAD")
                ));
            }

            case ENGINEERING_CONSOLE -> {
                ServerPlayNetworking.send(
                        player,
                        OpenEngineeringPadS2CPacket.fromService(SecondDawnRP.DEGRADATION_SERVICE)
                );
            }

            case ROSTER_CONSOLE -> {
                RosterNetworking.openRoster(player);
            }

            case MEDICAL_CONSOLE -> {
                SecondDawnRP.MEDICAL_TERMINAL_SERVICE.handleTerminalInteract(player);
            }

            // ── Phase 12 — Tactical stations ──────────────────────────────────

            case TACTICAL_CONSOLE -> {
                // Full console — GMs get the ship control screen, crew get full read view
                openTacticalStation(player, "ALL");
            }

            case TACTICAL_HELM -> {
                openTacticalStation(player, "HELM");
            }

            case TACTICAL_WEAPONS -> {
                openTacticalStation(player, "WEAPONS");
            }

            case TACTICAL_SHIELDS -> {
                openTacticalStation(player, "SHIELDS");
            }

            case SHIP_ORIGIN -> {
                // No screen — just acknowledge. The block's position is
                // already registered in the TerminalDesignatorRegistry.
                player.sendMessage(Text.literal(
                                "§b[Ship Origin] §fMarker confirmed at "
                                        + "X:" + player.getBlockX()
                                        + " Z:" + player.getBlockZ()
                                        + " §7— Tactical map will center here in standby mode."),
                        false);
            }

            default -> player.sendMessage(
                    Text.literal("§7[Terminal] Screen routing not yet wired for: " + type.name()),
                    false
            );
        }
    }

    /**
     * Opens the appropriate Tactical screen based on station type.
     * GMs (permission level 2+) get the full GM ship console.
     * Crew get the player TacticalScreen filtered to their station.
     */
    private void openTacticalStation(ServerPlayerEntity player, String stationFilter) {
        if (SecondDawnRP.TACTICAL_SERVICE == null) {
            player.sendMessage(Text.literal("§c[Tactical] System offline."), false);
            return;
        }

        var encounters = SecondDawnRP.TACTICAL_SERVICE.getEncounterService().getAllEncounters();

        // Find active encounter — or open standby screen
        var encounter = encounters.stream()
                .filter(e -> e.getStatus() ==
                        net.shard.seconddawnrp.tactical.data.EncounterState.Status.ACTIVE
                        || e.getStatus() ==
                        net.shard.seconddawnrp.tactical.data.EncounterState.Status.PAUSED)
                .findFirst()
                .orElse(encounters.isEmpty() ? null : encounters.iterator().next());

        boolean gmMode = player.hasPermissionLevel(2) && "ALL".equals(stationFilter);
        net.shard.seconddawnrp.tactical.network.TacticalNetworking
                .sendOpenPacket(player, encounter, stationFilter, gmMode);
    }

    // ── Ship origin lookup ────────────────────────────────────────────────────

    /**
     * Returns the nearest SHIP_ORIGIN designator to the given player.
     * Used by TacticalScreen to center the standby map on the correct ship.
     * Returns null if none registered within 512 blocks.
     */
    public net.minecraft.util.math.BlockPos getShipOriginNear(
            ServerPlayerEntity player, ServerWorld world) {
        String worldKey = world.getRegistryKey().getValue().toString();
        net.minecraft.util.math.BlockPos center = player.getBlockPos();

        return SecondDawnRP.TERMINAL_DESIGNATOR_REGISTRY
                .getNearby(worldKey, center, 512)
                .stream()
                .filter(e -> e.getType() == TerminalDesignatorType.SHIP_ORIGIN)
                .min(java.util.Comparator.comparingDouble(
                        e -> e.getPos().getSquaredDistance(center)))
                .map(TerminalDesignatorEntry::getPos)
                .orElse(null);
    }

    // ── Action bar prompt ─────────────────────────────────────────────────────

    public void tickActionBarPrompt(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        var eyePos = player.getEyePos();
        var lookVec = player.getRotationVec(1.0f);
        var endPos = eyePos.add(lookVec.multiply(REACH));

        HitResult hit = world.raycast(new RaycastContext(
                eyePos, endPos,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        if (hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        String worldKey = world.getRegistryKey().getValue().toString();

        Optional<TerminalDesignatorEntry> optional =
                SecondDawnRP.TERMINAL_DESIGNATOR_REGISTRY.get(worldKey, pos);
        if (optional.isEmpty()) return;

        TerminalDesignatorType type = optional.get().getType();

        Text prompt = Text.literal("")
                .append(Text.literal("[ " + type.getDisplayName() + " ]")
                        .withColor(type.isImplemented() ? type.getGlowColor() : 0xAAAAAA))
                .append(Text.literal(type.isImplemented()
                        ? "  §7Right-click to open"
                        : "  §8Not yet active"));

        player.sendMessage(prompt, true);
    }

    // ── Glow particles ────────────────────────────────────────────────────────

    public void tickGlowForPlayer(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;
        ItemStack held = player.getMainHandStack();
        if (!(held.getItem() instanceof TerminalDesignatorToolItem)) return;
        refreshGlowForPlayer(player, world);
    }

    public void refreshGlowForPlayer(ServerPlayerEntity player, ServerWorld world) {
        String worldKey = world.getRegistryKey().getValue().toString();
        BlockPos center = player.getBlockPos();
        List<TerminalDesignatorEntry> nearby =
                SecondDawnRP.TERMINAL_DESIGNATOR_REGISTRY.getNearby(worldKey, center, 32);

        for (TerminalDesignatorEntry entry : nearby) {
            spawnBlockOutline(world, player, entry.getPos(), entry.getType().getGlowColor());
        }
    }

    // ── Particle helpers ──────────────────────────────────────────────────────

    private void spawnBlockOutline(ServerWorld world, ServerPlayerEntity player,
                                   BlockPos pos, int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8)  & 0xFF) / 255f;
        float b = (rgb         & 0xFF) / 255f;

        double x0 = pos.getX() - 0.02, y0 = pos.getY() - 0.02, z0 = pos.getZ() - 0.02;
        double x1 = pos.getX() + 1.02, y1 = pos.getY() + 1.02, z1 = pos.getZ() + 1.02;

        var fx = new DustParticleEffect(new org.joml.Vector3f(r, g, b), PARTICLE_SIZE);

        spawnEdge(world, player, fx, x0, y0, z0, x1, y0, z0);
        spawnEdge(world, player, fx, x1, y0, z0, x1, y0, z1);
        spawnEdge(world, player, fx, x1, y0, z1, x0, y0, z1);
        spawnEdge(world, player, fx, x0, y0, z1, x0, y0, z0);

        spawnEdge(world, player, fx, x0, y1, z0, x1, y1, z0);
        spawnEdge(world, player, fx, x1, y1, z0, x1, y1, z1);
        spawnEdge(world, player, fx, x1, y1, z1, x0, y1, z1);
        spawnEdge(world, player, fx, x0, y1, z1, x0, y1, z0);

        spawnEdge(world, player, fx, x0, y0, z0, x0, y1, z0);
        spawnEdge(world, player, fx, x1, y0, z0, x1, y1, z0);
        spawnEdge(world, player, fx, x1, y0, z1, x1, y1, z1);
        spawnEdge(world, player, fx, x0, y0, z1, x0, y1, z1);
    }

    private void spawnEdge(ServerWorld world, ServerPlayerEntity player,
                           DustParticleEffect fx,
                           double x0, double y0, double z0,
                           double x1, double y1, double z1) {
        for (int i = 0; i <= PARTICLES_PER_EDGE; i++) {
            double t = (double) i / PARTICLES_PER_EDGE;
            world.spawnParticles(player, fx, true,
                    x0 + (x1 - x0) * t,
                    y0 + (y1 - y0) * t,
                    z0 + (z1 - z0) * t,
                    1, 0, 0, 0, 0);
        }
    }
}