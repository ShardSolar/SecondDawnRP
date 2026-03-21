package net.shard.seconddawnrp.tasksystem.terminal;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tasksystem.network.AcceptTerminalTaskC2SPacket;
import net.shard.seconddawnrp.tasksystem.terminal.TerminalScreenOpenData.TerminalTaskEntry;

import java.util.List;

public class TerminalScreen extends HandledScreen<TerminalScreenHandler> {

    private static final Identifier TEXTURE =
            SecondDawnRP.id("textures/gui/terminal.png");

    private static final int TEX_WIDTH  = 512;
    private static final int TEX_HEIGHT = 256;
    private static final int GUI_WIDTH  = 380;
    private static final int GUI_HEIGHT = 190;

    private static final int LIST_X      = 20;
    private static final int LIST_Y      = 72;
    private static final int LIST_WIDTH  = 160;
    private static final int LIST_HEIGHT = 100;
    private static final int ROW_HEIGHT  = 20;

    private static final int DETAIL_X      = 196;
    private static final int DETAIL_Y      = 72;
    private static final int DETAIL_WIDTH  = 166;
    private static final int DETAIL_HEIGHT = 82;

    private static final int ACCEPT_X = 196;
    private static final int ACCEPT_Y = 160;
    private static final int ACCEPT_W = 166;
    private static final int ACCEPT_H = 14;

    private static final int TAB_Y = 50;
    private static final int TAB_H = 18;

    // How many rows fit fully in the list panel
    private static final int VISIBLE_ROWS = LIST_HEIGHT / ROW_HEIGHT;

    // Scroll state
    private int listScrollOffset  = 0;  // first visible row index
    private int detailScrollOffset = 0; // first visible line index in detail

    public TerminalScreen(TerminalScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth  = GUI_WIDTH;
        this.backgroundHeight = GUI_HEIGHT;
        this.playerInventoryTitleY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        this.titleX = 0;
        this.titleY = 0;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        context.drawTexture(TEXTURE, x, y, 0, 0,
                backgroundWidth, backgroundHeight, TEX_WIDTH, TEX_HEIGHT);
        drawHeader(context, x, y);
        drawTaskList(context, x, y);
        drawDetailPanel(context, x, y);
        drawAcceptButton(context, x, y);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {}

    // ── Header ────────────────────────────────────────────────────────────────

    private void drawHeader(DrawContext context, int x, int y) {
        int lw = this.textRenderer.getWidth("AVAILABLE TASKS");
        context.drawText(this.textRenderer, "AVAILABLE TASKS",
                x + LIST_X + (LIST_WIDTH - lw) / 2,
                y + TAB_Y + (TAB_H - 8) / 2 + 1,
                0xFF8FD7E8, false);

        int dw = this.textRenderer.getWidth("TASK DETAILS");
        context.drawText(this.textRenderer, "TASK DETAILS",
                x + DETAIL_X + (DETAIL_WIDTH - dw) / 2,
                y + TAB_Y + (TAB_H - 8) / 2 + 1,
                0xFF8FD7E8, false);
    }

    // ── Task list with scroll ─────────────────────────────────────────────────

    private void drawTaskList(DrawContext context, int x, int y) {
        List<TerminalTaskEntry> tasks = handler.getTasks();

        if (tasks.isEmpty()) {
            context.drawText(this.textRenderer, "No tasks available.",
                    x + LIST_X + 4, y + LIST_Y + 4, 0xFFD0D0D0, false);
            return;
        }

        clampListScroll(tasks.size());

        context.enableScissor(
                x + LIST_X, y + LIST_Y,
                x + LIST_X + LIST_WIDTH, y + LIST_Y + LIST_HEIGHT
        );

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int dataIndex = i + listScrollOffset;
            if (dataIndex >= tasks.size()) break;

            int rowX = x + LIST_X;
            int rowY = y + LIST_Y + (i * ROW_HEIGHT);

            boolean selected = dataIndex == handler.getSelectedIndex();
            context.fill(rowX + 2, rowY + 2,
                    rowX + LIST_WIDTH - 2, rowY + ROW_HEIGHT - 2,
                    selected ? 0x20FFFFFF : 0x08000000);

            TerminalTaskEntry entry = tasks.get(dataIndex);
            context.drawText(this.textRenderer, trim(entry.displayName(), 18),
                    rowX + 6, rowY + 4,  0xFFF2E7D5, false);
            context.drawText(this.textRenderer, "[" + entry.divisionName() + "]",
                    rowX + 6, rowY + 12, 0xFF8FD7E8, false);
        }

        context.disableScissor();

        // Scroll indicators
        drawScrollIndicators(context,
                x + LIST_X, y + LIST_Y, LIST_WIDTH, LIST_HEIGHT,
                listScrollOffset, tasks.size(), VISIBLE_ROWS);
    }

