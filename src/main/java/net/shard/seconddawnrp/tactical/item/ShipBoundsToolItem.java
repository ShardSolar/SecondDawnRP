package net.shard.seconddawnrp.tactical.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tactical.data.ShipRegistryEntry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admin tool for registering a ship's real-build bounding box.
 *
 * Interaction model:
 *   Right-click block (no corner set): sets Corner 1, shows action bar prompt
 *   Right-click block (corner 1 set):  sets Corner 2, finalises bounds, draws particle box
 *   Right-click air:                   shows current context / corner status
 *   Sneak + right-click block/air:     clears stored corners (resets without changing ship)
 *
 * Ship ID set via command:
 *   /admin ship bounds settarget <shipId>
 *   (same pattern as DamageZoneToolItem.setContext)
 *
 * Once both corners are clicked the bounds are written to the ShipRegistryEntry
 * and persisted immediately. A particle box is drawn at the confirmed bounds so
 * the admin can verify coverage before walking away.
 */
public class ShipBoundsToolItem extends Item {

    /** Suppress use() when useOnBlock() fired on same tick. */
    private static final Map<UUID, Long> LAST_BLOCK_USE_MS = new ConcurrentHashMap<>();
    private static final long SUPPRESS_WINDOW_MS = 50L;

    // Particle box edge density — one particle every N blocks along each edge
    private static final double PARTICLE_STEP = 0.5;

    public ShipBoundsToolItem(Settings settings) {
        super(settings);
    }

    public static void drawParticleBoxPublic(ServerWorld sw, Box box, ServerPlayerEntity p) {
    }

