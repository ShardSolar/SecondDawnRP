package net.shard.seconddawnrp.gmevent.service;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.gmevent.network.ToolVisibilityS2CPacket;
import net.shard.seconddawnrp.registry.ModItems;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages tool visibility particle columns for GM tools.
 *
 * <p>When a GM equips a registration tool, this service queries the relevant
 * registry for all block positions within 48 blocks and sends them to the
 * client with a colour code. The client renders particle columns at each
 * position while the player holds the tool.
 *
 * <p>Called from {@code PlayerEquipmentChangeCallback} in the server initializer.
 *
 * <p>Colour registry (ARGB):
 * <ul>
 *   <li>Component Registration Tool — Amber   0xFFD7820A
 *   <li>Environmental Effect Tool   — Green   0xFF22AA28
 *   <li>Trigger Tool                — Yellow  0xFFCCAA18
 *   <li>Anomaly Marker Tool         — Purple  0xFFAA22CC
 *   <li>Warp Core Tool              — Cyan    0xFF22AACC
 * </ul>
 */
public class GmToolVisibilityService {

    private static final int RANGE = 48;

    private static final int COL_AMBER  = 0xFFD7820A;
    private static final int COL_GREEN  = 0xFF22AA28;
    private static final int COL_YELLOW = 0xFFCCAA18;
    private static final int COL_PURPLE = 0xFFAA22CC;
    private static final int COL_CYAN   = 0xFF22AACC;

    /**
     * Called when a player's main-hand item changes.
     * Sends the appropriate visibility packet or a clear packet.
     */
    public void onEquip(ServerPlayerEntity player, ItemStack newStack) {
        if (!isGM(player)) return;

        String worldKey = player.getWorld().getRegistryKey().getValue().toString();
        BlockPos playerPos = player.getBlockPos();

        if (newStack.isOf(ModItems.COMPONENT_REGISTRATION_TOOL)) {
            sendPositions(player, worldKey, playerPos,
                    getComponentPositions(worldKey, playerPos), COL_AMBER);

        } else if (newStack.isOf(ModItems.ENVIRONMENTAL_EFFECT_TOOL)) {
            sendPositions(player, worldKey, playerPos,
                    getEnvPositions(worldKey, playerPos), COL_GREEN);

        } else if (newStack.isOf(ModItems.TRIGGER_TOOL)) {
            sendPositions(player, worldKey, playerPos,
                    getTriggerPositions(worldKey, playerPos), COL_YELLOW);

        } else if (newStack.isOf(ModItems.ANOMALY_MARKER_TOOL)) {
            sendPositions(player, worldKey, playerPos,
                    getAnomalyPositions(worldKey, playerPos), COL_PURPLE);

        } else if (newStack.isOf(ModItems.WARP_CORE_TOOL)) {
            sendPositions(player, worldKey, playerPos,
                    getWarpCorePositions(worldKey, playerPos), COL_CYAN);

        } else {
            // Unequipped a tool or switched to something else — clear
            ServerPlayNetworking.send(player, ToolVisibilityS2CPacket.clear());
        }
    }

    // ── Position queries ──────────────────────────────────────────────────────

    private List<Long> getComponentPositions(String worldKey, BlockPos playerPos) {
        List<Long> positions = new ArrayList<>();
        SecondDawnRP.DEGRADATION_SERVICE.getAllComponents().stream()
                .filter(c -> c.getWorldKey().equals(worldKey))
                .filter(c -> BlockPos.fromLong(c.getBlockPosLong())
                        .isWithinDistance(playerPos, RANGE))
                .forEach(c -> positions.add(c.getBlockPosLong()));
        return positions;
    }

    private List<Long> getEnvPositions(String worldKey, BlockPos playerPos) {
        List<Long> positions = new ArrayList<>();
        SecondDawnRP.ENV_EFFECT_SERVICE.getAll().stream()
                .filter(e -> e.getWorldKey().equals(worldKey))
                .filter(e -> BlockPos.fromLong(e.getBlockPosLong())
                        .isWithinDistance(playerPos, RANGE))
                .forEach(e -> positions.add(e.getBlockPosLong()));
        return positions;
    }

    private List<Long> getTriggerPositions(String worldKey, BlockPos playerPos) {
        List<Long> positions = new ArrayList<>();
        SecondDawnRP.TRIGGER_SERVICE.getAll().stream()
                .filter(e -> e.getWorldKey().equals(worldKey))
                .filter(e -> BlockPos.fromLong(e.getBlockPosLong())
                        .isWithinDistance(playerPos, RANGE))
                .forEach(e -> positions.add(e.getBlockPosLong()));
        return positions;
    }

    private List<Long> getAnomalyPositions(String worldKey, BlockPos playerPos) {
        List<Long> positions = new ArrayList<>();
        SecondDawnRP.ANOMALY_SERVICE.getAll().stream()
                .filter(e -> e.getWorldKey().equals(worldKey))
                .filter(e -> BlockPos.fromLong(e.getBlockPosLong())
                        .isWithinDistance(playerPos, RANGE))
                .forEach(e -> positions.add(e.getBlockPosLong()));
        return positions;
    }

    private List<Long> getWarpCorePositions(String worldKey, BlockPos playerPos) {
        List<Long> positions = new ArrayList<>();
        SecondDawnRP.WARP_CORE_SERVICE.getEntry()
                .filter(e -> e.getWorldKey().equals(worldKey))
                .filter(e -> BlockPos.fromLong(e.getBlockPosLong())
                        .isWithinDistance(playerPos, RANGE))
                .ifPresent(e -> positions.add(e.getBlockPosLong()));
        return positions;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void sendPositions(ServerPlayerEntity player, String worldKey,
                                      BlockPos playerPos, List<Long> positions, int colour) {
        ServerPlayNetworking.send(player,
                new ToolVisibilityS2CPacket(positions, colour, worldKey));
    }

    private static boolean isGM(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2)
                || SecondDawnRP.PERMISSION_SERVICE.hasPermission(player, "st.gm.use");
    }
}