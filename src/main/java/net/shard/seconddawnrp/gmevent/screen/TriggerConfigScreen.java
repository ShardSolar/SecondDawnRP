package net.shard.seconddawnrp.gmevent.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.gmevent.data.*;
import net.shard.seconddawnrp.gmevent.network.SaveTriggerConfigC2SPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Config GUI for Trigger Blocks. Yellow accent (alert/sensor theme).
 */
public class TriggerConfigScreen extends Screen {

    private static final int W = 280;
    private static final int H = 230;
    private static final int PAD = 7;

    private static final int COL_BG      = 0xFF03091A;
    private static final int COL_HEADER  = 0xFF050B1A;
    private static final int COL_BORDER  = 0xFF8A7A10;
    private static final int COL_ACCENT  = 0xFFCCAA18;
    private static final int COL_DIM     = 0xFF3A3010;
    private static final int COL_DARK    = 0xFF02050F;

    private final String entryId;
    private TriggerMode triggerMode;
    private TriggerFireMode fireMode;
    private int radiusBlocks;
    private int cooldownTicks;
    private boolean armed;
    private final List<TriggerAction> actions;

    private int ox, oy;

    public TriggerConfigScreen(String entryId, TriggerMode triggerMode,
                               TriggerFireMode fireMode, int radiusBlocks,
                               int cooldownTicks, boolean armed,
                               List<TriggerAction> actions) {
        super(Text.literal("Trigger Config"));
        this.entryId      = entryId;
        this.triggerMode  = triggerMode;
        this.fireMode     = fireMode;
        this.radiusBlocks = radiusBlocks;
        this.cooldownTicks = cooldownTicks;
        this.armed        = armed;
        this.actions      = new ArrayList<>(actions);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext ctx, int mx, int my, float d) {}

