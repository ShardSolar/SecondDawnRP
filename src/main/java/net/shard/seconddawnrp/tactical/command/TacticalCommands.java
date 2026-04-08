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
 * GM:    /gm encounter create|addship|start|pause|resume|end|list
 *        /gm ship jump|warp|sublight|status|navstatus|zonedamage|zonerepair|zones
 * Admin: /admin shipyard set
 *        /admin ship register|unregister|list|sethomeship
 *        /admin hardpoint register|list|zone set
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
                            .ifPresent(e -> e.getAllShips()
                                    .forEach(s -> builder.suggest(s.getShipId())));
                } catch (Exception ignored) {}
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_CLASSES =
            (ctx, builder) -> {
                ShipClassDefinition.getAll().forEach(c -> builder.suggest(c.getClassId()));
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_FACTIONS =
            (ctx, builder) -> {
                builder.suggest("FRIENDLY");
                builder.suggest("HOSTILE");
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_CONTROL_MODES =
            (ctx, builder) -> {
                builder.suggest("GM_MANUAL");
                builder.suggest("PLAYER_CREW");
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_ARCS =
            (ctx, builder) -> {
                for (HardpointEntry.Arc arc : HardpointEntry.Arc.values())
                    builder.suggest(arc.name());
                return builder.buildFuture();
            };

    private static final SuggestionProvider<ServerCommandSource> SUGGEST_WEAPON_TYPES =
            (ctx, builder) -> {
                for (HardpointEntry.WeaponType wt : HardpointEntry.WeaponType.values())
                    builder.suggest(wt.name());
                return builder.buildFuture();
            };

    /** Suggests zone IDs from all loaded ship class definitions. */
    private static final SuggestionProvider<ServerCommandSource> SUGGEST_ZONE_IDS =
            (ctx, builder) -> {
                ShipClassDefinition.getAll().stream()
                        .flatMap(c -> c.getDamageZones().stream())
                        .distinct()
                        .forEach(builder::suggest);
                return builder.buildFuture();
            };

    /** Suggests zone IDs present on a specific ship in a named encounter. */
    private static final SuggestionProvider<ServerCommandSource> SUGGEST_SHIP_ZONES =
            (ctx, builder) -> {
                if (SecondDawnRP.TACTICAL_SERVICE == null) return builder.buildFuture();
                try {
                    String eid = StringArgumentType.getString(ctx, "encounterId");
                    String sid = StringArgumentType.getString(ctx, "shipId");
                    SecondDawnRP.TACTICAL_SERVICE.getHullDamageService()
                            .getZonesForShip(sid).keySet()
                            .forEach(builder::suggest);
                } catch (Exception ignored) {
                    // Fall back to all zone IDs from class definitions
                    ShipClassDefinition.getAll().stream()
                            .flatMap(c -> c.getDamageZones().stream())
                            .distinct()
                            .forEach(builder::suggest);
                }
                return builder.buildFuture();
            };

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                TacticalService tacticalService) {
        EncounterService es = tacticalService.getEncounterService();

        // ── /gm encounter ─────────────────────────────────────────────────────

        dispatcher.register(CommandManager.literal("gm")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("encounter")

                        .then(CommandManager.literal("create")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String result = es.createEncounter(
                                                    StringArgumentType.getString(ctx, "id"));
                                            feedback(ctx.getSource(), result, Formatting.GREEN);
                                            return 1;
                                        })))

                        .then(CommandManager.literal("addship")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .suggests(SUGGEST_REGISTRY_SHIPS)
                                                .then(CommandManager.argument("class", StringArgumentType.word())
                                                        .suggests(SUGGEST_CLASSES)
                                                        .then(CommandManager.argument("faction", StringArgumentType.word())
                                                                .suggests(SUGGEST_FACTIONS)
                                                                .then(CommandManager.argument("mode", StringArgumentType.word())
                                                                        .suggests(SUGGEST_CONTROL_MODES)
                                                                        .executes(ctx -> {
                                                                            // Route through TacticalService so zone init fires
                                                                            String result = tacticalService.addShip(
                                                                                    StringArgumentType.getString(ctx, "encounterId"),
                                                                                    StringArgumentType.getString(ctx, "shipId"),
                                                                                    StringArgumentType.getString(ctx, "class"),
                                                                                    StringArgumentType.getString(ctx, "faction"),
                                                                                    StringArgumentType.getString(ctx, "mode"));
                                                                            feedback(ctx.getSource(), result, Formatting.GREEN);
                                                                            return 1;
                                                                        })))))))

                        .then(CommandManager.literal("start")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .executes(ctx -> {
                                            String result = es.startEncounter(
                                                    StringArgumentType.getString(ctx, "id"));
                                            feedback(ctx.getSource(), result, Formatting.GREEN);
                                            return 1;
                                        })))

                        .then(CommandManager.literal("pause")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .executes(ctx -> {
                                            String result = es.pauseEncounter(
                                                    StringArgumentType.getString(ctx, "id"));
                                            feedback(ctx.getSource(), result, Formatting.YELLOW);
                                            return 1;
                                        })))

                        .then(CommandManager.literal("resume")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .executes(ctx -> {
                                            String result = es.resumeEncounter(
                                                    StringArgumentType.getString(ctx, "id"));
                                            feedback(ctx.getSource(), result, Formatting.GREEN);
                                            return 1;
                                        })))

                        .then(CommandManager.literal("end")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String result = es.endEncounter(
                                                            StringArgumentType.getString(ctx, "id"),
                                                            StringArgumentType.getString(ctx, "reason"));
                                                    feedback(ctx.getSource(), result, Formatting.YELLOW);
                                                    return 1;
                                                }))))

                        .then(CommandManager.literal("list")
                                .executes(ctx -> {
                                    var encounters = es.getAllEncounters();
                                    if (encounters.isEmpty()) {
                                        feedback(ctx.getSource(), "No active encounters.", Formatting.GRAY);
                                        return 0;
                                    }
                                    feedback(ctx.getSource(), "── Active Encounters ──", Formatting.AQUA);
                                    encounters.forEach(e -> feedback(ctx.getSource(),
                                            "  " + e.getEncounterId()
                                                    + " [" + e.getStatus().name() + "]"
                                                    + " — " + e.getShipCount() + " ships",
                                            Formatting.WHITE));
                                    return encounters.size();
                                }))
                )

                // ── /gm ship ──────────────────────────────────────────────────

                .then(CommandManager.literal("ship")

                        .then(CommandManager.literal("jump")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .executes(ctx -> {
                                                    String result = es.jumpShip(
                                                            StringArgumentType.getString(ctx, "encounterId"),
                                                            StringArgumentType.getString(ctx, "shipId"));
                                                    feedback(ctx.getSource(), result, Formatting.AQUA);
                                                    return 1;
                                                }))))

                        .then(CommandManager.literal("warp")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .then(CommandManager.argument("factor", IntegerArgumentType.integer(1, 9))
                                                        .executes(ctx -> {
                                                            String result = tacticalService.engageWarp(
                                                                    StringArgumentType.getString(ctx, "encounterId"),
                                                                    StringArgumentType.getString(ctx, "shipId"),
                                                                    IntegerArgumentType.getInteger(ctx, "factor"));
                                                            feedback(ctx.getSource(), result, Formatting.GREEN);
                                                            return 1;
                                                        })))))

                        .then(CommandManager.literal("sublight")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .executes(ctx -> {
                                                    String eid = StringArgumentType.getString(ctx, "encounterId");
                                                    String sid = StringArgumentType.getString(ctx, "shipId");
                                                    String result = es.getEncounter(eid)
                                                            .flatMap(e -> e.getShip(sid))
                                                            .map(ship -> tacticalService.getWarpService()
                                                                    .dropToSublight(ship))
                                                            .orElse("Ship or encounter not found.");
                                                    feedback(ctx.getSource(), result, Formatting.YELLOW);
                                                    return 1;
                                                }))))

                        // /gm ship status <encounterId> <shipId>
                        .then(CommandManager.literal("status")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .executes(ctx -> {
                                                    String eid = StringArgumentType.getString(ctx, "encounterId");
                                                    String sid = StringArgumentType.getString(ctx, "shipId");
                                                    es.getEncounter(eid)
                                                            .flatMap(e -> e.getShip(sid))
                                                            .ifPresentOrElse(ship -> {
                                                                ctx.getSource().sendFeedback(() -> Text.literal(
                                                                                "[Tactical] " + ship.getRegistryName()
                                                                                        + " [" + ship.getCombatId() + "]"
                                                                                        + "\n  Hull: " + ship.getHullIntegrity()
                                                                                        + "/" + ship.getHullMax()
                                                                                        + " (" + ship.getHullState().name() + ")"
                                                                                        + "\n  Shields: F=" + ship.getShield(ShipState.ShieldFacing.FORE)
                                                                                        + " A=" + ship.getShield(ShipState.ShieldFacing.AFT)
                                                                                        + " P=" + ship.getShield(ShipState.ShieldFacing.PORT)
                                                                                        + " S=" + ship.getShield(ShipState.ShieldFacing.STARBOARD)
                                                                                        + "\n  Power: " + ship.getPowerBudget()
                                                                                        + " | Warp: " + ship.getWarpSpeed()
                                                                                        + " | Torpedoes: " + ship.getTorpedoCount())
                                                                        .formatted(Formatting.AQUA), false);
                                                            }, () -> feedback(ctx.getSource(),
                                                                    "Ship not found.", Formatting.RED));
                                                    return 1;
                                                }))))

                        // /gm ship navstatus <encounterId> <shipId>
                        // Shows full navigation + power + zone penalty state for any ship
                        .then(CommandManager.literal("navstatus")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .executes(ctx -> {
                                                    String eid = StringArgumentType.getString(ctx, "encounterId");
                                                    String sid = StringArgumentType.getString(ctx, "shipId");
                                                    es.getEncounter(eid)
                                                            .flatMap(e -> e.getShip(sid))
                                                            .ifPresentOrElse(ship -> {
                                                                var hds = tacticalService.getHullDamageService();
                                                                var destroyed = hds.getDestroyedZoneIds(ship.getShipId());
                                                                String zones = destroyed.isEmpty()
                                                                        ? "none"
                                                                        : String.join(", ", destroyed);
                                                                ctx.getSource().sendFeedback(() -> Text.literal(
                                                                                "§b── " + ship.getRegistryName() + " Nav ──\n"
                                                                                        + "§7Position:  §f" + String.format("%.1f, %.1f",
                                                                                        ship.getPosX(), ship.getPosZ()) + "\n"
                                                                                        + "§7Heading:   §f" + (int)ship.getHeading()
                                                                                        + "° §8(target: " + (int)ship.getTargetHeading() + "°)\n"
                                                                                        + "§7Speed:     §f" + String.format("%.2f", ship.getSpeed())
                                                                                        + " §8(target: " + String.format("%.2f", ship.getTargetSpeed()) + ")\n"
                                                                                        + "§7Warp:      §f" + ship.getWarpSpeed()
                                                                                        + " §8(capable: " + ship.isWarpCapable() + ")\n"
                                                                                        + "§7Power:     §f" + ship.getPowerBudget()
                                                                                        + " §8Wpn:§c" + ship.getWeaponsPower()
                                                                                        + " §8Shld:§b" + ship.getShieldsPower()
                                                                                        + " §8Eng:§a" + ship.getEnginesPower()
                                                                                        + " §8Sens:§e" + ship.getSensorsPower() + "\n"
                                                                                        + "§7Torpedoes: §f" + ship.getTorpedoCount() + "\n"
                                                                                        + "§7Destroyed zones: §c" + zones)
                                                                        , false);
                                                            }, () -> feedback(ctx.getSource(),
                                                                    "Ship not found.", Formatting.RED));
                                                    return 1;
                                                }))))

                        // /gm ship zonedamage <encounterId> <shipId> <zoneId>
                        // Instantly destroys a zone for testing — bypasses HP, fires all penalties and block effects
                        .then(CommandManager.literal("zonedamage")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .then(CommandManager.argument("zoneId", StringArgumentType.word())
                                                        .suggests(SUGGEST_SHIP_ZONES)
                                                        .executes(ctx -> {
                                                            String eid    = StringArgumentType.getString(ctx, "encounterId");
                                                            String sid    = StringArgumentType.getString(ctx, "shipId");
                                                            String zoneId = StringArgumentType.getString(ctx, "zoneId");

                                                            var encounterOpt = es.getEncounter(eid);
                                                            if (encounterOpt.isEmpty()) {
                                                                feedback(ctx.getSource(), "Encounter not found.", Formatting.RED);
                                                                return 0;
                                                            }
                                                            var shipOpt = encounterOpt.get().getShip(sid);
                                                            if (shipOpt.isEmpty()) {
                                                                feedback(ctx.getSource(), "Ship not found.", Formatting.RED);
                                                                return 0;
                                                            }

                                                            var hds  = tacticalService.getHullDamageService();
                                                            var zone = hds.getZone(sid, zoneId);
                                                            if (zone.isEmpty()) {
                                                                feedback(ctx.getSource(),
                                                                        "Zone '" + zoneId + "' not found on ship. "
                                                                                + "Is the ship in an active encounter?",
                                                                        Formatting.RED);
                                                                return 0;
                                                            }

                                                            // Force HP to 0 and trigger full destroy path
                                                            zone.get().applyDamage(zone.get().getMaxHp() + 1);
                                                            // applyHullDamage won't fire because we're bypassing
                                                            // the damage path — call the internal destroy directly
                                                            // by draining hull to trigger zone check
                                                            // Simpler: just invoke the penalty + block destruction manually
                                                            hds.forceDestroyZone(
                                                                    encounterOpt.get(),
                                                                    shipOpt.get(),
                                                                    zone.get(),
                                                                    ctx.getSource().getServer());

                                                            feedback(ctx.getSource(),
                                                                    "Zone " + zoneId + " destroyed on "
                                                                            + shipOpt.get().getRegistryName() + ".",
                                                                    Formatting.RED);
                                                            return 1;
                                                        })))))

                        // /gm ship zonerepair <encounterId> <shipId> <zoneId>
                        // Instantly repairs a zone — clears penalties, restores blocks
                        .then(CommandManager.literal("zonerepair")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .then(CommandManager.argument("zoneId", StringArgumentType.word())
                                                        .suggests(SUGGEST_SHIP_ZONES)
                                                        .executes(ctx -> {
                                                            String eid    = StringArgumentType.getString(ctx, "encounterId");
                                                            String sid    = StringArgumentType.getString(ctx, "shipId");
                                                            String zoneId = StringArgumentType.getString(ctx, "zoneId");

                                                            var encounterOpt = es.getEncounter(eid);
                                                            if (encounterOpt.isEmpty()) {
                                                                feedback(ctx.getSource(), "Encounter not found.", Formatting.RED);
                                                                return 0;
                                                            }
                                                            var shipOpt = encounterOpt.get().getShip(sid);
                                                            if (shipOpt.isEmpty()) {
                                                                feedback(ctx.getSource(), "Ship not found.", Formatting.RED);
                                                                return 0;
                                                            }

                                                            tacticalService.getHullDamageService()
                                                                    .repairZone(shipOpt.get(), zoneId);

                                                            feedback(ctx.getSource(),
                                                                    "Zone " + zoneId + " repaired on "
                                                                            + shipOpt.get().getRegistryName() + ".",
                                                                    Formatting.GREEN);
                                                            return 1;
                                                        })))))

                        // /gm ship zones <encounterId> <shipId>
                        // Lists all zones with their HP and destroyed status
                        .then(CommandManager.literal("zones")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .executes(ctx -> {
                                                    String eid = StringArgumentType.getString(ctx, "encounterId");
                                                    String sid = StringArgumentType.getString(ctx, "shipId");

                                                    var encounterOpt = es.getEncounter(eid);
                                                    if (encounterOpt.isEmpty()) {
                                                        feedback(ctx.getSource(), "Encounter not found.", Formatting.RED);
                                                        return 0;
                                                    }
                                                    var shipOpt = encounterOpt.get().getShip(sid);
                                                    if (shipOpt.isEmpty()) {
                                                        feedback(ctx.getSource(), "Ship not found.", Formatting.RED);
                                                        return 0;
                                                    }

                                                    var hds   = tacticalService.getHullDamageService();
                                                    var zones = hds.getZonesForShip(sid);
                                                    if (zones.isEmpty()) {
                                                        feedback(ctx.getSource(),
                                                                "No zones initialised for " + sid + ".",
                                                                Formatting.GRAY);
                                                        return 0;
                                                    }

                                                    feedback(ctx.getSource(),
                                                            "── Zones: " + shipOpt.get().getRegistryName() + " ──",
                                                            Formatting.AQUA);
                                                    zones.forEach((zoneId, zone) -> {
                                                        String status = zone.isDestroyed() ? "§cDESTROYED"
                                                                : zone.isDamaged() ? "§eDamaged"
                                                                : "§aOK";
                                                        feedback(ctx.getSource(),
                                                                "  " + zoneId
                                                                        + " | " + zone.getCurrentHp()
                                                                        + "/" + zone.getMaxHp() + " HP"
                                                                        + " | " + status
                                                                        + " | " + zone.getRealShipBlocks().size()
                                                                        + " real blocks, "
                                                                        + zone.getModelBlocks().size() + " model blocks",
                                                                Formatting.WHITE);
                                                    });
                                                    return zones.size();
                                                }))))

                        // Power, heading, speed, torpedoes, warp, position, navstatus (home)
                        // kept from original

                        .then(CommandManager.literal("power")
                                .then(CommandManager.literal("home")
                                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 10000))
                                                .executes(ctx -> {
                                                    if (SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE == null
                                                            || !SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.hasHomeShip()) {
                                                        feedback(ctx.getSource(), "No home ship registered.", Formatting.RED);
                                                        return 0;
                                                    }
                                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                    var ship = SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.getHomeShipState();
                                                    ship.setManualPowerOverride(true);
                                                    ship.setPowerOutput(amount);
                                                    ship.setPowerBudget(amount);
                                                    ship.setShieldsPower((int)(amount * 0.35));
                                                    ship.setWeaponsPower((int)(amount * 0.30));
                                                    ship.setEnginesPower((int)(amount * 0.25));
                                                    ship.setSensorsPower(amount - ship.getShieldsPower()
                                                            - ship.getWeaponsPower() - ship.getEnginesPower());
                                                    feedback(ctx.getSource(),
                                                            "Home ship power set to " + amount + ".",
                                                            Formatting.GREEN);
                                                    return 1;
                                                })))
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 10000))
                                                        .executes(ctx -> {
                                                            String eid = StringArgumentType.getString(ctx, "encounterId");
                                                            String sid = StringArgumentType.getString(ctx, "shipId");
                                                            int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                                            es.getEncounter(eid)
                                                                    .flatMap(e -> e.getShip(sid))
                                                                    .ifPresentOrElse(ship -> {
                                                                        ship.setManualPowerOverride(true);
                                                                        ship.setPowerOutput(amount);
                                                                        ship.setPowerBudget(amount);
                                                                        ship.setShieldsPower((int)(amount * 0.35));
                                                                        ship.setWeaponsPower((int)(amount * 0.30));
                                                                        ship.setEnginesPower((int)(amount * 0.25));
                                                                        ship.setSensorsPower(amount - ship.getShieldsPower()
                                                                                - ship.getWeaponsPower() - ship.getEnginesPower());
                                                                        feedback(ctx.getSource(),
                                                                                "Power set to " + amount + " on "
                                                                                        + ship.getRegistryName()
                                                                                        + ". Shields=" + ship.getShieldsPower()
                                                                                        + " Wpn=" + ship.getWeaponsPower()
                                                                                        + " Eng=" + ship.getEnginesPower(),
                                                                                Formatting.GREEN);
                                                                    }, () -> feedback(ctx.getSource(),
                                                                            "Ship not found.", Formatting.RED));
                                                            return 1;
                                                        })))))

                        .then(CommandManager.literal("heading")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .then(CommandManager.argument("degrees", FloatArgumentType.floatArg(0f, 359f))
                                                        .executes(ctx -> {
                                                            String eid = StringArgumentType.getString(ctx, "encounterId");
                                                            String sid = StringArgumentType.getString(ctx, "shipId");
                                                            float deg  = FloatArgumentType.getFloat(ctx, "degrees");
                                                            es.getEncounter(eid)
                                                                    .flatMap(e -> e.getShip(sid))
                                                                    .ifPresentOrElse(ship -> {
                                                                        ship.setTargetHeading(deg);
                                                                        feedback(ctx.getSource(),
                                                                                ship.getRegistryName() + " heading → " + deg + "°",
                                                                                Formatting.GREEN);
                                                                    }, () -> feedback(ctx.getSource(),
                                                                            "Ship not found.", Formatting.RED));
                                                            return 1;
                                                        })))))

                        .then(CommandManager.literal("speed")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .then(CommandManager.argument("value", FloatArgumentType.floatArg(0f, 10f))
                                                        .executes(ctx -> {
                                                            String eid = StringArgumentType.getString(ctx, "encounterId");
                                                            String sid = StringArgumentType.getString(ctx, "shipId");
                                                            float spd  = FloatArgumentType.getFloat(ctx, "value");
                                                            es.getEncounter(eid)
                                                                    .flatMap(e -> e.getShip(sid))
                                                                    .ifPresentOrElse(ship -> {
                                                                        ship.setTargetSpeed(spd);
                                                                        feedback(ctx.getSource(),
                                                                                ship.getRegistryName() + " speed → " + spd,
                                                                                Formatting.GREEN);
                                                                    }, () -> feedback(ctx.getSource(),
                                                                            "Ship not found.", Formatting.RED));
                                                            return 1;
                                                        })))))

                        .then(CommandManager.literal("torpedoes")
                                .then(CommandManager.argument("encounterId", StringArgumentType.word())
                                        .suggests(SUGGEST_ENCOUNTERS)
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .suggests(SUGGEST_ENCOUNTER_SHIPS)
                                                .then(CommandManager.argument("count", IntegerArgumentType.integer(0, 100))
                                                        .executes(ctx -> {
                                                            String eid = StringArgumentType.getString(ctx, "encounterId");
                                                            String sid = StringArgumentType.getString(ctx, "shipId");
                                                            int count  = IntegerArgumentType.getInteger(ctx, "count");
                                                            es.getEncounter(eid)
                                                                    .flatMap(e -> e.getShip(sid))
                                                                    .ifPresentOrElse(ship -> {
                                                                        ship.setTorpedoCount(count);
                                                                        feedback(ctx.getSource(),
                                                                                ship.getRegistryName() + " torpedoes → " + count,
                                                                                Formatting.GREEN);
                                                                    }, () -> feedback(ctx.getSource(),
                                                                            "Ship not found.", Formatting.RED));
                                                            return 1;
                                                        })))))

                        .then(CommandManager.literal("warp")
                                .then(CommandManager.argument("factor", IntegerArgumentType.integer(0, 9))
                                        .executes(ctx -> {
                                            if (SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE == null
                                                    || !SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.hasHomeShip()) {
                                                feedback(ctx.getSource(), "No home ship registered.", Formatting.RED);
                                                return 0;
                                            }
                                            int factor = IntegerArgumentType.getInteger(ctx, "factor");
                                            SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.applyWarpSpeed(factor);
                                            feedback(ctx.getSource(),
                                                    factor == 0
                                                            ? "Home ship dropped to sublight."
                                                            : "Home ship warp speed set to warp " + factor + ".",
                                                    Formatting.GREEN);
                                            return 1;
                                        })))

                        .then(CommandManager.literal("position")
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.argument("x", DoubleArgumentType.doubleArg())
                                                .then(CommandManager.argument("z", DoubleArgumentType.doubleArg())
                                                        .executes(ctx -> {
                                                            if (SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE == null
                                                                    || !SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.hasHomeShip()) {
                                                                feedback(ctx.getSource(), "No home ship registered.", Formatting.RED);
                                                                return 0;
                                                            }
                                                            double x = DoubleArgumentType.getDouble(ctx, "x");
                                                            double z = DoubleArgumentType.getDouble(ctx, "z");
                                                            var ship = SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.getHomeShipState();
                                                            ship.setPosX(x);
                                                            ship.setPosZ(z);
                                                            ship.setTargetHeading(ship.getHeading());
                                                            ship.setTargetSpeed(0);
                                                            SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.savePosition();
                                                            feedback(ctx.getSource(),
                                                                    String.format("Home ship position set to (%.1f, %.1f).", x, z),
                                                                    Formatting.GREEN);
                                                            return 1;
                                                        })))))

                        // Home ship navstatus — kept for passive navigation context
                        .then(CommandManager.literal("homenavstatus")
                                .executes(ctx -> {
                                    if (SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE == null
                                            || !SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.hasHomeShip()) {
                                        feedback(ctx.getSource(), "No home ship registered.", Formatting.RED);
                                        return 0;
                                    }
                                    var ship = SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.getHomeShipState();
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                    "§b── Home Ship Navigation ──\n"
                                                            + "§7Position: §f" + String.format("%.1f, %.1f",
                                                            ship.getPosX(), ship.getPosZ()) + "\n"
                                                            + "§7Heading:  §f" + (int)ship.getHeading()
                                                            + "° §8(target: " + (int)ship.getTargetHeading() + "°)\n"
                                                            + "§7Speed:    §f" + String.format("%.1f", ship.getSpeed())
                                                            + " §8(target: " + String.format("%.1f", ship.getTargetSpeed()) + ")\n"
                                                            + "§7Warp:     §f" + ship.getWarpSpeed()
                                                            + " §8(capable: " + ship.isWarpCapable() + ")")
                                            , false);
                                    return 1;
                                }))
                )
        );

        // ── /admin ship + hardpoint + shipyard ────────────────────────────────

        dispatcher.register(CommandManager.literal("admin")
                .requires(src -> src.hasPermissionLevel(4))

                .then(CommandManager.literal("shipyard")
                        .then(CommandManager.literal("set")
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                    if (player == null) return 0;
                                    String worldKey = player.getWorld().getRegistryKey().getValue().toString();
                                    es.setShipyard(worldKey, player.getX(), player.getY(), player.getZ());
                                    feedback(ctx.getSource(),
                                            "Shipyard spawn set at current position.", Formatting.GREEN);
                                    return 1;
                                })))

                .then(CommandManager.literal("ship")

                        .then(CommandManager.literal("register")
                                .then(CommandManager.argument("shipId", StringArgumentType.word())
                                        .then(CommandManager.argument("name", StringArgumentType.string())
                                                .then(CommandManager.argument("class", StringArgumentType.word())
                                                        .suggests(SUGGEST_CLASSES)
                                                        .then(CommandManager.argument("faction", StringArgumentType.word())
                                                                .suggests(SUGGEST_FACTIONS)
                                                                .executes(ctx -> {
                                                                    String result = es.registerShip(
                                                                            StringArgumentType.getString(ctx, "shipId"),
                                                                            StringArgumentType.getString(ctx, "name"),
                                                                            StringArgumentType.getString(ctx, "class"),
                                                                            StringArgumentType.getString(ctx, "faction"));
                                                                    feedback(ctx.getSource(), result, Formatting.GREEN);
                                                                    return 1;
                                                                }))))))

                        .then(CommandManager.literal("unregister")
                                .then(CommandManager.argument("shipId", StringArgumentType.word())
                                        .suggests(SUGGEST_REGISTRY_SHIPS)
                                        .executes(ctx -> {
                                            String result = es.unregisterShip(
                                                    StringArgumentType.getString(ctx, "shipId"));
                                            feedback(ctx.getSource(), result, Formatting.YELLOW);
                                            return 1;
                                        })))

                        .then(CommandManager.literal("list")
                                .executes(ctx -> {
                                    var registry = es.getShipRegistry();
                                    if (registry.isEmpty()) {
                                        feedback(ctx.getSource(), "No ships registered.", Formatting.GRAY);
                                        return 0;
                                    }
                                    feedback(ctx.getSource(), "── Ship Registry ──", Formatting.AQUA);
                                    registry.values().forEach(e -> feedback(ctx.getSource(),
                                            "  " + e.getShipId()
                                                    + " | " + e.getRegistryName()
                                                    + " | " + e.getShipClass()
                                                    + " | " + e.getFaction()
                                                    + (e.isHomeShip() ? " §b[HOME]" : ""),
                                            Formatting.WHITE));
                                    return registry.size();
                                }))

                        .then(CommandManager.literal("sethomeship")
                                .then(CommandManager.argument("shipId", StringArgumentType.word())
                                        .suggests(SUGGEST_REGISTRY_SHIPS)
                                        .executes(ctx -> {
                                            String shipId  = StringArgumentType.getString(ctx, "shipId");
                                            var registry   = es.getShipRegistry();
                                            if (!registry.containsKey(shipId)) {
                                                feedback(ctx.getSource(), "Unknown ship: " + shipId, Formatting.RED);
                                                return 0;
                                            }
                                            registry.values().forEach(e -> e.setHomeShip(false));
                                            ShipRegistryEntry homeEntry = registry.get(shipId);
                                            homeEntry.setHomeShip(true);
                                            registry.values().forEach(e -> es.saveShipRegistryEntry(e));
                                            if (SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE != null) {
                                                SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.loadHomeShip();
                                            }
                                            feedback(ctx.getSource(),
                                                    "Home ship set to: " + homeEntry.getRegistryName()
                                                            + ". Passive movement active.",
                                                    Formatting.GREEN);
                                            return 1;
                                        }))))

                .then(CommandManager.literal("hardpoint")

                        .then(CommandManager.literal("register")
                                .then(CommandManager.argument("shipId", StringArgumentType.word())
                                        .suggests(SUGGEST_REGISTRY_SHIPS)
                                        .then(CommandManager.argument("arc", StringArgumentType.word())
                                                .suggests(SUGGEST_ARCS)
                                                .then(CommandManager.argument("type", StringArgumentType.word())
                                                        .suggests(SUGGEST_WEAPON_TYPES)
                                                        .executes(ctx -> {
                                                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                                                            if (player == null) return 0;
                                                            BlockPos pos   = player.getBlockPos();
                                                            String shipId  = StringArgumentType.getString(ctx, "shipId");
                                                            String arcStr  = StringArgumentType.getString(ctx, "arc").toUpperCase();
                                                            String typeStr = StringArgumentType.getString(ctx, "type").toUpperCase();
                                                            try {
                                                                HardpointEntry.Arc arc       = HardpointEntry.Arc.valueOf(arcStr);
                                                                HardpointEntry.WeaponType wt = HardpointEntry.WeaponType.valueOf(typeStr);
                                                                String result = es.registerHardpoint(shipId, pos, arc, wt);
                                                                feedback(ctx.getSource(), result, Formatting.GREEN);
                                                            } catch (IllegalArgumentException e) {
                                                                feedback(ctx.getSource(),
                                                                        "Invalid arc or type. Arc: FORE/AFT/PORT/STARBOARD  Type: PHASER_ARRAY/TORPEDO_TUBE",
                                                                        Formatting.RED);
                                                            }
                                                            return 1;
                                                        })))))

                        .then(CommandManager.literal("list")
                                .then(CommandManager.argument("shipId", StringArgumentType.word())
                                        .suggests(SUGGEST_REGISTRY_SHIPS)
                                        .executes(ctx -> {
                                            String shipId = StringArgumentType.getString(ctx, "shipId");
                                            var hps = es.getHardpoints(shipId);
                                            if (hps.isEmpty()) {
                                                feedback(ctx.getSource(),
                                                        "No hardpoints on " + shipId, Formatting.GRAY);
                                                return 0;
                                            }
                                            feedback(ctx.getSource(),
                                                    "── Hardpoints on " + shipId + " ──", Formatting.AQUA);
                                            hps.forEach(h -> feedback(ctx.getSource(),
                                                    "  " + h.getHardpointId()
                                                            + " | " + h.getWeaponType()
                                                            + " | " + h.getArc()
                                                            + " | HP: " + h.getHealth(),
                                                    Formatting.WHITE));
                                            return hps.size();
                                        })))

                        .then(CommandManager.literal("zone")
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.argument("shipId", StringArgumentType.word())
                                                .suggests(SUGGEST_REGISTRY_SHIPS)
                                                .then(CommandManager.argument("zoneId", StringArgumentType.word())
                                                        .suggests(SUGGEST_ZONE_IDS)
                                                        .then(CommandManager.argument("mode", StringArgumentType.word())
                                                                .suggests((ctx, builder) -> {
                                                                    builder.suggest("MODEL");
                                                                    builder.suggest("REAL");
                                                                    return builder.buildFuture();
                                                                })
                                                                .executes(ctx -> {
                                                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                                                    if (player == null) return 0;
                                                                    String shipId = StringArgumentType.getString(ctx, "shipId");
                                                                    String zoneId = StringArgumentType.getString(ctx, "zoneId");
                                                                    String mode   = StringArgumentType.getString(ctx, "mode").toUpperCase();
                                                                    if (!mode.equals("MODEL") && !mode.equals("REAL")) {
                                                                        feedback(ctx.getSource(),
                                                                                "Mode must be MODEL or REAL.", Formatting.RED);
                                                                        return 0;
                                                                    }
                                                                    net.shard.seconddawnrp.tactical.damage
                                                                            .DamageZoneToolItem.setContext(
                                                                                    player, shipId, zoneId, mode);
                                                                    return 1;
                                                                }))))))


                        .then(CommandManager.literal("clear")
                                .then(CommandManager.argument("shipId", StringArgumentType.word())
                                        .suggests(SUGGEST_REGISTRY_SHIPS)
                                        .then(CommandManager.argument("zoneId", StringArgumentType.word())
                                                .suggests(SUGGEST_ZONE_IDS)
                                                .then(CommandManager.argument("target", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> {
                                                            builder.suggest("MODEL");
                                                            builder.suggest("REAL");
                                                            builder.suggest("ALL");
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(ctx -> {
                                                            String shipId = StringArgumentType.getString(ctx, "shipId");
                                                            String zoneId = StringArgumentType.getString(ctx, "zoneId");
                                                            String target = StringArgumentType.getString(ctx, "target").toUpperCase();

                                                            if (SecondDawnRP.TACTICAL_SERVICE == null) {
                                                                feedback(ctx.getSource(), "Tactical service not available.", Formatting.RED);
                                                                return 0;
                                                            }

                                                            var zoneOpt = SecondDawnRP.TACTICAL_SERVICE
                                                                    .getHullDamageService().getZone(shipId, zoneId);
                                                            if (zoneOpt.isEmpty()) {
                                                                feedback(ctx.getSource(),
                                                                        "Zone '" + zoneId + "' not found on ship '" + shipId + "'.",
                                                                        Formatting.RED);
                                                                return 0;
                                                            }

                                                            net.shard.seconddawnrp.tactical.data.DamageZone zone = zoneOpt.get();
                                                            int cleared = 0;

                                                            if ("MODEL".equals(target) || "ALL".equals(target)) {
                                                                cleared += zone.getModelBlocks().size();
                                                                zone.clearModelBlocks();
                                                            }
                                                            if ("REAL".equals(target) || "ALL".equals(target)) {
                                                                cleared += zone.getRealShipBlocks().size();
                                                                zone.clearRealShipBlocks();
                                                            }

                                                            feedback(ctx.getSource(),
                                                                    "Cleared " + cleared + " block(s) from "
                                                                            + target + " on zone " + zoneId + ".",
                                                                    Formatting.YELLOW);
                                                            return cleared;
                                                        })))))

                        .then(CommandManager.literal("remove")
                                .then(CommandManager.argument("shipId", StringArgumentType.word())
                                        .suggests(SUGGEST_REGISTRY_SHIPS)
                                        .then(CommandManager.argument("zoneId", StringArgumentType.word())
                                                .suggests(SUGGEST_ZONE_IDS)
                                                .executes(ctx -> {
                                                    String shipId = StringArgumentType.getString(ctx, "shipId");
                                                    String zoneId = StringArgumentType.getString(ctx, "zoneId");

                                                    if (SecondDawnRP.TACTICAL_SERVICE == null) {
                                                        feedback(ctx.getSource(), "Tactical service not available.", Formatting.RED);
                                                        return 0;
                                                    }

                                                    boolean removed = SecondDawnRP.TACTICAL_SERVICE
                                                            .getHullDamageService().removeZone(shipId, zoneId);

                                                    feedback(ctx.getSource(),
                                                            removed
                                                                    ? "Zone " + zoneId + " removed from ship " + shipId + "."
                                                                    : "Zone '" + zoneId + "' not found on ship '" + shipId + "'.",
                                                            removed ? Formatting.YELLOW : Formatting.RED);
                                                    return removed ? 1 : 0;
                                                }))))

                        .then(CommandManager.literal("locate")
                                .then(CommandManager.argument("shipId", StringArgumentType.word())
                                        .suggests(SUGGEST_REGISTRY_SHIPS)
                                        .then(CommandManager.argument("zoneId", StringArgumentType.word())
                                                .suggests(SUGGEST_ZONE_IDS)
                                                .executes(ctx -> {
                                                    String shipId = StringArgumentType.getString(ctx, "shipId");
                                                    String zoneId = StringArgumentType.getString(ctx, "zoneId");
                                                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                                                    if (player == null) return 0;
                                                    if (SecondDawnRP.TACTICAL_SERVICE == null) return 0;

                                                    var zoneOpt = SecondDawnRP.TACTICAL_SERVICE
                                                            .getHullDamageService().getZone(shipId, zoneId);
                                                    if (zoneOpt.isEmpty()) {
                                                        feedback(ctx.getSource(),
                                                                "Zone '" + zoneId + "' not found on ship '" + shipId + "'.",
                                                                Formatting.RED);
                                                        return 0;
                                                    }

                                                    var zone = zoneOpt.get();
                                                    int count = 0;
                                                    for (net.minecraft.util.math.BlockPos pos : zone.getModelBlocks()) {
                                                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                                                                new net.shard.seconddawnrp.tactical.network.LocateZoneBlockS2CPacket(
                                                                        zoneId, "MODEL",
                                                                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                                                        count++;
                                                    }
                                                    for (net.minecraft.util.math.BlockPos pos : zone.getRealShipBlocks()) {
                                                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                                                                new net.shard.seconddawnrp.tactical.network.LocateZoneBlockS2CPacket(
                                                                        zoneId, "REAL",
                                                                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                                                        count++;
                                                    }

                                                    feedback(ctx.getSource(),
                                                            "Located " + count + " block(s) for zone " + zoneId + ".",
                                                            Formatting.AQUA);
                                                    return count;
                                                }))))
                )
        );
    }

    private static void feedback(ServerCommandSource src, String msg, Formatting color) {
        src.sendFeedback(() -> Text.literal(msg).formatted(color), false);
    }
}