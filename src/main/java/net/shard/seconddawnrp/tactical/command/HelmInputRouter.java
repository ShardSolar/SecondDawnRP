package net.shard.seconddawnrp.tactical.command;

import net.minecraft.server.network.ServerPlayerEntity;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tactical.data.EncounterState;
import net.shard.seconddawnrp.tactical.network.TacticalNetworking.HelmInputPayload;

/**
 * Routes HelmInputPayload to either the passive movement handler or the
 * active combat tick handler — no client change required.
 */
public class HelmInputRouter {

    private HelmInputRouter() {}

    public static void route(HelmInputPayload payload, ServerPlayerEntity player) {
        if (SecondDawnRP.TACTICAL_SERVICE == null) return;

        boolean encounterActive = SecondDawnRP.TACTICAL_SERVICE
                .getEncounterService()
                .getAllEncounters()
                .stream()
                .anyMatch(e -> e.getStatus() == EncounterState.Status.ACTIVE
                        || e.getStatus() == EncounterState.Status.PAUSED);

        if (encounterActive) {
            // Combat path — existing behaviour
            SecondDawnRP.TACTICAL_SERVICE.applyHelmInput(
                    payload.encounterId(), payload.shipId(),
                    payload.targetHeading(), payload.targetSpeed());
        } else {
            // Passive path — set target on home ship
            if (SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE == null) return;
            if (!SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.hasHomeShip()) {
                player.sendMessage(net.minecraft.text.Text.literal(
                        "§c[Helm] No home ship registered. Contact an admin."), false);
                return;
            }
            SecondDawnRP.PASSIVE_SHIP_MOVEMENT_SERVICE.applyHelmInput(
                    payload.targetHeading(), payload.targetSpeed());
            player.sendMessage(net.minecraft.text.Text.literal(
                    "§b[Helm] Course set — HDG "
                            + (int) payload.targetHeading() + "° SPD "
                            + String.format("%.1f", payload.targetSpeed())
                            + " — takes effect on next navigation tick."), false);
        }
    }
}