    // ── Right-click block ─────────────────────────────────────────────────────

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null || player.getWorld().isClient()) return ActionResult.PASS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;

        LAST_BLOCK_USE_MS.put(sp.getUuid(), System.currentTimeMillis());

        if (!sp.hasPermissionLevel(4)) {
            sp.sendMessage(Text.literal("[ShipBounds] Admin access required.")
                    .formatted(Formatting.RED), false);
            return ActionResult.SUCCESS;
        }

        ItemStack stack = context.getStack();
        NbtCompound nbt = getNbt(stack);

        // Sneak + right-click: clear corners only
        if (sp.isSneaking()) {
            nbt.remove("c1x"); nbt.remove("c1y"); nbt.remove("c1z");
            nbt.remove("c2x"); nbt.remove("c2y"); nbt.remove("c2z");
            writeNbt(stack, nbt);
            sp.sendMessage(Text.literal("[ShipBounds] Corners cleared. Ship target preserved.")
                    .formatted(Formatting.YELLOW), false);
            return ActionResult.SUCCESS;
        }

        String shipId = nbt.getString("shipId");
        if (shipId.isEmpty()) {
            sp.sendMessage(Text.literal(
                            "[ShipBounds] No ship targeted. Run: /admin ship bounds settarget <shipId>")
                    .formatted(Formatting.YELLOW), false);
            return ActionResult.SUCCESS;
        }

        BlockPos clicked = context.getBlockPos();

        boolean hasCorner1 = nbt.contains("c1x");

        if (!hasCorner1) {
            // ── Set Corner 1 ──────────────────────────────────────────────────
            nbt.putInt("c1x", clicked.getX());
            nbt.putInt("c1y", clicked.getY());
            nbt.putInt("c1z", clicked.getZ());
            writeNbt(stack, nbt);

            sp.sendMessage(Text.literal(
                            "[ShipBounds] Corner 1 set: " + formatPos(clicked)
                                    + "\nNow right-click the opposite corner of the ship build.")
                    .formatted(Formatting.GREEN), false);

            // Small marker particle at corner 1
            spawnCornerMarker((ServerWorld) sp.getWorld(), clicked, true);

        } else {
            // ── Set Corner 2 → finalise ───────────────────────────────────────
            BlockPos c1 = new BlockPos(nbt.getInt("c1x"), nbt.getInt("c1y"), nbt.getInt("c1z"));
            BlockPos c2 = clicked;

            // Validate: same world only (can't cross dimensions)
            if (SecondDawnRP.TACTICAL_SERVICE == null) {
                sp.sendMessage(Text.literal("[ShipBounds] Tactical service not available.")
                        .formatted(Formatting.RED), false);
                return ActionResult.SUCCESS;
            }

            // Route through EncounterService — that's where the live registry lives
            var entryOpt = SecondDawnRP.ENCOUNTER_SERVICE.getShipEntry(shipId);
            if (entryOpt.isEmpty()) {
                sp.sendMessage(Text.literal("[ShipBounds] Ship '" + shipId + "' not found in registry.")
                        .formatted(Formatting.RED), false);
                return ActionResult.SUCCESS;
            }

            ShipRegistryEntry entry = entryOpt.get();
            entry.setRealBounds(c1, c2);
            SecondDawnRP.ENCOUNTER_SERVICE.saveShipRegistryEntry(entry);

            // Clear corners from NBT — ready for next use
            nbt.remove("c1x"); nbt.remove("c1y"); nbt.remove("c1z");
            nbt.remove("c2x"); nbt.remove("c2y"); nbt.remove("c2z");
            writeNbt(stack, nbt);

            // Compute dimensions for feedback
            Box box = entry.getRealBoundsBox();
            int dx = Math.abs(c2.getX() - c1.getX()) + 1;
            int dy = Math.abs(c2.getY() - c1.getY()) + 1;
            int dz = Math.abs(c2.getZ() - c1.getZ()) + 1;

            sp.sendMessage(Text.literal(
                            "[ShipBounds] ✔ Bounds set for '" + shipId + "' (" + entry.getRegistryName() + "):"
                                    + "\n  Corner 1: " + formatPos(c1)
                                    + "\n  Corner 2: " + formatPos(c2)
                                    + "\n  Volume: " + dx + "×" + dy + "×" + dz + " blocks"
                                    + "\n  Engineering Pad and damage scoping now active.")
                    .formatted(Formatting.GREEN), false);

            // Draw particle box so admin can verify visually
            if (box != null && sp.getWorld() instanceof ServerWorld sw) {
                drawParticleBox(sw, box, sp);
            }

            // Also sync to TacticalService's in-memory copy if it has one
            // (TacticalService delegates to EncounterService now, but
            //  getShipAtPosition reads from its own copy — update it too)
            if (SecondDawnRP.TACTICAL_SERVICE != null) {
                SecondDawnRP.TACTICAL_SERVICE.syncShipRegistryEntry(entry);
            }
        }

        return ActionResult.SUCCESS;
    }

    // ── Right-click air: show status ──────────────────────────────────────────

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) return TypedActionResult.pass(user.getStackInHand(hand));
        if (!(user instanceof ServerPlayerEntity sp))
            return TypedActionResult.pass(user.getStackInHand(hand));

        // Suppress if useOnBlock fired on same tick
        Long lastBlock = LAST_BLOCK_USE_MS.get(sp.getUuid());
        if (lastBlock != null && System.currentTimeMillis() - lastBlock < SUPPRESS_WINDOW_MS)
            return TypedActionResult.success(user.getStackInHand(hand));

        ItemStack stack = user.getStackInHand(hand);
        NbtCompound nbt = getNbt(stack);
        String shipId = nbt.getString("shipId");

        if (shipId.isEmpty()) {
            sp.sendMessage(Text.literal(
                            "[ShipBounds] No ship targeted.\n"
                                    + "Run: /admin ship bounds settarget <shipId>")
                    .formatted(Formatting.YELLOW), false);
            return TypedActionResult.success(stack);
        }

        StringBuilder sb = new StringBuilder("[ShipBounds] Target: §b").append(shipId);

        if (nbt.contains("c1x")) {
            sb.append("\n§7Corner 1: §f")
                    .append(nbt.getInt("c1x")).append(", ")
                    .append(nbt.getInt("c1y")).append(", ")
                    .append(nbt.getInt("c1z"))
                    .append("\n§7Corner 2: §8(not set — right-click to place)");
        } else {
            sb.append("\n§7No corners set — right-click a block to place Corner 1.");
        }

        // Show existing bounds if configured
        var entryOpt = SecondDawnRP.ENCOUNTER_SERVICE != null
                ? SecondDawnRP.ENCOUNTER_SERVICE.getShipEntry(shipId)
                : java.util.Optional.<ShipRegistryEntry>empty();

        entryOpt.ifPresent(entry -> {
            if (entry.hasBounds()) {
                BlockPos min = entry.getRealBoundsMin();
                BlockPos max = entry.getRealBoundsMax();
                sb.append("\n§7Current bounds: §a")
                        .append(formatPos(min)).append(" §7→ §a").append(formatPos(max));

                // Draw existing particle box so admin can see current coverage
                Box box = entry.getRealBoundsBox();
                if (box != null && sp.getWorld() instanceof ServerWorld sw) {
                    drawParticleBox(sw, box, sp);
                }
            } else {
                sb.append("\n§7Current bounds: §8none");
            }
        });

        sp.sendMessage(Text.literal(sb.toString()), false);
        return TypedActionResult.success(stack);
    }

    // ── Context stamping (called from command) ────────────────────────────────

    /**
     * Set the target ship on the tool item in the player's main hand.
     * Clears any pending corners so the admin starts fresh on the new ship.
     * Called by: /admin ship bounds settarget <shipId>
     */
    public static void setTarget(ServerPlayerEntity player, String shipId) {
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof ShipBoundsToolItem)) {
            player.sendMessage(Text.literal("[ShipBounds] Hold the Ship Bounds Tool.")
                    .formatted(Formatting.RED), false);
            return;
        }
        NbtCompound nbt = new NbtCompound();
        nbt.putString("shipId", shipId);
        // Intentionally do NOT carry over old corners
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        player.sendMessage(Text.literal(
                        "[ShipBounds] Target set to: §b" + shipId
                                + "§r\nRight-click two opposite corners of the real ship build.")
                .formatted(Formatting.GREEN), false);
    }

    // ── Particle helpers ──────────────────────────────────────────────────────

    /**
     * Draw a wireframe box using END_ROD particles along all 12 edges.
     * Particles are sent only to the admin who triggered the action.
     */
    private static void drawParticleBox(ServerWorld world, Box box,
                                        ServerPlayerEntity recipient) {
        double x0 = box.minX, y0 = box.minY, z0 = box.minZ;
        double x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;

        // 12 edges of the box: 4 along X, 4 along Y, 4 along Z
        // Bottom face
        spawnEdge(world, recipient, x0, y0, z0, x1, y0, z0);
        spawnEdge(world, recipient, x0, y0, z1, x1, y0, z1);
        spawnEdge(world, recipient, x0, y0, z0, x0, y0, z1);
        spawnEdge(world, recipient, x1, y0, z0, x1, y0, z1);
        // Top face
        spawnEdge(world, recipient, x0, y1, z0, x1, y1, z0);
        spawnEdge(world, recipient, x0, y1, z1, x1, y1, z1);
        spawnEdge(world, recipient, x0, y1, z0, x0, y1, z1);
        spawnEdge(world, recipient, x1, y1, z0, x1, y1, z1);
        // Vertical edges
        spawnEdge(world, recipient, x0, y0, z0, x0, y1, z0);
        spawnEdge(world, recipient, x1, y0, z0, x1, y1, z0);
        spawnEdge(world, recipient, x0, y0, z1, x0, y1, z1);
        spawnEdge(world, recipient, x1, y0, z1, x1, y1, z1);
    }

    private static void spawnEdge(ServerWorld world, ServerPlayerEntity recipient,
                                  double x0, double y0, double z0,
                                  double x1, double y1, double z1) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double length = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (length == 0) return;
        int steps = (int) Math.ceil(length / PARTICLE_STEP);
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            double px = x0 + dx * t;
            double py = y0 + dy * t;
            double pz = z0 + dz * t;
            world.spawnParticles(recipient,
                    ParticleTypes.END_ROD,
                    true, px, py, pz,
                    1, 0, 0, 0, 0);
        }
    }

    /** Small burst of HAPPY_VILLAGER particles at a corner to mark it. */
    private static void spawnCornerMarker(ServerWorld world, BlockPos pos,
                                          boolean isCorner1) {
        var particle = isCorner1 ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.TOTEM_OF_UNDYING;
        world.spawnParticles(particle,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                12, 0.3, 0.3, 0.3, 0.1);
    }

    // ── NBT helpers ───────────────────────────────────────────────────────────

    private static NbtCompound getNbt(ItemStack stack) {
        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        return comp != null ? comp.copyNbt() : new NbtCompound();
    }

    private static void writeNbt(ItemStack stack, NbtCompound nbt) {
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static String formatPos(BlockPos p) {
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }
}