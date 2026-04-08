package net.shard.seconddawnrp.tactical.console;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.tactical.network.TacticalNetworking.*;

import java.util.ArrayList;
import java.util.List;

/**
 * GM Ship Control Screen — opened via /gm encounter or a dedicated GM block.
 * Allows GM to control any ship in the encounter directly.
 *
 * Controls:
 *   Ship selector dropdown
 *   Heading/Speed manual input
 *   AI Mode selector (Phase 13 — stubbed)
 *   Weapon fire buttons (GM bypass)
 *   Power reroute sliders
 *   Combat log
 */
public class GmShipConsoleScreen extends Screen {

    private static final int GUI_W = 380;
    private static final int GUI_H = 240;

    private final String encounterId;
    private final List<ShipSnapshot> ships;
    private List<String> combatLog = new ArrayList<>();

    private int ox, oy;
    private int selectedShipIdx = 0;
    private String selectedTargetId = null;

    // Tab
    private enum GmTab { SHIPS, MAP }
    private GmTab activeTab = GmTab.SHIPS;

    // Tooltip
    private String hoveredLogTooltip = null;
    private int tooltipX, tooltipY;

    // Input state
    private float   inputHeading   = 0;
    private float   inputSpeed     = 0;
    private boolean editingHeading = false;
    private boolean editingSpeed   = false;
    private String  inputBuffer    = "";

    // Colors
    private static final int COL_BG      = 0xE0080E18;
    private static final int COL_HEADER  = 0xFF00AAFF;
    private static final int COL_TEXT    = 0xFFCCEEFF;
    private static final int COL_DIM     = 0xFF667799;
    private static final int COL_BORDER  = 0xFF1A4A6A;
    private static final int COL_BTN     = 0xFF0A2040;
    private static final int COL_BTN_HOV = 0xFF1A3A60;
    private static final int COL_HOSTILE = 0xFFFF3333;
    private static final int COL_FRIEND  = 0xFF3399FF;

    public GmShipConsoleScreen(String encounterId, List<ShipSnapshot> ships,
                               List<String> combatLog) {
        super(Text.literal("GM Ship Console"));
        this.encounterId = encounterId;
        this.ships       = new ArrayList<>(ships);
        this.combatLog   = new ArrayList<>(combatLog);
    }

    public void applyUpdate(EncounterUpdatePayload payload) {
        this.ships.clear();
        this.ships.addAll(payload.ships());
        this.combatLog = new ArrayList<>(payload.recentLog());
        if (selectedShipIdx >= ships.size()) selectedShipIdx = 0;
    }

    @Override
    protected void init() {
        super.init();
        ox = (this.width  - GUI_W) / 2;
        oy = (this.height - GUI_H) / 2;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // suppress — solid fill in render()
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        hoveredLogTooltip = null;

        ctx.fill(ox, oy, ox + GUI_W, oy + GUI_H, COL_BG);
        ctx.fill(ox, oy, ox + GUI_W, oy + 20, 0xFF1A2840);
        ctx.drawBorder(ox, oy, GUI_W, GUI_H, COL_BORDER);

        ctx.drawText(textRenderer, "GM SHIP CONSOLE — " + encounterId,
                ox + (GUI_W - textRenderer.getWidth("GM SHIP CONSOLE — " + encounterId)) / 2,
                oy + 6, COL_HEADER, false);

        drawGmTab(ctx, ox + 4,  oy + 22, 55, 13, "SHIPS", GmTab.SHIPS, mx, my);
        drawGmTab(ctx, ox + 62, oy + 22, 55, 13, "MAP",   GmTab.MAP,   mx, my);
        ctx.fill(ox + 4, oy + 35, ox + GUI_W - 4, oy + 36, COL_BORDER);

        if (activeTab == GmTab.MAP) {
            drawGmMap(ctx, ox + 4, oy + 38, GUI_W - 8, GUI_H - 42, mx, my);
        } else {
            int listX = ox + 4;
            int listY = oy + 40;
            ctx.drawText(textRenderer, "§bSHIPS:", listX, listY, COL_HEADER, false);
            listY += 10;
            for (int i = 0; i < ships.size(); i++) {
                ShipSnapshot s = ships.get(i);
                boolean sel = i == selectedShipIdx;
                boolean hov = inBounds(mx, my, listX, listY - 1, 100, 10);
                if (sel) ctx.fill(listX - 1, listY - 1, listX + 101, listY + 9, 0x33FFFFFF);
                else if (hov) ctx.fill(listX - 1, listY - 1, listX + 101, listY + 9, 0x11FFFFFF);
                int c = "FRIENDLY".equals(s.faction()) ? COL_FRIEND : COL_HOSTILE;
                ctx.drawText(textRenderer,
                        (sel ? "►" : " ") + s.combatId() + " "
                                + s.registryName().substring(0, Math.min(8, s.registryName().length())),
                        listX + 2, listY, c, false);
                listY += 11;
            }

            if (!ships.isEmpty() && selectedShipIdx < ships.size()) {
                ShipSnapshot ship = ships.get(selectedShipIdx);
                drawShipControls(ctx, ox + 114, oy + 38, GUI_W - 118, GUI_H - 42,
                        ship, mx, my);
            }
        }

        super.render(ctx, mx, my, delta);

        if (hoveredLogTooltip != null) {
            ctx.drawTooltip(textRenderer,
                    java.util.List.of(Text.literal(hoveredLogTooltip)),
                    tooltipX, tooltipY);
        }
    }

