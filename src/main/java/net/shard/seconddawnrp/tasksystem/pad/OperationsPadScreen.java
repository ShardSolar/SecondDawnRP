package net.shard.seconddawnrp.tasksystem.pad;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.divison.Division;
import net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType;
import net.shard.seconddawnrp.tasksystem.network.AssignTaskC2SPacket;
import net.shard.seconddawnrp.tasksystem.network.CreateTaskC2SPacket;
import net.shard.seconddawnrp.tasksystem.network.ReviewTaskActionC2SPacket;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class OperationsPadScreen extends HandledScreen<AdminTaskScreenHandler> {

    private static final Identifier TEXTURE =
            SecondDawnRP.id("textures/gui/operations_pad.png");

    private static final int TEX_WIDTH  = 512;
    private static final int TEX_HEIGHT = 256;
    private static final int GUI_WIDTH  = 420;
    private static final int GUI_HEIGHT = 210;

    private static final int LIST_X      = 22;
    private static final int LIST_Y      = 80;
    private static final int LIST_WIDTH  = 168;
    private static final int LIST_HEIGHT = 112;
    private static final int ROW_HEIGHT  = 24;

    private static final int DETAIL_X      = 260;
    private static final int DETAIL_Y      = 80;
    private static final int DETAIL_WIDTH  = 172;
    private static final int DETAIL_HEIGHT = 112;

    private static final int TASKS_TAB_X  = 20;  private static final int TASKS_TAB_Y  = 34;
    private static final int TASKS_TAB_W  = 86;  private static final int TASKS_TAB_H  = 20;
    private static final int DETAIL_TAB_X = 116; private static final int DETAIL_TAB_Y = 34;
    private static final int DETAIL_TAB_W = 90;  private static final int DETAIL_TAB_H = 20;
    private static final int CREATE_TAB_X = 215; private static final int CREATE_TAB_Y = 34;
    private static final int CREATE_TAB_W = 92;  private static final int CREATE_TAB_H = 20;
    private static final int ASSIGN_TAB_X = 314; private static final int ASSIGN_TAB_Y = 34;
    private static final int ASSIGN_TAB_W = 96;  private static final int ASSIGN_TAB_H = 20;

    private static final int DETAIL_BUTTON_X   = 260;
    private static final int DETAIL_BUTTON_Y   = 194;
    private static final int DETAIL_BUTTON_W   = 40;
    private static final int DETAIL_BUTTON_H   = 12;
    private static final int DETAIL_BUTTON_GAP = 4;

    private static final int CR_X       = 22;
    private static final int CR_W       = 192;
    private static final int CR_ROW_H   = 12;
    private static final int CR_DESC_H  = 38;
    private static final int DESC_MAX   = 192;

    private static final int CR_Y_TASKID  = 62;
    private static final int CR_Y_NAME    = 78;
    private static final int CR_Y_DESC    = 94;
    private static final int CR_Y_DIV     = 136;
    private static final int CR_Y_OBJ     = 152;
    private static final int CR_Y_TARGET  = 168;
    private static final int CR_Y_AMOUNT  = 184;
    private static final int CR_Y_REWARD  = 196;

    private static final int CR_RX       = 220;
    private static final int CR_Y_APPROV = 62;
    private static final int CR_Y_HINT1  = 82;
    private static final int CR_Y_HINT2  = 92;
    private static final int CR_Y_CBTN   = 192;
    private static final int CR_BTN_W    = 106;
    private static final int CR_BTN_H    = 14;

    // How many task rows fit in the list panel
    private static final int VISIBLE_ROWS = LIST_HEIGHT / ROW_HEIGHT;
    // How many detail lines fit in the detail panel (leaving 18px for buttons)
    private static final int DETAIL_LINE_H       = 10;
    private static final int DETAIL_TITLE_H      = 14;
    private static final int DETAIL_USABLE_H     = DETAIL_HEIGHT - 18 - DETAIL_TITLE_H;
    private static final int VISIBLE_DETAIL_LINES = DETAIL_USABLE_H / DETAIL_LINE_H;

    // ── Scroll state ──────────────────────────────────────────────────────────
    private int listScrollOffset   = 0;
    private int detailScrollOffset = 0;

    // ── Form state ────────────────────────────────────────────────────────────
    private AdminTab     selectedTab         = AdminTab.TASKS;
    private CreateField  selectedCreateField = CreateField.TASK_ID;
    private AssignField  selectedAssignField = AssignField.MODE;
    private AssignMode   assignMode          = AssignMode.PLAYER;

    private String  createTaskId                      = "";
    private String  createDisplayName                 = "";
    private String  createDescription                 = "";
    private Division      createDivision              = Division.OPERATIONS;
    private TaskObjectiveType createObjectiveType     = TaskObjectiveType.BREAK_BLOCK;
    private String  createTargetId                    = "";
    private int     createRequiredAmount              = 1;
    private int     createRewardPoints                = 10;
    private boolean createOfficerConfirmationRequired = false;

    private String   assignPlayerName = "";
    private Division assignDivision   = Division.OPERATIONS;

    private int         validationFlashTicks = 0;
    private CreateField validationFlashField = null;

    // ── Enums ─────────────────────────────────────────────────────────────────
    public enum CreateField {
        TASK_ID, DISPLAY_NAME, DESCRIPTION,
        DIVISION, OBJECTIVE_TYPE, TARGET_ID,
        REQUIRED_AMOUNT, REWARD_POINTS,
        OFFICER_CONFIRMATION, CREATE_BUTTON
    }

    public enum AssignMode  { PLAYER, DIVISION, PUBLIC }
    public enum AssignField { MODE, PLAYER_NAME, DIVISION, ASSIGN_BUTTON }

    private enum AdminTab    { TASKS, DETAIL, CREATE, ASSIGN }
    private enum DetailAction { APPROVE, RETURN, FAIL, CANCEL }

    // ── Constructor ───────────────────────────────────────────────────────────
    public OperationsPadScreen(AdminTaskScreenHandler handler, PlayerInventory inventory, Text title) {
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

    public AdminTaskScreenHandler getScreenHandler() { return this.handler; }

    public void handleRefreshApplied() {
        if (selectedTab == AdminTab.DETAIL && handler.getSelectedTask() == null) {
            selectedTab = AdminTab.TASKS;
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (validationFlashTicks > 0) validationFlashTicks--;
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        context.drawTexture(TEXTURE, x, y, 0, 0,
                backgroundWidth, backgroundHeight, TEX_WIDTH, TEX_HEIGHT);
        drawTabHighlight(context, x, y);
        drawTabLabels(context, x, y);
        switch (selectedTab) {
            case TASKS  -> drawTaskList(context, x, y);
            case DETAIL -> drawTaskDetails(context, x, y);
            case CREATE -> drawCreateTab(context, x, y);
            case ASSIGN -> drawAssignTab(context, x, y);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {}

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private void drawTabHighlight(DrawContext context, int x, int y) {
        int hl = 0x10FFFFFF;
        switch (selectedTab) {
            case TASKS  -> context.fill(x+TASKS_TAB_X,  y+TASKS_TAB_Y,  x+TASKS_TAB_X+TASKS_TAB_W,   y+TASKS_TAB_Y+TASKS_TAB_H,   hl);
            case DETAIL -> context.fill(x+DETAIL_TAB_X, y+DETAIL_TAB_Y, x+DETAIL_TAB_X+DETAIL_TAB_W, y+DETAIL_TAB_Y+DETAIL_TAB_H, hl);
            case CREATE -> context.fill(x+CREATE_TAB_X, y+CREATE_TAB_Y, x+CREATE_TAB_X+CREATE_TAB_W, y+CREATE_TAB_Y+CREATE_TAB_H, hl);
            case ASSIGN -> context.fill(x+ASSIGN_TAB_X, y+ASSIGN_TAB_Y, x+ASSIGN_TAB_X+ASSIGN_TAB_W, y+ASSIGN_TAB_Y+ASSIGN_TAB_H, hl);
        }
    }

    private void drawTabLabels(DrawContext context, int x, int y) {
        drawCenteredTabText(context, "TASKS",  x+TASKS_TAB_X,  y+TASKS_TAB_Y,  TASKS_TAB_W,  TASKS_TAB_H);
        drawCenteredTabText(context, "DETAIL", x+DETAIL_TAB_X, y+DETAIL_TAB_Y, DETAIL_TAB_W, DETAIL_TAB_H);
        drawCenteredTabText(context, "CREATE", x+CREATE_TAB_X, y+CREATE_TAB_Y, CREATE_TAB_W, CREATE_TAB_H);
        drawCenteredTabText(context, "ASSIGN", x+ASSIGN_TAB_X, y+ASSIGN_TAB_Y, ASSIGN_TAB_W, ASSIGN_TAB_H);
    }

    private void drawCenteredTabText(DrawContext context, String text, int bx, int by, int bw, int bh) {
        int tw = this.textRenderer.getWidth(text);
        context.drawText(this.textRenderer, text, bx+(bw-tw)/2, by+(bh-8)/2+3, 0xFFF2E7D5, false);
    }

    // ── TASKS tab ─────────────────────────────────────────────────────────────
    private void drawTaskList(DrawContext context, int x, int y) {
        List<AdminTaskViewModel> tasks = handler.getTasks();
        clampListScroll(tasks.size());

        context.enableScissor(x+LIST_X, y+LIST_Y,
                x+LIST_X+LIST_WIDTH, y+LIST_Y+LIST_HEIGHT);

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int dataIndex = i + listScrollOffset;
            if (dataIndex >= tasks.size()) break;

            int rx = x+LIST_X, ry = y+LIST_Y+(i*ROW_HEIGHT);
            boolean sel = dataIndex == handler.getSelectedIndex();
            context.fill(rx+2, ry+2, rx+LIST_WIDTH-6, ry+ROW_HEIGHT-4,
                    sel ? 0x14FFFFFF : 0x06000000);

            AdminTaskViewModel task = tasks.get(dataIndex);
            context.drawText(this.textRenderer, trim(task.getTitle(),  18), rx+8, ry+6,  0xFFF2E7D5, false);
            context.drawText(this.textRenderer, trim(task.getStatus(), 18), rx+8, ry+16, 0xFFD0D0D0, false);
        }

        context.disableScissor();

        drawScrollIndicators(context,
                x+LIST_X, y+LIST_Y, LIST_WIDTH, LIST_HEIGHT,
                listScrollOffset, tasks.size(), VISIBLE_ROWS);
    }

    // ── DETAIL tab ────────────────────────────────────────────────────────────
    private void drawTaskDetails(DrawContext context, int x, int y) {
        AdminTaskViewModel selected = handler.getSelectedTask();
        int tx = x+DETAIL_X+4, ty = y+DETAIL_Y+2;

        context.enableScissor(x+DETAIL_X, y+DETAIL_Y,
                x+DETAIL_X+DETAIL_WIDTH, y+DETAIL_Y+DETAIL_HEIGHT-18);

        if (selected == null) {
            context.drawText(this.textRenderer, "No task selected", tx, ty, 0xFFF2E7D5, false);
            context.disableScissor();
            drawDetailButtons(context, x, y);
            return;
        }

        // Title always visible, not scrolled
        context.drawText(this.textRenderer, trim(selected.getTitle(), 20), tx, ty, 0xFFF2E7D5, false);
        ty += DETAIL_TITLE_H;

        List<String> lines = selected.getDetailLines();
        clampDetailScroll(lines.size());

        for (int i = 0; i < VISIBLE_DETAIL_LINES; i++) {
            int dataIndex = i + detailScrollOffset;
            if (dataIndex >= lines.size()) break;
            context.drawText(this.textRenderer,
                    trim(lines.get(dataIndex), 22),
                    tx, ty, getDetailLineColor(lines.get(dataIndex)), false);
            ty += DETAIL_LINE_H;
        }

        context.disableScissor();

        drawScrollIndicators(context,
                x+DETAIL_X, y+DETAIL_Y+DETAIL_TITLE_H,
                DETAIL_WIDTH, DETAIL_HEIGHT-18-DETAIL_TITLE_H,
                detailScrollOffset, lines.size(), VISIBLE_DETAIL_LINES);

        drawDetailButtons(context, x, y);
    }

    private void drawDetailButtons(DrawContext context, int x, int y) {
        drawDetailActionButton(context, x+DETAIL_BUTTON_X,                                    y+DETAIL_BUTTON_Y, "APP",  DetailAction.APPROVE);
        drawDetailActionButton(context, x+DETAIL_BUTTON_X+(DETAIL_BUTTON_W+DETAIL_BUTTON_GAP),    y+DETAIL_BUTTON_Y, "BACK", DetailAction.RETURN);
        drawDetailActionButton(context, x+DETAIL_BUTTON_X+2*(DETAIL_BUTTON_W+DETAIL_BUTTON_GAP),  y+DETAIL_BUTTON_Y, "FAIL", DetailAction.FAIL);
        drawDetailActionButton(context, x+DETAIL_BUTTON_X+3*(DETAIL_BUTTON_W+DETAIL_BUTTON_GAP),  y+DETAIL_BUTTON_Y, "X",    DetailAction.CANCEL);
    }

    // ── CREATE tab ────────────────────────────────────────────────────────────
    private void drawCreateTab(DrawContext context, int x, int y) {
        drawCreateRow(context, x, y, "TASK ID", withCursor(createTaskId,      selectedCreateField == CreateField.TASK_ID),      CR_X, CR_Y_TASKID, CR_ROW_H, selectedCreateField == CreateField.TASK_ID,      CreateField.TASK_ID);
        drawCreateRow(context, x, y, "NAME",    withCursor(createDisplayName,  selectedCreateField == CreateField.DISPLAY_NAME), CR_X, CR_Y_NAME,   CR_ROW_H, selectedCreateField == CreateField.DISPLAY_NAME, CreateField.DISPLAY_NAME);
        drawDescriptionArea(context, x, y);
        drawCreateRow(context, x, y, "DIV",    createDivision.name(),         CR_X, CR_Y_DIV,    CR_ROW_H, selectedCreateField == CreateField.DIVISION,      CreateField.DIVISION);
        drawCreateRow(context, x, y, "OBJ",    createObjectiveType.name(),    CR_X, CR_Y_OBJ,    CR_ROW_H, selectedCreateField == CreateField.OBJECTIVE_TYPE, CreateField.OBJECTIVE_TYPE);
        drawCreateRow(context, x, y, "TARGET", withCursor(createTargetId,     selectedCreateField == CreateField.TARGET_ID),    CR_X, CR_Y_TARGET, CR_ROW_H, selectedCreateField == CreateField.TARGET_ID,    CreateField.TARGET_ID);
        drawCreateRow(context, x, y, "AMOUNT", String.valueOf(createRequiredAmount), CR_X, CR_Y_AMOUNT, CR_ROW_H, selectedCreateField == CreateField.REQUIRED_AMOUNT, CreateField.REQUIRED_AMOUNT);
        drawCreateRow(context, x, y, "REWARD", String.valueOf(createRewardPoints),   CR_X, CR_Y_REWARD, CR_ROW_H, selectedCreateField == CreateField.REWARD_POINTS,   CreateField.REWARD_POINTS);
        drawCreateRow(context, x, y, "APPROVAL", createOfficerConfirmationRequired ? "YES" : "NO", CR_RX, CR_Y_APPROV, CR_ROW_H, selectedCreateField == CreateField.OFFICER_CONFIRMATION, CreateField.OFFICER_CONFIRMATION);
        drawHintText(context, x+CR_RX+2, y+CR_Y_HINT1, "LEFT CLICK = UP");
        drawHintText(context, x+CR_RX+2, y+CR_Y_HINT2, "RIGHT CLICK = DOWN");
        drawCreateButton(context, x, y);
    }

    private void drawDescriptionArea(DrawContext context, int x, int y) {
        int x1 = x+CR_X, y1 = y+CR_Y_DESC, x2 = x1+CR_W, y2 = y1+CR_DESC_H;
        boolean focused  = selectedCreateField == CreateField.DESCRIPTION;
        boolean flashing = focused && validationFlashField == CreateField.DESCRIPTION && validationFlashTicks > 0;

        context.fill(x1, y1, x2, y2, focused ? 0x22FFFFFF : 0x0A000000);

        if (flashing) {
            context.fill(x1, y1, x2, y1+1, 0xAAFF4444);
            context.fill(x1, y2-1, x2, y2,  0xAAFF4444);
            context.fill(x1, y1, x1+1, y2,  0xAAFF4444);
            context.fill(x2-1, y1, x2, y2,  0xAAFF4444);
        }

        context.drawText(this.textRenderer, "DESC", x1+4, y1+2,
                flashing ? 0xFFFF6666 : 0xFFFFB24A, false);

        String display = focused ? createDescription + "_" : createDescription;
        List<String> wrapped = wrapText(display, 22);
        int lineY = y1 + 2;
        for (int i = 0; i < Math.min(wrapped.size(), 3); i++) {
            context.drawText(this.textRenderer, wrapped.get(i), x1+34, lineY, 0xFFF2E7D5, false);
            lineY += 11;
        }

        String counter = createDescription.length() + "/" + DESC_MAX;
        int counterX = x2 - this.textRenderer.getWidth(counter) - 3;
        context.drawText(this.textRenderer, counter, counterX, y2-9,
                createDescription.length() >= DESC_MAX ? 0xFFFF6666 : 0xFF888888, false);
    }

    private List<String> wrapText(String text, int maxChars) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start) end = lastSpace + 1;
            }
            lines.add(text.substring(start, end));
            start = end;
        }
        return lines;
    }

    private void drawCreateButton(DrawContext context, int x, int y) {
        int x1 = x+CR_RX, y1 = y+CR_Y_CBTN;
        boolean selected = selectedCreateField == CreateField.CREATE_BUTTON;
        boolean canCreate = !createTaskId.isBlank() && !createDisplayName.isBlank() && !createDescription.isBlank();
        int bg = selected ? 0x30FFFFFF : canCreate ? 0x1A00FF88 : 0x0A000000;
        context.fill(x1, y1, x1+CR_BTN_W, y1+CR_BTN_H, bg);
        String text = "CREATE TASK";
        int tw = this.textRenderer.getWidth(text);
        context.drawText(this.textRenderer, text, x1+(CR_BTN_W-tw)/2, y1+3,
                canCreate ? 0xFF88FFB8 : 0xFF888888, false);
    }

    // ── ASSIGN tab ────────────────────────────────────────────────────────────
    private void drawAssignTab(DrawContext context, int x, int y) {
        AdminTaskViewModel selected = handler.getSelectedTask();
        String taskName = selected == null ? "NONE" : selected.getTitle();
        drawAssignRow(context, x, y, "TASK",   trim(taskName, 20),  220, 64,  false);
        drawAssignRow(context, x, y, "MODE",   assignMode.name(),   220, 80,  selectedAssignField == AssignField.MODE);
        drawAssignRow(context, x, y, "PLAYER", withCursor(assignPlayerName, selectedAssignField == AssignField.PLAYER_NAME), 220, 96, selectedAssignField == AssignField.PLAYER_NAME);
        drawAssignRow(context, x, y, "DIV",    assignDivision.name(), 220, 112, selectedAssignField == AssignField.DIVISION);
        drawAssignButton(context, x, y, 220, 136, selectedAssignField == AssignField.ASSIGN_BUTTON);
        String hint = switch (assignMode) {
            case PLAYER   -> "Assign to online player";
            case DIVISION -> "Send to division pool";
            case PUBLIC   -> "Publish to public pool";
        };
        drawHintText(context, x+222, y+156, hint);
    }

    // ── Draw helpers ──────────────────────────────────────────────────────────
    private void drawCreateRow(DrawContext context, int baseX, int baseY,
                               String label, String value,
                               int localX, int localY, int rowH,
                               boolean selected, CreateField field) {
        int x1 = baseX+localX, y1 = baseY+localY, x2 = x1+CR_W, y2 = y1+rowH;
        boolean flashing = selected && validationFlashField == field && validationFlashTicks > 0;
        context.fill(x1, y1, x2, y2, selected ? 0x20FFFFFF : 0x0A000000);
        context.drawText(this.textRenderer, label,          x1+4,  y1+2, flashing ? 0xFFFF6666 : 0xFFFFB24A, false);
        context.drawText(this.textRenderer, trim(value, 20), x1+58, y1+2, 0xFFF2E7D5, false);
    }

    private void drawAssignRow(DrawContext context, int baseX, int baseY,
                               String label, String value,
                               int localX, int localY, boolean selected) {
        int x1 = baseX+localX, y1 = baseY+localY;
        context.fill(x1, y1, x1+180, y1+12, selected ? 0x20FFFFFF : 0x0A000000);
        context.drawText(this.textRenderer, label,          x1+4,  y1+2, 0xFF8FD7E8, false);
        context.drawText(this.textRenderer, trim(value, 20), x1+58, y1+2, 0xFFF2E7D5, false);
    }

    private void drawAssignButton(DrawContext context, int baseX, int baseY,
                                  int localX, int localY, boolean selected) {
        int x1 = baseX+localX, y1 = baseY+localY;
        context.fill(x1, y1, x1+100, y1+14, selected ? 0x30FFFFFF : 0x14000000);
        String text = "ASSIGN TASK";
        int tw = this.textRenderer.getWidth(text);
        context.drawText(this.textRenderer, text, x1+(100-tw)/2, y1+3, 0xFFF2E7D5, false);
    }

    private void drawDetailActionButton(DrawContext context, int x, int y,
                                        String label, DetailAction action) {
        int color = switch (action) {
            case APPROVE -> 0x2038FF9A;
            case RETURN  -> 0x20FFE08A;
            case FAIL    -> 0x20FF8A8A;
            case CANCEL  -> 0x20D0D0D0;
        };
        context.fill(x, y, x+DETAIL_BUTTON_W, y+DETAIL_BUTTON_H, color);
        int tw = this.textRenderer.getWidth(label);
        context.drawText(this.textRenderer, label, x+(DETAIL_BUTTON_W-tw)/2, y+2, 0xFFF2E7D5, false);
    }

    private void drawScrollIndicators(DrawContext context,
                                      int panelX, int panelY,
                                      int panelW, int panelH,
                                      int scrollOffset, int totalItems, int visibleItems) {
        if (totalItems <= visibleItems) return;
        int arrowX = panelX + panelW - 8;
        if (scrollOffset > 0)
            context.drawText(this.textRenderer, "^", arrowX, panelY + 2, 0xFFAAAAAA, false);
        if (scrollOffset + visibleItems < totalItems)
            context.drawText(this.textRenderer, "v", arrowX, panelY + panelH - 10, 0xFFAAAAAA, false);
    }

    private void drawHintText(DrawContext context, int x, int y, String text) {
        context.drawText(this.textRenderer, text, x, y, 0xFFB8B8B8, false);
    }

    private String withCursor(String value, boolean selected) {
        return selected ? value + "_" : value;
    }

    private int getDetailLineColor(String line) {
        if (line == null || line.isBlank())    return 0xFFFFFFFF;
        if (line.startsWith("Task ID:"))       return 0xFFD0D0D0;
        if (line.startsWith("Description:"))   return 0xFFFFFFFF;
        if (line.startsWith("Objective:")
                || line.startsWith("Target:")
                || line.startsWith("Reward:")) return 0xFFFFB24A;
        if (line.startsWith("Division:"))      return 0xFF8FD7E8;
        if (line.startsWith("Status:"))        return 0xFF9ED9D6;
        return 0xFFD7D7D7;
    }

    private String trim(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength
                ? text
                : text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.x, y = this.y;

        if (inside(mouseX, mouseY, x+TASKS_TAB_X,  y+TASKS_TAB_Y,  TASKS_TAB_W,  TASKS_TAB_H))  { selectedTab = AdminTab.TASKS;  return true; }
        if (inside(mouseX, mouseY, x+DETAIL_TAB_X, y+DETAIL_TAB_Y, DETAIL_TAB_W, DETAIL_TAB_H)) { selectedTab = AdminTab.DETAIL; return true; }
        if (inside(mouseX, mouseY, x+CREATE_TAB_X, y+CREATE_TAB_Y, CREATE_TAB_W, CREATE_TAB_H)) { selectedTab = AdminTab.CREATE; return true; }
        if (inside(mouseX, mouseY, x+ASSIGN_TAB_X, y+ASSIGN_TAB_Y, ASSIGN_TAB_W, ASSIGN_TAB_H)) { selectedTab = AdminTab.ASSIGN; return true; }

        if (selectedTab == AdminTab.CREATE) {
            if (inside(mouseX, mouseY, x+CR_X, y+CR_Y_TASKID,  CR_W, CR_ROW_H))  { selectedCreateField = CreateField.TASK_ID;       return true; }
            if (inside(mouseX, mouseY, x+CR_X, y+CR_Y_NAME,    CR_W, CR_ROW_H))  { selectedCreateField = CreateField.DISPLAY_NAME;   return true; }
            if (inside(mouseX, mouseY, x+CR_X, y+CR_Y_DESC,    CR_W, CR_DESC_H)) { selectedCreateField = CreateField.DESCRIPTION;    return true; }
            if (inside(mouseX, mouseY, x+CR_X, y+CR_Y_DIV,     CR_W, CR_ROW_H))  { selectedCreateField = CreateField.DIVISION;       cycleCreateDivision();      return true; }
            if (inside(mouseX, mouseY, x+CR_X, y+CR_Y_OBJ,     CR_W, CR_ROW_H))  { selectedCreateField = CreateField.OBJECTIVE_TYPE; cycleCreateObjectiveType(); return true; }
            if (inside(mouseX, mouseY, x+CR_X, y+CR_Y_TARGET,  CR_W, CR_ROW_H))  { selectedCreateField = CreateField.TARGET_ID;      return true; }
            if (inside(mouseX, mouseY, x+CR_X, y+CR_Y_AMOUNT,  CR_W, CR_ROW_H))  {
                selectedCreateField = CreateField.REQUIRED_AMOUNT;
                if (button == 0) incrementCreateRequiredAmount(); else decrementCreateRequiredAmount();
                return true;
            }
            if (inside(mouseX, mouseY, x+CR_X, y+CR_Y_REWARD,  CR_W, CR_ROW_H))  {
                selectedCreateField = CreateField.REWARD_POINTS;
                if (button == 0) incrementCreateRewardPoints(); else decrementCreateRewardPoints();
                return true;
            }
            if (inside(mouseX, mouseY, x+CR_RX, y+CR_Y_APPROV, CR_W, CR_ROW_H)) {
                selectedCreateField = CreateField.OFFICER_CONFIRMATION;
                toggleCreateOfficerConfirmationRequired();
                return true;
            }
            if (inside(mouseX, mouseY, x+CR_RX, y+CR_Y_CBTN, CR_BTN_W, CR_BTN_H)) {
                selectedCreateField = CreateField.CREATE_BUTTON;
                tryCreateTask();
                return true;
            }
        }

        if (selectedTab == AdminTab.ASSIGN) {
            if (inside(mouseX, mouseY, x+220, y+80,  180, 12)) { selectedAssignField = AssignField.MODE;          cycleAssignMode();     return true; }
            if (inside(mouseX, mouseY, x+220, y+96,  180, 12)) { selectedAssignField = AssignField.PLAYER_NAME;   return true; }
            if (inside(mouseX, mouseY, x+220, y+112, 180, 12)) { selectedAssignField = AssignField.DIVISION;      cycleAssignDivision(); return true; }
            if (inside(mouseX, mouseY, x+220, y+136, 100, 14)) { selectedAssignField = AssignField.ASSIGN_BUTTON; sendAssignTaskPacket(); return true; }
        }

        if (selectedTab == AdminTab.DETAIL) {
            AdminTaskViewModel sel = handler.getSelectedTask();
            if (sel != null) {
                String taskId = sel.getTaskId();
                if (inside(mouseX, mouseY, x+DETAIL_BUTTON_X,                                    y+DETAIL_BUTTON_Y, DETAIL_BUTTON_W, DETAIL_BUTTON_H)) { sendReviewActionPacket(taskId, "APPROVE"); return true; }
                if (inside(mouseX, mouseY, x+DETAIL_BUTTON_X+(DETAIL_BUTTON_W+DETAIL_BUTTON_GAP),    y+DETAIL_BUTTON_Y, DETAIL_BUTTON_W, DETAIL_BUTTON_H)) { sendReviewActionPacket(taskId, "RETURN");  return true; }
                if (inside(mouseX, mouseY, x+DETAIL_BUTTON_X+2*(DETAIL_BUTTON_W+DETAIL_BUTTON_GAP),  y+DETAIL_BUTTON_Y, DETAIL_BUTTON_W, DETAIL_BUTTON_H)) { sendReviewActionPacket(taskId, "FAIL");    return true; }
                if (inside(mouseX, mouseY, x+DETAIL_BUTTON_X+3*(DETAIL_BUTTON_W+DETAIL_BUTTON_GAP),  y+DETAIL_BUTTON_Y, DETAIL_BUTTON_W, DETAIL_BUTTON_H)) { sendReviewActionPacket(taskId, "CANCEL");  return true; }
            }
        }

        // Task list row clicks — offset by scroll
        if (selectedTab == AdminTab.TASKS || selectedTab == AdminTab.DETAIL || selectedTab == AdminTab.ASSIGN) {
            List<AdminTaskViewModel> tasks = handler.getTasks();
            for (int i = 0; i < VISIBLE_ROWS; i++) {
                int dataIndex = i + listScrollOffset;
                if (dataIndex >= tasks.size()) break;
                int rx = x+LIST_X, ry = y+LIST_Y+(i*ROW_HEIGHT);
                if (inside(mouseX, mouseY, rx+2, ry+2, LIST_WIDTH-8, ROW_HEIGHT-6)) {
                    handler.setSelectedIndex(dataIndex);
                    detailScrollOffset = 0;
                    if (selectedTab == AdminTab.TASKS) selectedTab = AdminTab.DETAIL;
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int x = this.x, y = this.y;
        int delta = verticalAmount > 0 ? -1 : 1;

        // Scroll the task list when hovering over it
        if (inside(mouseX, mouseY, x+LIST_X, y+LIST_Y, LIST_WIDTH, LIST_HEIGHT)) {
            listScrollOffset = clamp(listScrollOffset + delta, 0,
                    Math.max(0, handler.getTasks().size() - VISIBLE_ROWS));
            return true;
        }

        // Scroll the detail panel when hovering over it
        if (selectedTab == AdminTab.DETAIL
                && inside(mouseX, mouseY, x+DETAIL_X, y+DETAIL_Y, DETAIL_WIDTH, DETAIL_HEIGHT)) {
            AdminTaskViewModel sel = handler.getSelectedTask();
            if (sel != null) {
                detailScrollOffset = clamp(detailScrollOffset + delta, 0,
                        Math.max(0, sel.getDetailLines().size() - VISIBLE_DETAIL_LINES));
            }
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ── Keyboard ──────────────────────────────────────────────────────────────
    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (selectedTab == AdminTab.CREATE && isCreateTypingField(selectedCreateField)) {
            appendToSelectedCreateField(chr);
            return true;
        }
        if (selectedTab == AdminTab.ASSIGN && selectedAssignField == AssignField.PLAYER_NAME) {
            appendToSelectedAssignField(chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectedTab == AdminTab.CREATE) {
            if (isCreateTypingField(selectedCreateField)) {
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) { backspaceSelectedCreateField(); return true; }
                if (keyCode != GLFW.GLFW_KEY_ESCAPE
                        && keyCode != GLFW.GLFW_KEY_ENTER
                        && keyCode != GLFW.GLFW_KEY_KP_ENTER
                        && keyCode != GLFW.GLFW_KEY_TAB) return true;
            }
            if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                    && selectedCreateField == CreateField.CREATE_BUTTON) {
                tryCreateTask();
                return true;
            }
        }
        if (selectedTab == AdminTab.ASSIGN) {
            if (selectedAssignField == AssignField.PLAYER_NAME) {
                if (keyCode == GLFW.GLFW_KEY_BACKSPACE) { backspaceSelectedAssignField(); return true; }
                if (keyCode != GLFW.GLFW_KEY_ESCAPE
                        && keyCode != GLFW.GLFW_KEY_ENTER
                        && keyCode != GLFW.GLFW_KEY_KP_ENTER
                        && keyCode != GLFW.GLFW_KEY_TAB) return true;
            }
            if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                    && selectedAssignField == AssignField.ASSIGN_BUTTON) {
                sendAssignTaskPacket();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Validation + send ─────────────────────────────────────────────────────
    private void tryCreateTask() {
        if (createTaskId.isBlank())      { flashField(CreateField.TASK_ID);      return; }
        if (createDisplayName.isBlank()) { flashField(CreateField.DISPLAY_NAME); return; }
        if (createDescription.isBlank()) { flashField(CreateField.DESCRIPTION);  return; }
        if (createTargetId.isBlank())    { flashField(CreateField.TARGET_ID);    return; }
        sendCreateTaskPacket();
        resetCreateForm();
    }

    private void flashField(CreateField field) {
        validationFlashField = field;
        validationFlashTicks = 40;
        selectedCreateField  = field;
    }

    private void resetCreateForm() {
        createTaskId = ""; createDisplayName = ""; createDescription = "";
        createDivision = Division.OPERATIONS;
        createObjectiveType = TaskObjectiveType.BREAK_BLOCK;
        createTargetId = ""; createRequiredAmount = 1; createRewardPoints = 10;
        createOfficerConfirmationRequired = false;
        selectedCreateField = CreateField.TASK_ID;
        validationFlashField = null; validationFlashTicks = 0;
    }

    private void sendCreateTaskPacket() {
        ClientPlayNetworking.send(new CreateTaskC2SPacket(
                createTaskId, createDisplayName, createDescription,
                createDivision.name(), createObjectiveType.name(),
                createTargetId, createRequiredAmount, createRewardPoints,
                createOfficerConfirmationRequired));
    }

    private void sendAssignTaskPacket() {
        AdminTaskViewModel sel = handler.getSelectedTask();
        if (sel == null) return;
        ClientPlayNetworking.send(new AssignTaskC2SPacket(
                sel.getTaskId(), assignMode.name(), assignPlayerName, assignDivision.name()));
    }

    private void sendReviewActionPacket(String taskId, String actionName) {
        ClientPlayNetworking.send(new ReviewTaskActionC2SPacket(taskId, actionName));
    }

    // ── Field helpers ─────────────────────────────────────────────────────────
    private boolean isCreateTypingField(CreateField field) {
        return field == CreateField.TASK_ID || field == CreateField.DISPLAY_NAME
                || field == CreateField.DESCRIPTION || field == CreateField.TARGET_ID;
    }

    private void appendToSelectedCreateField(char c) {
        if (!isAllowedChar(c)) return;
        switch (selectedCreateField) {
            case TASK_ID      -> { if (createTaskId.length()      < 32)       createTaskId      += c; }
            case DISPLAY_NAME -> { if (createDisplayName.length() < 48)       createDisplayName += c; }
            case DESCRIPTION  -> { if (createDescription.length() < DESC_MAX) createDescription += c; }
            case TARGET_ID    -> { if (createTargetId.length()    < 48)       createTargetId    += c; }
            default -> {}
        }
    }

    private void backspaceSelectedCreateField() {
        switch (selectedCreateField) {
            case TASK_ID      -> createTaskId      = backspace(createTaskId);
            case DISPLAY_NAME -> createDisplayName = backspace(createDisplayName);
            case DESCRIPTION  -> createDescription = backspace(createDescription);
            case TARGET_ID    -> createTargetId    = backspace(createTargetId);
            default -> {}
        }
    }

    private void appendToSelectedAssignField(char c) {
        if (!isAllowedChar(c)) return;
        if (selectedAssignField == AssignField.PLAYER_NAME && assignPlayerName.length() < 32)
            assignPlayerName += c;
    }

    private void backspaceSelectedAssignField() {
        if (selectedAssignField == AssignField.PLAYER_NAME)
            assignPlayerName = backspace(assignPlayerName);
    }

    private void cycleCreateDivision()      { Division[] v = Division.values(); createDivision = v[(createDivision.ordinal() + 1) % v.length]; }
    private void cycleCreateObjectiveType() { TaskObjectiveType[] v = TaskObjectiveType.values(); createObjectiveType = v[(createObjectiveType.ordinal() + 1) % v.length]; }
    private void incrementCreateRequiredAmount() { createRequiredAmount = Math.min(999,  createRequiredAmount + 1); }
    private void decrementCreateRequiredAmount() { createRequiredAmount = Math.max(1,    createRequiredAmount - 1); }
    private void incrementCreateRewardPoints()   { createRewardPoints   = Math.min(9999, createRewardPoints   + 5); }
    private void decrementCreateRewardPoints()   { createRewardPoints   = Math.max(0,    createRewardPoints   - 5); }
    private void toggleCreateOfficerConfirmationRequired() { createOfficerConfirmationRequired = !createOfficerConfirmationRequired; }
    private void cycleAssignMode()     { AssignMode[] v = AssignMode.values(); assignMode = v[(assignMode.ordinal() + 1) % v.length]; }
    private void cycleAssignDivision() { Division[] v = Division.values();     assignDivision = v[(assignDivision.ordinal() + 1) % v.length]; }

    private String backspace(String v) { return (v == null || v.isEmpty()) ? "" : v.substring(0, v.length() - 1); }
    private boolean isAllowedChar(char c) { return c >= 32 && c != 127; }
    private boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx <= x + w && my >= y && my <= y + h; }

    // ── Scroll helpers ────────────────────────────────────────────────────────
    private void clampListScroll(int totalItems) {
        listScrollOffset = clamp(listScrollOffset, 0, Math.max(0, totalItems - VISIBLE_ROWS));
    }

    private void clampDetailScroll(int totalLines) {
        detailScrollOffset = clamp(detailScrollOffset, 0, Math.max(0, totalLines - VISIBLE_DETAIL_LINES));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}