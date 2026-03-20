package net.shard.seconddawnrp.tasksystem.pad;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.shard.seconddawnrp.SecondDawnRP;
import net.shard.seconddawnrp.divison.Division;
import net.shard.seconddawnrp.playerdata.PlayerProfile;
import net.shard.seconddawnrp.registry.ModScreenHandlers;
import net.shard.seconddawnrp.tasksystem.data.TaskObjectiveType;

import java.util.ArrayList;
import java.util.List;

public class AdminTaskScreenHandler extends ScreenHandler {

    public static final int BUTTON_CREATE_SUBMIT = 200;
    public static final int BUTTON_CYCLE_DIVISION = 201;
    public static final int BUTTON_CYCLE_OBJECTIVE = 202;
    public static final int BUTTON_AMOUNT_DOWN = 203;
    public static final int BUTTON_AMOUNT_UP = 204;
    public static final int BUTTON_REWARD_DOWN = 205;
    public static final int BUTTON_REWARD_UP = 206;
    public static final int BUTTON_TOGGLE_APPROVAL = 207;

    public static final int BUTTON_ASSIGN_MODE = 300;
    public static final int BUTTON_ASSIGN_DIVISION = 301;
    public static final int BUTTON_ASSIGN_SUBMIT = 302;

    private final List<AdminTaskViewModel> tasks = new ArrayList<>();
    private int selectedIndex = -1;

    private String createTaskId = "";
    private String createDisplayName = "";
    private String createDescription = "";
    private Division createDivision = Division.OPERATIONS;
    private TaskObjectiveType createObjectiveType = TaskObjectiveType.BREAK_BLOCK;
    private String createTargetId = "";
    private int createRequiredAmount = 1;
    private int createRewardPoints = 10;
    private boolean createOfficerConfirmationRequired = false;

    private CreateField selectedCreateField = CreateField.TASK_ID;

    private AssignMode assignMode = AssignMode.PLAYER;
    private String assignPlayerName = "";
    private Division assignDivision = Division.OPERATIONS;
    private AssignField selectedAssignField = AssignField.MODE;

    public enum CreateField {
        TASK_ID,
        DISPLAY_NAME,
        DESCRIPTION,
        DIVISION,
        OBJECTIVE_TYPE,
        TARGET_ID,
        REQUIRED_AMOUNT,
        REWARD_POINTS,
        OFFICER_CONFIRMATION,
        CREATE_BUTTON
    }

    public enum AssignMode {
        PLAYER,
        DIVISION,
        PUBLIC
    }

    public enum AssignField {
        MODE,
        PLAYER_NAME,
        DIVISION,
        ASSIGN_BUTTON
    }

    public AdminTaskScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(ModScreenHandlers.ADMIN_TASK_SCREEN, syncId);