    private void drawShipControls(DrawContext ctx, int x, int y, int w, int h,
                                  ShipSnapshot ship, int mx, int my) {
        ctx.drawBorder(x - 2, y - 2, w + 4, h + 4, COL_BORDER);

        ctx.drawText(textRenderer,
                "§f" + ship.registryName() + " §8[" + ship.combatId() + "]",
                x, y, COL_TEXT, false);
        ctx.drawText(textRenderer,
                "§8Hull: §f" + ship.hullIntegrity() + "/" + ship.hullMax()
                        + " §8| " + ship.hullState(),
                x, y + 10, COL_DIM, false);

        y += 22;
        ctx.drawText(textRenderer, "§bHELM:", x, y, COL_HEADER, false);
        y += 10;
        ctx.drawText(textRenderer,
                "§8Heading: §f" + (int)ship.heading()
                        + "°  Speed: " + String.format("%.1f", ship.speed()),
                x, y, COL_DIM, false);
        y += 10;

        boolean hHov = inBounds(mx, my, x, y, 80, 12);
        ctx.fill(x, y, x + 80, y + 12,
                editingHeading ? 0xFF0A2850 : (hHov ? COL_BTN_HOV : COL_BTN));
        ctx.drawBorder(x, y, 80, 12, COL_BORDER);
        ctx.drawText(textRenderer,
                "HDG: " + (editingHeading ? inputBuffer + "_" : (int)inputHeading + "°"),
                x + 3, y + 2, COL_TEXT, false);

        boolean sHov = inBounds(mx, my, x + 84, y, 60, 12);
        ctx.fill(x + 84, y, x + 144, y + 12,
                editingSpeed ? 0xFF0A2850 : (sHov ? COL_BTN_HOV : COL_BTN));
        ctx.drawBorder(x + 84, y, 60, 12, COL_BORDER);
        ctx.drawText(textRenderer,
                "SPD: " + (editingSpeed ? inputBuffer + "_" : String.format("%.1f", inputSpeed)),
                x + 87, y + 2, COL_TEXT, false);

        boolean setHov = inBounds(mx, my, x + 148, y, 50, 12);
        ctx.fill(x + 148, y, x + 198, y + 12, setHov ? COL_BTN_HOV : COL_BTN);
        ctx.drawBorder(x + 148, y, 50, 12, COL_BORDER);
        ctx.drawText(textRenderer, "SET HELM", x + 151, y + 2, COL_TEXT, false);
        y += 16;

        ctx.drawText(textRenderer, "§bWEAPONS:", x, y, COL_HEADER, false);
        y += 10;
        List<ShipSnapshot> enemies = ships.stream()
                .filter(s -> !s.faction().equals(ship.faction()) && !s.destroyed()).toList();
        for (ShipSnapshot enemy : enemies) {
            boolean sel = enemy.shipId().equals(selectedTargetId);
            boolean hov = inBounds(mx, my, x, y - 1, 120, 10);
            if (sel) ctx.fill(x - 1, y - 1, x + 121, y + 9, 0x33FF3333);
            else if (hov) ctx.fill(x - 1, y - 1, x + 121, y + 9, 0x11FFFFFF);
            ctx.drawText(textRenderer,
                    (sel ? "§c► " : "§7○ ") + enemy.combatId()
                            + " §8(" + enemy.hullIntegrity() + "hp)", x, y, COL_HOSTILE, false);
            y += 10;
        }

        y += 2;
        boolean hasTarget = selectedTargetId != null;
        drawGmButton(ctx, x,      y, 60, 12, "§c FIRE PH", hasTarget, mx, my);
        drawGmButton(ctx, x + 64, y, 60, 12, "§e TORPEDO",
                hasTarget && ship.torpedoCount() > 0, mx, my);
        y += 16;

        ctx.drawText(textRenderer, "§bSHIELDS:", x, y, COL_HEADER, false);
        y += 10;
        int shieldTotal = ship.shieldFore() + ship.shieldAft()
                + ship.shieldPort() + ship.shieldStarboard();
        int shieldMax = Math.max(1, shieldTotal);
        drawGmShieldBar(ctx, x, y,      w - 4, "F", ship.shieldFore(),      shieldMax / 4);
        drawGmShieldBar(ctx, x, y + 9,  w - 4, "A", ship.shieldAft(),       shieldMax / 4);
        drawGmShieldBar(ctx, x, y + 18, w - 4, "P", ship.shieldPort(),      shieldMax / 4);
        drawGmShieldBar(ctx, x, y + 27, w - 4, "S", ship.shieldStarboard(), shieldMax / 4);
        y += 38;

        ctx.drawText(textRenderer, "§bLOG:", x, y, COL_HEADER, false);
        y += 10;
        int logStart = Math.max(0, combatLog.size() - 4);
        for (int i = logStart; i < combatLog.size() && y < oy + GUI_H - 8; i++) {
            String entry   = combatLog.get(i);
            int bracket    = entry.indexOf(']');
            String display = bracket >= 0 ? entry.substring(bracket + 2) : entry;
            String truncated = display.length() > 45
                    ? display.substring(0, 43) + ".." : display;
            ctx.drawText(textRenderer, "§8" + truncated, x, y, COL_DIM, false);
            if (display.length() > 45 && inBounds(mx, my, x, y - 1, w, 9)) {
                hoveredLogTooltip = display;
                tooltipX = (int)mx;
                tooltipY = (int)my;
            }
            y += 8;
        }
    }

