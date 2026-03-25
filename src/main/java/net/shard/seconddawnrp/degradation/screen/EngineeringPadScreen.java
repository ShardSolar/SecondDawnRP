package net.shard.seconddawnrp.degradation.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.shard.seconddawnrp.degradation.data.ComponentStatus;
import net.shard.seconddawnrp.degradation.network.OpenEngineeringPadS2CPacket.ComponentSnapshot;
import net.shard.seconddawnrp.degradation.network.OpenEngineeringPadS2CPacket.WarpCoreSnapshot;

import java.util.List;

/**
 * Fully code-drawn Engineering PAD screen — no texture sampling, so it is
 * always pixel-crisp at every GUI scale. Every element is rendered with
 * context.fill() or the font renderer.
 */
public class EngineeringPadScreen extends Screen {

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final int COL_BG         = 0xFF03091A;
    private static final int COL_BG2        = 0xFF07101F;
    private static final int COL_HEADER_BG  = 0xFF050B1A;
    private static final int COL_BORDER     = 0xFFB96408;
    private static final int COL_ACCENT     = 0xFFD7820A;
    private static final int COL_DIM        = 0xFF502C04;
    private static final int COL_DIM2       = 0xFF1E0F02;
    private static final int COL_DARK_PNL   = 0xFF02050F;
    private static final int COL_ROW_DIV    = 0xFF0A1226;

    private static final int COL_OFFLINE    = 0xFFA01C12;
    private static final int COL_CRITICAL   = 0xFFC84608;
    private static final int COL_DEGRADED   = 0xFFB97308;
    private static final int COL_NOMINAL    = 0xFF2D8214;

    private static final int COL_TEXT_GOLD  = 0xFFD4AA44;
    private static final int COL_TEXT_DIM   = 0xFF886622;
    private static final int COL_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COL_TEXT_GRAY  = 0xFF888888;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int W = 260;
    private static final int H = 240;

    private static final int PAD       = 6;
    private static final int ROW_H     = 14;
    private static final int ROWS_VIS  = 8;

    private final List<ComponentSnapshot> components;
    private final List<WarpCoreSnapshot> warpCores;
    private final String focusedCoreId;
    private final String warpCoreState;
    private final int warpCoreFuel;
    private final int warpCoreMaxFuel;
    private final int warpCorePower;
    private int scrollOffset = 0;
    private int ox, oy; // panel origin

    public EngineeringPadScreen(
            List<ComponentSnapshot> components,
            List<WarpCoreSnapshot> warpCores,
            String focusedCoreId,
            String warpCoreState,
            int warpCoreFuel,
            int warpCoreMaxFuel,
            int warpCorePower) {
        super(Text.literal("Engineering Systems"));
        this.components   = components;
        this.warpCores    = warpCores != null ? warpCores : List.of();
        this.focusedCoreId = focusedCoreId;
        this.warpCoreState = warpCoreState;
        this.warpCoreFuel = warpCoreFuel;
        this.warpCoreMaxFuel = warpCoreMaxFuel;
        this.warpCorePower = warpCorePower;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // intentionally empty — we draw our own background
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int max = Math.max(0, components.size() - ROWS_VIS);
        scrollOffset = (int) Math.max(0, Math.min(max, scrollOffset - v));
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ox = (width  - W) / 2;
        oy = (height - H) / 2;

        this.renderBackground(ctx, mx, my, delta);
        drawPanel(ctx, mx, my);
        super.render(ctx, mx, my, delta);
    }

