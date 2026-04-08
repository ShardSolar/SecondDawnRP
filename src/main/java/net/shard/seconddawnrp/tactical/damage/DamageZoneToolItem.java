package net.shard.seconddawnrp.tactical.damage;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tactical.data.DamageZone;
import net.shard.seconddawnrp.tactical.network.LocateZoneBlockS2CPacket;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admin tool for mapping blocks to damage zone IDs.
 *
 * Right-click block (normal):  register block → fires locator particles
 * Right-click block (sneak):   remove block → fires locator particles
 * Right-click air:             cycle mode MODEL ↔ REAL
 *
 * Set context with: /admin hardpoint zone set <shipId> <zoneId> <MODEL|REAL>
 *
 * useOnBlock() stamps LAST_BLOCK_USE_MS so use() can suppress itself when it
 * fires on the same tick as a block interaction — Fabric fires both events on
 * right-click regardless of ActionResult from useOnBlock on some block types.
 */
public class DamageZoneToolItem extends Item {

    /**
     * Per-player timestamp of the last useOnBlock() call (server ms).
     * use() ignores the cycle-mode logic if the delta is under 50ms.
     */
    private static final Map<UUID, Long> LAST_BLOCK_USE_MS = new ConcurrentHashMap<>();
    private static final long SUPPRESS_WINDOW_MS = 50L;

    public DamageZoneToolItem(Settings settings) {
        super(settings);
    }

