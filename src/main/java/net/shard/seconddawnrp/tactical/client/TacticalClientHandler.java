package net.shard.seconddawnrp.tactical.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.shard.seconddawnrp.tactical.console.GmShipConsoleScreen;
import net.shard.seconddawnrp.tactical.console.TacticalScreen;
import net.shard.seconddawnrp.tactical.console.TacticalScreenHandler;
import net.shard.seconddawnrp.tactical.console.TacticalScreenHandler.StationFilter;
import net.shard.seconddawnrp.tactical.network.TacticalNetworking.*;

/**
 * Client-side handler for Tactical packets.
 * Registered in SecondDawnRPClient.onInitializeClient() only.
 */
public class TacticalClientHandler {

    public static void registerClientReceivers() {

        // Encounter update — push to whichever Tactical screen is open
        ClientPlayNetworking.registerGlobalReceiver(EncounterUpdatePayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.currentScreen instanceof TacticalScreen screen) {
                        screen.getScreenHandler().applyUpdate(payload);
                    } else if (mc.currentScreen instanceof GmShipConsoleScreen gmScreen) {
                        gmScreen.applyUpdate(payload);
                    }
                }));

        // Standby map update — anomalies + ship origin, pushed every ~10s and on GM activate/deactivate
        ClientPlayNetworking.registerGlobalReceiver(
                net.shard.seconddawnrp.tactical.network.TacticalNetworking.StandbyUpdatePayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.currentScreen instanceof TacticalScreen screen) {
                        screen.getScreenHandler().applyStandbyUpdate(
                                payload.shipOriginX(), payload.shipOriginZ(),
                                payload.anomalies());
                    }
                    // GM console doesn't show the standby map — no-op there
                }));

        // Stellar navigation update — home ship position, orbital zones, warp state
        // Pushed every 30s by ShipPositionService passive tick
        ClientPlayNetworking.registerGlobalReceiver(
                net.shard.seconddawnrp.tactical.network.TacticalNetworking.StellarNavUpdatePayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.currentScreen instanceof TacticalScreen screen) {
                        screen.getScreenHandler().applyStellarNavUpdate(payload);
                    }
                }));

        // Open packet — sent by TacticalConsoleBlock or TerminalDesignatorService
        ClientPlayNetworking.registerGlobalReceiver(OpenTacticalPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player == null) return;

                    // GM console only makes sense during an active encounter.
                    // In standby, even GMs get TacticalScreen (with full FULL filter access).
                    boolean hasActiveEncounter = payload.status() != null
                            && (payload.status().equals("ACTIVE")
                            || payload.status().equals("PAUSED"));

                    if (payload.gmMode() && hasActiveEncounter) {
                        // GM gets full ship control console during combat
                        mc.setScreen(new GmShipConsoleScreen(
                                payload.encounterId(),
                                payload.ships(),
                                payload.combatLog()));
                    } else {
                        // Standby, or non-GM player — TacticalScreen
                        StationFilter filter = parseStation(payload.station());

                        var handler = new TacticalScreenHandler(
                                0, mc.player.getInventory(), filter);
                        handler.applyUpdate(new EncounterUpdatePayload(
                                payload.encounterId(), payload.status(),
                                payload.ships(), payload.combatLog()));
                        handler.applyOpenData(payload);

                        mc.setScreen(new TacticalScreen(handler,
                                net.minecraft.text.Text.literal(stationTitle(filter))));
                    }
                }));
    }

    private static StationFilter parseStation(String station) {
        if (station == null) return StationFilter.FULL;
        return switch (station.toUpperCase()) {
            case "HELM"    -> StationFilter.HELM;
            case "WEAPONS" -> StationFilter.WEAPONS;
            case "SHIELDS" -> StationFilter.SHIELDS;
            case "SENSORS" -> StationFilter.SENSORS;
            default        -> StationFilter.FULL;
        };
    }

    private static String stationTitle(StationFilter filter) {
        return switch (filter) {
            case HELM    -> "Helm Console";
            case WEAPONS -> "Weapons Console";
            case SHIELDS -> "Shields Console";
            case SENSORS -> "Sensors Console";
            default      -> "Tactical Console";
        };
    }
}