    private void drawGmTab(DrawContext ctx, int tx, int ty, int tw, int th,
                           String label, GmTab tab, int mx, int my) {
        boolean active = activeTab == tab;
        boolean hov    = inBounds(mx, my, tx, ty, tw, th);
        ctx.fill(tx, ty, tx + tw, ty + th,
                active ? 0xFF1A3050 : (hov ? 0xFF142030 : 0xFF0A1828));
        ctx.drawBorder(tx, ty, tw, th, active ? COL_HEADER : COL_BORDER);
        int lw = textRenderer.getWidth(label);
        ctx.drawText(textRenderer, label,
                tx + (tw - lw) / 2, ty + 3,
                active ? COL_HEADER : COL_DIM, false);
    }

    private void drawGmMap(DrawContext ctx, int x, int y, int w, int h,
                           int mx, int my) {
        ctx.drawBorder(x, y, w, h, COL_BORDER);
        int mapCX = x + w / 2;
        int mapCY = y + h / 2;
        float scale = 0.12f;

        for (int ring : new int[]{40, 80, 120})
            drawGmCircle(ctx, mapCX, mapCY, (int)(ring * scale), COL_BORDER);

        ctx.fill(mapCX - 1, y + 2,  mapCX + 1, y + h - 2, 0x22FFFFFF);
        ctx.fill(x + 2,    mapCY - 1, x + w - 2, mapCY + 1, 0x22FFFFFF);

        for (ShipSnapshot ship : ships) {
            if (ship.destroyed()) continue;
            int sx = mapCX + (int)(ship.posX() * scale);
            int sz = mapCY + (int)(ship.posZ() * scale);
            int color = "FRIENDLY".equals(ship.faction()) ? COL_FRIEND : COL_HOSTILE;

            ctx.fill(sx - 3, sz - 3, sx + 3, sz + 3, color);
            double rad = Math.toRadians(ship.heading());
            int hx = sx + (int)(Math.cos(rad) * 8);
            int hz = sz + (int)(Math.sin(rad) * 8);
            ctx.fill(sx, sz, hx, hz, color);
            ctx.drawText(textRenderer,
                    ship.combatId() + " §8" + String.format("%.0f°", ship.heading()),
                    sx + 4, sz - 4, color, false);

            int maxS = Math.max(1, (ship.shieldFore() + ship.shieldAft()
                    + ship.shieldPort() + ship.shieldStarboard()) / 4);
            drawMapShieldDot(ctx, sx,     sz - 6, ship.shieldFore(),      maxS);
            drawMapShieldDot(ctx, sx,     sz + 6, ship.shieldAft(),       maxS);
            drawMapShieldDot(ctx, sx - 6, sz,     ship.shieldPort(),      maxS);
            drawMapShieldDot(ctx, sx + 6, sz,     ship.shieldStarboard(), maxS);

            if (inBounds(mx, my, sx - 6, sz - 6, 12, 12)) {
                hoveredLogTooltip = ship.registryName()
                        + " | Hull: " + ship.hullIntegrity() + "/" + ship.hullMax()
                        + " | Spd: " + String.format("%.1f", ship.speed())
                        + " | Warp: " + ship.warpSpeed();
                tooltipX = mx; tooltipY = my;
            }
        }

        ctx.drawText(textRenderer, "§9■ FRIENDLY  §c■ HOSTILE",
                x + 4, y + h - 10, 0xFFAAAAAA, false);
    }

