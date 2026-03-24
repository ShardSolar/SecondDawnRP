package net.shard.seconddawnrp.gmevent.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.gmevent.data.*;
import net.shard.seconddawnrp.gmevent.network.SaveEnvConfigC2SPacket;

import java.util.*;

/**
 * Config GUI for Environmental Effect Blocks.
 * Dark navy + green accent theme.
 *
 * <p>Layout:
 * <pre>
 *  ┌─ Title bar ─────────────────────────────────────────────┐
 *  │ ID label                                                 │
 *  ├─ Vanilla effects list (scrollable, toggleable) ──────────┤
 *  │  [✓] Slowness  amp[-][+] dur[-][+]                      │
 *  │  [ ] Weakness  amp[-][+] dur[-][+]                       │
 *  ├─ Medical condition list (scrollable, single select) ──────┤
 *  │  [✓] Radiation Sickness (Moderate)                       │
 *  │  [ ] Neural Toxin (Severe)                               │
 *  ├─ Mode toggles ───────────────────────────────────────────┤
 *  │  [Linger: IMMEDIATE] [Fire: CONTINUOUS] [Vis: VISIBLE]   │
 *  │  [Radius -] [Radius +] radius=8      [ACTIVE/INACTIVE]   │
 *  ├─ Footer ─────────────────────────────────────────────────┤
 *  │  [Save]                                       [Close]    │
 *  └──────────────────────────────────────────────────────────┘
 * </pre>
 */
public class EnvEffectConfigScreen extends Screen {

    private static final int W   = 310;
    private static final int H   = 280;
    private static final int PAD = 7;
    private static final int ROW = 12;

    // Colour palette
    private static final int COL_BG      = 0xFF03091A;
    private static final int COL_HEADER  = 0xFF050B1A;
    private static final int COL_BORDER  = 0xFF1A7A20;
    private static final int COL_ACCENT  = 0xFF22AA28;
    private static final int COL_DIM     = 0xFF0A3A10;
    private static final int COL_DARK    = 0xFF02050F;
    private static final int COL_SEL     = 0xFF082808;
    private static final int COL_HOVER   = 0xFF0D400D;

    // ── Data ──────────────────────────────────────────────────────────────────
    private final String entryId;
    private final List<String> activeEffects;        // format: "id:amp:dur"
    private String medConditionId;
    private String medConditionSeverity;
    private int radiusBlocks;
    private LingerMode lingerMode;
    private int lingerDurationTicks;
    private EnvFireMode fireMode;
    private int onEntryCooldownTicks;
    private EnvVisibility visibility;
    private boolean active;

    // ── Registry (received from server) ───────────────────────────────────────
    private List<VanillaEffectDefinition> effectRegistry = new ArrayList<>();
    private List<MedicalConditionDefinition> conditionRegistry = new ArrayList<>();

    // ── Scroll state ──────────────────────────────────────────────────────────
    private int effectScrollOffset    = 0;
    private int conditionScrollOffset = 0;
    private static final int EFFECT_VISIBLE_ROWS    = 4;
    private static final int CONDITION_VISIBLE_ROWS = 3;

    private int ox, oy;

    // Button references that need dynamic labels
    private ButtonWidget lingerBtn, fireBtn, visBtn, activeBtn;

    public EnvEffectConfigScreen(
            String entryId, List<String> vanillaEffects, String medConditionId,
            String medConditionSeverity, int radiusBlocks, LingerMode lingerMode,
            int lingerDurationTicks, EnvFireMode fireMode, int onEntryCooldownTicks,
            EnvVisibility visibility, boolean active) {
        super(Text.literal("Env Effect Config"));
        this.entryId              = entryId;
        this.activeEffects        = new ArrayList<>(vanillaEffects);
        this.medConditionId       = medConditionId;
        this.medConditionSeverity = medConditionSeverity;
        this.radiusBlocks         = radiusBlocks;
        this.lingerMode           = lingerMode;
        this.lingerDurationTicks  = lingerDurationTicks;
        this.fireMode             = fireMode;
        this.onEntryCooldownTicks = onEntryCooldownTicks;
        this.visibility           = visibility;
        this.active               = active;
    }

