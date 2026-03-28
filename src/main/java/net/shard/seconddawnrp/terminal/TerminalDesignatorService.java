package net.shard.seconddawnrp.terminal;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.degradation.network.OpenEngineeringPadS2CPacket;
import net.shard.seconddawnrp.tasksystem.pad.AdminTaskScreenHandler;
import net.shard.seconddawnrp.tasksystem.util.TaskPermissionUtil;

import java.util.List;

/**
 * Service layer for terminal designations.
 *
 * Responsibilities:
 *   1. Dispatch player right-click interactions to the correct existing screen.
 *      Each implemented type opens the screen exactly the same way the PADD
 *      item does — same handler, same permission check, same data.
 *   2. Render colored block-outline particles when the Terminal Designator Tool
 *      is held (server-side DustParticleEffect push, no client mod needed).
 */
public class TerminalDesignatorService {

    private static final float PARTICLE_SIZE      = 0.8f;
    private static final int   PARTICLES_PER_EDGE = 8;

    // ── Interact dispatch ─────────────────────────────────────────────────────

    /**
     * Called by TerminalDesignatorInteractListener when a non-sneaking player
     * right-clicks a designated block.
     *
     * @return true if consumed (prevents vanilla block behaviour).
     */
    public boolean handleInteract(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        String worldKey = world.getRegistryKey().getValue().toString();
        var optional = SecondDawnRP.TERMINAL_DESIGNATOR_REGISTRY.get(worldKey, pos);
        if (optional.isEmpty()) return false;

        TerminalDesignatorEntry entry = optional.get();
        TerminalDesignatorType type = entry.getType();

        if (!hasPermission(player, type)) {
            player.sendMessage(Text.literal("§c[" + type.getDisplayName() + "] Access denied."), false);
            return true;
        }

        if (!type.isImplemented()) {
            player.sendMessage(Text.literal(
                    "§7[" + type.getDisplayName() + "] This terminal type is not yet active."
            ), false);
            return true;
        }

        openScreen(player, type);
        return true;
    }

    /**
     * Opens the correct screen using the exact same call the PADD item uses.
     * OPS_TERMINAL: openHandledScreen with AdminTaskScreenHandler (not a packet).
     * ENGINEERING_CONSOLE: sends OpenEngineeringPadS2CPacket (that's how the pad item works).
     */
    private void openScreen(ServerPlayerEntity player, TerminalDesignatorType type) {
        switch (type) {

            case OPS_TERMINAL -> {
                // Identical to OperationsPadItem.use() — permission check then openHandledScreen
                if (!TaskPermissionUtil.canOpenOperationsPad(player)) {
                    player.sendMessage(
                            Text.literal("You do not have permission to use the Operations PAD."),
                            false);
                    return;
                }
                player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                        (syncId, inventory, p) -> new AdminTaskScreenHandler(syncId, inventory),
                        Text.literal("Operations PAD")
                ));
            }

            case ENGINEERING_CONSOLE -> {
                // EngineeringPadItem sends this packet to open the screen client-side
                ServerPlayNetworking.send(player,
                        OpenEngineeringPadS2CPacket.fromService(SecondDawnRP.DEGRADATION_SERVICE));
            }

            default -> player.sendMessage(
                    Text.literal("§7[Terminal] Screen routing not yet wired for: " + type.name()),
                    false);
        }
    }

    // ── Glow particles ────────────────────────────────────────────────────────

    /**
     * Called every 10 ticks per player from SecondDawnRP.java tick loop.
     * Only fires if the player is holding the Terminal Designator Tool.
     */
    public void tickGlowForPlayer(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;
        ItemStack held = player.getMainHandStack();
        if (!(held.getItem() instanceof TerminalDesignatorToolItem)) return;
        refreshGlowForPlayer(player, world);
    }

    /**
     * Spawns outline particles around all designated terminals within 32 blocks.
     * Also called immediately after designating a new terminal.
     */
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

    // ── Permission ────────────────────────────────────────────────────────────

    private boolean hasPermission(ServerPlayerEntity player, TerminalDesignatorType type) {
        try {
            return SecondDawnRP.PERMISSION_SERVICE.hasPermission(player, type.getPermissionNode());
        } catch (Exception e) {
            return player.hasPermissionLevel(2);
        }
    }
}