    private void drawMapShieldDot(DrawContext ctx, int x, int y, int current, int max) {
        float pct = max <= 0 ? 0 : (float)current / max;
        int color = pct > 0.5f ? 0xFF2266FF
                : pct > 0.2f ? 0xFFFFBB33
                : pct > 0    ? 0xFFFF6622
                : 0xFF440000;
        ctx.fill(x - 1, y - 1, x + 1, y + 1, color);
    }

    private void drawGmCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int i = 0; i < 20; i++) {
            double angle = i * Math.PI * 2 / 20;
            int px = cx + (int)(Math.cos(angle) * r);
            int py = cy + (int)(Math.sin(angle) * r);
            ctx.fill(px, py, px + 1, py + 1, color);
        }
    }

    private void drawGmShieldBar(DrawContext ctx, int x, int y, int w,
                                 String label, int current, int max) {
        float pct = max <= 0 ? 0 : Math.min(1f, (float)current / max);
        int color = pct > 0.5f ? 0xFF2266FF : pct > 0.2f ? 0xFFFFBB33 : 0xFFFF4444;
        ctx.drawText(textRenderer, label, x, y, COL_DIM, false);
        int bx = x + 10;
        int bw = w - 30;
        ctx.fill(bx, y, bx + bw, y + 7, 0xFF111820);
        ctx.fill(bx, y, bx + (int)(bw * pct), y + 7, color);
        ctx.drawBorder(bx, y, bw, 7, COL_BORDER);
        ctx.drawText(textRenderer, String.valueOf(current), bx + bw + 3, y, COL_TEXT, false);
    }

    private void drawGmButton(DrawContext ctx, int bx, int by, int bw, int bh,
                              String label, boolean enabled, int mx, int my) {
        boolean hov = enabled && inBounds(mx, my, bx, by, bw, bh);
        ctx.fill(bx, by, bx + bw, by + bh, hov ? COL_BTN_HOV : COL_BTN);
        ctx.drawBorder(bx, by, bw, bh, enabled ? COL_BORDER : 0xFF222233);
        ctx.drawText(textRenderer, label,
                bx + (bw - textRenderer.getWidth(label)) / 2, by + 2,
                enabled ? 0xFFFFFFFF : COL_DIM, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        if (inBounds(mx, my, ox + 4,  oy + 22, 55, 13)) { activeTab = GmTab.SHIPS; return true; }
        if (inBounds(mx, my, ox + 62, oy + 22, 55, 13)) { activeTab = GmTab.MAP;   return true; }

        if (activeTab == GmTab.MAP) return super.mouseClicked(mx, my, button);

        // Ship list selection
        int listY = oy + 50;
        for (int i = 0; i < ships.size(); i++) {
            if (inBounds(mx, my, ox + 4, listY - 1, 100, 10)) {
                selectedShipIdx = i;
                editingHeading  = false;
                editingSpeed    = false;
                inputBuffer     = "";
                return true;
            }
            listY += 11;
        }

        if (ships.isEmpty() || selectedShipIdx >= ships.size())
            return super.mouseClicked(mx, my, button);

        ShipSnapshot ship = ships.get(selectedShipIdx);

        int cx = ox + 114;
        int cy = oy + 24 + 22 + 10 + 10;

        // Heading input
        if (inBounds(mx, my, cx, cy, 80, 12)) {
            editingHeading = true; editingSpeed = false;
            inputBuffer = String.valueOf((int)inputHeading);
            return true;
        }
        // Speed input
        if (inBounds(mx, my, cx + 84, cy, 60, 12)) {
            editingSpeed = true; editingHeading = false;
            inputBuffer = String.valueOf(inputSpeed);
            return true;
        }
        // Set helm
        if (inBounds(mx, my, cx + 148, cy, 50, 12)) {
            editingHeading = false; editingSpeed = false;
            ClientPlayNetworking.send(new HelmInputPayload(
                    encounterId, ship.shipId(), inputHeading, inputSpeed, false));
            return true;
        }

        // Target selection
        int ty = oy + 24 + 22 + 10 + 10 + 16 + 10;
        List<ShipSnapshot> enemies = ships.stream()
                .filter(s -> !s.faction().equals(ship.faction()) && !s.destroyed()).toList();
        for (ShipSnapshot enemy : enemies) {
            if (inBounds(mx, my, cx, ty - 1, 120, 10)) {
                selectedTargetId = enemy.shipId(); return true;
            }
            ty += 10;
        }

        // Fire buttons — use ship.shipId() and selectedTargetId (not bare variables)
        ty += 2;
        if (inBounds(mx, my, cx, ty, 60, 12) && selectedTargetId != null) {
            ClientPlayNetworking.send(new WeaponFirePayload(
                    encounterId, ship.shipId(), selectedTargetId, "PHASER", "AUTO"));
            return true;
        }
        if (inBounds(mx, my, cx + 64, ty, 60, 12)
                && selectedTargetId != null && ship.torpedoCount() > 0) {
            ClientPlayNetworking.send(new WeaponFirePayload(
                    encounterId, ship.shipId(), selectedTargetId, "TORPEDO", "AUTO"));
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (editingHeading || editingSpeed) {
            if (key == 256) {
                editingHeading = editingSpeed = false; inputBuffer = ""; return true;
            }
            if (key == 257 || key == 335) {
                try {
                    if (editingHeading) inputHeading = Float.parseFloat(inputBuffer);
                    else                inputSpeed   = Float.parseFloat(inputBuffer);
                } catch (NumberFormatException ignored) {}
                editingHeading = editingSpeed = false; inputBuffer = "";
                return true;
            }
            if (key == 259 && !inputBuffer.isEmpty()) {
                inputBuffer = inputBuffer.substring(0, inputBuffer.length() - 1);
                return true;
            }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        if ((editingHeading || editingSpeed)
                && (Character.isDigit(chr) || chr == '.' || chr == '-')) {
            if (inputBuffer.length() < 8) inputBuffer += chr;
            return true;
        }
        return super.charTyped(chr, mods);
    }

    private boolean inBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override public boolean shouldPause() { return false; }
}