package net.shard.seconddawnrp.tasksystem.pad;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;

import java.util.List;

public class OperationsPadScreen extends HandledScreen<AdminTaskScreenHandler> {

    private static final Identifier TEXTURE =
            SecondDawnRP.id("textures/gui/operations_pad.png");

    private static final int TEX_WIDTH = 512;
    private static final int TEX_HEIGHT = 256;

    private static final int GUI_WIDTH = 420;
    private static final int GUI_HEIGHT = 210;

    // Task list panel
    private static final int LIST_X = 22;
    private static final int LIST_Y = 80;
    private static final int LIST_WIDTH = 168;
    private static final int LIST_HEIGHT = 112;
    private static final int ROW_HEIGHT = 24;

    // Detail panel
    private static final int DETAIL_X = 260;
    private static final int DETAIL_Y = 80;
    private static final int DETAIL_WIDTH = 172;
    private static final int DETAIL_HEIGHT = 112;

    // Tabs
    private static final int TASKS_TAB_X = 20;
    private static final int TASKS_TAB_Y = 34;
    private static final int TASKS_TAB_W = 86;
    private static final int TASKS_TAB_H = 20;

    private static final int DETAIL_TAB_X = 116;
    private static final int DETAIL_TAB_Y = 34;
    private static final int DETAIL_TAB_W = 90;
    private static final int DETAIL_TAB_H = 20;

    private static final int CREATE_TAB_X = 215;
    private static final int CREATE_TAB_Y = 34;
    private static final int CREATE_TAB_W = 92;
    private static final int CREATE_TAB_H = 20;

    private static final int ASSIGN_TAB_X = 314;
    private static final int ASSIGN_TAB_Y = 34;
    private static final int ASSIGN_TAB_W = 96;
    private static final int ASSIGN_TAB_H = 20;

    private AdminTab selectedTab = AdminTab.TASKS;

    public OperationsPadScreen(AdminTaskScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = GUI_WIDTH;
        this.backgroundHeight = GUI_HEIGHT;
        this.playerInventoryTitleY = 10000;
    }

    private enum AdminTab {
        TASKS,
        DETAIL,
        CREATE,
        ASSIGN
    }

    @Override
    protected void init() {
        super.init();
        this.titleX = 0;
        this.titleY = 0;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = this.x;
        int y = this.y;

        context.drawTexture(
                TEXTURE,
                x,
                y,
                0,
                0,
                backgroundWidth,
                backgroundHeight,
                TEX_WIDTH,
                TEX_HEIGHT
        );

        drawTabHighlight(context, x, y);
        drawTaskList(context, x, y);
        drawTaskDetails(context, x, y);
        drawTabLabels(context, x, y);
    }

    private void drawTabHighlight(DrawContext context, int x, int y) {
        switch (selectedTab) {
            case TASKS -> context.fill(
                    x + TASKS_TAB_X,
                    y + TASKS_TAB_Y,
                    x + TASKS_TAB_X + TASKS_TAB_W,
                    y + TASKS_TAB_Y + TASKS_TAB_H,
                    0x10FFFFFF
            );
            case DETAIL -> context.fill(
                    x + DETAIL_TAB_X,
                    y + DETAIL_TAB_Y,
                    x + DETAIL_TAB_X + DETAIL_TAB_W,
                    y + DETAIL_TAB_Y + DETAIL_TAB_H,
                    0x10FFFFFF
            );
            case CREATE -> context.fill(
                    x + CREATE_TAB_X,
                    y + CREATE_TAB_Y,
                    x + CREATE_TAB_X + CREATE_TAB_W,
                    y + CREATE_TAB_Y + CREATE_TAB_H,
                    0x10FFFFFF
            );
            case ASSIGN -> context.fill(
                    x + ASSIGN_TAB_X,
                    y + ASSIGN_TAB_Y,
                    x + ASSIGN_TAB_X + ASSIGN_TAB_W,
                    y + ASSIGN_TAB_Y + ASSIGN_TAB_H,
                    0x10FFFFFF
            );
        }
    }

    private void drawTabLabels(DrawContext context, int x, int y) {
        drawCenteredTabText(context, "TASKS", x + TASKS_TAB_X, y + TASKS_TAB_Y, TASKS_TAB_W, TASKS_TAB_H);
        drawCenteredTabText(context, "DETAIL", x + DETAIL_TAB_X, y + DETAIL_TAB_Y, DETAIL_TAB_W, DETAIL_TAB_H);
        drawCenteredTabText(context, "CREATE", x + CREATE_TAB_X, y + CREATE_TAB_Y, CREATE_TAB_W, CREATE_TAB_H);
        drawCenteredTabText(context, "ASSIGN", x + ASSIGN_TAB_X, y + ASSIGN_TAB_Y, ASSIGN_TAB_W, ASSIGN_TAB_H);
    }

    private void drawCenteredTabText(DrawContext context, String text, int boxX, int boxY, int boxW, int boxH) {
        int textWidth = this.textRenderer.getWidth(text);
        int textX = boxX + (boxW - textWidth) / 2;
        int textY = boxY + (boxH - 8) / 2 + 3;

        context.drawText(this.textRenderer, text, textX, textY, 0xFFF2E7D5, false);
    }

