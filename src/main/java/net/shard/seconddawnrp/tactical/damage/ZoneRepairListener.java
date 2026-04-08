package net.shard.seconddawnrp.tactical.damage;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tactical.data.DamageZone;
import net.shard.seconddawnrp.tactical.data.ShipState;
import net.shard.seconddawnrp.tactical.service.EncounterService;
import net.shard.seconddawnrp.tactical.service.HullDamageService;

import java.util.Map;
import java.util.Optional;

/**
 * Listens for Engineering player right-clicks on damaged zone blocks.
 * Requires 4 Stone Bricks in hand (MVP repair material).
 * Calls HullDamageService.repairZone() with the correct ServerWorld so
 * DamageModelMapper can restore physical blocks.
 */
public class ZoneRepairListener {

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (SecondDawnRP.TACTICAL_SERVICE == null) return ActionResult.PASS;

            BlockPos pos = hitResult.getBlockPos();
            HullDamageService hullDamageService =
                    SecondDawnRP.TACTICAL_SERVICE.getHullDamageService();
            EncounterService encounterService =
                    SecondDawnRP.TACTICAL_SERVICE.getEncounterService();

            for (var encounter : encounterService.getAllEncounters()) {
                for (ShipState ship : encounter.getAllShips()) {
                    // Find if this block belongs to a damaged zone on this ship
                    String zoneId = findZoneForBlock(
                            hullDamageService.getZonesForShip(ship.getShipId()), pos);
                    if (zoneId == null) continue;

                    // Only act if zone is actually damaged
                    if (!hullDamageService.isZoneDamaged(ship.getShipId(), zoneId)) continue;

                    // Engineering division check (GMs bypass)
                    if (!sp.hasPermissionLevel(2) && !isEngineering(sp)) {
                        sp.sendMessage(Text.literal(
                                        "[Tactical] Engineering division required to repair zones.")
                                .formatted(Formatting.RED), false);
                        return ActionResult.FAIL;
                    }

                    // Repair material check — 4 stone bricks
                    var stack = sp.getMainHandStack();
                    if (stack.getItem() != net.minecraft.item.Items.STONE_BRICKS
                            || stack.getCount() < 4) {
                        sp.sendMessage(Text.literal(
                                        "[Tactical] Repair requires 4 Stone Bricks. Zone: " + zoneId)
                                .formatted(Formatting.YELLOW), false);
                        return ActionResult.SUCCESS; // consume click
                    }

                    stack.decrement(4);

                    // Pass the ServerWorld so DamageModelMapper can restore blocks
                    hullDamageService.repairZone(ship, zoneId, (ServerWorld) world);

                    encounter.log("[REPAIR] Zone " + zoneId
                            + " repaired by " + sp.getName().getString());

                    sp.sendMessage(Text.literal(
                                    "[Tactical] Zone " + zoneId + " repaired successfully.")
                            .formatted(Formatting.GREEN), false);

                    return ActionResult.SUCCESS;
                }
            }

            return ActionResult.PASS;
        });
    }

    /**
     * Searches all zones on a ship for a real ship block matching the clicked position.
     * Returns the zoneId if found, null otherwise.
     */
    private static String findZoneForBlock(Map<String, DamageZone> zones, BlockPos pos) {
        long encoded = pos.asLong();
        for (var entry : zones.entrySet()) {
            for (BlockPos rp : entry.getValue().getRealShipBlocks()) {
                if (rp.asLong() == encoded) return entry.getKey();
            }
        }
        return null;
    }

    private static boolean isEngineering(ServerPlayerEntity player) {
        var profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(player.getUuid());
        if (profile == null || profile.getDivision() == null) return false;
        return profile.getDivision().name().equals("ENGINEERING");
    }
}