package net.shard.seconddawnrp.tactical.console;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.tactical.data.ShipState;
import net.shard.seconddawnrp.tactical.network.TacticalNetworking.*;

import java.util.List;

/**
 * Four-panel Tactical Console GUI.
 * Extends Screen (not HandledScreen) — opened directly via mc.setScreen(),
 * state is pushed via EncounterUpdatePayload packets.
 *
 * ┌────────────────────────┬────────────────────────┐
 * │   NAVIGATION (top-L)   │   WEAPONS (top-R)      │
 * │   Tactical map grid    │   Targets, facing, fire │
 * ├────────────────────────┼────────────────────────┤
 * │   SHIELDS (bot-L)      │   STATUS (bot-R)        │
 * │   4-facing bars+sliders│   Hull, power, log ▲▼  │
 * └────────────────────────┴────────────────────────┘
 *
 * Changes from original:
 *  - Encounter log is scrollable (mouse wheel or ▲/▼ buttons)
 *  - Weapons panel has FORE/AFT/PORT/STBD facing selector buttons
 *  - Selected facing sent in WeaponFirePayload as targetFacing string
 */
public class TacticalScreen extends Screen {

    private final TacticalScreenHandler handler;

    private static final int GUI_W  = 420;
    private static final int GUI_H  = 260;
    private static final int HALF_W = GUI_W / 2;
    private static final int HALF_H = (GUI_H - 22) / 2;
    private static final int PANEL_Y = 22;

    // Colors
    private static final int COL_BG        = 0xE0080E18;
    private static final int COL_TITLE     = 0xFF1A2840;
    private static final int COL_BORDER    = 0xFF1A4A6A;
    private static final int COL_HEADER    = 0xFF00AAFF;
    private static final int COL_FRIENDLY  = 0xFF3399FF;
    private static final int COL_HOSTILE   = 0xFFFF3333;
    private static final int COL_UNKNOWN   = 0xFFFFAA00;
    private static final int COL_TEXT      = 0xFFCCEEFF;
    private static final int COL_DIM       = 0xFF667799;
    private static final int COL_GREEN     = 0xFF33FF88;
    private static final int COL_RED       = 0xFFFF4444;
    private static final int COL_YELLOW    = 0xFFFFBB33;
    private static final int COL_BTN       = 0xFF0A2040;
    private static final int COL_BTN_HOV   = 0xFF1A3A60;
    private static final int COL_BTN_SEL   = 0xFF003366;
    private static final int COL_SHIELD_OK = 0xFF2266FF;
    private static final int COL_SHIELD_LOW= 0xFFFF6622;
    private static final int COL_HULL_OK   = 0xFF22CC44;
    private static final int COL_HULL_CRIT = 0xFFCC2222;

    private static final float MAP_SCALE = 0.15f;
    private static final float ZOOM_MIN  = 0.25f;
    private static final float ZOOM_MAX  = 4.0f;

    public TacticalScreen(TacticalScreenHandler handler, Text title) {
        super(title);
        this.handler = handler;
    }

    public TacticalScreen(TacticalScreenHandler handler) {
        this(handler, Text.literal("Tactical Console"));
    }

    public TacticalScreenHandler getScreenHandler() { return handler; }

    private int x, y;

    // Tooltip
    private String hoveredTooltip = null;
    private int tooltipX, tooltipY;

    // Helm input state
    private float   inputHeading    = 0;
    private float   inputSpeed      = 0;
    private boolean editingHeading  = false;
    private boolean editingSpeed    = false;
    private boolean mapExpanded     = false;
    private float   mapZoom         = 1.0f;
    private String  helmInputBuffer = "";

    // Log scroll — 0 = show oldest visible, increases to show newer
    private int logScrollOffset = 0;

    // Selected facing for weapon targeting — null means AUTO (compute from position)
    private ShipState.ShieldFacing selectedFacing = null;