    @Override
    protected void init() {
        ox = (width - W) / 2;
        oy = (height - H) / 2;

        // Row 1 — trigger / fire mode toggles
        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Trigger: " + triggerMode.name()),
                        b -> { triggerMode = triggerMode == TriggerMode.RADIUS ? TriggerMode.INTERACT : TriggerMode.RADIUS;
                            b.setMessage(Text.literal("Trigger: " + triggerMode.name())); })
                .dimensions(ox + PAD, oy + 80, 110, 14).build());

        addDrawableChild(ButtonWidget.builder(
                        Text.literal("Fire: " + fireMode.name()),
                        b -> { fireMode = fireMode == TriggerFireMode.PER_PLAYER ? TriggerFireMode.FIRST_ENTRY : TriggerFireMode.PER_PLAYER;
                            b.setMessage(Text.literal("Fire: " + fireMode.name())); })
                .dimensions(ox + PAD + 115, oy + 80, 120, 14).build());

        // Row 2 — radius controls (left side)
        addDrawableChild(ButtonWidget.builder(Text.literal("R -"),
                        b -> { if (radiusBlocks > 1) radiusBlocks--; })
                .dimensions(ox + PAD, oy + 100, 30, 14).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("R +"),
                        b -> { if (radiusBlocks < 32) radiusBlocks++; })
                .dimensions(ox + PAD + 34, oy + 100, 30, 14).build());

        // Row 2 — cooldown controls (centre)
        addDrawableChild(ButtonWidget.builder(Text.literal("CD -"),
                        b -> { if (cooldownTicks > 20) cooldownTicks -= 20; })
                .dimensions(ox + PAD + 90, oy + 100, 35, 14).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("CD +"),
                        b -> { cooldownTicks += 20; })
                .dimensions(ox + PAD + 129, oy + 100, 35, 14).build());

        // Row 2 — armed toggle (right side)
        addDrawableChild(ButtonWidget.builder(
                        Text.literal(armed ? "ARMED" : "UNARMED"),
                        b -> { armed = !armed; b.setMessage(Text.literal(armed ? "ARMED" : "UNARMED")); })
                .dimensions(ox + W - 86 - PAD, oy + 100, 86, 14).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Save"),
                        b -> save())
                .dimensions(ox + PAD, oy + H - 22, 80, 14).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"),
                        b -> close())
                .dimensions(ox + W - 86 - PAD, oy + H - 22, 80, 14).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ox = (width - W) / 2;
        oy = (height - H) / 2;
        renderBackground(ctx, mx, my, delta);
        drawPanel(ctx);
        super.render(ctx, mx, my, delta);
    }

    private void drawPanel(DrawContext ctx) {
        int x = ox, y = oy;
        fill(ctx, x, y, x+W, y+H, COL_BG);
        hline(ctx, x, y,     x+W, COL_BORDER);
        hline(ctx, x, y+H-1, x+W, COL_BORDER);
        vline(ctx, x,     y, y+H, COL_BORDER);
        vline(ctx, x+W-1, y, y+H, COL_BORDER);
        hline(ctx, x+1, y+1,   x+W-1, COL_DIM);
        hline(ctx, x+1, y+H-2, x+W-1, COL_DIM);

        fill(ctx, x+2, y+2, x+W-2, y+14, COL_HEADER);
        hline(ctx, x+2, y+14, x+W-2, COL_BORDER);
        ctx.drawText(textRenderer, Text.literal("Trigger Block Config").formatted(Formatting.YELLOW),
                x+PAD, y+4, 0xFFFFFFFF, false);

        ctx.drawText(textRenderer, Text.literal("ID: " + entryId).formatted(Formatting.DARK_GRAY),
                x+PAD, y+17, 0xFF666666, false);

        // Actions list
        hline(ctx, x+2, y+27, x+W-2, COL_DIM);
        ctx.drawText(textRenderer, Text.literal("Actions (edit via /gm trigger addaction):")
                .formatted(Formatting.YELLOW), x+PAD, y+30, 0xFFCCCCCC, false);
        int ay = y + 41;
        if (actions.isEmpty()) {
            ctx.drawText(textRenderer, Text.literal("  None").formatted(Formatting.DARK_GRAY),
                    x+PAD, ay, 0xFF444444, false);
            ay += 9;
        } else {
            for (int i = 0; i < Math.min(actions.size(), 4); i++) {
                TriggerAction a = actions.get(i);
                String label = a.getType().name() + ": " + a.getPayload();
                if (label.length() > 44) label = label.substring(0, 43) + "…";
                ctx.drawText(textRenderer, Text.literal("  " + label).formatted(Formatting.GRAY),
                        x+PAD, ay, 0xFFCCCCCC, false);
                ay += 9;
            }
        }

        hline(ctx, x+2, ay+2, x+W-2, COL_DIM);
        ctx.drawText(textRenderer, Text.literal("Trigger / Fire mode:").formatted(Formatting.YELLOW),
                x+PAD, y+73, 0xFFCCCCCC, false);
        // Label below the button rows (buttons sit at y+80 and y+100, bottom edge y+114)
        ctx.drawText(textRenderer,
                Text.literal("Radius: " + radiusBlocks + " blocks   Cooldown: " + cooldownTicks + " ticks")
                        .formatted(Formatting.GRAY),
                x+PAD, y+118, 0xFFAAAAAA, false);

        hline(ctx, x+2, y+H-26, x+W-2, COL_BORDER);
    }

    private void save() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                new SaveTriggerConfigC2SPacket(entryId, triggerMode, fireMode,
                        radiusBlocks, cooldownTicks, armed, actions));
        close();
    }

    private void fill(DrawContext ctx, int x0, int y0, int x1, int y1, int col) {
        if (x1>x0 && y1>y0) ctx.fill(x0, y0, x1, y1, col);
    }
    private void hline(DrawContext ctx, int x0, int y, int x1, int col) { ctx.fill(x0, y, x1, y+1, col); }
    private void vline(DrawContext ctx, int x, int y0, int y1, int col) { ctx.fill(x, y0, x+1, y1, col); }
}