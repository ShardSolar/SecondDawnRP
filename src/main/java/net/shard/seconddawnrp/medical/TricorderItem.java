package net.shard.seconddawnrp.medical;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.character.LongTermInjury;
import net.shard.seconddawnrp.playerdata.PlayerProfile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class TricorderItem extends Item {

    public TricorderItem(Settings settings) {
        super(settings);
    }

    @Override
    public net.minecraft.util.ActionResult useOnEntity(ItemStack stack, PlayerEntity user,
                                                       LivingEntity entity, Hand hand) {
        if (user.getWorld().isClient()) return net.minecraft.util.ActionResult.PASS;
        if (!(user instanceof ServerPlayerEntity scanner)) return net.minecraft.util.ActionResult.PASS;
        if (hand != Hand.MAIN_HAND) return net.minecraft.util.ActionResult.PASS;
        if (!(entity instanceof ServerPlayerEntity target)) return net.minecraft.util.ActionResult.PASS;

        sendScanResult(scanner, target, scanner.getUuid().equals(target.getUuid()));
        return net.minecraft.util.ActionResult.SUCCESS;
    }

    @Override
    public net.minecraft.util.TypedActionResult<ItemStack> use(World world,
                                                               PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity sp) {
            sendScanResult(sp, sp, true);
        }
        return net.minecraft.util.TypedActionResult.success(user.getStackInHand(hand));
    }

    private void sendScanResult(ServerPlayerEntity scanner,
                                ServerPlayerEntity target,
                                boolean isSelf) {

        PlayerProfile scannerProfile =
                SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(scanner.getUuid());
        boolean isMedical = SecondDawnRP.MEDICAL_SERVICE.isMedicalOfficer(scannerProfile);

        PlayerProfile targetProfile =
                SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(target.getUuid());

        String targetName = targetProfile != null
                ? targetProfile.getDisplayName()
                : target.getName().getString();

        String targetSpecies = targetProfile != null && targetProfile.getSpecies() != null
                ? targetProfile.getSpecies()
                : "Unknown";

        List<MedicalService.ConditionDetail> details =
                SecondDawnRP.MEDICAL_SERVICE.getActiveConditions(target.getUuid());

        // ── Header ─────────────────────────────────────────────
        scanner.sendMessage(Text.literal(
                        "────────── Tricorder Scan ──────────")
                .formatted(Formatting.DARK_AQUA), false);

        scanner.sendMessage(Text.literal(
                        "  Subject: " + targetName + "  §7(" + targetSpecies + ")")
                .formatted(Formatting.WHITE), false);

        // ── Live Status Effects (NEW) ───────────────────────────
        Collection<StatusEffectInstance> effects = target.getStatusEffects();

        if (!effects.isEmpty()) {
            scanner.sendMessage(Text.literal("  §bActive Effects:"), false);

            for (StatusEffectInstance effect : effects) {
                String name = effect.getEffectType().value().getTranslationKey();
                name = name.replace("effect.minecraft.", "");

                int amp = effect.getAmplifier() + 1;
                int seconds = effect.getDuration() / 20;

                scanner.sendMessage(Text.literal(
                                "    §7• " + name + " " + amp + " §8(" + formatSeconds(seconds) + ")"),
                        false);
            }
        }

        // ── Conditions ─────────────────────────────────────────
        if (details.isEmpty()) {
            scanner.sendMessage(Text.literal(
                    "  §aNo active medical conditions detected."), false);
        } else {
            scanner.sendMessage(Text.literal(
                    "  §cConditions detected: " + details.size()), false);

            for (MedicalService.ConditionDetail detail : details) {
                LongTermInjury condition = detail.condition();
                Optional<MedicalConditionTemplate> templateOpt =
                        Optional.ofNullable(detail.template());

                String condName = detail.displayName();
                String colour   = detail.severityColour();
                String severity = templateOpt.map(t -> t.severity().label()).orElse("UNKNOWN");

                scanner.sendMessage(Text.literal(
                        "  " + colour + "● " + condName + " §8[" + severity + "]"), false);

                // ── Detailed view for medical officers ───────────
                if (isMedical && !isSelf && templateOpt.isPresent()) {
                    MedicalConditionTemplate template = templateOpt.get();

                    for (MedicalConditionTemplate.TreatmentStep step : template.treatmentPlan()) {
                        boolean done = detail.completedSteps().contains(step.stepKey());

                        String icon = done ? "§a✔" : "§c✗";
                        String surg = step.requiresSurgery() ? " §d[SURGEON]" : "";

                        if (done) {
                            scanner.sendMessage(Text.literal(
                                    "    " + icon + " §7" + step.label() + surg), false);
                            continue;
                        }

                        // ── Item requirement ───────────────────────
                        String itemTag = " §8— " + step.item().replace("minecraft:", "")
                                + "×" + step.quantity();

                        // ── Timing logic (UPGRADED) ───────────────
                        String timingTag = "";

                        if (step.hasTiming()) {
                            MedicalService.TimingInfo info =
                                    SecondDawnRP.MEDICAL_SERVICE.getStepTimingInfo(
                                            condition, template, step);

                            timingTag = switch (info.state()) {
                                case WAITING ->
                                        " §e⏳ Too early — " + formatSeconds(info.secondsUntilOpen());

                                case OPEN -> {
                                    if (info.secondsUntilExpiry() > 0) {
                                        if (info.secondsUntilExpiry() < 30) {
                                            yield " §c⚠ Closing — "
                                                    + formatSeconds(info.secondsUntilExpiry());
                                        }
                                        yield " §a✔ Open — "
                                                + formatSeconds(info.secondsUntilExpiry());
                                    }
                                    yield " §a✔ Open";
                                }

                                case EXPIRED ->
                                        " §c✗ FAILED — reset required";

                                case NONE -> "";
                            };
                        }

                        scanner.sendMessage(Text.literal(
                                "    " + icon + " §7" + step.label()
                                        + surg + itemTag + timingTag), false);
                    }

                    if (detail.isReadyToResolve()) {
                        scanner.sendMessage(Text.literal(
                                "    §a✔ All steps complete — ready to resolve."), false);
                    }
                }
            }
        }

        // ── Footer ─────────────────────────────────────────────
        scanner.sendMessage(Text.literal(
                        "───────────────────────────────")
                .formatted(Formatting.DARK_AQUA), false);
    }

    private static String formatSeconds(long totalSeconds) {
        if (totalSeconds <= 0) return "0s";
        long min = totalSeconds / 60;
        long sec = totalSeconds % 60;
        return min > 0 ? min + "m " + sec + "s" : sec + "s";
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                              List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Medical Tricorder").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Right-click player: scan conditions")
                .formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Right-click air: self-scan")
                .formatted(Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Shows live effects + treatment data")
                .formatted(Formatting.DARK_GRAY));
    }
}