    /** Called after construction with the registry data from the server packet. */
    public void setRegistry(List<VanillaEffectDefinition> effects,
                            List<MedicalConditionDefinition> conditions) {
        this.effectRegistry    = effects;
        this.conditionRegistry = conditions;
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}

    @Override
    protected void init() {
        ox = (width - W) / 2;
        oy = (height - H) / 2;

        // Effect scroll arrows
        addDrawableChild(ButtonWidget.builder(Text.literal("▲"),
                        b -> { if (effectScrollOffset > 0) effectScrollOffset--; })
                .dimensions(ox + W - PAD - 14, oy + 30, 14, 12).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▼"),
                        b -> { if (effectScrollOffset < Math.max(0, effectRegistry.size() - EFFECT_VISIBLE_ROWS))
                            effectScrollOffset++; })
                .dimensions(ox + W - PAD - 14, oy + 30 + ROW * EFFECT_VISIBLE_ROWS, 14, 12).build());

        // Condition scroll arrows
        int condY = oy + 30 + ROW * EFFECT_VISIBLE_ROWS + 22;
        addDrawableChild(ButtonWidget.builder(Text.literal("▲"),
                        b -> { if (conditionScrollOffset > 0) conditionScrollOffset--; })
                .dimensions(ox + W - PAD - 14, condY, 14, 12).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▼"),
                        b -> { if (conditionScrollOffset < Math.max(0, conditionRegistry.size() - CONDITION_VISIBLE_ROWS))
                            conditionScrollOffset++; })
                .dimensions(ox + W - PAD - 14, condY + ROW * CONDITION_VISIBLE_ROWS, 14, 12).build());

        // Mode toggles
        int toggleY = oy + 30 + ROW * (EFFECT_VISIBLE_ROWS + CONDITION_VISIBLE_ROWS) + 46;
        lingerBtn = addDrawableChild(ButtonWidget.builder(
                        Text.literal("Linger: " + lingerMode.name()),
                        b -> { lingerMode = nextLinger(); b.setMessage(Text.literal("Linger: " + lingerMode.name())); })
                .dimensions(ox + PAD, toggleY, 90, 13).build());

        fireBtn = addDrawableChild(ButtonWidget.builder(
                        Text.literal("Fire: " + fireMode.name()),
                        b -> { fireMode = nextFire(); b.setMessage(Text.literal("Fire: " + fireMode.name())); })
                .dimensions(ox + PAD + 94, toggleY, 90, 13).build());

        visBtn = addDrawableChild(ButtonWidget.builder(
                        Text.literal("Vis: " + visibility.name()),
                        b -> { visibility = nextVis(); b.setMessage(Text.literal("Vis: " + visibility.name())); })
                .dimensions(ox + PAD + 188, toggleY, 80, 13).build());

        // Radius row
        int radY = toggleY + 17;
        addDrawableChild(ButtonWidget.builder(Text.literal("R -"),
                        b -> { if (radiusBlocks > 1) radiusBlocks--; })
                .dimensions(ox + PAD, radY, 28, 13).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("R +"),
                        b -> { if (radiusBlocks < 64) radiusBlocks++; })
                .dimensions(ox + PAD + 32, radY, 28, 13).build());

        activeBtn = addDrawableChild(ButtonWidget.builder(
                        Text.literal(active ? "ACTIVE" : "INACTIVE"),
                        b -> { active = !active; b.setMessage(Text.literal(active ? "ACTIVE" : "INACTIVE")); })
                .dimensions(ox + W - 82 - PAD, radY, 82, 13).build());

        // Footer
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> save())
                .dimensions(ox + PAD, oy + H - 20, 80, 13).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(ox + W - 86 - PAD, oy + H - 20, 80, 13).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ox = (width - W) / 2;
        oy = (height - H) / 2;
        renderBackground(ctx, mx, my, delta);
        drawPanel(ctx, mx, my);
        super.render(ctx, mx, my, delta);
    }

    private void drawPanel(DrawContext ctx, int mx, int my) {
        int x = ox, y = oy;

        // Background + border
        fill(ctx, x, y, x+W, y+H, COL_BG);
        hline(ctx, x, y,     x+W, COL_BORDER);
        hline(ctx, x, y+H-1, x+W, COL_BORDER);
        vline(ctx, x,     y, y+H, COL_BORDER);
        vline(ctx, x+W-1, y, y+H, COL_BORDER);
        hline(ctx, x+1, y+1,     x+W-1, COL_DIM);
        hline(ctx, x+1, y+H-2,   x+W-1, COL_DIM);

        // Title bar
        fill(ctx, x+2, y+2, x+W-2, y+14, COL_HEADER);
        hline(ctx, x+2, y+14, x+W-2, COL_BORDER);
        ctx.drawText(textRenderer, Text.literal("Environmental Effect Config")
                .formatted(Formatting.GREEN), x+PAD, y+4, 0xFFFFFFFF, false);

        // ID
        ctx.drawText(textRenderer, Text.literal("ID: " + entryId).formatted(Formatting.DARK_GRAY),
                x+PAD, y+17, 0xFF555555, false);

        // ── Vanilla effects section ───────────────────────────────────────────
        int ey = y + 27;
        hline(ctx, x+2, ey, x+W-2, COL_DIM);
        ctx.drawText(textRenderer, Text.literal("Effects  (click to toggle)")
                .formatted(Formatting.GREEN), x+PAD, ey+2, 0xFFCCCCCC, false);
        ey += 13;

        int listW = W - PAD*2 - 16; // leave room for scroll arrow
        for (int i = 0; i < EFFECT_VISIBLE_ROWS; i++) {
            int idx = i + effectScrollOffset;
            if (idx >= effectRegistry.size()) break;
            VanillaEffectDefinition def = effectRegistry.get(idx);

            // Find current amplitude/duration for this effect (if active)
            int[] params = findActiveParams(def.getEffectId());
            boolean isActive = params != null;
            int amp = isActive ? params[0] : def.getDefaultAmplitude();
            int dur = isActive ? params[1] : def.getDefaultDurationTicks();

            int rowY = ey + i * ROW;
            boolean hover = mx >= x+PAD && mx < x+PAD+listW
                    && my >= rowY && my < rowY + ROW - 1;

            fill(ctx, x+PAD, rowY, x+PAD+listW, rowY+ROW-1,
                    isActive ? COL_SEL : hover ? COL_HOVER : COL_DARK);

            // Checkbox indicator
            ctx.drawText(textRenderer,
                    Text.literal(isActive ? "■" : "□").formatted(isActive ? Formatting.GREEN : Formatting.DARK_GRAY),
                    x+PAD+2, rowY+2, 0xFFFFFFFF, false);

            // Effect name
            ctx.drawText(textRenderer, Text.literal(def.getDisplayName())
                            .formatted(isActive ? Formatting.WHITE : Formatting.GRAY),
                    x+PAD+13, rowY+2, 0xFFFFFFFF, false);

            if (isActive) {
                // Amplitude controls
                int ctrlX = x+PAD + 110;
                ctx.drawText(textRenderer,
                        Text.literal("Amp:" + amp).formatted(Formatting.AQUA),
                        ctrlX, rowY+2, 0xFFFFFFFF, false);
                ctx.drawText(textRenderer, Text.literal("[-]").formatted(Formatting.GRAY),
                        ctrlX + 40, rowY+2, 0xFFFFFFFF, false);
                ctx.drawText(textRenderer, Text.literal("[+]").formatted(Formatting.GRAY),
                        ctrlX + 54, rowY+2, 0xFFFFFFFF, false);

                // Duration controls
                int durX = ctrlX + 74;
                String durLabel = dur >= 20 ? (dur/20) + "s" : dur + "t";
                ctx.drawText(textRenderer,
                        Text.literal("Dur:" + durLabel).formatted(Formatting.YELLOW),
                        durX, rowY+2, 0xFFFFFFFF, false);
                ctx.drawText(textRenderer, Text.literal("[-]").formatted(Formatting.GRAY),
                        durX + 40, rowY+2, 0xFFFFFFFF, false);
                ctx.drawText(textRenderer, Text.literal("[+]").formatted(Formatting.GRAY),
                        durX + 54, rowY+2, 0xFFFFFFFF, false);
            }
        }

        // ── Medical conditions section ─────────────────────────────────────────
        int cy = ey + EFFECT_VISIBLE_ROWS * ROW + 4;
        hline(ctx, x+2, cy, x+W-2, COL_DIM);
        ctx.drawText(textRenderer, Text.literal("Medical condition  (single select)")
                .formatted(Formatting.GREEN), x+PAD, cy+2, 0xFFCCCCCC, false);
        cy += 13;

        for (int i = 0; i < CONDITION_VISIBLE_ROWS; i++) {
            int idx = i + conditionScrollOffset;
            if (idx >= conditionRegistry.size()) break;
            MedicalConditionDefinition cond = conditionRegistry.get(idx);
            boolean isSel = cond.getConditionId().equals(medConditionId);

            int rowY = cy + i * ROW;
            boolean hover = mx >= x+PAD && mx < x+PAD+listW
                    && my >= rowY && my < rowY + ROW - 1;

            fill(ctx, x+PAD, rowY, x+PAD+listW, rowY+ROW-1,
                    isSel ? COL_SEL : hover ? COL_HOVER : COL_DARK);

            ctx.drawText(textRenderer,
                    Text.literal(isSel ? "■" : "□").formatted(isSel ? Formatting.GREEN : Formatting.DARK_GRAY),
                    x+PAD+2, rowY+2, 0xFFFFFFFF, false);
            ctx.drawText(textRenderer,
                    Text.literal(cond.getDisplayName()
                                    + " (" + (isSel ? medConditionSeverity : cond.getDefaultSeverity()) + ")")
                            .formatted(isSel ? Formatting.WHITE : Formatting.GRAY),
                    x+PAD+13, rowY+2, 0xFFFFFFFF, false);
        }

        // ── Mode labels ───────────────────────────────────────────────────────
        int toggleLabelY = cy + CONDITION_VISIBLE_ROWS * ROW + 7;
        hline(ctx, x+2, toggleLabelY, x+W-2, COL_DIM);

        // Radius label (below the R-/R+ buttons)
        int radLabelY = toggleLabelY + 32;
        ctx.drawText(textRenderer,
                Text.literal("Radius: " + radiusBlocks + " blocks").formatted(Formatting.GRAY),
                x+PAD+66, radLabelY, 0xFFAAAAAA, false);

        // Footer divider
        hline(ctx, x+2, y+H-24, x+W-2, COL_BORDER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = ox, y = oy;
        int listW = W - PAD*2 - 16;

        // ── Effect row clicks ─────────────────────────────────────────────────
        int ey = y + 27 + 13;
        for (int i = 0; i < EFFECT_VISIBLE_ROWS; i++) {
            int idx = i + effectScrollOffset;
            if (idx >= effectRegistry.size()) break;
            VanillaEffectDefinition def = effectRegistry.get(idx);
            int rowY = ey + i * ROW;
            if (mouseX < x+PAD || mouseX >= x+PAD+listW
                    || mouseY < rowY || mouseY >= rowY+ROW-1) continue;

            int[] params = findActiveParams(def.getEffectId());
            boolean isActive = params != null;

            // Check control buttons if active
            if (isActive) {
                int ctrlX = x+PAD + 110;
                int amp = params[0];
                int dur = params[1];
                // Amp -
                if (mouseX >= ctrlX+40 && mouseX < ctrlX+53) {
                    updateActiveEffect(def.getEffectId(), Math.max(0, amp-1), dur);
                    return true;
                }
                // Amp +
                if (mouseX >= ctrlX+54 && mouseX < ctrlX+67) {
                    updateActiveEffect(def.getEffectId(), Math.min(4, amp+1), dur);
                    return true;
                }
                int durX = ctrlX + 74;
                // Dur -
                if (mouseX >= durX+40 && mouseX < durX+53) {
                    updateActiveEffect(def.getEffectId(), amp, Math.max(20, dur-20));
                    return true;
                }
                // Dur +
                if (mouseX >= durX+54 && mouseX < durX+67) {
                    updateActiveEffect(def.getEffectId(), amp, Math.min(6000, dur+20));
                    return true;
                }
            }

            // Toggle the effect
            if (isActive) {
                removeActiveEffect(def.getEffectId());
            } else {
                addActiveEffect(def.getEffectId(), def.getDefaultAmplitude(), def.getDefaultDurationTicks());
            }
            return true;
        }

        // ── Condition row clicks ──────────────────────────────────────────────
        int condSectionY = ey + EFFECT_VISIBLE_ROWS * ROW + 4;
        int cy = condSectionY + 13;
        for (int i = 0; i < CONDITION_VISIBLE_ROWS; i++) {
            int idx = i + conditionScrollOffset;
            if (idx >= conditionRegistry.size()) break;
            MedicalConditionDefinition cond = conditionRegistry.get(idx);
            int rowY = cy + i * ROW;
            if (mouseX >= x+PAD && mouseX < x+PAD+listW
                    && mouseY >= rowY && mouseY < rowY+ROW-1) {
                if (cond.getConditionId().equals(medConditionId)) {
                    // Deselect
                    medConditionId = null;
                    medConditionSeverity = "Moderate";
                } else {
                    medConditionId = cond.getConditionId();
                    medConditionSeverity = cond.getDefaultSeverity();
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── Effect helpers ────────────────────────────────────────────────────────

    /** Returns [amplitude, durationTicks] if this effect is active, null otherwise. */
    private int[] findActiveParams(String effectId) {
        for (String s : activeEffects) {
            String[] parts = s.split(":");
            // Format: namespace:path:amp:dur
            if (parts.length >= 4) {
                String id = parts[0] + ":" + parts[1];
                if (id.equals(effectId)) {
                    try {
                        return new int[]{ Integer.parseInt(parts[2]), Integer.parseInt(parts[3]) };
                    } catch (NumberFormatException e) {
                        return new int[]{ 0, 200 };
                    }
                }
            }
        }
        return null;
    }

    private void addActiveEffect(String effectId, int amp, int dur) {
        activeEffects.add(effectId + ":" + amp + ":" + dur);
    }

    private void removeActiveEffect(String effectId) {
        activeEffects.removeIf(s -> {
            String[] parts = s.split(":");
            if (parts.length >= 2) return (parts[0] + ":" + parts[1]).equals(effectId);
            return false;
        });
    }

    private void updateActiveEffect(String effectId, int newAmp, int newDur) {
        removeActiveEffect(effectId);
        addActiveEffect(effectId, newAmp, newDur);
    }

    // ── Mode cycling ──────────────────────────────────────────────────────────
    private LingerMode nextLinger() {
        return switch (lingerMode) {
            case IMMEDIATE  -> LingerMode.LINGER;
            case LINGER     -> LingerMode.PERSISTENT;
            case PERSISTENT -> LingerMode.IMMEDIATE;
        };
    }
    private EnvFireMode nextFire() {
        return fireMode == EnvFireMode.CONTINUOUS ? EnvFireMode.ON_ENTRY : EnvFireMode.CONTINUOUS;
    }
    private EnvVisibility nextVis() {
        return visibility == EnvVisibility.VISIBLE ? EnvVisibility.HIDDEN : EnvVisibility.VISIBLE;
    }

    // ── Save ──────────────────────────────────────────────────────────────────
    private void save() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new SaveEnvConfigC2SPacket(
                        entryId, new ArrayList<>(activeEffects), medConditionId,
                        medConditionSeverity, radiusBlocks, lingerMode, lingerDurationTicks,
                        fireMode, onEntryCooldownTicks, visibility, active));
        close();
    }

    // ── Draw helpers ──────────────────────────────────────────────────────────
    private void fill(DrawContext ctx, int x0, int y0, int x1, int y1, int col) {
        if (x1>x0 && y1>y0) ctx.fill(x0, y0, x1, y1, col);
    }
    private void hline(DrawContext ctx, int x0, int y, int x1, int col) { ctx.fill(x0, y, x1, y+1, col); }
    private void vline(DrawContext ctx, int x, int y0, int y1, int col) { ctx.fill(x, y0, x+1, y1, col); }
}