package net.shard.seconddawnrp.tasksystem.pad;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tasksystem.network.SubmitManualConfirmC2SPacket;

import java.util.List;

public class TaskPadScreen extends HandledScreen<TaskPadScreenHandler> {

    private static final Identifier TEXTURE =
            SecondDawnRP.id("textures/gui/task_pad.png");

    private static final int TEX_WIDTH = 512;
    private static final int TEX_HEIGHT = 256;

    private static final int GUI_WIDTH = 380;
    private static final int GUI_HEIGHT = 190;

    private static final int CONTENT_X = 24;
    private static final int CONTENT_Y = 90;
    private static final int CONTENT_WIDTH = 326;
    private static final int CONTENT_HEIGHT = 92;

    private static final int ACTIVE_TAB_X = 19;
    private static final int ACTIVE_TAB_Y = 47;
    private static final int ACTIVE_TAB_W = 110;
    private static final int ACTIVE_TAB_H = 20;

    private static final int HISTORY_TAB_X = 136;
    private static final int HISTORY_TAB_Y = 47;
    private static final int HISTORY_TAB_W = 113;
    private static final int HISTORY_TAB_H = 20;

    private static final int SUBMIT_BUTTON_X = 254;
    private static final int SUBMIT_BUTTON_Y = 47;
    private static final int SUBMIT_BUTTON_W = 96;
    private static final int SUBMIT_BUTTON_H = 20;

    private static final int TASK_BLOCK_HEIGHT = 70;

    private boolean showingHistory = false;

    public TaskPadScreen(TaskPadScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = GUI_WIDTH;
        this.backgroundHeight = GUI_HEIGHT;
        this.playerInventoryTitleY = 10000;
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
        if (!showingHistory) {
            drawActiveSelectionHighlight(context, x, y);
        }
        drawContent(context, x, y);
        drawTabLabels(context, x, y);

        if (!showingHistory) {
            drawSubmitButton(context, x, y);
        }
    }

    private void drawTabHighlight(DrawContext context, int x, int y) {
        if (showingHistory) {
            context.fill(
                    x + HISTORY_TAB_X,
                    y + HISTORY_TAB_Y,
                    x + HISTORY_TAB_X + HISTORY_TAB_W,
                    y + HISTORY_TAB_Y + HISTORY_TAB_H,
                    0x10FFFFFF
            );
        } else {
            context.fill(
                    x + ACTIVE_TAB_X,
                    y + ACTIVE_TAB_Y,
                    x + ACTIVE_TAB_X + ACTIVE_TAB_W,
                    y + ACTIVE_TAB_Y + ACTIVE_TAB_H,
                    0x10FFFFFF
            );
        }
    }

    private void drawActiveSelectionHighlight(DrawContext context, int x, int y) {
        int selectedIndex = handler.getSelectedActiveTaskIndex();
        if (selectedIndex < 0) {
            return;
        }

        int highlightY = y + CONTENT_Y + 12 + (selectedIndex * TASK_BLOCK_HEIGHT);
        int bottom = highlightY + TASK_BLOCK_HEIGHT - 6;
        int contentBottom = y + CONTENT_Y + CONTENT_HEIGHT;

        if (highlightY >= contentBottom) {
            return;
        }

        context.fill(
                x + CONTENT_X + 2,
                highlightY,
                x + CONTENT_X + CONTENT_WIDTH - 4,
                Math.min(bottom, contentBottom),
                0x14FFFFFF
        );
    }

    private void drawTabLabels(DrawContext context, int x, int y) {
        drawCenteredTabText(context, "ACTIVE", x + ACTIVE_TAB_X, y + ACTIVE_TAB_Y, ACTIVE_TAB_W, ACTIVE_TAB_H);
        drawCenteredTabText(context, "HISTORY", x + HISTORY_TAB_X, y + HISTORY_TAB_Y, HISTORY_TAB_W, HISTORY_TAB_H);
    }