    private void drawPanel(DrawContext ctx, int mx, int my) {
        int x = ox, y = oy;

        // ── Outer panel ───────────────────────────────────────────────────
        fill(ctx, x, y, x+W, y+H, COL_BG);
        hborder(ctx, x, y, x+W, COL_BORDER);
        hborder(ctx, x, y+H-1, x+W, COL_BORDER);
        vborder(ctx, x, y, y+H, COL_BORDER);
        vborder(ctx, x+W-1, y, y+H, COL_BORDER);

        // inner dim border
        hborder(ctx, x+1, y+1, x+W-1, COL_DIM);
        hborder(ctx, x+1, y+H-2, x+W-1, COL_DIM);
        vborder(ctx, x+1, y+1, y+H-1, COL_DIM);
        vborder(ctx, x+W-2, y+1, y+H-1, COL_DIM);

        int cy = y + 2;

        // ── Title bar ─────────────────────────────────────────────────────
        fill(ctx, x+2, cy, x+W-2, cy+13, COL_HEADER_BG);
        hborder(ctx, x+2, cy+13, x+W-2, COL_BORDER);

        // dots
        int[] dotCols = {0xFFAA2323, 0xFFA08210, COL_ACCENT};
        for (int i = 0; i < 3; i++) {
            fill(ctx, x+PAD+i*9, cy+4, x+PAD+i*9+5, cy+9, dotCols[i]);
        }

        ctx.drawText(textRenderer,
                Text.literal("Engineering Systems PADD").formatted(Formatting.GOLD),
                x+32, cy+3, COL_TEXT_WHITE, false);

        cy += 14;

        // ── Alert banner ──────────────────────────────────────────────────
        long bad = components.stream()
                .filter(s -> s.status() == ComponentStatus.CRITICAL
                        || s.status() == ComponentStatus.OFFLINE).count();
        int alertBg   = bad > 0 ? 0xFF180402 : 0xFF021402;
        int alertBord = bad > 0 ? 0xFF6E2305 : 0xFF1A5208;
        int alertDot  = bad > 0 ? 0xFFC82D12 : 0xFF34A018;
        String alertMsg = bad > 0
                ? bad + " component" + (bad == 1 ? "" : "s") + " need attention"
                : "All systems nominal";
        int alertTextCol = bad > 0 ? 0xFFFF7744 : 0xFF88CC66;

        fill(ctx, x+2, cy, x+W-2, cy+11, alertBg);
        hborder(ctx, x+2, cy,    x+W-2, alertBord);
        hborder(ctx, x+2, cy+10, x+W-2, alertBord);
        fill(ctx, x+PAD, cy+3, x+PAD+4, cy+8, alertDot);
        ctx.drawText(textRenderer, Text.literal(alertMsg),
                x+PAD+7, cy+2, alertTextCol, false);

        cy += 12;

        // ── Summary cells ─────────────────────────────────────────────────
        long[] counts = {
                components.stream().filter(s -> s.status()==ComponentStatus.OFFLINE).count(),
                components.stream().filter(s -> s.status()==ComponentStatus.CRITICAL).count(),
                components.stream().filter(s -> s.status()==ComponentStatus.DEGRADED).count(),
                components.stream().filter(s -> s.status()==ComponentStatus.NOMINAL).count(),
        };
        String[] sLabels = {"Offline","Critical","Degraded","Nominal"};
        int[] sBarCols   = {COL_OFFLINE, COL_CRITICAL, COL_DEGRADED, COL_NOMINAL};
        int cellW = (W - 4) / 4;

        for (int i = 0; i < 4; i++) {
            int cx2 = x + 2 + i * cellW;
            fill(ctx, cx2, cy, cx2+cellW-1, cy+22, COL_BG2);
            hborder(ctx, cx2, cy,    cx2+cellW-1, COL_DIM);
            hborder(ctx, cx2, cy+21, cx2+cellW-1, COL_DIM);
            vborder(ctx, cx2,       cy, cy+22, COL_DIM);
            vborder(ctx, cx2+cellW-2, cy, cy+22, COL_DIM);
            // label
            ctx.drawText(textRenderer, Text.literal(sLabels[i]),
                    cx2 + (cellW - textRenderer.getWidth(sLabels[i])) / 2,
                    cy+2, COL_TEXT_DIM, false);
            // count with status colour
            String val = String.valueOf(counts[i]);
            int textCol = i==3 ? COL_NOMINAL : (i==2 ? COL_DEGRADED : (i==1 ? COL_CRITICAL : COL_OFFLINE));
            ctx.drawText(textRenderer, Text.literal(val),
                    cx2 + (cellW - textRenderer.getWidth(val)) / 2,
                    cy+12, textCol | 0xFF000000, false);
        }

        cy += 23;
        hborder(ctx, x+2, cy, x+W-2, COL_DIM);
        cy += 2;

        // ── Ship health bar ───────────────────────────────────────────────
        fill(ctx, x+2, cy, x+W-2, cy+11, COL_DARK_PNL);
        hborder(ctx, x+2, cy,    x+W-2, COL_DIM);
        hborder(ctx, x+2, cy+10, x+W-2, COL_DIM);

        ctx.drawText(textRenderer, Text.literal("Ship health"),
                x+PAD, cy+2, COL_TEXT_DIM, false);

        int avg = components.isEmpty() ? 100
                : (int) components.stream().mapToInt(ComponentSnapshot::health).average().orElse(100);

        int barX = x + 70;
        int barW = W - 70 - 36;
        fill(ctx, barX, cy+2, barX+barW, cy+8, 0xFF080E20);
        int fw = Math.max(1, barW * avg / 100);
        fill(ctx, barX+1, cy+3, barX+fw, cy+7, COL_DEGRADED);

        String avgStr = avg + "%";
        fill(ctx, x+W-32, cy+2, x+W-4, cy+8, COL_ACCENT);
        ctx.drawText(textRenderer, Text.literal(avgStr),
                x+W-30, cy+2, COL_TEXT_WHITE, false);

        cy += 13;
        hborder(ctx, x+2, cy, x+W-2, COL_DIM);
        cy += 2;

        // ── Warp Core panel ───────────────────────────────────────────────
        cy = drawWarpCorePanel(ctx, x, cy, y);

        // ── Components section ────────────────────────────────────────────
        ctx.drawText(textRenderer, Text.literal("Components").formatted(Formatting.GOLD),
                x+PAD, cy, COL_TEXT_WHITE, false);
        cy += 10;

        if (components.isEmpty()) {
            ctx.drawText(textRenderer,
                    Text.literal("No components registered.").formatted(Formatting.DARK_GRAY),
                    x+PAD, cy+2, COL_TEXT_GRAY, false);
            cy += ROW_H * ROWS_VIS;
        } else {
            int nameColW = 88;
            int barStart = x + PAD + nameColW + 4;
            int barEnd   = x + W - 34;
            int barPxW   = barEnd - barStart;

            int end = Math.min(scrollOffset + ROWS_VIS, components.size());
            for (int i = scrollOffset; i < end; i++) {
                ComponentSnapshot snap = components.get(i);
                int ry = cy + (i - scrollOffset) * ROW_H;

                if (i > scrollOffset) {
                    hborder(ctx, x+PAD, ry, x+W-PAD, COL_ROW_DIV);
                }

                // name
                String name = snap.displayName();
                while (name.length() > 1 && textRenderer.getWidth(name) > nameColW) {
                    name = name.substring(0, name.length()-1);
                }
                if (!name.equals(snap.displayName())) name += "…";
                ctx.drawText(textRenderer, Text.literal(name),
                        x+PAD, ry+3, COL_TEXT_GOLD, false);

                // bar track
                fill(ctx, barStart, ry+3, barEnd, ry+10, COL_DARK_PNL);

                // bar fill
                int bfw = Math.max(1, barPxW * snap.health() / 100);
                fill(ctx, barStart+1, ry+4, barStart+bfw, ry+9,
                        barFillCol(snap.status()));

                // pct
                String pct = snap.health() + "%";
                ctx.drawText(textRenderer, Text.literal(pct),
                        barEnd+3, ry+3, barTextCol(snap.status()), false);

                // hover tooltip with coordinates and repair info
                if (mx >= x+PAD && mx <= x+W-PAD && my >= ry && my <= ry+ROW_H) {
                    net.minecraft.util.math.BlockPos bpos =
                            net.minecraft.util.math.BlockPos.fromLong(snap.blockPosLong());

                    var lines = new java.util.ArrayList<net.minecraft.text.Text>();
                    lines.add(Text.literal(snap.displayName()).formatted(Formatting.WHITE));
                    lines.add(Text.literal(snap.status().name()).formatted(statusFmt(snap.status())));
                    lines.add(Text.literal("Health: " + snap.health() + "/100").formatted(Formatting.GRAY));
                    lines.add(Text.literal("Pos: " + bpos.getX() + ", " + bpos.getY() + ", " + bpos.getZ())
                            .formatted(Formatting.GRAY));
                    // Repair item
                    if (snap.repairItemId() != null && !snap.repairItemId().isEmpty()) {
                        String itemName = snap.repairItemId().contains(":")
                                ? snap.repairItemId().split(":")[1].replace("_", " ")
                                : snap.repairItemId().replace("_", " ");
                        lines.add(Text.literal("Repair: " + snap.repairItemCount() + "x " + itemName)
                                .formatted(Formatting.AQUA));
                    }
                    lines.add(Text.literal("/engineering locate " + snap.componentId())
                            .formatted(Formatting.DARK_GRAY));
                    ctx.drawTooltip(textRenderer, lines, mx, my);
                }
            }

            cy += ROWS_VIS * ROW_H;

            // scroll indicator
            if (components.size() > ROWS_VIS) {
                int trackH = ROWS_VIS * ROW_H;
                int trackY = cy - trackH;
                vborder(ctx, x+W-PAD-1, trackY, trackY+trackH, COL_DIM);
                int thumbH = Math.max(4, trackH * ROWS_VIS / components.size());
                int thumbY = trackY + (trackH - thumbH) * scrollOffset
                        / Math.max(1, components.size() - ROWS_VIS);
                fill(ctx, x+W-PAD-1, thumbY, x+W-PAD, thumbY+thumbH, COL_ACCENT);
            }
        }

        // Repair tasks section removed — component list shows status directly

        // ── Footer ────────────────────────────────────────────────────────
        int footY = y + H - 13;
        fill(ctx, x+2, footY, x+W-2, y+H-2, COL_HEADER_BG);
        hborder(ctx, x+2, footY, x+W-2, COL_BORDER);

        for (int i = 0; i < 3; i++) {
            fill(ctx, x+PAD+i*30, footY+3, x+PAD+i*30+22, footY+9, COL_DIM);
        }
        fill(ctx, x+W-18, footY+3, x+W-12, footY+9, COL_ACCENT);
        fill(ctx, x+W-10, footY+3, x+W-4,  footY+9, 0xFF9B1C12);

        // left/right edge ticks
        int[] lticks = {cy-ROWS_VIS*ROW_H-10, cy-ROWS_VIS*ROW_H+20, cy-10};
        for (int ty2 : lticks) {
            if (ty2 > y+14 && ty2 < footY-4)
                fill(ctx, x, ty2, x+2, ty2+8, COL_ACCENT);
        }
    }


