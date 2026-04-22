package net.shard.seconddawnrp.tactical.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tactical.data.HardpointEntry;
import net.shard.seconddawnrp.tactical.data.ShipClassDefinition;
import net.shard.seconddawnrp.tactical.data.ShipRegistryEntry;
import net.shard.seconddawnrp.tactical.data.ShipState;
import net.shard.seconddawnrp.tactical.service.EncounterService;
import net.shard.seconddawnrp.tactical.service.TacticalService;

/**
 * All Tactical commands with tab completion.
 *
 * GM:       /gm encounter create|addship|start|pause|resume|end|list
 *           /gm ship jump|warp|sublight|status|navstatus|zonedamage|zonerepair|zones
 *           /gm ship power|heading|speed|torpedoes|warp|position|homenavstatus
 * Admin:    /admin shipyard set
 *           /admin ship register|unregister|list|sethomeship|bounds
 *           /admin hardpoint register|list|zone|clear|remove|locate
 * Tactical: /tactical zones clear|list
 */
public class TacticalCommands {

    // ── Suggestion providers ──────────────────────────────────────────────────

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_ENCOUNTERS =
            (ctx, builder) -> {
                if (SecondDawnRP.ENCOUNTER_SERVICE == null) return builder.buildFuture();
                SecondDawnRP.ENCOUNTER_SERVICE.getAllEncounters()
                        .forEach(e -> builder.suggest(e.getEncounterId()));
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_REGISTRY_SHIPS =
            (ctx, builder) -> {
                if (SecondDawnRP.ENCOUNTER_SERVICE == null) return builder.buildFuture();
                SecondDawnRP.ENCOUNTER_SERVICE.getShipRegistry().keySet()
                        .forEach(builder::suggest);
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_ENCOUNTER_SHIPS =
            (ctx, builder) -> {
                if (SecondDawnRP.ENCOUNTER_SERVICE == null) return builder.buildFuture();
                try {
                    String eid = StringArgumentType.getString(ctx, "encounterId");
                    SecondDawnRP.ENCOUNTER_SERVICE.getEncounter(eid)
                            .ifPresent(e -> e.getAllShips().forEach(s -> builder.suggest(s.getShipId())));
                } catch (Exception ignored) {}
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_CLASSES =
            (ctx, builder) -> { ShipClassDefinition.getAll().forEach(c -> builder.suggest(c.getClassId())); return builder.buildFuture(); };

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_FACTIONS =
            (ctx, builder) -> { builder.suggest("FRIENDLY"); builder.suggest("HOSTILE"); return builder.buildFuture(); };

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_CONTROL_MODES =
            (ctx, builder) -> { builder.suggest("GM_MANUAL"); builder.suggest("PLAYER_CREW"); return builder.buildFuture(); };

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_ARCS =
            (ctx, builder) -> { for (HardpointEntry.Arc a : HardpointEntry.Arc.values()) builder.suggest(a.name()); return builder.buildFuture(); };

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_WEAPON_TYPES =
            (ctx, builder) -> { for (HardpointEntry.WeaponType t : HardpointEntry.WeaponType.values()) builder.suggest(t.name()); return builder.buildFuture(); };

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_ZONE_IDS =
            (ctx, builder) -> {
                ShipClassDefinition.getAll().stream().flatMap(c -> c.getDamageZones().stream()).distinct().forEach(builder::suggest);
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_SHIP_ZONES =
            (ctx, builder) -> {
                if (SecondDawnRP.TACTICAL_SERVICE == null) return builder.buildFuture();
                try {
                    SecondDawnRP.TACTICAL_SERVICE.getHullDamageService()
                            .getZonesForShip(StringArgumentType.getString(ctx, "shipId")).keySet()
                            .forEach(builder::suggest);
                } catch (Exception ignored) {
                    ShipClassDefinition.getAll().stream().flatMap(c -> c.getDamageZones().stream()).distinct().forEach(builder::suggest);
                }
                return builder.buildFuture();
            };

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                TacticalService tacticalService) {
        EncounterService es = tacticalService.getEncounterService();

        // ═════════════════════════════════════════════════════════════════════
        // /gm
        // ═════════════════════════════════════════════════════════════════════
        dispatcher.register(CommandManager.literal("gm")
                .requires(src -> src.hasPermissionLevel(2))

                .then(CommandManager.literal("encounter")
                        .then(CommandManager.literal("create")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(ctx -> { feedback(ctx.getSource(), es.createEncounter(StringArgumentType.getString(ctx, "id")), Formatting.GREEN); return 1; })))
                        .then(CommandManager.literal("addship")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_REGISTRY_SHIPS)
                                                .then(CommandManager.argument("class", StringArgumentType.word()).suggests(SUGGEST_CLASSES)
                                                        .then(CommandManager.argument("faction", StringArgumentType.word()).suggests(SUGGEST_FACTIONS)
                                                                .then(CommandManager.argument("mode", StringArgumentType.word()).suggests(SUGGEST_CONTROL_MODES)
                                                                        .executes(ctx -> {
                                                                            feedback(ctx.getSource(), tacticalService.addShip(
                                                                                    StringArgumentType.getString(ctx, "encounterId"),
                                                                                    StringArgumentType.getString(ctx, "shipId"),
                                                                                    StringArgumentType.getString(ctx, "class"),
                                                                                    StringArgumentType.getString(ctx, "faction"),
                                                                                    StringArgumentType.getString(ctx, "mode")), Formatting.GREEN);
                                                                            return 1;
                                                                        })))))))
                        .then(CommandManager.literal("start")
                                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .executes(ctx -> { feedback(ctx.getSource(), es.startEncounter(StringArgumentType.getString(ctx, "id")), Formatting.GREEN); return 1; })))
                        .then(CommandManager.literal("pause")
                                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .executes(ctx -> { feedback(ctx.getSource(), es.pauseEncounter(StringArgumentType.getString(ctx, "id")), Formatting.YELLOW); return 1; })))
                        .then(CommandManager.literal("resume")
                                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .executes(ctx -> { feedback(ctx.getSource(), es.resumeEncounter(StringArgumentType.getString(ctx, "id")), Formatting.GREEN); return 1; })))
                        .then(CommandManager.literal("end")
                                .then(CommandManager.argument("id", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                                                .executes(ctx -> { feedback(ctx.getSource(), es.endEncounter(StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "reason")), Formatting.YELLOW); return 1; }))))
                        .then(CommandManager.literal("list")
                                .executes(ctx -> {
                                    var list = es.getAllEncounters();
                                    if (list.isEmpty()) { feedback(ctx.getSource(), "No active encounters.", Formatting.GRAY); return 0; }
                                    feedback(ctx.getSource(), "── Active Encounters ──", Formatting.AQUA);
                                    list.forEach(e -> feedback(ctx.getSource(), "  " + e.getEncounterId() + " [" + e.getStatus().name() + "] — " + e.getShipCount() + " ships", Formatting.WHITE));
                                    return list.size();
                                }))
                ) // end /gm encounter

                .then(CommandManager.literal("ship")
                        .then(CommandManager.literal("jump")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .executes(ctx -> { feedback(ctx.getSource(), es.jumpShip(StringArgumentType.getString(ctx, "encounterId"), StringArgumentType.getString(ctx, "shipId")), Formatting.AQUA); return 1; }))))
                        .then(CommandManager.literal("warp")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .then(CommandManager.argument("factor", IntegerArgumentType.integer(1, 9))
                                                        .executes(ctx -> { feedback(ctx.getSource(), tacticalService.engageWarp(StringArgumentType.getString(ctx, "encounterId"), StringArgumentType.getString(ctx, "shipId"), IntegerArgumentType.getInteger(ctx, "factor")), Formatting.GREEN); return 1; })))))
                        .then(CommandManager.literal("sublight")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .executes(ctx -> {
                                                    feedback(ctx.getSource(), es.getEncounter(StringArgumentType.getString(ctx, "encounterId"))
                                                            .flatMap(e -> e.getShip(StringArgumentType.getString(ctx, "shipId")))
                                                            .map(s -> tacticalService.getWarpService().dropToSublight(s))
                                                            .orElse("Ship or encounter not found."), Formatting.YELLOW);
                                                    return 1;
                                                }))))
                        .then(CommandManager.literal("status")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .executes(ctx -> {
                                                    es.getEncounter(StringArgumentType.getString(ctx, "encounterId"))
                                                            .flatMap(e -> e.getShip(StringArgumentType.getString(ctx, "shipId")))
                                                            .ifPresentOrElse(s -> ctx.getSource().sendFeedback(() -> Text.literal(
                                                                            "[Tactical] " + s.getRegistryName() + " [" + s.getCombatId() + "]"
                                                                                    + "\n  Hull: " + s.getHullIntegrity() + "/" + s.getHullMax() + " (" + s.getHullState().name() + ")"
                                                                                    + "\n  Shields: F=" + s.getShield(ShipState.ShieldFacing.FORE) + " A=" + s.getShield(ShipState.ShieldFacing.AFT) + " P=" + s.getShield(ShipState.ShieldFacing.PORT) + " S=" + s.getShield(ShipState.ShieldFacing.STARBOARD)
                                                                                    + "\n  Power: " + s.getPowerBudget() + " | Warp: " + s.getWarpSpeed() + " | Torpedoes: " + s.getTorpedoCount()).formatted(Formatting.AQUA), false),
                                                                    () -> feedback(ctx.getSource(), "Ship not found.", Formatting.RED));
                                                    return 1;
                                                }))))
                        .then(CommandManager.literal("navstatus")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .executes(ctx -> {
                                                    es.getEncounter(StringArgumentType.getString(ctx, "encounterId"))
                                                            .flatMap(e -> e.getShip(StringArgumentType.getString(ctx, "shipId")))
                                                            .ifPresentOrElse(s -> {
                                                                var dz = tacticalService.getHullDamageService().getDestroyedZoneIds(s.getShipId());
                                                                ctx.getSource().sendFeedback(() -> Text.literal(
                                                                        "§b── " + s.getRegistryName() + " Nav ──\n"
                                                                                + "§7Position: §f" + String.format("%.1f, %.1f", s.getPosX(), s.getPosZ()) + "\n"
                                                                                + "§7Heading:  §f" + (int)s.getHeading() + "° §8(target: " + (int)s.getTargetHeading() + "°)\n"
                                                                                + "§7Speed:    §f" + String.format("%.2f", s.getSpeed()) + " §8(target: " + String.format("%.2f", s.getTargetSpeed()) + ")\n"
                                                                                + "§7Warp:     §f" + s.getWarpSpeed() + " §8(capable: " + s.isWarpCapable() + ")\n"
                                                                                + "§7Power:    §f" + s.getPowerBudget() + " §8Wpn:§c" + s.getWeaponsPower() + " §8Shld:§b" + s.getShieldsPower() + " §8Eng:§a" + s.getEnginesPower() + " §8Sens:§e" + s.getSensorsPower() + "\n"
                                                                                + "§7Torpedoes:§f" + s.getTorpedoCount() + "\n"
                                                                                + "§7Destroyed zones: §c" + (dz.isEmpty() ? "none" : String.join(", ", dz))), false);
                                                            }, () -> feedback(ctx.getSource(), "Ship not found.", Formatting.RED));
                                                    return 1;
                                                }))))
                        .then(CommandManager.literal("zonedamage")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .then(CommandManager.argument("zoneId", StringArgumentType.word()).suggests(SUGGEST_SHIP_ZONES)
                                                        .executes(ctx -> {
                                                            var enc = es.getEncounter(StringArgumentType.getString(ctx, "encounterId"));
                                                            if (enc.isEmpty()) { feedback(ctx.getSource(), "Encounter not found.", Formatting.RED); return 0; }
                                                            var ship = enc.get().getShip(StringArgumentType.getString(ctx, "shipId"));
                                                            if (ship.isEmpty()) { feedback(ctx.getSource(), "Ship not found.", Formatting.RED); return 0; }
                                                            String zid = StringArgumentType.getString(ctx, "zoneId");
                                                            var hds = tacticalService.getHullDamageService();
                                                            var zone = hds.getZone(ship.get().getShipId(), zid);
                                                            if (zone.isEmpty()) { feedback(ctx.getSource(), "Zone '" + zid + "' not found.", Formatting.RED); return 0; }
                                                            zone.get().applyDamage(zone.get().getMaxHp() + 1);
                                                            hds.forceDestroyZone(enc.get(), ship.get(), zone.get(), ctx.getSource().getServer());
                                                            feedback(ctx.getSource(), "Zone " + zid + " destroyed on " + ship.get().getRegistryName() + ".", Formatting.RED);
                                                            return 1;
                                                        })))))
                        .then(CommandManager.literal("zonerepair")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .then(CommandManager.argument("zoneId", StringArgumentType.word()).suggests(SUGGEST_SHIP_ZONES)
                                                        .executes(ctx -> {
                                                            var enc = es.getEncounter(StringArgumentType.getString(ctx, "encounterId"));
                                                            if (enc.isEmpty()) { feedback(ctx.getSource(), "Encounter not found.", Formatting.RED); return 0; }
                                                            var ship = enc.get().getShip(StringArgumentType.getString(ctx, "shipId"));
                                                            if (ship.isEmpty()) { feedback(ctx.getSource(), "Ship not found.", Formatting.RED); return 0; }
                                                            tacticalService.getHullDamageService().repairZone(ship.get(), StringArgumentType.getString(ctx, "zoneId"));
                                                            feedback(ctx.getSource(), "Zone repaired on " + ship.get().getRegistryName() + ".", Formatting.GREEN);
                                                            return 1;
                                                        })))))
                        .then(CommandManager.literal("zones")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .executes(ctx -> {
                                                    var enc = es.getEncounter(StringArgumentType.getString(ctx, "encounterId"));
                                                    if (enc.isEmpty()) { feedback(ctx.getSource(), "Encounter not found.", Formatting.RED); return 0; }
                                                    var ship = enc.get().getShip(StringArgumentType.getString(ctx, "shipId"));
                                                    if (ship.isEmpty()) { feedback(ctx.getSource(), "Ship not found.", Formatting.RED); return 0; }
                                                    var zones = tacticalService.getHullDamageService().getZonesForShip(ship.get().getShipId());
                                                    if (zones.isEmpty()) { feedback(ctx.getSource(), "No zones initialised.", Formatting.GRAY); return 0; }
                                                    feedback(ctx.getSource(), "── Zones: " + ship.get().getRegistryName() + " ──", Formatting.AQUA);
                                                    zones.forEach((zid, z) -> feedback(ctx.getSource(),
                                                            "  " + zid + " | " + z.getCurrentHp() + "/" + z.getMaxHp() + " | "
                                                                    + (z.isDestroyed() ? "§cDESTROYED" : z.isDamaged() ? "§eDamaged" : "§aOK")
                                                                    + " | " + z.getRealShipBlocks().size() + "R " + z.getModelBlocks().size() + "M", Formatting.WHITE));
                                                    return zones.size();
                                                }))))
                        .then(CommandManager.literal("power")
                                .then(CommandManager.literal("home")
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 10000))
                                                .executes(ctx -> {
                                                    if (SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE == null || !SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.hasHomeShip()) { feedback(ctx.getSource(), "No home ship registered.", Formatting.RED); return 0; }
                                                    int a = IntegerArgumentType.getInteger(ctx, "amount");
                                                    var s = SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.getHomeShipState();
                                                    s.setManualPowerOverride(true); s.setPowerOutput(a); s.setPowerBudget(a);
                                                    s.setShieldsPower((int)(a*0.35)); s.setWeaponsPower((int)(a*0.30)); s.setEnginesPower((int)(a*0.25));
                                                    s.setSensorsPower(a - s.getShieldsPower() - s.getWeaponsPower() - s.getEnginesPower());
                                                    feedback(ctx.getSource(), "Home ship power set to " + a + ".", Formatting.GREEN); return 1;
                                                })))
                                .then(CommandManager.argument("encounterId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 10000))
                                                        .executes(ctx -> {
                                                            int a = IntegerArgumentType.getInteger(ctx, "amount");
                                                            es.getEncounter(StringArgumentType.getString(ctx, "encounterId")).flatMap(e -> e.getShip(StringArgumentType.getString(ctx, "shipId"))).ifPresentOrElse(s -> {
                                                                s.setManualPowerOverride(true); s.setPowerOutput(a); s.setPowerBudget(a);
                                                                s.setShieldsPower((int)(a*0.35)); s.setWeaponsPower((int)(a*0.30)); s.setEnginesPower((int)(a*0.25));
                                                                s.setSensorsPower(a - s.getShieldsPower() - s.getWeaponsPower() - s.getEnginesPower());
                                                                feedback(ctx.getSource(), "Power set to " + a + " on " + s.getRegistryName() + ".", Formatting.GREEN);
                                                            }, () -> feedback(ctx.getSource(), "Ship not found.", Formatting.RED));
                                                            return 1;
                                                        })))))
                        .then(CommandManager.literal("heading")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .then(CommandManager.argument("degrees", FloatArgumentType.floatArg(0f, 359f))
                                                        .executes(ctx -> {
                                                            es.getEncounter(StringArgumentType.getString(ctx, "encounterId")).flatMap(e -> e.getShip(StringArgumentType.getString(ctx, "shipId"))).ifPresentOrElse(s -> { s.setTargetHeading(FloatArgumentType.getFloat(ctx, "degrees")); feedback(ctx.getSource(), s.getRegistryName() + " heading → " + FloatArgumentType.getFloat(ctx, "degrees") + "°", Formatting.GREEN); }, () -> feedback(ctx.getSource(), "Ship not found.", Formatting.RED));
                                                            return 1;
                                                        })))))
                        .then(CommandManager.literal("speed")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .then(CommandManager.argument("value", FloatArgumentType.floatArg(0f, 10f))
                                                        .executes(ctx -> {
                                                            es.getEncounter(StringArgumentType.getString(ctx, "encounterId")).flatMap(e -> e.getShip(StringArgumentType.getString(ctx, "shipId"))).ifPresentOrElse(s -> { s.setTargetSpeed(FloatArgumentType.getFloat(ctx, "value")); feedback(ctx.getSource(), s.getRegistryName() + " speed → " + FloatArgumentType.getFloat(ctx, "value"), Formatting.GREEN); }, () -> feedback(ctx.getSource(), "Ship not found.", Formatting.RED));
                                                            return 1;
                                                        })))))
                        .then(CommandManager.literal("torpedoes")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .then(CommandManager.argument("count", IntegerArgumentType.integer(0, 100))
                                                        .executes(ctx -> {
                                                            es.getEncounter(StringArgumentType.getString(ctx, "encounterId")).flatMap(e -> e.getShip(StringArgumentType.getString(ctx, "shipId"))).ifPresentOrElse(s -> { s.setTorpedoCount(IntegerArgumentType.getInteger(ctx, "count")); feedback(ctx.getSource(), s.getRegistryName() + " torpedoes → " + IntegerArgumentType.getInteger(ctx, "count"), Formatting.GREEN); }, () -> feedback(ctx.getSource(), "Ship not found.", Formatting.RED));
                                                            return 1;
                                                        })))))
                        .then(CommandManager.literal("warp")
                                .then(CommandManager.argument("factor", IntegerArgumentType.integer(0, 9))
                                        .executes(ctx -> {
                                            if (SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE == null || !SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.hasHomeShip()) { feedback(ctx.getSource(), "No home ship registered.", Formatting.RED); return 0; }
                                            int f = IntegerArgumentType.getInteger(ctx, "factor");
                                            SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.applyWarpSpeed(f);
                                            feedback(ctx.getSource(), f == 0 ? "Home ship dropped to sublight." : "Home ship warp speed set to warp " + f + ".", Formatting.GREEN); return 1;
                                        })))
                        .then(CommandManager.literal("position")
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.argument("x", DoubleArgumentType.doubleArg())
                                                .then(CommandManager.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(ctx -> {
                                                            if (SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE == null || !SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.hasHomeShip()) { feedback(ctx.getSource(), "No home ship registered.", Formatting.RED); return 0; }
                                                            double x = DoubleArgumentType.getDouble(ctx, "x"), z = DoubleArgumentType.getDouble(ctx, "z");
                                                            var s = SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.getHomeShipState();
                                                            s.setPosX(x); s.setPosZ(z); s.setTargetHeading(s.getHeading()); s.setTargetSpeed(0);
                                                            SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.savePosition();
                                                            feedback(ctx.getSource(), String.format("Home ship position set to (%.1f, %.1f).", x, z), Formatting.GREEN); return 1;
                                                        })))))
                        .then(CommandManager.literal("homenavstatus")
                                .executes(ctx -> {
                                    if (SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE == null || !SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.hasHomeShip()) { feedback(ctx.getSource(), "No home ship registered.", Formatting.RED); return 0; }
                                    var s = SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.getHomeShipState();
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            "§b── Home Ship Navigation ──\n§7Position: §f" + String.format("%.1f, %.1f", s.getPosX(), s.getPosZ())
                                                    + "\n§7Heading:  §f" + (int)s.getHeading() + "° §8(target: " + (int)s.getTargetHeading() + "°)"
                                                    + "\n§7Speed:    §f" + String.format("%.1f", s.getSpeed()) + " §8(target: " + String.format("%.1f", s.getTargetSpeed()) + ")"
                                                    + "\n§7Warp:     §f" + s.getWarpSpeed() + " §8(capable: " + s.isWarpCapable() + ")"), false);
                                    return 1;
                                }))
                ) // end /gm ship
        ); // end dispatcher.register(/gm)

        // ═════════════════════════════════════════════════════════════════════
        // /tactical
        // ═════════════════════════════════════════════════════════════════════
        dispatcher.register(CommandManager.literal("tactical")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("zones")
                        .then(CommandManager.literal("clear")
                                .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_REGISTRY_SHIPS)
                                        .executes(ctx -> {
                                            if (SecondDawnRP.TACTICAL_SERVICE == null) { feedback(ctx.getSource(), "Tactical service not available.", Formatting.RED); return 0; }
                                            String sid = StringArgumentType.getString(ctx, "shipId");
                                            int n = SecondDawnRP.TACTICAL_SERVICE.clearAllZonesForShip(sid);
                                            feedback(ctx.getSource(), "Cleared " + n + " zone block registration(s) for ship '" + sid + "'.", Formatting.YELLOW);
                                            return n;
                                        })))
                        .then(CommandManager.literal("list")
                                .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_REGISTRY_SHIPS)
                                        .executes(ctx -> {
                                            if (SecondDawnRP.TACTICAL_SERVICE == null) { feedback(ctx.getSource(), "Tactical service not available.", Formatting.RED); return 0; }
                                            String sid = StringArgumentType.getString(ctx, "shipId");
                                            var counts = SecondDawnRP.TACTICAL_SERVICE.listZonesForShip(sid);
                                            if (counts.isEmpty()) { feedback(ctx.getSource(), "No zone blocks registered for ship '" + sid + "'.", Formatting.GRAY); return 0; }
                                            feedback(ctx.getSource(), "── Zone blocks for " + sid + " ──", Formatting.AQUA);
                                            counts.forEach((zid, c) -> feedback(ctx.getSource(), "  " + zid + " | model: " + c[0] + " | real: " + c[1], Formatting.WHITE));
                                            return counts.size();
                                        }))))
        ); // end dispatcher.register(/tactical)

        // ═════════════════════════════════════════════════════════════════════
        // /admin
        // ═════════════════════════════════════════════════════════════════════
        dispatcher.register(CommandManager.literal("admin")
                .requires(src -> src.hasPermissionLevel(4))

                // /admin shipyard set
                .then(CommandManager.literal("shipyard")
                        .then(CommandManager.literal("set")
                                .executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayer();
                                    if (p == null) return 0;
                                    es.setShipyard(p.getWorld().getRegistryKey().getValue().toString(), p.getX(), p.getY(), p.getZ());
                                    feedback(ctx.getSource(), "Shipyard spawn set at current position.", Formatting.GREEN);
                                    return 1;
                                })))

                // /admin ship ...
                .then(CommandManager.literal("ship")
                        .then(CommandManager.literal("register")
                                .then(CommandManager.argument("shipId", StringArgumentType.word())
                                        .then(CommandManager.argument("name", StringArgumentType.string())
                                                .then(CommandManager.argument("class", StringArgumentType.word()).suggests(SUGGEST_CLASSES)
                                                        .then(CommandManager.argument("faction", StringArgumentType.word()).suggests(SUGGEST_FACTIONS)
                                                                .executes(ctx -> {
                                                                    feedback(ctx.getSource(), es.registerShip(
                                                                            StringArgumentType.getString(ctx, "shipId"),
                                                                            StringArgumentType.getString(ctx, "name"),
                                                                            StringArgumentType.getString(ctx, "class"),
                                                                            StringArgumentType.getString(ctx, "faction")), Formatting.GREEN);
                                                                    return 1;
                                                                }))))))
                        .then(CommandManager.literal("unregister")
                                .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_REGISTRY_SHIPS)
                                        .executes(ctx -> { feedback(ctx.getSource(), es.unregisterShip(StringArgumentType.getString(ctx, "shipId")), Formatting.YELLOW); return 1; })))
                        .then(CommandManager.literal("list")
                                .executes(ctx -> {
                                    var reg = es.getShipRegistry();
                                    if (reg.isEmpty()) { feedback(ctx.getSource(), "No ships registered.", Formatting.GRAY); return 0; }
                                    feedback(ctx.getSource(), "── Ship Registry ──", Formatting.AQUA);
                                    reg.values().forEach(e -> feedback(ctx.getSource(),
                                            "  " + e.getShipId() + " | " + e.getRegistryName() + " | " + e.getShipClass() + " | " + e.getFaction()
                                                    + (e.isHomeShip() ? " §b[HOME]" : "") + (e.hasBounds() ? " §7[bounds set]" : " §8[no bounds]"),
                                            Formatting.WHITE));
                                    return reg.size();
                                }))
                        .then(CommandManager.literal("sethomeship")
                                .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_REGISTRY_SHIPS)
                                        .executes(ctx -> {
                                            String sid = StringArgumentType.getString(ctx, "shipId");
                                            var reg = es.getShipRegistry();
                                            if (!reg.containsKey(sid)) { feedback(ctx.getSource(), "Unknown ship: " + sid, Formatting.RED); return 0; }
                                            reg.values().forEach(e -> e.setHomeShip(false));
                                            reg.get(sid).setHomeShip(true);
                                            reg.values().forEach(es::saveShipRegistryEntry);
                                            if (SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE != null) SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.loadHomeShip();
                                            feedback(ctx.getSource(), "Home ship set to: " + reg.get(sid).getRegistryName() + ".", Formatting.GREEN);
                                            return 1;
                                        })))

                        // /admin ship bounds settarget|show|set
                        .then(CommandManager.literal("bounds")
                                .then(CommandManager.literal("settarget")
                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_REGISTRY_SHIPS)
                                                .executes(ctx -> {
                                                    ServerPlayerEntity p = ctx.getSource().getPlayer();
                                                    if (p == null) { feedback(ctx.getSource(), "Only players can use this.", Formatting.RED); return 0; }
                                                    String sid = StringArgumentType.getString(ctx, "shipId");
                                                    if (!SecondDawnRP.ENCOUNTER_SERVICE.getShipRegistry().containsKey(sid)) { feedback(ctx.getSource(), "Ship '" + sid + "' not found.", Formatting.RED); return 0; }
                                                    net.shard.seconddawnrp.tactical.item.ShipBoundsToolItem.setTarget(p, sid);
                                                    return 1;
                                                })))
                                .then(CommandManager.literal("show")
                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_REGISTRY_SHIPS)
                                                .executes(ctx -> {
                                                    ServerPlayerEntity p = ctx.getSource().getPlayer();
                                                    if (p == null) return 0;
                                                    String sid = StringArgumentType.getString(ctx, "shipId");
                                                    var entry = SecondDawnRP.ENCOUNTER_SERVICE.getShipEntry(sid);
                                                    if (entry.isEmpty()) { feedback(ctx.getSource(), "Ship '" + sid + "' not found.", Formatting.RED); return 0; }
                                                    if (!entry.get().hasBounds()) { feedback(ctx.getSource(), "Ship '" + sid + "' has no bounds set.", Formatting.GRAY); return 0; }
                                                    BlockPos mn = entry.get().getRealBoundsMin(), mx = entry.get().getRealBoundsMax();
                                                    feedback(ctx.getSource(), "Bounds for '" + sid + "':\n  Min: " + mn.getX() + ", " + mn.getY() + ", " + mn.getZ() + "\n  Max: " + mx.getX() + ", " + mx.getY() + ", " + mx.getZ(), Formatting.AQUA);
                                                    net.minecraft.util.math.Box box = entry.get().getRealBoundsBox();
                                                    if (box != null && p.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)
                                                        net.shard.seconddawnrp.tactical.item.ShipBoundsToolItem.drawParticleBoxPublic(sw, box, p);
                                                    return 1;
                                                })))
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_REGISTRY_SHIPS)
                                                .then(CommandManager.argument("x1", IntegerArgumentType.integer())
                                                        .then(CommandManager.argument("y1", IntegerArgumentType.integer())
                                                                .then(CommandManager.argument("z1", IntegerArgumentType.integer())
                                                                        .then(CommandManager.argument("x2", IntegerArgumentType.integer())
                                                                                .then(CommandManager.argument("y2", IntegerArgumentType.integer())
                                                                                        .then(CommandManager.argument("z2", IntegerArgumentType.integer())
                                                                                                .executes(ctx -> {
                                                                                                    if (SecondDawnRP.TACTICAL_SERVICE == null) { feedback(ctx.getSource(), "Tactical service not available.", Formatting.RED); return 0; }
                                                                                                    String sid = StringArgumentType.getString(ctx, "shipId");
                                                                                                    BlockPos c1 = new BlockPos(IntegerArgumentType.getInteger(ctx, "x1"), IntegerArgumentType.getInteger(ctx, "y1"), IntegerArgumentType.getInteger(ctx, "z1"));
                                                                                                    BlockPos c2 = new BlockPos(IntegerArgumentType.getInteger(ctx, "x2"), IntegerArgumentType.getInteger(ctx, "y2"), IntegerArgumentType.getInteger(ctx, "z2"));
                                                                                                    if (!SecondDawnRP.TACTICAL_SERVICE.setShipBounds(sid, c1, c2)) { feedback(ctx.getSource(), "Ship '" + sid + "' not found.", Formatting.RED); return 0; }
                                                                                                    feedback(ctx.getSource(), "Bounds set for '" + sid + "': " + (Math.abs(c2.getX()-c1.getX())+1) + "×" + (Math.abs(c2.getY()-c1.getY())+1) + "×" + (Math.abs(c2.getZ()-c1.getZ())+1) + " blocks.", Formatting.GREEN);
                                                                                                    return 1;
                                                                                                })))))))
                                        ) // end /admin ship bounds+sethomeship+list+unregister+register chain
                                ) // end /admin ship

                                // /admin hardpoint ...
                                .then(CommandManager.literal("hardpoint")
                                        .then(CommandManager.literal("register")
                                                .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_REGISTRY_SHIPS)
                                                        .then(CommandManager.argument("arc", StringArgumentType.word()).suggests(SUGGEST_ARCS)
                                                                .then(CommandManager.argument("type", StringArgumentType.word()).suggests(SUGGEST_WEAPON_TYPES)
                                                                        .executes(ctx -> {
                                                                            ServerPlayerEntity p = ctx.getSource().getPlayer();
                                                                            if (p == null) return 0;
                                                                            try {
                                                                                feedback(ctx.getSource(), es.registerHardpoint(
                                                                                        StringArgumentType.getString(ctx, "shipId"), p.getBlockPos(),
                                                                                        HardpointEntry.Arc.valueOf(StringArgumentType.getString(ctx, "arc").toUpperCase()),
                                                                                        HardpointEntry.WeaponType.valueOf(StringArgumentType.getString(ctx, "type").toUpperCase())), Formatting.GREEN);
                                                                            } catch (IllegalArgumentException e) { feedback(ctx.getSource(), "Invalid arc or type.", Formatting.RED); }
                                                                            return 1;
                                                                        })))))
                                        .then(CommandManager.literal("list")
                                                .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_REGISTRY_SHIPS)
                                                        .executes(ctx -> {
                                                            String sid = StringArgumentType.getString(ctx, "shipId");
                                                            var hps = es.getHardpoints(sid);
                                                            if (hps.isEmpty()) { feedback(ctx.getSource(), "No hardpoints on " + sid, Formatting.GRAY); return 0; }
                                                            feedback(ctx.getSource(), "── Hardpoints on " + sid + " ──", Formatting.AQUA);
                                                            hps.forEach(h -> feedback(ctx.getSource(), "  " + h.getHardpointId() + " | " + h.getWeaponType() + " | " + h.getArc() + " | HP: " + h.getHealth(), Formatting.WHITE));
                                                            return hps.size();
                                                        })))
                                        .then(CommandManager.literal("zone")
                                                .then(CommandManager.literal("set")
                                                        .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_REGISTRY_SHIPS)
                                                                .then(CommandManager.argument("zoneId", StringArgumentType.word()).suggests(SUGGEST_ZONE_IDS)
                                                                        .then(CommandManager.argument("mode", StringArgumentType.word())
                                                                                .suggests((ctx, b) -> { b.suggest("MODEL"); b.suggest("REAL"); return b.buildFuture(); })
                                                                                .executes(ctx -> {
                                                                                    ServerPlayerEntity p = ctx.getSource().getPlayer();
                                                                                    if (p == null) return 0;
                                                                                    String mode = StringArgumentType.getString(ctx, "mode").toUpperCase();
                                                                                    if (!mode.equals("MODEL") && !mode.equals("REAL")) { feedback(ctx.getSource(), "Mode must be MODEL or REAL.", Formatting.RED); return 0; }
                                                                                    net.shard.seconddawnrp.tactical.damage.DamageZoneToolItem.setContext(p, StringArgumentType.getString(ctx, "shipId"), StringArgumentType.getString(ctx, "zoneId"), mode);
                                                                                    return 1;
                                                                                }))))))
                                        .then(CommandManager.literal("clear")
                                                .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_REGISTRY_SHIPS)
                                                        .then(CommandManager.argument("zoneId", StringArgumentType.word()).suggests(SUGGEST_ZONE_IDS)
                                                                .then(CommandManager.argument("target", StringArgumentType.word())
                                                                        .suggests((ctx, b) -> { b.suggest("MODEL"); b.suggest("REAL"); b.suggest("ALL"); return b.buildFuture(); })
                                                                        .executes(ctx -> {
                                                                            if (SecondDawnRP.TACTICAL_SERVICE == null) { feedback(ctx.getSource(), "Tactical service not available.", Formatting.RED); return 0; }
                                                                            String sid = StringArgumentType.getString(ctx, "shipId");
                                                                            String zid = StringArgumentType.getString(ctx, "zoneId");
                                                                            String tgt = StringArgumentType.getString(ctx, "target").toUpperCase();
                                                                            var zoneOpt = SecondDawnRP.TACTICAL_SERVICE.getHullDamageService().getZone(sid, zid);
                                                                            if (zoneOpt.isEmpty()) { feedback(ctx.getSource(), "Zone '" + zid + "' not found on ship '" + sid + "'.", Formatting.RED); return 0; }
                                                                            var zone = zoneOpt.get();
                                                                            int cleared = 0;
                                                                            if ("MODEL".equals(tgt) || "ALL".equals(tgt)) { cleared += zone.getModelBlocks().size(); zone.clearModelBlocks(); }
                                                                            if ("REAL".equals(tgt) || "ALL".equals(tgt)) { cleared += zone.getRealShipBlocks().size(); zone.clearRealShipBlocks(); }
                                                                            feedback(ctx.getSource(), "Cleared " + cleared + " block(s) from " + tgt + " on zone " + zid + ".", Formatting.YELLOW);
                                                                            return cleared;
                                                                        })))))
                                        .then(CommandManager.literal("remove")
                                                .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_REGISTRY_SHIPS)
                                                        .then(CommandManager.argument("zoneId", StringArgumentType.word()).suggests(SUGGEST_ZONE_IDS)
                                                                .executes(ctx -> {
                                                                    if (SecondDawnRP.TACTICAL_SERVICE == null) { feedback(ctx.getSource(), "Tactical service not available.", Formatting.RED); return 0; }
                                                                    boolean ok = SecondDawnRP.TACTICAL_SERVICE.getHullDamageService().removeZone(StringArgumentType.getString(ctx, "shipId"), StringArgumentType.getString(ctx, "zoneId"));
                                                                    feedback(ctx.getSource(), ok ? "Zone removed." : "Zone not found.", ok ? Formatting.YELLOW : Formatting.RED);
                                                                    return ok ? 1 : 0;
                                                                }))))
                                        .then(CommandManager.literal("locate")
                                                .then(CommandManager.argument("shipId", StringArgumentType.word()).suggests(SUGGEST_REGISTRY_SHIPS)
                                                        .then(CommandManager.argument("zoneId", StringArgumentType.word()).suggests(SUGGEST_ZONE_IDS)
                                                                .executes(ctx -> {
                                                                    ServerPlayerEntity p = ctx.getSource().getPlayer();
                                                                    if (p == null || SecondDawnRP.TACTICAL_SERVICE == null) return 0;
                                                                    String sid = StringArgumentType.getString(ctx, "shipId");
                                                                    String zid = StringArgumentType.getString(ctx, "zoneId");
                                                                    var zoneOpt = SecondDawnRP.TACTICAL_SERVICE.getHullDamageService().getZone(sid, zid);
                                                                    if (zoneOpt.isEmpty()) { feedback(ctx.getSource(), "Zone '" + zid + "' not found on ship '" + sid + "'.", Formatting.RED); return 0; }
                                                                    var zone = zoneOpt.get();
                                                                    int count = 0;
                                                                    for (BlockPos pos : zone.getModelBlocks()) { net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new net.shard.seconddawnrp.tactical.network.LocateZoneBlockS2CPacket(zid, "MODEL", pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5)); count++; }
                                                                    for (BlockPos pos : zone.getRealShipBlocks()) { net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new net.shard.seconddawnrp.tactical.network.LocateZoneBlockS2CPacket(zid, "REAL", pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5)); count++; }
                                                                    feedback(ctx.getSource(), "Located " + count + " block(s) for zone " + zid + ".", Formatting.AQUA);
                                                                    return count;
                                                                }))))))
                ) // end /admin hardpoint
        ); // end dispatcher.register(/admin)

    }

    private static void feedback(ServerCommandSource src, String msg, Formatting color) {
        src.sendFeedback(() -> Text.literal(msg).formatted(color), false);
    }
}