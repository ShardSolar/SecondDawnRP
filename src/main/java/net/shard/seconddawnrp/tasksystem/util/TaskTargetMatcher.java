package net.shard.seconddawnrp.tasksystem.util;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public final class TaskTargetMatcher {

    private TaskTargetMatcher() {}

    // ── Block matching ────────────────────────────────────────────────────────

    public static boolean blockMatches(Block block, String targetId) {
        if (block == null || targetId == null || targetId.isBlank()) return false;

        Identifier blockId = Registries.BLOCK.getId(block);
        if (blockId == null) return false;

        return normalizeId(targetId).equals(blockId.toString());
    }

    // ── Item matching ─────────────────────────────────────────────────────────

    /**
     * Returns true if the given ItemStack matches the targetId.
     * targetId format: "minecraft:iron_ingot" or just "iron_ingot"
     */
    public static boolean itemMatches(ItemStack stack, String targetId) {
        if (stack == null || stack.isEmpty() || targetId == null || targetId.isBlank()) return false;

        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        if (itemId == null) return false;

        return normalizeId(targetId).equals(itemId.toString());
    }

    // ── Location matching ─────────────────────────────────────────────────────

    /**
     * Returns true if the given position is within range of the target coordinate.
     * targetId format: "x,y,z" or "x,y,z,radius" (radius defaults to 3 if omitted)
     * Example: "100,64,-200" or "100,64,-200,5"
     */
    public static boolean locationMatches(BlockPos playerPos, String targetId) {
        if (playerPos == null || targetId == null || targetId.isBlank()) return false;

        String[] parts = targetId.trim().split(",");
        if (parts.length < 3) return false;

        try {
            int tx = Integer.parseInt(parts[0].trim());
            int ty = Integer.parseInt(parts[1].trim());
            int tz = Integer.parseInt(parts[2].trim());
            int radius = parts.length >= 4 ? Integer.parseInt(parts[3].trim()) : 3;

            int dx = playerPos.getX() - tx;
            int dy = playerPos.getY() - ty;
            int dz = playerPos.getZ() - tz;

            return (dx * dx + dy * dy + dz * dz) <= (radius * radius);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    private static String normalizeId(String raw) {
        String value = raw.trim().toLowerCase();
        if (!value.contains(":")) {
            value = "minecraft:" + value;
        }
        return value;
    }
}