    // ── Detail panel with scroll ──────────────────────────────────────────────

    private void drawDetailPanel(DrawContext context, int x, int y) {
        TerminalTaskEntry selected = handler.getSelectedTask();

        if (selected == null) {
            context.enableScissor(x + DETAIL_X, y + DETAIL_Y,
                    x + DETAIL_X + DETAIL_WIDTH, y + DETAIL_Y + DETAIL_HEIGHT);
            context.drawText(this.textRenderer, "Select a task.",
                    x + DETAIL_X + 4, y + DETAIL_Y + 2, 0xFFD0D0D0, false);
            context.disableScissor();
            return;
        }

        // Build all detail lines so we can scroll them
        List<String> lines = buildDetailLines(selected);
        int visibleLineCount = DETAIL_HEIGHT / 10;
        clampDetailScroll(lines.size(), visibleLineCount);

        context.enableScissor(x + DETAIL_X, y + DETAIL_Y,
                x + DETAIL_X + DETAIL_WIDTH, y + DETAIL_Y + DETAIL_HEIGHT);

        int textX = x + DETAIL_X + 4;
        int textY = y + DETAIL_Y + 2;

        for (int i = 0; i < visibleLineCount; i++) {
            int dataIndex = i + detailScrollOffset;
            if (dataIndex >= lines.size()) break;

            String line = lines.get(dataIndex);
            context.drawText(this.textRenderer, line, textX, textY,
                    getDetailLineColor(line), false);
            textY += 10;
        }

        context.disableScissor();

        // Scroll indicators for detail panel
        drawScrollIndicators(context,
                x + DETAIL_X, y + DETAIL_Y, DETAIL_WIDTH, DETAIL_HEIGHT,
                detailScrollOffset, lines.size(), visibleLineCount);
    }