    private void drawCenteredTabText(DrawContext context, String text, int boxX, int boxY, int boxW, int boxH) {
        int textWidth = this.textRenderer.getWidth(text);
        int textX = boxX + (boxW - textWidth) / 2;
        int textY = boxY + (boxH - 8) / 2 + 1;

        context.drawText(this.textRenderer, text, textX, textY, 0xFFF2E7D5, false);
    }

    private void drawSubmitButton(DrawContext context, int x, int y) {
        int x1 = x + SUBMIT_BUTTON_X;
        int y1 = y + SUBMIT_BUTTON_Y;
        int x2 = x1 + SUBMIT_BUTTON_W;
        int y2 = y1 + SUBMIT_BUTTON_H;

        context.fill(x1, y1, x2, y2, 0x1800FFAA);

        String text = "SUBMIT";
        int textWidth = this.textRenderer.getWidth(text);
        int textX = x1 + (SUBMIT_BUTTON_W - textWidth) / 2;
        int textY = y1 + (SUBMIT_BUTTON_H - 8) / 2 + 1;

        context.drawText(this.textRenderer, text, textX, textY, 0xFFF2E7D5, false);
    }

    private void drawContent(DrawContext context, int x, int y) {
        List<String> lines = showingHistory ? handler.getCompletedLines() : handler.getActiveLines();

        int textX = x + CONTENT_X + 4;
        int textY = y + CONTENT_Y;
        int bottomY = y + CONTENT_Y + CONTENT_HEIGHT;

        context.enableScissor(
                x + CONTENT_X,
                y + CONTENT_Y,
                x + CONTENT_X + CONTENT_WIDTH,
                y + CONTENT_Y + CONTENT_HEIGHT
        );

        for (String line : lines) {
            context.drawText(
                    this.textRenderer,
                    trim(line, 42),
                    textX,
                    textY,
                    getLineColor(line),
                    false
            );

            textY += 10;
            if (textY > bottomY) {
                break;
            }
        }

        context.disableScissor();
    }

    private int getLineColor(String line) {
        if (line == null || line.isBlank()) return 0xFFFFFFFF;
        if (line.contains("Active Tasks") || line.contains("Completed Tasks")) return 0xFF8FD7E8;
        if (line.startsWith("Objective:") || line.startsWith("Target:") || line.startsWith("Reward:")) return 0xFFFFB24A;
        if (line.startsWith("Status:")) return 0xFF9ED9D6;
        if (line.startsWith("Progress:")) return 0xFFF2E7D5;
        if (line.startsWith("[")) return 0xFFD0D0D0;
        return 0xFFFFFFFF;
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

        if (inside(mouseX, mouseY, x + ACTIVE_TAB_X, y + ACTIVE_TAB_Y, ACTIVE_TAB_W, ACTIVE_TAB_H)) {
            showingHistory = false;
            return true;
        }

        if (inside(mouseX, mouseY, x + HISTORY_TAB_X, y + HISTORY_TAB_Y, HISTORY_TAB_W, HISTORY_TAB_H)) {
            showingHistory = true;
            return true;
        }

        if (!showingHistory) {
            List<String> taskIds = handler.getActiveTaskIds();
            for (int i = 0; i < taskIds.size(); i++) {
                int blockY = y + CONTENT_Y + 12 + (i * TASK_BLOCK_HEIGHT);
                if (inside(mouseX, mouseY, x + CONTENT_X, blockY, CONTENT_WIDTH, TASK_BLOCK_HEIGHT - 6)) {
                    handler.setSelectedActiveTaskIndex(i);
                    return true;
                }
            }

            if (inside(mouseX, mouseY, x + SUBMIT_BUTTON_X, y + SUBMIT_BUTTON_Y, SUBMIT_BUTTON_W, SUBMIT_BUTTON_H)) {
                String selectedTaskId = handler.getSelectedActiveTaskId();
                if (selectedTaskId != null) {
                    ClientPlayNetworking.send(new SubmitManualConfirmC2SPacket(selectedTaskId));
                }
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