    // ── Right-click block ─────────────────────────────────────────────────────

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null || player.getWorld().isClient()) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

        // Stamp immediately so use() suppresses itself on this same tick
        LAST_BLOCK_USE_MS.put(sp.getUuid(), System.currentTimeMillis());

        if (!sp.hasPermissionLevel(4)) {
            sp.sendMessage(Text.literal("[DamageZone] Admin access required.")
                    .formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }

        if (SecondDawnRP.TACTICAL_SERVICE == null) {
            sp.sendMessage(Text.literal("[DamageZone] Tactical service not available.")
                    .formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }

        ItemStack stack = context.getStack();
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            sp.sendMessage(Text.literal(
                            "[DamageZone] Set context first: /admin hardpoint zone set <shipId> <zoneId> <MODEL|REAL>")
                    .formatted(Formatting.YELLOW), false);
            return ActionResult.SUCCESS;
        }

        NbtCompound compound = customData.copyNbt();
        String shipId = compound.getString("shipId");
        String zoneId = compound.getString("zoneId");
        String mode   = compound.getString("mode");

        if (shipId.isEmpty() || zoneId.isEmpty()) {
            sp.sendMessage(Text.literal("[DamageZone] No shipId/zoneId set in context.")
                    .formatted(Formatting.YELLOW), false);
            return ActionResult.SUCCESS;
        }

        BlockPos pos = context.getBlockPos();
        var hullDamageService = SecondDawnRP.TACTICAL_SERVICE.getHullDamageService();

        if (sp.isSneaking()) {
            // ── Sneak + right-click: remove block ─────────────────────────────
            Optional<DamageZone> zoneOpt = hullDamageService.getZone(shipId, zoneId);
            if (zoneOpt.isEmpty()) {
                sp.sendMessage(Text.literal("[DamageZone] Zone '" + zoneId
                                + "' not found on ship '" + shipId + "'.")
                        .formatted(Formatting.RED), false);
                return ActionResult.SUCCESS;
            }

            DamageZone zone = zoneOpt.get();
            boolean removedModel = zone.removeModelBlock(pos);
            boolean removedReal  = zone.removeRealShipBlock(pos);

            if (removedModel) {
                hullDamageService.persistRemoveModelBlock(shipId, zoneId, pos.asLong());
                sp.sendMessage(Text.literal("[DamageZone] Removed model block "
                                + formatPos(pos) + " from " + zoneId)
                        .formatted(Formatting.YELLOW), false);
                sendLocatePacket(sp, zoneId, "MODEL", pos);
            } else if (removedReal) {
                hullDamageService.persistRemoveRealBlock(shipId, zoneId, pos.asLong());
                sp.sendMessage(Text.literal("[DamageZone] Removed real block "
                                + formatPos(pos) + " from " + zoneId)
                        .formatted(Formatting.YELLOW), false);
                sendLocatePacket(sp, zoneId, "REAL", pos);
            } else {
                sp.sendMessage(Text.literal("[DamageZone] Block " + formatPos(pos)
                                + " is not registered to " + zoneId + ".")
                        .formatted(Formatting.GRAY), false);
            }
        } else {
            // ── Normal right-click: register block ────────────────────────────
            if ("MODEL".equals(mode)) {
                hullDamageService.registerModelBlock(shipId, zoneId, pos);
                sp.sendMessage(Text.literal("[DamageZone] Model block "
                                + formatPos(pos) + " → " + zoneId)
                        .formatted(Formatting.GREEN), false);
                sendLocatePacket(sp, zoneId, "MODEL", pos);
            } else {
                hullDamageService.registerRealBlock(shipId, zoneId, pos);
                sp.sendMessage(Text.literal("[DamageZone] Real block "
                                + formatPos(pos) + " → " + zoneId)
                        .formatted(Formatting.AQUA), false);
                sendLocatePacket(sp, zoneId, "REAL", pos);
            }
        }

        return ActionResult.SUCCESS;
    }

    // ── Right-click air: cycle mode ───────────────────────────────────────────

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return TypedActionResult.pass(user.getStackInHand(hand));
        if (!(user instanceof ServerPlayerEntity sp))
            return TypedActionResult.pass(user.getStackInHand(hand));

        // Suppress if useOnBlock() fired on this same interaction
        Long lastBlock = LAST_BLOCK_USE_MS.get(sp.getUuid());
        if (lastBlock != null
                && System.currentTimeMillis() - lastBlock < SUPPRESS_WINDOW_MS) {
            return TypedActionResult.success(user.getStackInHand(hand));
        }

        ItemStack stack = user.getStackInHand(hand);
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);

        if (customData == null) {
            sp.sendMessage(Text.literal(
                            "[DamageZone] No context set. Use /admin hardpoint zone set first.")
                    .formatted(Formatting.YELLOW), false);
            return TypedActionResult.success(stack);
        }

        // Cycle mode MODEL ↔ REAL
        NbtCompound compound = customData.copyNbt();
        String newMode = "MODEL".equals(compound.getString("mode")) ? "REAL" : "MODEL";
        compound.putString("mode", newMode);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(compound));
        sp.sendMessage(Text.literal("[DamageZone] Mode: " + newMode)
                .formatted(Formatting.AQUA), true);
        return TypedActionResult.success(stack);
    }

    // ── Context stamping ──────────────────────────────────────────────────────

    public static void setContext(ServerPlayerEntity player, String shipId,
                                  String zoneId, String mode) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof DamageZoneToolItem)) {
            player.sendMessage(Text.literal("[DamageZone] Hold the Damage Zone Tool.")
                    .formatted(Formatting.RED), false);
            return;
        }
        NbtCompound compound = new NbtCompound();
        compound.putString("shipId", shipId);
        compound.putString("zoneId", zoneId);
        compound.putString("mode",   mode.toUpperCase());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(compound));
        player.sendMessage(Text.literal("[DamageZone] Context: "
                        + shipId + " / " + zoneId + " / " + mode.toUpperCase())
                .formatted(Formatting.GREEN), false);
    }

    // ── Packet helper ─────────────────────────────────────────────────────────

    private static void sendLocatePacket(ServerPlayerEntity player, String zoneId,
                                         String mode, BlockPos pos) {
        ServerPlayNetworking.send(player, new LocateZoneBlockS2CPacket(
                zoneId, mode,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5));
    }

    private String formatPos(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }
}