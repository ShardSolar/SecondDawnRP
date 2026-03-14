package net.shard.shipyardsrp.tasksystem.padd;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.shard.shipyardsrp.ShipyardsRP;

import java.util.List;

public class TaskPaddScreen extends HandledScreen<TaskPaddScreenHandler> {

    private static final Identifier BACKGROUND_TEXTURE =
            Identifier.of(ShipyardsRP.MOD_ID, "textures/gui/task_padd.png");

    private static final int BG_WIDTH = 248;
    private static final int BG_HEIGHT = 190;

    private boolean showingCompleted = false;
    private int scrollOffset = 0;

    public TaskPaddScreen(TaskPaddScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = BG_WIDTH;
        this.backgroundHeight = BG_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(
                BACKGROUND_TEXTURE,
                this.x,
                this.y,
                0,
                0,
                this.backgroundWidth,
                this.backgroundHeight
        );

        drawTabHighlight(context);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        drawBoldText(context, "Task PADD", 68, 18, 0xD8D8D8);

        int activeColor = showingCompleted ? 0x8E7B52 : 0xFFFFFF;
        int historyColor = showingCompleted ? 0xFFFFFF : 0x8E7B52;

        drawBoldText(context, "ACTIVE", 50, 46, activeColor);
        drawBoldText(context, "HISTORY", 96, 46, historyColor);

        String sectionLabel = showingCompleted ? "Completed History" : "Active Tasks";
        drawBoldText(context, sectionLabel, 38, 66, 0xAEE6FF);

        drawTaskLines(context);
    }

    private void drawTaskLines(DrawContext context) {
        List<String> lines = showingCompleted
                ? handler.getCompletedLines()
                : handler.getActiveLines();

        int contentX = 38;
        int contentY = 82;
        int lineHeight = 10;
        int maxVisibleLines = 9;

        int start = Math.min(scrollOffset, Math.max(0, lines.size() - maxVisibleLines));
        int end = Math.min(lines.size(), start + maxVisibleLines);

        int visualIndex = 0;
        for (int i = start; i < end; i++) {
            String line = lines.get(i);

            if (i == 0 && ("Active Tasks".equals(line) || "Completed Tasks".equals(line))) {
                continue;
            }

            int y = contentY + (visualIndex * lineHeight);

            int color = 0xFFFFFF;
            if (line.startsWith("Status: Awaiting approval")) {
                color = 0xFFD86A;
            } else if (line.startsWith("Status: Complete")) {
                color = 0x7DFF9D;
            } else if (line.startsWith("Status: In progress")) {
                color = 0xAEE6FF;
            } else if (line.startsWith("Reward:")) {
                color = 0xF2D18B;
            } else if (line.startsWith("Completed:")) {
                color = 0xD7B8FF;
            } else if (line.startsWith("Objective:")) {
                color = 0xFFD86A;
            } else if (line.startsWith("Target:")) {
                color = 0xFFB866;
            }

            drawBoldText(context, line, contentX, y, color);
            visualIndex++;
        }

        if (lines.size() > maxVisibleLines) {
            drawBoldText(
                    context,
                    "SCROLL " + (start + 1) + "-" + end + "/" + lines.size(),
                    38,
                    176,
                    0xB0B0B0
            );
        }
    }

    private void drawBoldText(DrawContext context, String text, int x, int y, int color) {
        context.drawText(
                this.textRenderer,
                Text.literal(text).setStyle(Style.EMPTY.withBold(true)),
                x,
                y,
                color,
                false
        );
    }

    private void drawTabHighlight(DrawContext context) {
        if (!showingCompleted) {
            // ACTIVE tab highlight
            context.fill(this.x + 44, this.y + 40, this.x + 91, this.y + 56, 0x35FFFFFF);
        } else {
            // HISTORY tab highlight
            context.fill(this.x + 90, this.y + 40, this.x + 149, this.y + 56, 0x35FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double localX = mouseX - this.x;
        double localY = mouseY - this.y;

        // ACTIVE tab click zone
        if (isWithin(localX, localY, 42, 38, 52, 22)) {
            showingCompleted = false;
            scrollOffset = 0;
            return true;
        }

// HISTORY tab click zone
        if (isWithin(localX, localY, 88, 38, 64, 22)) {
            showingCompleted = true;
            scrollOffset = 0;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isWithin(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        List<String> lines = showingCompleted
                ? handler.getCompletedLines()
                : handler.getActiveLines();

        int maxVisibleLines = 9;
        int maxScroll = Math.max(0, lines.size() - maxVisibleLines);

        if (verticalAmount < 0) {
            scrollOffset = Math.min(scrollOffset + 1, maxScroll);
            return true;
        } else if (verticalAmount > 0) {
            scrollOffset = Math.max(scrollOffset - 1, 0);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // TAB
        if (keyCode == 258) {
            showingCompleted = !showingCompleted;
            scrollOffset = 0;
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}