    private void drawTaskList(DrawContext context, int x, int y) {
        List<AdminTaskViewModel> tasks = handler.getTasks();

        context.enableScissor(
                x + LIST_X,
                y + LIST_Y,
                x + LIST_X + LIST_WIDTH,
                y + LIST_Y + LIST_HEIGHT
        );

        for (int i = 0; i < tasks.size(); i++) {
            int rowX = x + LIST_X;
            int rowY = y + LIST_Y + (i * ROW_HEIGHT);
            int rowRight = rowX + LIST_WIDTH - 6;
            int rowBottom = rowY + ROW_HEIGHT - 4;

            if (rowBottom > y + LIST_Y + LIST_HEIGHT) {
                break;
            }

            boolean selected = i == handler.getSelectedIndex();

            context.fill(
                    rowX + 2,
                    rowY + 2,
                    rowRight,
                    rowBottom,
                    selected ? 0x14FFFFFF : 0x06000000
            );

            AdminTaskViewModel task = tasks.get(i);

            context.drawText(
                    this.textRenderer,
                    trim(task.getTitle(), 18),
                    rowX + 8,
                    rowY + 6,
                    0xFFF2E7D5,
                    false
            );

            context.drawText(
                    this.textRenderer,
                    trim(task.getStatus(), 18),
                    rowX + 8,
                    rowY + 16,
                    0xFFD0D0D0,
                    false
            );
        }

        context.disableScissor();
    }

    private void drawTaskDetails(DrawContext context, int x, int y) {
        AdminTaskViewModel selected = handler.getSelectedTask();
        int textX = x + DETAIL_X + 4;
        int textY = y + DETAIL_Y + 2;
        int bottomY = y + DETAIL_Y + DETAIL_HEIGHT;

        context.enableScissor(
                x + DETAIL_X,
                y + DETAIL_Y,
                x + DETAIL_X + DETAIL_WIDTH,
                y + DETAIL_Y + DETAIL_HEIGHT
        );

        if (selected == null) {
            context.drawText(this.textRenderer, "No task selected", textX, textY, 0xFFF2E7D5, false);
            context.disableScissor();
            return;
        }

        context.drawText(
                this.textRenderer,
                trim(selected.getTitle(), 20),
                textX,
                textY,
                0xFFF2E7D5,
                false
        );
        textY += 14;

        for (String line : selected.getDetailLines()) {
            context.drawText(
                    this.textRenderer,
                    trim(line, 22),
                    textX,
                    textY,
                    getDetailLineColor(line),
                    false
            );

            textY += 10;
            if (textY > bottomY) {
                break;
            }
        }

        context.disableScissor();
    }

    private int getDetailLineColor(String line) {
        if (line == null || line.isBlank()) return 0xFFFFFFFF;
        if (line.startsWith("Task ID:")) return 0xFFD0D0D0;
        if (line.startsWith("Description:")) return 0xFFFFFFFF;
        if (line.startsWith("Objective:") || line.startsWith("Target:") || line.startsWith("Reward:")) return 0xFFFFB24A;
        if (line.startsWith("Division:")) return 0xFF8FD7E8;
        if (line.startsWith("Status:")) return 0xFF9ED9D6;
        return 0xFFD7D7D7;
    }

    private String trim(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength
                ? text
                : text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.x;
        int y = this.y;

        if (inside(mouseX, mouseY, x + TASKS_TAB_X, y + TASKS_TAB_Y, TASKS_TAB_W, TASKS_TAB_H)) {
            selectedTab = AdminTab.TASKS;
            return true;
        }

        if (inside(mouseX, mouseY, x + DETAIL_TAB_X, y + DETAIL_TAB_Y, DETAIL_TAB_W, DETAIL_TAB_H)) {
            selectedTab = AdminTab.DETAIL;
            return true;
        }

        if (inside(mouseX, mouseY, x + CREATE_TAB_X, y + CREATE_TAB_Y, CREATE_TAB_W, CREATE_TAB_H)) {
            selectedTab = AdminTab.CREATE;
            return true;
        }

        if (inside(mouseX, mouseY, x + ASSIGN_TAB_X, y + ASSIGN_TAB_Y, ASSIGN_TAB_W, ASSIGN_TAB_H)) {
            selectedTab = AdminTab.ASSIGN;
            return true;
        }

        List<AdminTaskViewModel> tasks = handler.getTasks();
        for (int i = 0; i < tasks.size(); i++) {
            int rowX = x + LIST_X;
            int rowY = y + LIST_Y + (i * ROW_HEIGHT);
            int rowRight = rowX + LIST_WIDTH - 6;
            int rowBottom = rowY + ROW_HEIGHT - 4;

            if (inside(mouseX, mouseY, rowX + 2, rowY + 2, rowRight - (rowX + 2), rowBottom - (rowY + 2))) {
                handler.setSelectedIndex(i);
                selectedTab = AdminTab.DETAIL;
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Title is baked into the texture.
    }
}