    private int drawWarpCorePanel(DrawContext ctx, int x, int cy, int panelY) {
        ctx.drawText(textRenderer,
                Text.literal("Warp core").formatted(Formatting.GOLD),
                x + PAD, cy, COL_TEXT_WHITE, false);
        cy += 10;

        if (warpCores.isEmpty()) {
            ctx.drawText(textRenderer,
                    Text.literal("Not registered").formatted(Formatting.DARK_GRAY),
                    x + PAD, cy, 0xFF444433, false);
            cy += 12;
            hborder(ctx, x + 2, cy, x + W - 2, COL_DIM);
            cy += 2;
            return cy;
        }

        // If no focused core (opened in air), don't show warp core panel at all
        if (focusedCoreId == null || focusedCoreId.isEmpty()) {
            hborder(ctx, x + 2, cy, x + W - 2, COL_DIM);
            cy += 2;
            return cy;
        }

        // One row per registered core — two lines each
        for (WarpCoreSnapshot wc : warpCores) {
            int stateCol = switch (wc.state()) {
                case "ONLINE"   -> 0xFF2D8214;
                case "STARTING" -> 0xFF1A6A7A;
                case "UNSTABLE" -> 0xFFB97308;
                case "CRITICAL" -> 0xFFC84608;
                case "FAILED"   -> 0xFF8A1010;
                default          -> 0xFF444441;
            };

            // Line 1: ID + state + power%
            String shortId = wc.entryId().length() > 16
                    ? wc.entryId().substring(0, 16) : wc.entryId();
            ctx.drawText(textRenderer, Text.literal(shortId),
                    x + PAD, cy + 1, 0xFF443322, false);
            fill(ctx, x + PAD + 3, cy, x + PAD + 5, cy + 9, stateCol);
            ctx.drawText(textRenderer, Text.literal(wc.state()),
                    x + PAD + 108, cy + 1, stateCol, false);
            ctx.drawText(textRenderer, Text.literal("PWR " + wc.power() + "%"),
                    x + W - PAD - 50, cy + 1, 0xFFAAAAAA, false);
            cy += 10;

            // Line 2: fuel bar + coil indicator
            int fuelW = W - PAD * 2 - (wc.coilCount() > 0 ? 60 : 4);
            int fuelFill = wc.maxFuel() > 0 ? Math.max(0, fuelW * wc.fuel() / wc.maxFuel()) : 0;
            int fuelCol = wc.fuel() > 20 ? 0xFF2D8214 : wc.fuel() > 5 ? 0xFFB97308 : 0xFFC84608;
            fill(ctx, x + PAD, cy, x + PAD + fuelW, cy + 7, COL_DARK_PNL);
            if (fuelFill > 0)
                fill(ctx, x + PAD + 1, cy + 1, x + PAD + fuelFill, cy + 6, fuelCol);
            if (wc.coilCount() > 0) {
                int coilCol = wc.coilHealth() > 60 ? 0xFF2D8214
                        : wc.coilHealth() > 35 ? 0xFFB97308 : 0xFFC84608;
                ctx.drawText(textRenderer,
                        Text.literal("×" + wc.coilCount() + " " + wc.coilHealth() + "%"),
                        x + PAD + fuelW + 4, cy, coilCol, false);
            }
            cy += 9;
            hborder(ctx, x + PAD, cy, x + W - PAD, COL_DIM2);
            cy += 2;
        }

        hborder(ctx, x + 2, cy, x + W - 2, COL_DIM);
        cy += 2;
        return cy;
    }