    private List<String> buildDetailLines(TerminalTaskEntry e) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(trim(e.displayName(), 22));
        lines.add("Division: " + e.divisionName());
        lines.add("Objective: " + e.objectiveType());
        lines.add("Target: " + e.target());
        lines.add("Required: " + e.requiredAmount());
        lines.add("Reward: " + e.rewardPoints() + " RP");
        if (e.officerConfirmationRequired()) {
            lines.add("Needs officer approval");
        }
        return lines;
    }

    private int getDetailLineColor(String line) {
        if (line == null || line.isBlank()) return 0xFFFFFFFF;
        if (line.startsWith("Division:"))  return 0xFF8FD7E8;
        if (line.startsWith("Objective:") || line.startsWith("Target:") || line.startsWith("Reward:")) return 0xFFFFB24A;
        if (line.startsWith("Required:"))  return 0xFFF2E7D5;
        if (line.startsWith("Needs"))      return 0xFFFFB24A;
        return 0xFFF2E7D5;
    }

    // ── Accept button ─────────────────────────────────────────────────────────

    private void drawAcceptButton(DrawContext context, int x, int y) {
        TerminalTaskEntry selected = handler.getSelectedTask();
        boolean hasTask = selected != null;

        int x1 = x + ACCEPT_X, y1 = y + ACCEPT_Y;
        int x2 = x1 + ACCEPT_W, y2 = y1 + ACCEPT_H;

        context.fill(x1, y1, x2, y2, hasTask ? 0x2038FF9A : 0x10FFFFFF);

        String label = hasTask
                ? "ACCEPT: " + trim(selected.displayName(), 16)
                : "SELECT A TASK";
        int lw = this.textRenderer.getWidth(label);
        context.drawText(this.textRenderer, label,
                x1 + (ACCEPT_W - lw) / 2,
                y1 + (ACCEPT_H - 8) / 2 + 1,
                hasTask ? 0xFF38FF9A : 0xFFB0B0B0, false);
    }

    // ── Scroll indicators ─────────────────────────────────────────────────────

    /**
     * Draws small arrow indicators at the top/bottom of a scrollable panel
     * when there is content above or below the visible window.
     */
    private void drawScrollIndicators(DrawContext context,
                                      int panelX, int panelY,
                                      int panelW, int panelH,
                                      int scrollOffset, int totalItems, int visibleItems) {
        if (totalItems <= visibleItems) return;

        int arrowX = panelX + panelW - 8;

        // Up arrow — shown when scrolled down
        if (scrollOffset > 0) {
            context.drawText(this.textRenderer, "^",
                    arrowX, panelY + 2, 0xFFAAAAAA, false);
        }

        // Down arrow — shown when more content below
        if (scrollOffset + visibleItems < totalItems) {
            context.drawText(this.textRenderer, "v",
                    arrowX, panelY + panelH - 10, 0xFFAAAAAA, false);
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.x, y = this.y;

        List<TerminalTaskEntry> tasks = handler.getTasks();
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int dataIndex = i + listScrollOffset;
            if (dataIndex >= tasks.size()) break;

            int rowX = x + LIST_X;
            int rowY = y + LIST_Y + (i * ROW_HEIGHT);

            if (inside(mouseX, mouseY, rowX + 2, rowY + 2,
                    LIST_WIDTH - 4, ROW_HEIGHT - 4)) {
                handler.setSelectedIndex(dataIndex);
                detailScrollOffset = 0; // reset detail scroll on new selection
                return true;
            }
        }

        if (inside(mouseX, mouseY, x + ACCEPT_X, y + ACCEPT_Y, ACCEPT_W, ACCEPT_H)) {
            TerminalTaskEntry selected = handler.getSelectedTask();
            if (selected != null) {
                ClientPlayNetworking.send(new AcceptTerminalTaskC2SPacket(selected.taskId()));
                this.close();
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int x = this.x, y = this.y;
        int delta = verticalAmount > 0 ? -1 : 1;

        // Scroll list panel
        if (inside(mouseX, mouseY, x + LIST_X, y + LIST_Y, LIST_WIDTH, LIST_HEIGHT)) {
            listScrollOffset = clamp(listScrollOffset + delta, 0,
                    Math.max(0, handler.getTasks().size() - VISIBLE_ROWS));
            return true;
        }

        // Scroll detail panel
        if (inside(mouseX, mouseY, x + DETAIL_X, y + DETAIL_Y, DETAIL_WIDTH, DETAIL_HEIGHT)) {
            TerminalTaskEntry selected = handler.getSelectedTask();
            if (selected != null) {
                int totalLines = buildDetailLines(selected).size();
                int visibleLines = DETAIL_HEIGHT / 10;
                detailScrollOffset = clamp(detailScrollOffset + delta, 0,
                        Math.max(0, totalLines - visibleLines));
            }
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void clampListScroll(int totalItems) {
        listScrollOffset = clamp(listScrollOffset, 0,
                Math.max(0, totalItems - VISIBLE_ROWS));
    }

    private void clampDetailScroll(int totalLines, int visibleLines) {
        detailScrollOffset = clamp(detailScrollOffset, 0,
                Math.max(0, totalLines - visibleLines));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private String trim(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength
                ? text
                : text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }
}