    @Override
    protected void init() {
        super.init();
        this.x = (this.width  - GUI_W) / 2;
        this.y = (this.height - GUI_H) / 2;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hoveredTooltip = null;
        drawBackground(ctx, delta, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);

        if (mapExpanded) drawExpandedMap(ctx, mouseX, mouseY);

        if (hoveredTooltip != null) {
            ctx.drawTooltip(textRenderer,
                    List.of(Text.literal(hoveredTooltip)), tooltipX, tooltipY);
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // suppress — solid fill in drawBackground()
    }

    private void drawBackground(DrawContext ctx, float delta, int mx, int my) {
        int ox = this.x, oy = this.y;

        ctx.fill(ox, oy, ox + GUI_W, oy + GUI_H, COL_BG);
        ctx.fill(ox, oy, ox + GUI_W, oy + PANEL_Y, COL_TITLE);
        ctx.drawBorder(ox, oy, GUI_W, GUI_H, COL_BORDER);

        String stationLabel = switch (handler.getStationFilter()) {
            case HELM    -> "HELM CONSOLE";
            case WEAPONS -> "WEAPONS CONSOLE";
            case SHIELDS -> "SHIELD CONSOLE";
            case SENSORS -> "SENSORS CONSOLE";
            default      -> "TACTICAL CONSOLE";
        };
        String encounterInfo = handler.isStandby()
                ? stationLabel + " — STANDBY"
                : stationLabel + " — " + handler.getEncounterId()
                + " [" + handler.getEncounterStatus() + "]";
        ctx.drawText(textRenderer, encounterInfo,
                ox + (GUI_W - textRenderer.getWidth(encounterInfo)) / 2,
                oy + 7, COL_HEADER, false);

        ctx.fill(ox + HALF_W, oy + PANEL_Y, ox + HALF_W + 1, oy + GUI_H, COL_BORDER);
        ctx.fill(ox, oy + PANEL_Y + HALF_H, ox + GUI_W,
                oy + PANEL_Y + HALF_H + 1, COL_BORDER);

        drawPanelHeader(ctx, ox, oy + PANEL_Y, HALF_W,
                "NAVIGATION" + (handler.canSeeNavigation()
                        ? (handler.canUseHelm() ? "" : " §8[READ]") : " §8[LOCKED]"), mx, my);
        drawPanelHeader(ctx, ox + HALF_W, oy + PANEL_Y, HALF_W,
                "WEAPONS" + (handler.canUseWeapons() ? "" : " §8[LOCKED]"), mx, my);
        drawPanelHeader(ctx, ox, oy + PANEL_Y + HALF_H, HALF_W,
                "SHIELDS" + (handler.canUseShields() ? "" : " §8[LOCKED]"), mx, my);
        drawPanelHeader(ctx, ox + HALF_W, oy + PANEL_Y + HALF_H, HALF_W, "STATUS", mx, my);

        drawNavigationPanel(ctx, ox + 4,          oy + PANEL_Y + 12,         HALF_W - 8, HALF_H - 16, mx, my);
        drawWeaponsPanel   (ctx, ox + HALF_W + 4, oy + PANEL_Y + 12,         HALF_W - 8, HALF_H - 16, mx, my);
        drawShieldsPanel   (ctx, ox + 4,          oy + PANEL_Y + HALF_H + 12, HALF_W - 8, HALF_H - 16, mx, my);
        drawStatusPanel    (ctx, ox + HALF_W + 4, oy + PANEL_Y + HALF_H + 12, HALF_W - 8, HALF_H - 16, mx, my);
    }

    // ── Navigation panel ──────────────────────────────────────────────────────

    private void drawNavigationPanel(DrawContext ctx, int x, int y, int w, int h,
                                     int mx, int my) {
        int mapCX = x + w / 2;
        int mapCY = y + (h - 22) / 2;

        if (handler.isStandby()) {
            drawStandbyMap(ctx, x, y, w, h - 22, mapCX, mapCY, mx, my);
            if (!mapExpanded) drawExpandButton(ctx, x + w - 36, y + 1, 32, 11, mx, my);
        } else {
            drawEncounterMap(ctx, x, y, w, h - 22, mapCX, mapCY, mx, my);
            drawExpandButton(ctx, x + w - 36, y + 1, 32, 11, mx, my);
        }

        if (handler.canUseHelm()) {
            int ctrlY = y + h - 20;
            float displayHeading = (handler.isStandby() && !editingHeading)
                    ? handler.getStellarTargetHeading() : inputHeading;
            float displaySpeed   = (handler.isStandby() && !editingSpeed)
                    ? handler.getStellarTargetSpeed()   : inputSpeed;

            ctx.drawText(textRenderer,
                    handler.isStandby() ? "§8STELLAR NAVIGATION" : "§8HELM",
                    x + 4, ctrlY - 2, COL_DIM, false);
            ctrlY += 10;

            boolean hHov = inBounds(mx, my, x + 4, ctrlY, 70, 11);
            ctx.fill(x + 4, ctrlY, x + 74, ctrlY + 11,
                    editingHeading ? 0xFF0A2850 : (hHov ? COL_BTN_HOV : COL_BTN));
            ctx.drawBorder(x + 4, ctrlY, 70, 11,
                    editingHeading ? COL_HEADER : COL_BORDER);
            ctx.drawText(textRenderer,
                    "H:" + (editingHeading
                            ? helmInputBuffer + "_" : (int)displayHeading + "°"),
                    x + 7, ctrlY + 2, COL_TEXT, false);

            boolean sHov = inBounds(mx, my, x + 78, ctrlY, 55, 11);
            ctx.fill(x + 78, ctrlY, x + 133, ctrlY + 11,
                    editingSpeed ? 0xFF0A2850 : (sHov ? COL_BTN_HOV : COL_BTN));
            ctx.drawBorder(x + 78, ctrlY, 55, 11,
                    editingSpeed ? COL_HEADER : COL_BORDER);
            ctx.drawText(textRenderer,
                    "S:" + (editingSpeed
                            ? helmInputBuffer + "_"
                            : String.format("%.1f", displaySpeed)),
                    x + 81, ctrlY + 2, COL_TEXT, false);

            boolean setHov = inBounds(mx, my, x + 137, ctrlY, 40, 11);
            ctx.fill(x + 137, ctrlY, x + 177, ctrlY + 11,
                    setHov ? COL_BTN_HOV : COL_BTN);
            ctx.drawBorder(x + 137, ctrlY, 40, 11, COL_BORDER);
            ctx.drawText(textRenderer, "SET", x + 149, ctrlY + 2, COL_GREEN, false);
        }
    }

    // ── Standby / stellar / encounter maps ────────────────────────────────────

    private void drawStandbyMap(DrawContext ctx, int x, int y, int w, int h,
                                int mapCX, int mapCY, int mx, int my) {
        if (handler.hasStellarData()) {
            drawStellarNavMap(ctx, x, y, w, h, mapCX, mapCY, mx, my);
        } else {
            drawBasicStandbyMap(ctx, x, y, w, h, mapCX, mapCY, mx, my);
        }
        ctx.drawText(textRenderer, "§9■§7FRN  §c■§7HST  §e◆§7ANO  §b●§7ORB",
                x + 4, y + h - 8, COL_DIM, false);

        boolean zoomInHov  = inBounds(mx, my, x + 4,  y + 2, 14, 11);
        boolean zoomOutHov = inBounds(mx, my, x + 20, y + 2, 14, 11);
        ctx.fill(x + 4,  y + 2, x + 18, y + 13, zoomInHov  ? COL_BTN_HOV : COL_BTN);
        ctx.fill(x + 20, y + 2, x + 34, y + 13, zoomOutHov ? COL_BTN_HOV : COL_BTN);
        ctx.drawBorder(x + 4,  y + 2, 14, 11, COL_BORDER);
        ctx.drawBorder(x + 20, y + 2, 14, 11, COL_BORDER);
        ctx.drawText(textRenderer, "§f+", x + 8,  y + 3, COL_GREEN, false);
        ctx.drawText(textRenderer, "§f-", x + 24, y + 3, COL_TEXT,  false);
        ctx.drawText(textRenderer, "§8" + String.format("%.0f%%", mapZoom * 100),
                x + 37, y + 3, COL_DIM, false);
    }

    private void drawStellarNavMap(DrawContext ctx, int x, int y, int w, int h,
                                   int mapCX, int mapCY, int mx, int my) {
        final float STELLAR_SCALE = 0.15f * mapZoom;
        double shipX = handler.getStellarPosX();
        double shipZ = handler.getStellarPosZ();

        for (int ring : new int[]{200, 500, 1000})
            drawCircleApprox(ctx, mapCX, mapCY, (int)(ring * STELLAR_SCALE), COL_BORDER);

        for (var zone : handler.getOrbitalZones()) {
            double relX = zone.centerX() - shipX;
            double relZ = zone.centerZ() - shipZ;
            int zx = mapCX + (int)(relX * STELLAR_SCALE);
            int zz = mapCY + (int)(relZ * STELLAR_SCALE);
            int zr = Math.max(3, (int)(zone.radius() * STELLAR_SCALE));
            int zoneColor = switch (zone.reachability()) {
                case "REACHABLE"                  -> 0xFF33FF66;
                case "IN_RANGE_INSUFFICIENT_WARP" -> 0xFFFFAA00;
                default                           -> 0xFFFF4444;
            };
            drawCircleApprox(ctx, zx, zz, zr, zoneColor);
            ctx.drawText(textRenderer, "§f" + zone.displayName(),
                    zx + zr + 2, zz - 4, zoneColor, false);
            if (inBounds(mx, my, zx - zr, zz - zr, zr * 2, zr * 2)) {
                double dist = Math.sqrt(Math.pow(zone.centerX() - shipX, 2)
                        + Math.pow(zone.centerZ() - shipZ, 2));
                String eta = computeEta(dist, handler.getStellarSpeed(),
                        handler.getStellarWarpSpeed());
                hoveredTooltip = zone.displayName()
                        + " | " + zone.reachability().replace('_', ' ')
                        + " | Dist: " + (int)dist + " | ETA: " + eta;
                tooltipX = mx; tooltipY = my;
            }
        }

        for (var anomaly : handler.getAnomalies()) {
            double relX = anomaly.posX() - handler.getShipOriginX();
            double relZ = anomaly.posZ() - handler.getShipOriginZ();
            int ax = mapCX + (int)(relX * STELLAR_SCALE);
            int az = mapCY + (int)(relZ * STELLAR_SCALE);
            int color = anomaly.color() | 0xFF000000;
            ctx.fill(ax - 1, az - 2, ax + 2, az - 1, color);
            ctx.fill(ax - 2, az - 1, ax + 3, az + 1, color);
            ctx.fill(ax - 1, az + 1, ax + 2, az + 2, color);
            if (inBounds(mx, my, ax - 3, az - 3, 6, 6)) {
                hoveredTooltip = anomaly.label();
                tooltipX = mx; tooltipY = my;
            }
        }

        double headingRad = Math.toRadians(handler.getStellarHeading());
        int hx = mapCX + (int)(Math.cos(headingRad) * 8);
        int hz = mapCY + (int)(Math.sin(headingRad) * 8);
        ctx.fill(mapCX - 2, mapCY - 2, mapCX + 2, mapCY + 2, 0xFF3399FF);
        ctx.fill(mapCX, mapCY, hx, hz, 0xFF3399FF);

        int sy = y + h - 28;
        String warpStr = handler.getStellarWarpSpeed() > 0
                ? "§bWARP " + handler.getStellarWarpSpeed() : "§7SUBLIGHT";
        ctx.drawText(textRenderer,
                "§8POS: §f" + (int)shipX + "," + (int)shipZ
                        + "  §8HDG: §f" + (int)handler.getStellarHeading() + "°"
                        + "  §8SPD: §f" + String.format("%.0f", handler.getStellarSpeed())
                        + "  " + warpStr, x + 4, sy, COL_TEXT, false);

        double nearestDist = Double.MAX_VALUE; String nearestName = "";
        for (var zone : handler.getOrbitalZones()) {
            double d = Math.sqrt(Math.pow(zone.centerX() - shipX, 2)
                    + Math.pow(zone.centerZ() - shipZ, 2));
            if (d < nearestDist) { nearestDist = d; nearestName = zone.displayName(); }
        }
        if (!nearestName.isEmpty()) {
            String eta = computeEta(nearestDist, handler.getStellarSpeed(),
                    handler.getStellarWarpSpeed());
            ctx.drawText(textRenderer, "§8ETA §f" + nearestName + ": §b" + eta,
                    x + 4, sy + 9, COL_DIM, false);
        }
    }

    private String computeEta(double distance, float speed, int warpSpeed) {
        float effectiveSpeed = speed > 0 ? speed : 1;
        float unitsPerMinute = warpSpeed > 0
                ? warpSpeed * 2000f : effectiveSpeed * 2f;
        if (unitsPerMinute <= 0) return "---";
        double minutes = distance / unitsPerMinute;
        if (minutes < 1)    return "<1 min";
        if (minutes < 60)   return (int)minutes + " min";
        if (minutes < 1440) return String.format("%.1f hr",   minutes / 60.0);
        return                      String.format("%.1f days", minutes / 1440.0);
    }

    private void drawBasicStandbyMap(DrawContext ctx, int x, int y, int w, int h,
                                     int mapCX, int mapCY, int mx, int my) {
        double originX = handler.getShipOriginX();
        double originZ = handler.getShipOriginZ();

        for (int ring : new int[]{30, 60, 90})
            drawCircleApprox(ctx, mapCX, mapCY, (int)(ring * MAP_SCALE), COL_BORDER);

        ctx.fill(mapCX - 3, mapCY - 1, mapCX + 3, mapCY + 1, 0xFFFFFFFF);
        ctx.fill(mapCX - 1, mapCY - 3, mapCX + 1, mapCY + 3, 0xFFFFFFFF);
        ctx.drawText(textRenderer, "§fORIGIN", mapCX + 4, mapCY - 4, 0xFFFFFFFF, false);

        for (var anomaly : handler.getAnomalies()) {
            double relX = anomaly.posX() - originX;
            double relZ = anomaly.posZ() - originZ;
            int ax = mapCX + (int)(relX * MAP_SCALE);
            int az = mapCY + (int)(relZ * MAP_SCALE);
            int color = anomaly.color() | 0xFF000000;
            ctx.fill(ax,     az - 3, ax + 1, az - 2, color);
            ctx.fill(ax - 2, az - 1, ax + 3, az,     color);
            ctx.fill(ax - 3, az,     ax + 4, az + 1, color);
            ctx.fill(ax - 2, az + 1, ax + 3, az + 2, color);
            ctx.fill(ax,     az + 2, ax + 1, az + 3, color);
            ctx.drawText(textRenderer, anomaly.label(), ax + 5, az - 4, color, false);
            if (inBounds(mx, my, ax - 4, az - 4, 8, 8)) {
                hoveredTooltip = anomaly.label()
                        + " | X:" + (int)anomaly.posX()
                        + " Z:" + (int)anomaly.posZ();
                tooltipX = mx; tooltipY = my;
            }
        }

        if (handler.getAnomalies().isEmpty()) {
            long pulse = (System.currentTimeMillis() / 600) % 4;
            String dots = ".".repeat((int)pulse);
            ctx.drawText(textRenderer, "§8SCANNING" + dots,
                    mapCX - textRenderer.getWidth("SCANNING....") / 2,
                    mapCY + 10, COL_DIM, false);
        }
    }

    private void drawEncounterMap(DrawContext ctx, int x, int y, int w, int h,
                                  int mapCX, int mapCY, int mx, int my) {
        for (int ring : new int[]{30, 60, 90})
            drawCircleApprox(ctx, mapCX, mapCY, (int)(ring * MAP_SCALE), COL_BORDER);

        for (ShipSnapshot ship : handler.getShips()) {
            if (ship.destroyed()) continue;
            int sx = mapCX + (int)(ship.posX() * MAP_SCALE);
            int sz = mapCY + (int)(ship.posZ() * MAP_SCALE);
            int color = "FRIENDLY".equals(ship.faction()) ? COL_FRIENDLY : COL_HOSTILE;
            ctx.fill(sx - 2, sz - 2, sx + 2, sz + 2, color);
            double rad = Math.toRadians(ship.heading());
            int hx = sx + (int)(Math.cos(rad) * 6);
            int hz = sz + (int)(Math.sin(rad) * 6);
            ctx.fill(sx, sz, hx, hz, color);
            ctx.drawText(textRenderer, ship.combatId(), sx + 3, sz - 4, color, false);
            drawShieldRing(ctx, sx, sz, ship);
            if (inBounds(mx, my, sx - 4, sz - 4, 8, 8)) {
                hoveredTooltip = ship.registryName()
                        + " | Hull: " + ship.hullIntegrity() + "/" + ship.hullMax()
                        + " | Spd: " + String.format("%.1f", ship.speed());
                tooltipX = mx; tooltipY = my;
            }
        }

        for (var anomaly : handler.getAnomalies()) {
            int ax = mapCX + (int)(anomaly.posX() * MAP_SCALE);
            int az = mapCY + (int)(anomaly.posZ() * MAP_SCALE);
            int color = anomaly.color() | 0xFF000000;
            ctx.fill(ax - 1, az - 1, ax + 2, az + 2, color);
            if (inBounds(mx, my, ax - 3, az - 3, 6, 6)) {
                hoveredTooltip = anomaly.label();
                tooltipX = mx; tooltipY = my;
            }
        }

        ctx.drawText(textRenderer, "§9■§7FRN  §c■§7HST  §e◆§7ANO",
                x + 4, y + h - 8, COL_DIM, false);
    }

    // ── Weapons panel ─────────────────────────────────────────────────────────

    private void drawWeaponsPanel(DrawContext ctx, int x, int y, int w, int h,
                                  int mx, int my) {
        ShipSnapshot ps = handler.isStandby()
                ? handler.getHomeShipSnapshot() : handler.getPlayerShip();

        if (ps == null) {
            ctx.drawText(textRenderer,
                    handler.isStandby() ? "§8No home ship data yet." : "§7No friendly ship.",
                    x + 4, y + 4, COL_DIM, false);
            return;
        }

        if (handler.isStandby()) {
            // Standby: ship status readout only, no fire controls
            float hullPct  = ps.hullMax() <= 0 ? 1 : (float)ps.hullIntegrity() / ps.hullMax();
            int   hullColor = hullPct > 0.5f ? COL_GREEN : hullPct > 0.25f ? COL_YELLOW : COL_RED;
            ctx.drawText(textRenderer, "§bSHIP STATUS:", x + 4, y, COL_HEADER, false);
            ctx.drawText(textRenderer,
                    "§8Hull: " + hullColor(hullPct) + ps.hullIntegrity() + "§8/" + ps.hullMax(),
                    x + 80, y, COL_TEXT, false);
            int ty = y + 10;
            int bw = w - 8;
            ctx.fill(x + 4, ty, x + 4 + bw, ty + 6, 0xFF111820);
            ctx.fill(x + 4, ty, x + 4 + (int)(bw * hullPct), ty + 6, hullColor);
            ctx.drawBorder(x + 4, ty, bw, 6, COL_BORDER);
            ty += 10;
            ctx.drawText(textRenderer,
                    "§bTPDO: §f" + ps.torpedoCount()
                            + "  §bWARP: §f" + (ps.warpSpeed() > 0 ? ps.warpSpeed() : "SUBLIGHT"),
                    x + 4, ty, COL_TEXT, false);
            ty += 10;
            ctx.drawText(textRenderer, "§bPOWER:", x + 4, ty, COL_HEADER, false);
            ty += 9;
            ctx.drawText(textRenderer,
                    "§8Bgt:§f" + ps.powerBudget()
                            + " §8Wpn:§c" + ps.weaponsPower()
                            + " §8Shd:§b" + ps.shieldsPower(),
                    x + 4, ty, COL_TEXT, false);
            ty += 9;
            ctx.drawText(textRenderer,
                    "§8Eng:§a" + ps.enginesPower()
                            + " §8Sen:§e" + ps.sensorsPower(),
                    x + 4, ty, COL_TEXT, false);
            return;
        }

        // ── Active encounter ──────────────────────────────────────────────────

        // Target list
        ctx.drawText(textRenderer, "§bTARGETS:", x + 4, y, COL_HEADER, false);
        int rowY = y + 10;
        for (ShipSnapshot hostile : handler.getHostileShips()) {
            if (hostile.destroyed()) continue;
            boolean selected = hostile.shipId().equals(handler.getSelectedTargetId());
            boolean hov = inBounds(mx, my, x + 2, rowY - 1, w - 4, 10);
            if (selected) ctx.fill(x + 2, rowY - 1, x + w - 2, rowY + 9, 0x44FF3333);
            else if (hov) ctx.fill(x + 2, rowY - 1, x + w - 2, rowY + 9, 0x22FFFFFF);
            ctx.drawText(textRenderer,
                    (selected ? "§c► " : "§7○ ") + "§f" + hostile.combatId()
                            + " §8| Hull: " + hostile.hullIntegrity() + "/" + hostile.hullMax(),
                    x + 4, rowY, COL_TEXT, false);
            rowY += 12;
        }

        // Facing selector — current mode shown in header, no overflow label needed
        rowY += 2;
        String facingHeader = "§8FACING: "
                + (selectedFacing != null ? "§b" + selectedFacing.name() : "§7AUTO");
        ctx.drawText(textRenderer, facingHeader, x + 4, rowY, COL_DIM, false);
        rowY += 9;

        ShipState.ShieldFacing[] facings = {
                ShipState.ShieldFacing.FORE,
                ShipState.ShieldFacing.AFT,
                ShipState.ShieldFacing.PORT,
                ShipState.ShieldFacing.STARBOARD
        };
        String[] facingLabels = {"FORE", "AFT", "PORT", "STBD"};
        int facingBtnW = (w - 8 - 6) / 4;
        for (int i = 0; i < 4; i++) {
            int bx = x + 4 + i * (facingBtnW + 2);
            boolean sel = facings[i] == selectedFacing;
            boolean hov = inBounds(mx, my, bx, rowY, facingBtnW, 11);
            ctx.fill(bx, rowY, bx + facingBtnW, rowY + 11,
                    sel ? COL_BTN_SEL : (hov ? COL_BTN_HOV : COL_BTN));
            ctx.drawBorder(bx, rowY, facingBtnW, 11,
                    sel ? COL_HEADER : COL_BORDER);
            int lw = textRenderer.getWidth(facingLabels[i]);
            ctx.drawText(textRenderer, facingLabels[i],
                    bx + (facingBtnW - lw) / 2, rowY + 2,
                    sel ? COL_HEADER : COL_TEXT, false);
        }

        rowY += 14;
        ctx.drawText(textRenderer, "§bTPDO: §f" + ps.torpedoCount(),
                x + 4, rowY, COL_TEXT, false);

        // Fire buttons
        boolean canFire = handler.getSelectedTargetId() != null && handler.canUseWeapons();
        int btnY = y + h - 28;
        drawTacticalButton(ctx, x + 4, btnY, (w - 12) / 2, 13,
                "§c⚡ PHASERS", canFire, mx, my);
        drawTacticalButton(ctx, x + (w - 12) / 2 + 8, btnY, (w - 12) / 2, 13,
                "§e⦿ TORPEDO", canFire && ps.torpedoCount() > 0, mx, my);
        drawTacticalButton(ctx, x + 4, btnY + 16, w - 8, 11,
                "§b⟳ EVASIVE MANEUVER", handler.canUseHelm(), mx, my);
    }

    // ── Shields panel ─────────────────────────────────────────────────────────

    private void drawShieldsPanel(DrawContext ctx, int x, int y, int w, int h,
                                  int mx, int my) {
        ShipSnapshot ps = handler.isStandby()
                ? handler.getHomeShipSnapshot() : handler.getPlayerShip();
        if (ps == null) {
            if (!handler.isStandby())
                ctx.drawText(textRenderer, "§7No ship data.", x + 4, y + 4, COL_DIM, false);
            return;
        }
        ctx.drawText(textRenderer, "§bSHIELD FACINGS:", x + 4, y, COL_HEADER, false);
        int barW = w - 60, barH = 8, labelW = 38;
        int total = ps.shieldFore() + ps.shieldAft() + ps.shieldPort() + ps.shieldStarboard();
        drawShieldBar(ctx, x + 4, y + 14, barW, barH, labelW, "FORE", ps.shieldFore(), total);
        drawShieldBar(ctx, x + 4, y + 28, barW, barH, labelW, "AFT",  ps.shieldAft(),  total);
        drawShieldBar(ctx, x + 4, y + 42, barW, barH, labelW, "PORT", ps.shieldPort(), total);
        drawShieldBar(ctx, x + 4, y + 56, barW, barH, labelW, "STBD", ps.shieldStarboard(), total);
        ctx.drawText(textRenderer,
                "§8Shield Power: " + ps.shieldsPower() + "/" + ps.powerBudget(),
                x + 4, y + 72, COL_DIM, false);
        drawTacticalButton(ctx, x + 4, y + h - 14, w - 8, 12,
                "§b= BALANCE SHIELDS",
                !handler.isStandby() && handler.canUseShields(), mx, my);
    }

    private void drawShieldBar(DrawContext ctx, int x, int y, int barW, int barH,
                               int labelW, String label, int current, int max) {
        float pct = max <= 0 ? 0 : Math.min(1f, (float)current / (max / 4f));
        int color = pct > 0.5f ? COL_SHIELD_OK : pct > 0.2f ? COL_YELLOW : COL_RED;
        ctx.drawText(textRenderer, label, x, y, COL_DIM, false);
        int bx = x + labelW;
        ctx.fill(bx, y, bx + barW, y + barH, 0xFF111820);
        ctx.fill(bx, y, bx + (int)(barW * pct), y + barH, color);
        ctx.drawBorder(bx, y, barW, barH, COL_BORDER);
        ctx.drawText(textRenderer, current + "", bx + barW + 3, y, COL_TEXT, false);
    }

    // ── Status panel (scrollable encounter log) ───────────────────────────────

    private void drawStatusPanel(DrawContext ctx, int x, int y, int w, int h,
                                 int mx, int my) {
        if (handler.isStandby()) {
            drawStandbyStatusPanel(ctx, x, y, w, h);
            return;
        }

        ShipSnapshot ps = handler.getPlayerShip();
        int ty = y;

        if (ps != null) {
            ctx.drawText(textRenderer, "§bHULL:", x + 4, ty, COL_HEADER, false);
            ty += 10;
            float hullPct = (float)ps.hullIntegrity() / Math.max(1, ps.hullMax());
            int hullColor = hullPct > 0.75f ? COL_HULL_OK
                    : hullPct > 0.5f ? COL_GREEN
                    : hullPct > 0.25f ? COL_YELLOW : COL_HULL_CRIT;
            ctx.fill(x + 4, ty, x + 4 + (w - 8), ty + 8, 0xFF111820);
            ctx.fill(x + 4, ty, x + 4 + (int)((w - 8) * hullPct), ty + 8, hullColor);
            ctx.drawBorder(x + 4, ty, w - 8, 8, COL_BORDER);
            ctx.drawText(textRenderer,
                    ps.hullIntegrity() + "/" + ps.hullMax() + " [" + ps.hullState() + "]",
                    x + 4, ty + 10, hullColor, false);
            ty += 22;
            ctx.drawText(textRenderer, "§bPOWER:", x + 4, ty, COL_HEADER, false);
            ty += 10;
            ctx.drawText(textRenderer,
                    "§8Bgt: §f" + ps.powerBudget()
                            + "  §8Wpn: §c" + ps.weaponsPower()
                            + "  §8Shld: §b" + ps.shieldsPower(),
                    x + 4, ty, COL_TEXT, false);
            ty += 10;
            ctx.drawText(textRenderer,
                    "§8Eng: §a" + ps.enginesPower()
                            + "  §8Sens: §e" + ps.sensorsPower()
                            + "  §8Warp: §f" + ps.warpSpeed(),
                    x + 4, ty, COL_TEXT, false);
            ty += 14;
        }

        // Log header with ▲/▼ scroll buttons
        ctx.drawText(textRenderer, "§bENCOUNTER LOG:", x + 4, ty, COL_HEADER, false);
        int scrollBtnX = x + w - 20;
        boolean upHov = inBounds(mx, my, scrollBtnX,      ty, 8, 8);
        boolean dnHov = inBounds(mx, my, scrollBtnX + 10, ty, 8, 8);
        ctx.fill(scrollBtnX,      ty, scrollBtnX + 8,  ty + 8, upHov ? COL_BTN_HOV : COL_BTN);
        ctx.fill(scrollBtnX + 10, ty, scrollBtnX + 18, ty + 8, dnHov ? COL_BTN_HOV : COL_BTN);
        ctx.drawBorder(scrollBtnX,      ty, 8, 8, COL_BORDER);
        ctx.drawBorder(scrollBtnX + 10, ty, 8, 8, COL_BORDER);
        ctx.drawText(textRenderer, "▲", scrollBtnX + 1,      ty + 1, COL_TEXT, false);
        ctx.drawText(textRenderer, "▼", scrollBtnX + 10 + 1, ty + 1, COL_TEXT, false);
        ty += 10;

        List<String> log       = handler.getCombatLog();
        int lineH              = 8;
        int logAreaH           = y + h - ty;
        int visibleLines       = Math.max(1, logAreaH / lineH);
        int maxScroll          = Math.max(0, log.size() - visibleLines);
        if (logScrollOffset > maxScroll) logScrollOffset = maxScroll;
        if (logScrollOffset < 0)         logScrollOffset = 0;

        int logStart = logScrollOffset;
        int logEnd   = Math.min(log.size(), logStart + visibleLines);

        for (int i = logStart; i < logEnd; i++) {
            String entry   = log.get(i);
            int bracket    = entry.indexOf(']');
            String display = bracket >= 0 ? entry.substring(bracket + 2) : entry;
            String truncated = display.length() > 38
                    ? display.substring(0, 36) + ".." : display;
            ctx.drawText(textRenderer, "§8" + truncated, x + 4, ty, COL_DIM, false);
            if (display.length() > 38
                    && inBounds(mx, my, x + 4, ty - 1, HALF_W - 8, 9)) {
                hoveredTooltip = display;
                tooltipX = mx; tooltipY = my;
            }
            ty += lineH;
        }

        if (log.size() > visibleLines) {
            String indicator = (logScrollOffset + 1) + "-" + logEnd + "/" + log.size();
            ctx.drawText(textRenderer, "§8" + indicator,
                    x + w - textRenderer.getWidth(indicator) - 2,
                    y + h - 8, COL_DIM, false);
        }
    }

    private void drawStandbyStatusPanel(DrawContext ctx, int x, int y, int w, int h) {
        ShipSnapshot snap = handler.getHomeShipSnapshot();
        ctx.drawText(textRenderer, "§7TACTICAL ONLINE", x + 4, y + 4, COL_HEADER, false);
        if (snap == null) {
            ctx.drawText(textRenderer, "§8Awaiting home ship data.", x + 4, y + 16, COL_DIM, false);
            return;
        }
        int ty = y + 16;
        ctx.drawText(textRenderer, "§bHULL:", x + 4, ty, COL_HEADER, false);
        ty += 10;
        float hullPct = (float)snap.hullIntegrity() / Math.max(1, snap.hullMax());
        int hullColor = hullPct > 0.75f ? COL_HULL_OK
                : hullPct > 0.5f ? COL_GREEN
                : hullPct > 0.25f ? COL_YELLOW : COL_HULL_CRIT;
        ctx.fill(x + 4, ty, x + 4 + (w - 8), ty + 8, 0xFF111820);
        ctx.fill(x + 4, ty, x + 4 + (int)((w - 8) * hullPct), ty + 8, hullColor);
        ctx.drawBorder(x + 4, ty, w - 8, 8, COL_BORDER);
        ctx.drawText(textRenderer, snap.hullIntegrity() + "/" + snap.hullMax(),
                x + 4, ty + 10, hullColor, false);
        ty += 22;
        ctx.drawText(textRenderer, "§bPOWER:", x + 4, ty, COL_HEADER, false);
        ty += 10;
        ctx.drawText(textRenderer,
                "§8Budget: §f" + snap.powerBudget()
                        + "  §8Wpn: §c" + snap.weaponsPower()
                        + "  §8Shld: §b" + snap.shieldsPower(),
                x + 4, ty, COL_TEXT, false);
        ty += 10;
        ctx.drawText(textRenderer,
                "§8Eng: §a" + snap.enginesPower()
                        + "  §8Sens: §e" + snap.sensorsPower()
                        + "  §8Warp: §f" + snap.warpSpeed(),
                x + 4, ty, COL_TEXT, false);
        ty += 14;
        String warpStr = snap.warpCapable()
                ? "§bWARP " + snap.warpSpeed() + " ENGAGED" : "§8SUBLIGHT ONLY";
        ctx.drawText(textRenderer, warpStr, x + 4, ty, COL_DIM, false);
    }

    // ── Expanded map overlay ──────────────────────────────────────────────────

    private void drawExpandedMap(DrawContext ctx, int mx, int my) {
        int ex = this.x, ey = this.y;
        int ew = HALF_W * 2, eh = HALF_H * 2 + PANEL_Y;
        ctx.fill(ex, ey, ex + ew, ey + eh, COL_BG);
        ctx.drawBorder(ex, ey, ew, eh, COL_HEADER);
        ctx.fill(ex, ey, ex + ew, ey + 12, 0xFF0A1A2F);
        ctx.drawText(textRenderer, "§b— TACTICAL MAP —",
                ex + ew / 2 - 45, ey + 2, COL_HEADER, false);

        int closeBtnX = ex + ew - 38, closeBtnY = ey + 1;
        boolean closeHov = inBounds(mx, my, closeBtnX, closeBtnY, 36, 11);
        ctx.fill(closeBtnX, closeBtnY, closeBtnX + 36, closeBtnY + 11,
                closeHov ? COL_BTN_HOV : COL_BTN);
        ctx.drawBorder(closeBtnX, closeBtnY, 36, 11, COL_BORDER);
        ctx.drawText(textRenderer, "§8CLOSE", closeBtnX + 5, closeBtnY + 2, COL_DIM, false);

        int mapArea_x = ex + 8, mapArea_y = ey + 16;
        int mapArea_w = ew - 16, mapArea_h = eh - 24;
        int mapCX = mapArea_x + mapArea_w / 2;
        int mapCY = mapArea_y + mapArea_h / 2;

        if (handler.isStandby()) {
            drawStandbyMap(ctx, mapArea_x, mapArea_y, mapArea_w, mapArea_h - 20,
                    mapCX, mapCY, mx, my);
            if (handler.canUseHelm()) {
                int ctrlY = mapArea_y + mapArea_h - 18;
                ctx.drawText(textRenderer, "§8STELLAR NAVIGATION",
                        ex + 8, ctrlY - 2, COL_DIM, false);
                ctrlY += 8;
                boolean hHov = inBounds(mx, my, ex + 8, ctrlY, 70, 11);
                ctx.fill(ex + 8, ctrlY, ex + 78, ctrlY + 11,
                        editingHeading ? 0xFF0A2850 : (hHov ? COL_BTN_HOV : COL_BTN));
                ctx.drawBorder(ex + 8, ctrlY, 70, 11,
                        editingHeading ? COL_HEADER : COL_BORDER);
                ctx.drawText(textRenderer,
                        "H:" + (editingHeading ? helmInputBuffer + "_" : (int)inputHeading + "°"),
                        ex + 11, ctrlY + 2, COL_TEXT, false);
                boolean sHov = inBounds(mx, my, ex + 82, ctrlY, 55, 11);
                ctx.fill(ex + 82, ctrlY, ex + 137, ctrlY + 11,
                        editingSpeed ? 0xFF0A2850 : (sHov ? COL_BTN_HOV : COL_BTN));
                ctx.drawBorder(ex + 82, ctrlY, 55, 11,
                        editingSpeed ? COL_HEADER : COL_BORDER);
                ctx.drawText(textRenderer,
                        "S:" + (editingSpeed ? helmInputBuffer + "_"
                                : String.format("%.1f", inputSpeed)),
                        ex + 85, ctrlY + 2, COL_TEXT, false);
                boolean setHov = inBounds(mx, my, ex + 141, ctrlY, 40, 11);
                ctx.fill(ex + 141, ctrlY, ex + 181, ctrlY + 11,
                        setHov ? COL_BTN_HOV : COL_BTN);
                ctx.drawBorder(ex + 141, ctrlY, 40, 11, COL_BORDER);
                ctx.drawText(textRenderer, "SET", ex + 153, ctrlY + 2, COL_GREEN, false);
            }
        } else {
            drawEncounterMap(ctx, mapArea_x, mapArea_y, mapArea_w, mapArea_h,
                    mapCX, mapCY, mx, my);
        }
    }

    private void drawExpandButton(DrawContext ctx, int bx, int by, int bw, int bh,
                                  int mx, int my) {
        boolean hov = inBounds(mx, my, bx, by, bw, bh);
        ctx.fill(bx, by, bx + bw, by + bh, hov ? COL_BTN_HOV : COL_BTN);
        ctx.drawBorder(bx, by, bw, bh, COL_BORDER);
        String label = mapExpanded ? "§8CLOSE" : "§b⤢ MAP";
        int lw = textRenderer.getWidth(label);
        ctx.drawText(textRenderer, label, bx + (bw - lw) / 2, by + 3, COL_TEXT, false);
    }

    private void drawShieldRing(DrawContext ctx, int sx, int sz, ShipSnapshot ship) {
        int maxS = Math.max(1,
                ship.shieldFore() + ship.shieldAft()
                        + ship.shieldPort() + ship.shieldStarboard());
        drawShieldDot(ctx, sx,     sz - 5, ship.shieldFore(),      maxS / 4);
        drawShieldDot(ctx, sx,     sz + 5, ship.shieldAft(),       maxS / 4);
        drawShieldDot(ctx, sx - 5, sz,     ship.shieldPort(),      maxS / 4);
        drawShieldDot(ctx, sx + 5, sz,     ship.shieldStarboard(), maxS / 4);
    }

    private void drawShieldDot(DrawContext ctx, int x, int y, int current, int max) {
        float pct = max <= 0 ? 0 : (float)current / max;
        int color = pct > 0.5f ? COL_SHIELD_OK
                : pct > 0.2f ? COL_YELLOW
                : pct > 0    ? COL_SHIELD_LOW
                : COL_RED;
        ctx.fill(x - 1, y - 1, x + 1, y + 1, color);
    }

    private void drawCircleApprox(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI * 2 / 16;
            int px = cx + (int)(Math.cos(angle) * r);
            int py = cy + (int)(Math.sin(angle) * r);
            ctx.fill(px, py, px + 1, py + 1, color);
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        int ox = this.x, oy = this.y;

        // Expand button
        {
            int navPanelX  = ox + 4;
            int navPanelY  = oy + PANEL_Y + 12;
            int navPanelW  = HALF_W - 8;
            int expandBtnX = navPanelX + navPanelW - 36;
            int expandBtnY = navPanelY + 1;
            if (inBounds(mx, my, expandBtnX, expandBtnY, 32, 11)) {
                mapExpanded = !mapExpanded;
                return true;
            }
        }

        if (mapExpanded) {
            int ew = HALF_W * 2, eh = HALF_H * 2 + PANEL_Y;
            int closeBtnX = ox + ew - 38, closeBtnY = oy + 1;
            if (inBounds(mx, my, closeBtnX, closeBtnY, 36, 11)) {
                mapExpanded = false; return true;
            }
            if (handler.isStandby() && handler.canUseHelm()) {
                int ctrlY = oy + eh - 16 - 8;
                if (inBounds(mx, my, ox + 8, ctrlY, 70, 11)) {
                    editingHeading = true; editingSpeed = false;
                    helmInputBuffer = String.valueOf((int)inputHeading); return true;
                }
                if (inBounds(mx, my, ox + 82, ctrlY, 55, 11)) {
                    editingSpeed = true; editingHeading = false;
                    helmInputBuffer = String.format("%.1f", inputSpeed); return true;
                }
                if (inBounds(mx, my, ox + 141, ctrlY, 40, 11)) {
                    editingHeading = editingSpeed = false;
                    sendHelmInput(); return true;
                }
            }
            if (handler.isStandby()) {
                int mapX = ox + 8, mapY = oy + 16;
                if (inBounds(mx, my, mapX + 4, mapY + 2, 14, 11)) {
                    mapZoom = Math.min(ZOOM_MAX, mapZoom * 2.0f); return true;
                }
                if (inBounds(mx, my, mapX + 20, mapY + 2, 14, 11)) {
                    mapZoom = Math.max(ZOOM_MIN, mapZoom * 0.5f); return true;
                }
            }
            return true;
        }

        boolean inStandby = handler.isStandby();

        if (!inStandby) {
            int wpx = ox + HALF_W + 4;
            int wpy = oy + PANEL_Y + 12;
            int wpw = HALF_W - 8;
            int wph = HALF_H - 16;
            ShipSnapshot ps = handler.getPlayerShip();

            // Target selection
            int rowY = wpy + 10;
            for (ShipSnapshot hostile : handler.getHostileShips()) {
                if (hostile.destroyed()) continue;
                if (inBounds(mx, my, wpx + 2, rowY - 1, wpw - 4, 10)) {
                    handler.setSelectedTargetId(hostile.shipId()); return true;
                }
                rowY += 12;
            }

            // Facing selector — must mirror draw offsets exactly
            rowY += 2 + 9; // "+2 gap" then "9 for label"
            ShipState.ShieldFacing[] facings = {
                    ShipState.ShieldFacing.FORE,
                    ShipState.ShieldFacing.AFT,
                    ShipState.ShieldFacing.PORT,
                    ShipState.ShieldFacing.STARBOARD
            };
            int facingBtnW = (wpw - 8 - 6) / 4;
            for (int i = 0; i < 4; i++) {
                int bx = wpx + 4 + i * (facingBtnW + 2);
                if (inBounds(mx, my, bx, rowY, facingBtnW, 11)) {
                    // Toggle — click active facing again to return to AUTO
                    selectedFacing = (facings[i] == selectedFacing) ? null : facings[i];
                    return true;
                }
            }

            // Fire buttons
            if (ps != null) {
                int fireBtnY = wpy + wph - 28;
                int halfBtnW = (wpw - 12) / 2;
                if (handler.canUseWeapons()
                        && handler.getSelectedTargetId() != null
                        && inBounds(mx, my, wpx + 4, fireBtnY, halfBtnW, 13)) {
                    sendWeaponFire("PHASER"); return true;
                }
                if (handler.canUseWeapons()
                        && handler.getSelectedTargetId() != null
                        && ps.torpedoCount() > 0
                        && inBounds(mx, my, wpx + halfBtnW + 8, fireBtnY, halfBtnW, 13)) {
                    sendWeaponFire("TORPEDO"); return true;
                }
                if (handler.canUseHelm()
                        && inBounds(mx, my, wpx + 4, fireBtnY + 16, wpw - 8, 11)) {
                    sendEvasive(); return true;
                }
            }

            // Log scroll buttons — must mirror drawStatusPanel ty calculation
            {
                int stx = ox + HALF_W + 4;
                int sty = oy + PANEL_Y + HALF_H + 12;
                int stw = HALF_W - 8;
                // Replicate ty offset: hull block (10+8+10+22=50 if ps!=null) + power (10+10+10+14=44)
                int ty = sty + (ps != null ? 46 + 10 + 10 + 14 : 0);
                int scrollBtnX = stx + stw - 20;
                if (inBounds(mx, my, scrollBtnX, ty, 8, 8)) {
                    logScrollOffset = Math.max(0, logScrollOffset - 1); return true;
                }
                if (inBounds(mx, my, scrollBtnX + 10, ty, 8, 8)) {
                    logScrollOffset++; return true;
                }
            }

            // Shield balance
            int shpx = ox + 4;
            int shpy = oy + PANEL_Y + HALF_H + 12;
            int shph = HALF_H - 16;
            if (handler.canUseShields()
                    && inBounds(mx, my, shpx + 4, shpy + shph - 14, HALF_W - 12, 12)) {
                sendBalanceShields(); return true;
            }
        }

        // Zoom buttons (standby nav map)
        if (handler.isStandby()) {
            int mapX = ox + 4, mapY = oy + PANEL_Y + 12;
            if (inBounds(mx, my, mapX + 4, mapY + 2, 14, 11)) {
                mapZoom = Math.min(ZOOM_MAX, mapZoom * 2.0f); return true;
            }
            if (inBounds(mx, my, mapX + 20, mapY + 2, 14, 11)) {
                mapZoom = Math.max(ZOOM_MIN, mapZoom * 0.5f); return true;
            }
        }

        // Helm input
        if (handler.canUseHelm()) {
            int helmPanelX = ox + 4;
            int helmPanelY = oy + PANEL_Y + 12;
            int helmPanelH = HALF_H - 16;
            int ctrlY      = helmPanelY + helmPanelH - 20 + 10;
            if (inBounds(mx, my, helmPanelX + 4, ctrlY, 70, 11)) {
                editingHeading = true; editingSpeed = false;
                helmInputBuffer = String.valueOf((int)inputHeading); return true;
            }
            if (inBounds(mx, my, helmPanelX + 78, ctrlY, 55, 11)) {
                editingSpeed = true; editingHeading = false;
                helmInputBuffer = String.format("%.1f", inputSpeed); return true;
            }
            if (inBounds(mx, my, helmPanelX + 137, ctrlY, 40, 11)) {
                editingHeading = editingSpeed = false;
                sendHelmInput(); return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        int ox = this.x, oy = this.y;

        // Scroll over status panel → scroll encounter log
        if (!handler.isStandby()) {
            int stx = ox + HALF_W + 4;
            int sty = oy + PANEL_Y + HALF_H + 12;
            if (inBounds(mx, my, stx, sty, HALF_W - 8, HALF_H - 16)) {
                if (vAmount > 0) logScrollOffset = Math.max(0, logScrollOffset - 1);
                else             logScrollOffset++;
                return true;
            }
        }

        // Scroll over nav panel → zoom map
        if (handler.isStandby()) {
            int navX = ox + 4, navY = oy + PANEL_Y + 12;
            if (inBounds(mx, my, navX, navY, HALF_W - 8, HALF_H - 16) || mapExpanded) {
                if (vAmount > 0) mapZoom = Math.min(ZOOM_MAX, mapZoom * 1.25f);
                else             mapZoom = Math.max(ZOOM_MIN, mapZoom / 1.25f);
                return true;
            }
        }

        return super.mouseScrolled(mx, my, hAmount, vAmount);
    }

    // ── Keyboard ──────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (editingHeading || editingSpeed) {
            if (key == 256) { editingHeading = editingSpeed = false; helmInputBuffer = ""; return true; }
            if (key == 257 || key == 335) { commitHelmInput(); return true; }
            if (key == 259 && !helmInputBuffer.isEmpty()) {
                helmInputBuffer = helmInputBuffer.substring(0, helmInputBuffer.length() - 1);
                return true;
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        if (editingHeading || editingSpeed) {
            if ((Character.isDigit(chr) || chr == '.' || chr == '-')
                    && helmInputBuffer.length() < 6)
                helmInputBuffer += chr;
            return true;
        }
        return super.charTyped(chr, mods);
    }

    // ── Packet sends ──────────────────────────────────────────────────────────

    private void sendHelmInput() {
        ShipSnapshot ps = handler.getPlayerShip();
        String encId  = ps != null ? handler.getEncounterId() : "";
        String shipId = ps != null ? ps.shipId() : "";
        ClientPlayNetworking.send(new net.shard.seconddawnrp.tactical.network
                .TacticalNetworking.HelmInputPayload(
                encId, shipId, inputHeading, inputSpeed, false));
    }

    private void commitHelmInput() {
        try {
            if (editingHeading) inputHeading = Float.parseFloat(helmInputBuffer) % 360;
            if (editingSpeed)   inputSpeed   = Math.max(0, Float.parseFloat(helmInputBuffer));
        } catch (NumberFormatException ignored) {}
        editingHeading = editingSpeed = false;
        helmInputBuffer = "";
        sendHelmInput();
    }

    private void sendWeaponFire(String type) {
        ShipSnapshot ps = handler.getPlayerShip();
        if (ps == null || handler.getSelectedTargetId() == null) return;
        String facingStr = selectedFacing != null ? selectedFacing.name() : "AUTO";
        ClientPlayNetworking.send(new net.shard.seconddawnrp.tactical.network
                .TacticalNetworking.WeaponFirePayload(
                handler.getEncounterId(), ps.shipId(),
                handler.getSelectedTargetId(), type, facingStr));
    }

    private void sendEvasive() {
        ShipSnapshot ps = handler.getPlayerShip();
        if (ps == null) return;
        ClientPlayNetworking.send(new net.shard.seconddawnrp.tactical.network
                .TacticalNetworking.HelmInputPayload(
                handler.getEncounterId(), ps.shipId(), 0, 0, true));
    }

    private void sendBalanceShields() {
        ShipSnapshot ps = handler.getPlayerShip();
        if (ps == null) return;
        int quarter = ps.powerBudget() / 4;
        ClientPlayNetworking.send(new net.shard.seconddawnrp.tactical.network
                .TacticalNetworking.ShieldDistributePayload(
                handler.getEncounterId(), ps.shipId(),
                quarter, quarter, quarter, quarter));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void drawPanelHeader(DrawContext ctx, int x, int y, int w,
                                 String label, int mx, int my) {
        ctx.fill(x, y, x + w, y + 12, 0xFF0A1828);
        ctx.drawText(textRenderer, label,
                x + (w - textRenderer.getWidth(label)) / 2,
                y + 2, COL_HEADER, false);
    }

    private void drawTacticalButton(DrawContext ctx, int bx, int by, int bw, int bh,
                                    String label, boolean enabled, int mx, int my) {
        boolean hov = enabled && inBounds(mx, my, bx, by, bw, bh);
        int bg = !enabled ? 0xFF0A1020 : hov ? COL_BTN_HOV : COL_BTN;
        ctx.fill(bx, by, bx + bw, by + bh, bg);
        ctx.drawBorder(bx, by, bw, bh, enabled ? COL_BORDER : 0xFF222233);
        int lw = textRenderer.getWidth(label);
        ctx.drawText(textRenderer, label,
                bx + (bw - lw) / 2, by + (bh - 7) / 2 + 1,
                enabled ? 0xFFFFFFFF : COL_DIM, false);
    }

    private static String hullColor(float pct) {
        if (pct > 0.5f)  return "§a";
        if (pct > 0.25f) return "§e";
        return "§c";
    }

    private boolean inBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override public boolean shouldPause() { return false; }
}