        reloadTasks();
        if (!tasks.isEmpty()) {
            selectedIndex = 0;
        }
    }

    public void reloadTasks() {
        tasks.clear();
        tasks.addAll(SecondDawnRP.TASK_SERVICE.buildAdminTaskViews());
        if (selectedIndex >= tasks.size()) {
            selectedIndex = tasks.isEmpty() ? -1 : 0;
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    public boolean onButtonClick(PlayerEntity player, int id) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return false;
        }

        switch (id) {
            case BUTTON_CREATE_SUBMIT -> {
                return submitCreateTask(serverPlayer);
            }
            case BUTTON_CYCLE_DIVISION -> {
                cycleCreateDivision();
                return true;
            }
            case BUTTON_CYCLE_OBJECTIVE -> {
                cycleCreateObjectiveType();
                return true;
            }
            case BUTTON_AMOUNT_DOWN -> {
                decrementCreateRequiredAmount();
                return true;
            }
            case BUTTON_AMOUNT_UP -> {
                incrementCreateRequiredAmount();
                return true;
            }
            case BUTTON_REWARD_DOWN -> {
                decrementCreateRewardPoints();
                return true;
            }
            case BUTTON_REWARD_UP -> {
                incrementCreateRewardPoints();
                return true;
            }
            case BUTTON_TOGGLE_APPROVAL -> {
                toggleCreateOfficerConfirmationRequired();
                return true;
            }
            case BUTTON_ASSIGN_MODE -> {
                cycleAssignMode();
                return true;
            }
            case BUTTON_ASSIGN_DIVISION -> {
                cycleAssignDivision();
                return true;
            }
            case BUTTON_ASSIGN_SUBMIT -> {
                return submitAssignTask(serverPlayer);
            }
            default -> {
                return false;
            }
        }
    }

    public List<AdminTaskViewModel> getTasks() {
        return tasks;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int selectedIndex) {
        if (selectedIndex >= 0 && selectedIndex < tasks.size()) {
            this.selectedIndex = selectedIndex;
        }
    }

    public AdminTaskViewModel getSelectedTask() {
        if (selectedIndex < 0 || selectedIndex >= tasks.size()) {
            return null;
        }
        return tasks.get(selectedIndex);
    }

    public String getCreateTaskId() {
        return createTaskId;
    }

    public String getCreateDisplayName() {
        return createDisplayName;
    }

    public String getCreateDescription() {
        return createDescription;
    }

    public Division getCreateDivision() {
        return createDivision;
    }

    public TaskObjectiveType getCreateObjectiveType() {
        return createObjectiveType;
    }

    public String getCreateTargetId() {
        return createTargetId;
    }

    public int getCreateRequiredAmount() {
        return createRequiredAmount;
    }

    public int getCreateRewardPoints() {
        return createRewardPoints;
    }

    public boolean isCreateOfficerConfirmationRequired() {
        return createOfficerConfirmationRequired;
    }

    public CreateField getSelectedCreateField() {
        return selectedCreateField;
    }

    public void setSelectedCreateField(CreateField field) {
        this.selectedCreateField = field;
    }

    public AssignMode getAssignMode() {
        return assignMode;
    }

    public String getAssignPlayerName() {
        return assignPlayerName;
    }

    public Division getAssignDivision() {
        return assignDivision;
    }

    public AssignField getSelectedAssignField() {
        return selectedAssignField;
    }

    public void setSelectedAssignField(AssignField field) {
        this.selectedAssignField = field;
    }

    public void cycleCreateDivision() {
        Division[] values = Division.values();
        int next = (createDivision.ordinal() + 1) % values.length;
        createDivision = values[next];
    }

    public void cycleCreateObjectiveType() {
        TaskObjectiveType[] values = TaskObjectiveType.values();
        int next = (createObjectiveType.ordinal() + 1) % values.length;
        createObjectiveType = values[next];
    }

    public void incrementCreateRequiredAmount() {
        createRequiredAmount = Math.min(999, createRequiredAmount + 1);
    }

    public void decrementCreateRequiredAmount() {
        createRequiredAmount = Math.max(1, createRequiredAmount - 1);
    }

    public void incrementCreateRewardPoints() {
        createRewardPoints = Math.min(9999, createRewardPoints + 5);
    }

    public void decrementCreateRewardPoints() {
        createRewardPoints = Math.max(0, createRewardPoints - 5);
    }

    public void toggleCreateOfficerConfirmationRequired() {
        createOfficerConfirmationRequired = !createOfficerConfirmationRequired;
    }

    public void cycleAssignMode() {
        AssignMode[] values = AssignMode.values();
        int next = (assignMode.ordinal() + 1) % values.length;
        assignMode = values[next];
    }

    public void cycleAssignDivision() {
        Division[] values = Division.values();
        int next = (assignDivision.ordinal() + 1) % values.length;
        assignDivision = values[next];
    }

    public void appendToSelectedCreateField(char c) {
        if (!isAllowedChar(c)) {
            return;
        }

        switch (selectedCreateField) {
            case TASK_ID -> {
                if (createTaskId.length() < 32) createTaskId += c;
            }
            case DISPLAY_NAME -> {
                if (createDisplayName.length() < 48) createDisplayName += c;
            }
            case DESCRIPTION -> {
                if (createDescription.length() < 96) createDescription += c;
            }
            case TARGET_ID -> {
                if (createTargetId.length() < 48) createTargetId += c;
            }
            default -> {
            }
        }
    }

    public void appendToSelectedAssignField(char c) {
        if (!isAllowedChar(c)) {
            return;
        }

        if (selectedAssignField == AssignField.PLAYER_NAME) {
            if (assignPlayerName.length() < 32) {
                assignPlayerName += c;
            }
        }
    }

    public void backspaceSelectedCreateField() {
        switch (selectedCreateField) {
            case TASK_ID -> createTaskId = backspace(createTaskId);
            case DISPLAY_NAME -> createDisplayName = backspace(createDisplayName);
            case DESCRIPTION -> createDescription = backspace(createDescription);
            case TARGET_ID -> createTargetId = backspace(createTargetId);
            default -> {
            }
        }
    }

    public void backspaceSelectedAssignField() {
        if (selectedAssignField == AssignField.PLAYER_NAME) {
            assignPlayerName = backspace(assignPlayerName);
        }
    }

    private String backspace(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.substring(0, value.length() - 1);
    }

    private boolean isAllowedChar(char c) {
        return c >= 32 && c != 127;
    }

    private String sanitizeTaskId(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase().replace(' ', '_');
    }

    private boolean isCreateFormValid() {
        return !sanitizeTaskId(createTaskId).isBlank()
                && !createDisplayName.trim().isBlank()
                && !createDescription.trim().isBlank()
                && !createTargetId.trim().isBlank()
                && createRequiredAmount > 0
                && createRewardPoints >= 0;
    }

    public boolean submitCreateTask(ServerPlayerEntity player) {
        if (!isCreateFormValid()) {
            player.sendMessage(Text.literal("Task creation failed. Check all fields."), false);
            return false;
        }

        String taskId = sanitizeTaskId(createTaskId);

        boolean created = SecondDawnRP.TASK_SERVICE.createPoolTask(
                taskId,
                createDisplayName.trim(),
                createDescription.trim(),
                createDivision,
                createObjectiveType,
                createTargetId.trim(),
                createRequiredAmount,
                createRewardPoints,
                createOfficerConfirmationRequired,
                player.getUuid()
        );

        if (!created) {
            player.sendMessage(Text.literal("Task creation failed. Task ID may already exist."), false);
            return false;
        }

        player.sendMessage(Text.literal("Task created: " + createDisplayName.trim()), false);
        clearCreateForm();
        reloadTasks();
        return true;
    }

    public boolean submitAssignTask(ServerPlayerEntity player) {
        AdminTaskViewModel selectedTask = getSelectedTask();
        if (selectedTask == null) {
            player.sendMessage(Text.literal("No task selected."), false);
            return false;
        }

        String taskId = selectedTask.getTaskId();

        boolean success = switch (assignMode) {
            case PUBLIC -> SecondDawnRP.TASK_SERVICE.publishPoolTask(taskId);

            case DIVISION -> SecondDawnRP.TASK_SERVICE.assignPoolTaskToDivisionPool(taskId, assignDivision);

            case PLAYER -> {
                if (assignPlayerName.trim().isBlank()) {
                    player.sendMessage(Text.literal("Enter a player name."), false);
                    yield false;
                }

                ServerPlayerEntity targetPlayer = player.getServer()
                        .getPlayerManager()
                        .getPlayer(assignPlayerName.trim());

                if (targetPlayer == null) {
                    player.sendMessage(Text.literal("Player not found or not online."), false);
                    yield false;
                }

                PlayerProfile profile = SecondDawnRP.PROFILE_MANAGER.getLoadedProfile(targetPlayer.getUuid());
                if (profile == null) {
                    player.sendMessage(Text.literal("Player profile is not loaded."), false);
                    yield false;
                }

                yield SecondDawnRP.TASK_SERVICE.assignPoolTaskToPlayer(
                        taskId,
                        profile,
                        player.getUuid()
                );
            }
        };

        if (!success) {
            player.sendMessage(Text.literal("Assignment failed."), false);
            return false;
        }

        player.sendMessage(Text.literal("Task assigned: " + selectedTask.getTitle()), false);
        reloadTasks();
        clearAssignForm();
        return true;
    }

    public void clearCreateForm() {
        createTaskId = "";
        createDisplayName = "";
        createDescription = "";
        createDivision = Division.OPERATIONS;
        createObjectiveType = TaskObjectiveType.BREAK_BLOCK;
        createTargetId = "";
        createRequiredAmount = 1;
        createRewardPoints = 10;
        createOfficerConfirmationRequired = false;
        selectedCreateField = CreateField.TASK_ID;
    }

    public void clearAssignForm() {
        assignMode = AssignMode.PLAYER;
        assignPlayerName = "";
        assignDivision = Division.OPERATIONS;
        selectedAssignField = AssignField.MODE;
    }
}