    // ── Draw helpers ──────────────────────────────────────────────────────────

    private void fill(DrawContext ctx, int x0, int y0, int x1, int y1, int col) {
        if (x1 > x0 && y1 > y0) ctx.fill(x0, y0, x1, y1, col);
    }

    private void hborder(DrawContext ctx, int x0, int y, int x1, int col) {
        ctx.fill(x0, y, x1, y+1, col);
    }

    private void vborder(DrawContext ctx, int x, int y0, int y1, int col) {
        ctx.fill(x, y0, x+1, y1, col);
    }

    private static int barFillCol(ComponentStatus s) {
        return switch (s) {
            case NOMINAL  -> 0xFF2DB814;
            case DEGRADED -> 0xFFCC8800;
            case CRITICAL -> 0xFFBB2200;
            case OFFLINE  -> 0xFF660000;
        };
    }

    private static int barTextCol(ComponentStatus s) {
        return switch (s) {
            case NOMINAL  -> 0xFF44CC22;
            case DEGRADED -> 0xFFEEAA00;
            case CRITICAL -> 0xFFCC3300;
            case OFFLINE  -> 0xFFAA2200;
        };
    }

    private static Formatting statusFmt(ComponentStatus s) {
        return switch (s) {
            case NOMINAL  -> Formatting.GREEN;
            case DEGRADED -> Formatting.YELLOW;
            case CRITICAL -> Formatting.RED;
            case OFFLINE  -> Formatting.DARK_RED;
        };
    }
}