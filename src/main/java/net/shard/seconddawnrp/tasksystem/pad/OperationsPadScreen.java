package net.shard.seconddawnrp.tasksystem.pad;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.tasksystem.network.AssignTaskC2SPacket;
import net.shard.seconddawnrp.tasksystem.network.CreateTaskC2SPacket;
import net.shard.seconddawnrp.tasksystem.network.ReviewTaskActionC2SPacket;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class OperationsPadScreen extends HandledScreen<AdminTaskScreenHandler> {

    private static final Identifier TEXTURE =
            SecondDawnRP.id("textures/gui/operations_pad.png");

    private static final int TEX_WIDTH = 512;
    private static final int TEX_HEIGHT = 256;

    private static final int GUI_WIDTH = 420;
    private static final int GUI_HEIGHT = 210;

    private static final int LIST_X = 22;
    private static final int LIST_Y = 80;
    private static final int LIST_WIDTH = 168;
    private static final int LIST_HEIGHT = 112;
    private static final int ROW_HEIGHT = 24;

    private static final int DETAIL_X = 260;
    private static final int DETAIL_Y = 80;
    private static final int DETAIL_WIDTH = 172;
    private static final int DETAIL_HEIGHT = 112;

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

    private static final int DETAIL_BUTTON_X = 260;
    private static final int DETAIL_BUTTON_Y = 194;
    private static final int DETAIL_BUTTON_W = 40;
    private static final int DETAIL_BUTTON_H = 12;
    private static final int DETAIL_BUTTON_GAP = 4;

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

    private enum DetailAction {
        APPROVE,
        RETURN,
        FAIL,
        CANCEL
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
        drawTabLabels(context, x, y);

        switch (selectedTab) {
            case TASKS -> drawTaskList(context, x, y);
            case DETAIL -> drawTaskDetails(context, x, y);
            case CREATE -> drawCreateTab(context, x, y);
            case ASSIGN -> drawAssignTab(context, x, y);
        }
    }

    private void drawTabHighlight(DrawContext context, int x, int y) {
        switch (selectedTab) {
            case TASKS -> context.fill(x + TASKS_TAB_X, y + TASKS_TAB_Y, x + TASKS_TAB_X + TASKS_TAB_W, y + TASKS_TAB_Y + TASKS_TAB_H, 0x10FFFFFF);
            case DETAIL -> context.fill(x + DETAIL_TAB_X, y + DETAIL_TAB_Y, x + DETAIL_TAB_X + DETAIL_TAB_W, y + DETAIL_TAB_Y + DETAIL_TAB_H, 0x10FFFFFF);
            case CREATE -> context.fill(x + CREATE_TAB_X, y + CREATE_TAB_Y, x + CREATE_TAB_X + CREATE_TAB_W, y + CREATE_TAB_Y + CREATE_TAB_H, 0x10FFFFFF);
            case ASSIGN -> context.fill(x + ASSIGN_TAB_X, y + ASSIGN_TAB_Y, x + ASSIGN_TAB_X + ASSIGN_TAB_W, y + ASSIGN_TAB_Y + ASSIGN_TAB_H, 0x10FFFFFF);
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

            context.drawText(this.textRenderer, trim(task.getTitle(), 18), rowX + 8, rowY + 6, 0xFFF2E7D5, false);
            context.drawText(this.textRenderer, trim(task.getStatus(), 18), rowX + 8, rowY + 16, 0xFFD0D0D0, false);
        }

        context.disableScissor();
    }

    private void drawTaskDetails(DrawContext context, int x, int y) {
        AdminTaskViewModel selected = handler.getSelectedTask();
        int textX = x + DETAIL_X + 4;
        int textY = y + DETAIL_Y + 2;
        int bottomY = y + DETAIL_Y + DETAIL_HEIGHT - 18;

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

        context.drawText(this.textRenderer, trim(selected.getTitle(), 20), textX, textY, 0xFFF2E7D5, false);
        textY += 14;

        for (String line : selected.getDetailLines()) {
            context.drawText(this.textRenderer, trim(line, 22), textX, textY, getDetailLineColor(line), false);
            textY += 10;
            if (textY > bottomY) {
                break;
            }
        }

        context.disableScissor();

        drawDetailActionButton(context, x + DETAIL_BUTTON_X, y + DETAIL_BUTTON_Y, "APP", DetailAction.APPROVE);
        drawDetailActionButton(context, x + DETAIL_BUTTON_X + (DETAIL_BUTTON_W + DETAIL_BUTTON_GAP), y + DETAIL_BUTTON_Y, "BACK", DetailAction.RETURN);
        drawDetailActionButton(context, x + DETAIL_BUTTON_X + 2 * (DETAIL_BUTTON_W + DETAIL_BUTTON_GAP), y + DETAIL_BUTTON_Y, "FAIL", DetailAction.FAIL);
        drawDetailActionButton(context, x + DETAIL_BUTTON_X + 3 * (DETAIL_BUTTON_W + DETAIL_BUTTON_GAP), y + DETAIL_BUTTON_Y, "X", DetailAction.CANCEL);
    }

    private void drawCreateTab(DrawContext context, int x, int y) {
        drawCreateRow(context, x, y, "TASK ID",
                withCursor(handler.getCreateTaskId(),
                        handler.getSelectedCreateField() == AdminTaskScreenHandler.CreateField.TASK_ID),
                22, 64,
                handler.getSelectedCreateField() == AdminTaskScreenHandler.CreateField.TASK_ID);

        drawCreateRow(context, x, y, "NAME",
                withCursor(handler.getCreateDisplayName(),
                        handler.getSelectedCreateField() == AdminTaskScreenHandler.CreateField.DISPLAY_NAME),
                22, 80,
                handler.getSelectedCreateField() == AdminTaskScreenHandler.CreateField.DISPLAY_NAME);

        drawCreateRow(context, x, y, "DESC",
                withCursor(handler.getCreateDescription(),
                        handler.getSelectedCreateField() == AdminTaskScreenHandler.CreateField.DESCRIPTION),
                22, 96,
                handler.getSelectedCreateField() == AdminTaskScreenHandler.CreateField.DESCRIPTION);

        drawCreateRow(context, x, y, "DIV",
                handler.getCreateDivision().name(),
                22, 112,
                handler.getSelectedCreateField() == AdminTaskScreenHandler.CreateField.DIVISION);

        drawCreateRow(context, x, y, "OBJ",
                handler.getCreateObjectiveType().name(),
                22, 128,
                handler.getSelectedCreateField() == AdminTaskScreenHandler.CreateField.OBJECTIVE_TYPE);

        drawCreateRow(context, x, y, "TARGET",
                withCursor(handler.getCreateTargetId(),
                        handler.getSelectedCreateField() == AdminTaskScreenHandler.CreateField.TARGET_ID),
                22, 144,
                handler.getSelectedCreateField() == AdminTaskScreenHandler.CreateField.TARGET_ID);

        drawCreateRow(context, x, y, "AMOUNT",
                String.valueOf(handler.getCreateRequiredAmount()),
                22, 160,
                handler.getSelectedCreateField() == AdminTaskScreenHandler.CreateField.REQUIRED_AMOUNT);

        drawCreateRow(context, x, y, "REWARD",
                String.valueOf(handler.getCreateRewardPoints()),
                22, 176,
                handler.getSelectedCreateField() == AdminTaskScreenHandler.CreateField.REWARD_POINTS);

        drawCreateRow(context, x, y, "APPROVAL",
                handler.isCreateOfficerConfirmationRequired() ? "YES" : "NO",
                220, 64,
                handler.getSelectedCreateField() == AdminTaskScreenHandler.CreateField.OFFICER_CONFIRMATION);

        drawHintText(context, x + 222, y + 84, "LEFT CLICK = UP");
        drawHintText(context, x + 222, y + 94, "RIGHT CLICK = DOWN");

        drawCreateButton(context, x, y, 220, 176,
                handler.getSelectedCreateField() == AdminTaskScreenHandler.CreateField.CREATE_BUTTON);
    }

    private void drawAssignTab(DrawContext context, int x, int y) {
        AdminTaskViewModel selected = handler.getSelectedTask();
        String selectedTaskName = selected == null ? "NONE" : selected.getTitle();

        drawAssignRow(context, x, y, "TASK", trim(selectedTaskName, 20), 220, 64, false);

        drawAssignRow(context, x, y, "MODE",
                handler.getAssignMode().name(),
                220, 80,
                handler.getSelectedAssignField() == AdminTaskScreenHandler.AssignField.MODE);

        drawAssignRow(context, x, y, "PLAYER",
                withCursor(handler.getAssignPlayerName(),
                        handler.getSelectedAssignField() == AdminTaskScreenHandler.AssignField.PLAYER_NAME),
                220, 96,
                handler.getSelectedAssignField() == AdminTaskScreenHandler.AssignField.PLAYER_NAME);

        drawAssignRow(context, x, y, "DIV",
                handler.getAssignDivision().name(),
                220, 112,
                handler.getSelectedAssignField() == AdminTaskScreenHandler.AssignField.DIVISION);

        drawAssignButton(context, x, y, 220, 136,
                handler.getSelectedAssignField() == AdminTaskScreenHandler.AssignField.ASSIGN_BUTTON);

        String hint = switch (handler.getAssignMode()) {
            case PLAYER -> "Assign to online player";
            case DIVISION -> "Send to division pool";
            case PUBLIC -> "Publish to public pool";
        };
        drawHintText(context, x + 222, y + 156, hint);
    }

    private String withCursor(String value, boolean selected) {
        return selected ? value + "_" : value;
    }

    private void drawCreateRow(DrawContext context, int baseX, int baseY, String label, String value, int localX, int localY, boolean selected) {
        int x1 = baseX + localX;
        int y1 = baseY + localY;
        int x2 = x1 + 180;
        int y2 = y1 + 12;

        context.fill(x1, y1, x2, y2, selected ? 0x20FFFFFF : 0x0A000000);
        context.drawText(this.textRenderer, label, x1 + 4, y1 + 2, 0xFFFFB24A, false);
        context.drawText(this.textRenderer, trim(value, 20), x1 + 58, y1 + 2, 0xFFF2E7D5, false);
    }

    private void drawCreateButton(DrawContext context, int baseX, int baseY, int localX, int localY, boolean selected) {
        int x1 = baseX + localX;
        int y1 = baseY + localY;
        int x2 = x1 + 100;
        int y2 = y1 + 14;

        context.fill(x1, y1, x2, y2, selected ? 0x30FFFFFF : 0x14000000);

        String text = "CREATE TASK";
        int textWidth = this.textRenderer.getWidth(text);
        int textX = x1 + (100 - textWidth) / 2;
        int textY = y1 + 3;

        context.drawText(this.textRenderer, text, textX, textY, 0xFFF2E7D5, false);
    }

    private void drawAssignRow(DrawContext context, int baseX, int baseY, String label, String value, int localX, int localY, boolean selected) {
        int x1 = baseX + localX;
        int y1 = baseY + localY;
        int x2 = x1 + 180;
        int y2 = y1 + 12;

        context.fill(x1, y1, x2, y2, selected ? 0x20FFFFFF : 0x0A000000);
        context.drawText(this.textRenderer, label, x1 + 4, y1 + 2, 0xFF8FD7E8, false);
        context.drawText(this.textRenderer, trim(value, 20), x1 + 58, y1 + 2, 0xFFF2E7D5, false);
    }

    private void drawAssignButton(DrawContext context, int baseX, int baseY, int localX, int localY, boolean selected) {
        int x1 = baseX + localX;
        int y1 = baseY + localY;
        int x2 = x1 + 100;
        int y2 = y1 + 14;

        context.fill(x1, y1, x2, y2, selected ? 0x30FFFFFF : 0x14000000);

        String text = "ASSIGN TASK";
        int textWidth = this.textRenderer.getWidth(text);
        int textX = x1 + (100 - textWidth) / 2;
        int textY = y1 + 3;

        context.drawText(this.textRenderer, text, textX, textY, 0xFFF2E7D5, false);
    }

    private void drawDetailActionButton(DrawContext context, int x, int y, String label, DetailAction action) {
        int color = switch (action) {
            case APPROVE -> 0x2038FF9A;
            case RETURN -> 0x20FFE08A;
            case FAIL -> 0x20FF8A8A;
            case CANCEL -> 0x20D0D0D0;
        };

        context.fill(x, y, x + DETAIL_BUTTON_W, y + DETAIL_BUTTON_H, color);

        int textWidth = this.textRenderer.getWidth(label);
        int textX = x + (DETAIL_BUTTON_W - textWidth) / 2;
        int textY = y + 2;

        context.drawText(this.textRenderer, label, textX, textY, 0xFFF2E7D5, false);
    }

    private void drawHintText(DrawContext context, int x, int y, String text) {
        context.drawText(this.textRenderer, text, x, y, 0xFFB8B8B8, false);
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
        return text.length() <= maxLength ? text : text.substring(0, Math.max(0, maxLength - 3)) + "...";
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

        if (selectedTab == AdminTab.CREATE) {
            if (inside(mouseX, mouseY, x + 22, y + 64, 180, 12)) {
                handler.setSelectedCreateField(AdminTaskScreenHandler.CreateField.TASK_ID);
                return true;
            }
            if (inside(mouseX, mouseY, x + 22, y + 80, 180, 12)) {
                handler.setSelectedCreateField(AdminTaskScreenHandler.CreateField.DISPLAY_NAME);
                return true;
            }
            if (inside(mouseX, mouseY, x + 22, y + 96, 180, 12)) {
                handler.setSelectedCreateField(AdminTaskScreenHandler.CreateField.DESCRIPTION);
                return true;
            }
            if (inside(mouseX, mouseY, x + 22, y + 112, 180, 12)) {
                handler.setSelectedCreateField(AdminTaskScreenHandler.CreateField.DIVISION);
                handler.cycleCreateDivision();
                return true;
            }
            if (inside(mouseX, mouseY, x + 22, y + 128, 180, 12)) {
                handler.setSelectedCreateField(AdminTaskScreenHandler.CreateField.OBJECTIVE_TYPE);
                handler.cycleCreateObjectiveType();
                return true;
            }
            if (inside(mouseX, mouseY, x + 22, y + 144, 180, 12)) {
                handler.setSelectedCreateField(AdminTaskScreenHandler.CreateField.TARGET_ID);
                return true;
            }
            if (inside(mouseX, mouseY, x + 22, y + 160, 180, 12)) {
                handler.setSelectedCreateField(AdminTaskScreenHandler.CreateField.REQUIRED_AMOUNT);
                if (button == 0) {
                    handler.incrementCreateRequiredAmount();
                } else {
                    handler.decrementCreateRequiredAmount();
                }
                return true;
            }
            if (inside(mouseX, mouseY, x + 22, y + 176, 180, 12)) {
                handler.setSelectedCreateField(AdminTaskScreenHandler.CreateField.REWARD_POINTS);
                if (button == 0) {
                    handler.incrementCreateRewardPoints();
                } else {
                    handler.decrementCreateRewardPoints();
                }
                return true;
            }
            if (inside(mouseX, mouseY, x + 220, y + 64, 180, 12)) {
                handler.setSelectedCreateField(AdminTaskScreenHandler.CreateField.OFFICER_CONFIRMATION);
                handler.toggleCreateOfficerConfirmationRequired();
                return true;
            }
            if (inside(mouseX, mouseY, x + 220, y + 176, 100, 14)) {
                handler.setSelectedCreateField(AdminTaskScreenHandler.CreateField.CREATE_BUTTON);
                sendCreateTaskPacket();
                return true;
            }
        }

        if (selectedTab == AdminTab.ASSIGN) {
            if (inside(mouseX, mouseY, x + 220, y + 80, 180, 12)) {
                handler.setSelectedAssignField(AdminTaskScreenHandler.AssignField.MODE);
                handler.cycleAssignMode();
                return true;
            }

            if (inside(mouseX, mouseY, x + 220, y + 96, 180, 12)) {
                handler.setSelectedAssignField(AdminTaskScreenHandler.AssignField.PLAYER_NAME);
                return true;
            }

            if (inside(mouseX, mouseY, x + 220, y + 112, 180, 12)) {
                handler.setSelectedAssignField(AdminTaskScreenHandler.AssignField.DIVISION);
                handler.cycleAssignDivision();
                return true;
            }

            if (inside(mouseX, mouseY, x + 220, y + 136, 100, 14)) {
                handler.setSelectedAssignField(AdminTaskScreenHandler.AssignField.ASSIGN_BUTTON);
                sendAssignTaskPacket();
                return true;
            }
        }

        if (selectedTab == AdminTab.DETAIL) {
            AdminTaskViewModel selected = handler.getSelectedTask();
            if (selected != null) {
                String taskId = selected.getTaskId();

                if (inside(mouseX, mouseY, x + DETAIL_BUTTON_X, y + DETAIL_BUTTON_Y, DETAIL_BUTTON_W, DETAIL_BUTTON_H)) {
                    sendReviewActionPacket(taskId, "APPROVE");
                    return true;
                }

                if (inside(mouseX, mouseY,
                        x + DETAIL_BUTTON_X + (DETAIL_BUTTON_W + DETAIL_BUTTON_GAP),
                        y + DETAIL_BUTTON_Y,
                        DETAIL_BUTTON_W,
                        DETAIL_BUTTON_H)) {
                    sendReviewActionPacket(taskId, "RETURN");
                    return true;
                }

                if (inside(mouseX, mouseY,
                        x + DETAIL_BUTTON_X + 2 * (DETAIL_BUTTON_W + DETAIL_BUTTON_GAP),
                        y + DETAIL_BUTTON_Y,
                        DETAIL_BUTTON_W,
                        DETAIL_BUTTON_H)) {
                    sendReviewActionPacket(taskId, "FAIL");
                    return true;
                }

                if (inside(mouseX, mouseY,
                        x + DETAIL_BUTTON_X + 3 * (DETAIL_BUTTON_W + DETAIL_BUTTON_GAP),
                        y + DETAIL_BUTTON_Y,
                        DETAIL_BUTTON_W,
                        DETAIL_BUTTON_H)) {
                    sendReviewActionPacket(taskId, "CANCEL");
                    return true;
                }
            }
        }

        if (selectedTab == AdminTab.TASKS || selectedTab == AdminTab.DETAIL || selectedTab == AdminTab.ASSIGN) {
            List<AdminTaskViewModel> tasks = handler.getTasks();
            for (int i = 0; i < tasks.size(); i++) {
                int rowX = x + LIST_X;
                int rowY = y + LIST_Y + (i * ROW_HEIGHT);
                int rowRight = rowX + LIST_WIDTH - 6;
                int rowBottom = rowY + ROW_HEIGHT - 4;

                if (inside(mouseX, mouseY, rowX + 2, rowY + 2, rowRight - (rowX + 2), rowBottom - (rowY + 2))) {
                    handler.setSelectedIndex(i);
                    if (selectedTab == AdminTab.TASKS) {
                        selectedTab = AdminTab.DETAIL;
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendCreateTaskPacket() {
        CreateTaskC2SPacket packet = new CreateTaskC2SPacket(
                handler.getCreateTaskId(),
                handler.getCreateDisplayName(),
                handler.getCreateDescription(),
                handler.getCreateDivision().name(),
                handler.getCreateObjectiveType().name(),
                handler.getCreateTargetId(),
                handler.getCreateRequiredAmount(),
                handler.getCreateRewardPoints(),
                handler.isCreateOfficerConfirmationRequired()
        );

        ClientPlayNetworking.send(packet);
        handler.clearCreateForm();
    }

    private void sendAssignTaskPacket() {
        AdminTaskViewModel selected = handler.getSelectedTask();
        if (selected == null) {
            return;
        }

        AssignTaskC2SPacket packet = new AssignTaskC2SPacket(
                selected.getTaskId(),
                handler.getAssignMode().name(),
                handler.getAssignPlayerName(),
                handler.getAssignDivision().name()
        );

        ClientPlayNetworking.send(packet);
        handler.clearAssignForm();
    }

    private void sendReviewActionPacket(String taskId, String actionName) {
        ClientPlayNetworking.send(new ReviewTaskActionC2SPacket(taskId, actionName));
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (selectedTab == AdminTab.CREATE) {
            handler.appendToSelectedCreateField(chr);
            return true;
        }

        if (selectedTab == AdminTab.ASSIGN) {
            handler.appendToSelectedAssignField(chr);
            return true;
        }

        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectedTab == AdminTab.CREATE) {
            AdminTaskScreenHandler.CreateField field = handler.getSelectedCreateField();

            boolean typingField =
                    field == AdminTaskScreenHandler.CreateField.TASK_ID
                            || field == AdminTaskScreenHandler.CreateField.DISPLAY_NAME
                            || field == AdminTaskScreenHandler.CreateField.DESCRIPTION
                            || field == AdminTaskScreenHandler.CreateField.TARGET_ID;

            if (typingField) {
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    handler.backspaceSelectedCreateField();
                    return true;
                }

                if (keyCode != GLFW.GLFW_KEY_ESCAPE
                        && keyCode != GLFW.GLFW_KEY_ENTER
                        && keyCode != GLFW.GLFW_KEY_KP_ENTER
                        && keyCode != GLFW.GLFW_KEY_TAB) {
                    return true;
                }
            }

            if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                    && field == AdminTaskScreenHandler.CreateField.CREATE_BUTTON) {
                sendCreateTaskPacket();
                return true;
            }
        }

        if (selectedTab == AdminTab.ASSIGN) {
            AdminTaskScreenHandler.AssignField field = handler.getSelectedAssignField();

            boolean typingField =
                    field == AdminTaskScreenHandler.AssignField.PLAYER_NAME;

            if (typingField) {
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    handler.backspaceSelectedAssignField();
                    return true;
                }

                if (keyCode != GLFW.GLFW_KEY_ESCAPE
                        && keyCode != GLFW.GLFW_KEY_ENTER
                        && keyCode != GLFW.GLFW_KEY_KP_ENTER
                        && keyCode != GLFW.GLFW_KEY_TAB) {
                    return true;
                }
            }

            if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                    && field == AdminTaskScreenHandler.AssignField.ASSIGN_BUTTON) {
                sendAssignTaskPacket();
                return true;
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Title baked into texture.
    }
}