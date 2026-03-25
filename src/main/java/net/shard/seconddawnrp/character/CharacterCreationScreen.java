package net.shard.seconddawnrp.character;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Character Creation Terminal screen.
 *
 * <p>Three-step flow rendered as tabs within a single screen:
 * <ol>
 *   <li>Identity — Character Name + Species selection</li>
 *   <li>Biography — freeform bio text</li>
 *   <li>Confirm — read-only summary before submitting</li>
 * </ol>
 *
 * <p>Code-drawn with no texture sampling, matching the Engineering PAD palette.
 */
public class CharacterCreationScreen extends Screen {

    // ── Palette — identical to EngineeringPadScreen ───────────────────────────
    private static final int COL_BG         = 0xFF03091A;
    private static final int COL_BG2        = 0xFF07101F;
    private static final int COL_HEADER_BG  = 0xFF050B1A;
    private static final int COL_BORDER     = 0xFFB96408;
    private static final int COL_ACCENT     = 0xFFD7820A;
    private static final int COL_DIM        = 0xFF502C04;
    private static final int COL_DIM2       = 0xFF1E0F02;
    private static final int COL_DARK_PNL   = 0xFF02050F;
    private static final int COL_ROW_DIV    = 0xFF0A1226;
    private static final int COL_TEXT_GOLD  = 0xFFD4AA44;
    private static final int COL_TEXT_DIM   = 0xFF886622;
    private static final int COL_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COL_TEXT_GRAY  = 0xFF888888;
    private static final int COL_GREEN      = 0xFF2D8214;
    private static final int COL_RED        = 0xFFA01C12;

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int W   = 280;
    private static final int H   = 220;
    private static final int PAD = 8;

    // ── State ─────────────────────────────────────────────────────────────────
    private final List<OpenCharacterCreationS2CPacket.SpeciesSnapshot> speciesList;
    private final boolean speciesLocked;

    private int currentStep = 0; // 0=Identity, 1=Bio, 2=Confirm

    // Step 0 — Identity
    private TextFieldWidget nameField;
    private int selectedSpeciesIndex = 0;

    // Step 1 — Bio
    private TextFieldWidget bioField;

    // Derived
    private int ox, oy;

    // ── Hover state ───────────────────────────────────────────────────────────
    private boolean hoverNext, hoverBack, hoverConfirm;
    private boolean hoverSpeciesUp, hoverSpeciesDown;

    public CharacterCreationScreen(
            List<OpenCharacterCreationS2CPacket.SpeciesSnapshot> speciesList,
            String currentName,
            String currentSpeciesId,
            String currentBio,
            boolean speciesLocked
    ) {
        super(Text.literal("Character Creation"));
        this.speciesList   = speciesList;
        this.speciesLocked = speciesLocked;

        // Pre-select species if already set
        if (!currentSpeciesId.isBlank()) {
            for (int i = 0; i < speciesList.size(); i++) {
                if (speciesList.get(i).id().equals(currentSpeciesId)) {
                    selectedSpeciesIndex = i;
                    break;
                }
            }
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    // ── Init ──────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        ox = (width  - W) / 2;
        oy = (height - H) / 2;

        // Name field — Step 0
        nameField = new TextFieldWidget(textRenderer,
                ox + PAD + 2, oy + 60, W - PAD * 2 - 4, 12,
                Text.literal("Character Name"));
        nameField.setMaxLength(SaveCharacterCreationC2SPacket.MAX_NAME_LENGTH);
        nameField.setPlaceholder(Text.literal("e.g. James T. Kirk").formatted(Formatting.DARK_GRAY));
        nameField.setVisible(currentStep == 0);
        addDrawableChild(nameField);

        // Bio field — Step 1
        bioField = new TextFieldWidget(textRenderer,
                ox + PAD, oy + 55, W - PAD * 2, 12,
                Text.literal("Biography"));
        bioField.setMaxLength(SaveCharacterCreationC2SPacket.MAX_BIO_LENGTH);
        bioField.setPlaceholder(Text.literal("Write your character's biography...").formatted(Formatting.DARK_GRAY));
        bioField.setVisible(currentStep == 1);
        addDrawableChild(bioField);
    }

    private void updateFieldVisibility() {
        if (nameField != null) nameField.setVisible(currentStep == 0);
        if (bioField  != null) bioField.setVisible(currentStep == 1);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // intentionally empty
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        ox = (width  - W) / 2;
        oy = (height - H) / 2;

        updateHover(mx, my);
        drawPanel(ctx, mx, my);
        super.render(ctx, mx, my, delta);
    }

    private void updateHover(int mx, int my) {
        // Next / Back / Confirm buttons
        int btnY = oy + H - 24;
        hoverNext    = mx >= ox + W - 70 && mx <= ox + W - PAD && my >= btnY && my <= btnY + 14;
        hoverBack    = mx >= ox + PAD    && mx <= ox + PAD + 60 && my >= btnY && my <= btnY + 14;
        hoverConfirm = hoverNext && currentStep == 2;

        // Species arrows (step 0 only)
        int arrowY = oy + 85;
        hoverSpeciesUp   = mx >= ox + W - 32 && mx <= ox + W - PAD && my >= arrowY     && my <= arrowY + 12;
        hoverSpeciesDown = mx >= ox + W - 32 && mx <= ox + W - PAD && my >= arrowY + 14 && my <= arrowY + 26;
    }

    private void drawPanel(DrawContext ctx, int mx, int my) {
        int x = ox, y = oy;

        // ── Outer panel ───────────────────────────────────────────────────────
        fill(ctx, x, y, x+W, y+H, COL_BG);
        hborder(ctx, x,   y,     x+W, COL_BORDER);
        hborder(ctx, x,   y+H-1, x+W, COL_BORDER);
        vborder(ctx, x,   y,     y+H, COL_BORDER);
        vborder(ctx, x+W-1, y,   y+H, COL_BORDER);
        hborder(ctx, x+1, y+1,   x+W-1, COL_DIM);
        hborder(ctx, x+1, y+H-2, x+W-1, COL_DIM);
        vborder(ctx, x+1, y+1,   y+H-1, COL_DIM);
        vborder(ctx, x+W-2, y+1, y+H-1, COL_DIM);

        // ── Title bar ─────────────────────────────────────────────────────────
        fill(ctx, x+2, y+2, x+W-2, y+15, COL_HEADER_BG);
        hborder(ctx, x+2, y+15, x+W-2, COL_BORDER);

        int[] dotCols = {0xFFAA2323, 0xFFA08210, COL_ACCENT};
        for (int i = 0; i < 3; i++) fill(ctx, x+PAD+i*9, y+5, x+PAD+i*9+5, y+10, dotCols[i]);

        ctx.drawText(textRenderer,
                Text.literal("Character Creation Terminal").formatted(Formatting.GOLD),
                x+36, y+4, COL_TEXT_WHITE, false);

        // ── Step tabs ─────────────────────────────────────────────────────────
        String[] tabs = {"1. Identity", "2. Biography", "3. Confirm"};
        int tabW = (W - 4) / 3;
        for (int i = 0; i < 3; i++) {
            int tx = x + 2 + i * tabW;
            boolean active = currentStep == i;
            fill(ctx, tx, y+16, tx+tabW-1, y+27,
                    active ? COL_BG2 : COL_DARK_PNL);
            hborder(ctx, tx, y+16, tx+tabW-1,
                    active ? COL_ACCENT : COL_DIM);
            hborder(ctx, tx, y+26, tx+tabW-1,
                    active ? COL_BG2 : COL_DIM);
            vborder(ctx, tx,        y+16, y+27, COL_DIM);
            vborder(ctx, tx+tabW-2, y+16, y+27, COL_DIM);
            ctx.drawText(textRenderer, Text.literal(tabs[i]),
                    tx + (tabW - textRenderer.getWidth(tabs[i])) / 2,
                    y+18,
                    active ? COL_TEXT_GOLD : COL_TEXT_GRAY, false);
        }

        // ── Step content ──────────────────────────────────────────────────────
        switch (currentStep) {
            case 0 -> drawIdentityStep(ctx, mx, my);
            case 1 -> drawBioStep(ctx, mx, my);
            case 2 -> drawConfirmStep(ctx, mx, my);
        }

        // ── Footer buttons ────────────────────────────────────────────────────
        drawFooter(ctx, mx, my);
    }

    // ── Step 0: Identity ──────────────────────────────────────────────────────

    private void drawIdentityStep(DrawContext ctx, int mx, int my) {
        int x = ox, y = oy;
        int contentY = y + 30;

        // Character name label on its own line, field full-width below
        ctx.drawText(textRenderer,
                Text.literal("Character Name").formatted(Formatting.GOLD),
                x+PAD, contentY + 2, COL_TEXT_GOLD, false);

        int fieldY = contentY + 14;
        fill(ctx, x+PAD, fieldY, x+W-PAD, fieldY+14, COL_DARK_PNL);
        hborder(ctx, x+PAD, fieldY,    x+W-PAD, COL_DIM);
        hborder(ctx, x+PAD, fieldY+13, x+W-PAD, COL_DIM);
        vborder(ctx, x+PAD,     fieldY, fieldY+14, COL_DIM);
        vborder(ctx, x+W-PAD-1, fieldY, fieldY+14, COL_DIM);

        // Update name field to sit inside the full-width box
        if (nameField != null) {
            nameField.setX(x + PAD + 2);
            nameField.setY(fieldY + 1);
            nameField.setWidth(W - PAD * 2 - 4);
        }

        // ── Species section ───────────────────────────────────────────────────
        int specY = contentY + 36;
        ctx.drawText(textRenderer,
                Text.literal("Species").formatted(Formatting.GOLD),
                x+PAD, specY, COL_TEXT_GOLD, false);

        if (speciesLocked) {
            ctx.drawText(textRenderer,
                    Text.literal("[Locked — GM override required]").formatted(Formatting.DARK_GRAY),
                    x+PAD+52, specY, COL_TEXT_GRAY, false);
        }

        // Species display box
        int boxY = specY + 12;
        fill(ctx, x+PAD, boxY, x+W-PAD-36, boxY+14, COL_DARK_PNL);
        hborder(ctx, x+PAD, boxY,    x+W-PAD-36, COL_DIM);
        hborder(ctx, x+PAD, boxY+13, x+W-PAD-36, COL_DIM);
        vborder(ctx, x+PAD,          boxY, boxY+14, COL_DIM);
        vborder(ctx, x+W-PAD-37,     boxY, boxY+14, COL_DIM);

        if (!speciesList.isEmpty()) {
            OpenCharacterCreationS2CPacket.SpeciesSnapshot sel =
                    speciesList.get(selectedSpeciesIndex);
            ctx.drawText(textRenderer, Text.literal(sel.displayName()),
                    x+PAD+3, boxY+3, COL_TEXT_WHITE, false);
        }

        // Up/down arrows (disabled if locked)
        if (!speciesLocked && speciesList.size() > 1) {
            int arrowX = x + W - PAD - 34;
            // Up arrow
            int upCol = hoverSpeciesUp ? COL_ACCENT : COL_DIM;
            fill(ctx, arrowX, boxY, arrowX+32, boxY+12, COL_DARK_PNL);
            hborder(ctx, arrowX, boxY,    arrowX+32, COL_DIM);
            hborder(ctx, arrowX, boxY+11, arrowX+32, COL_DIM);
            ctx.drawText(textRenderer, Text.literal("▲"), arrowX+12, boxY+2, upCol, false);

            // Down arrow
            int downCol = hoverSpeciesDown ? COL_ACCENT : COL_DIM;
            fill(ctx, arrowX, boxY+13, arrowX+32, boxY+25, COL_DARK_PNL);
            hborder(ctx, arrowX, boxY+13, arrowX+32, COL_DIM);
            hborder(ctx, arrowX, boxY+24, arrowX+32, COL_DIM);
            ctx.drawText(textRenderer, Text.literal("▼"), arrowX+12, boxY+15, downCol, false);
        }

        // Species description
        if (!speciesList.isEmpty()) {
            String desc = speciesList.get(selectedSpeciesIndex).description();
            int descY = boxY + 28;
            fill(ctx, x+PAD, descY, x+W-PAD, descY+40, COL_BG2);
            hborder(ctx, x+PAD, descY,    x+W-PAD, COL_DIM2);
            hborder(ctx, x+PAD, descY+39, x+W-PAD, COL_DIM2);
            drawWrappedText(ctx, desc, x+PAD+3, descY+3, W-PAD*2-6, COL_TEXT_GRAY);
        }

        // Hint
        ctx.drawText(textRenderer,
                Text.literal("Species can only be changed by a GM after creation.")
                        .formatted(Formatting.DARK_GRAY),
                x+PAD, oy+H-34, COL_TEXT_GRAY, false);
    }

    // ── Step 1: Biography ─────────────────────────────────────────────────────

    private void drawBioStep(DrawContext ctx, int mx, int my) {
        int x = ox, y = oy;
        int contentY = y + 30;

        ctx.drawText(textRenderer,
                Text.literal("Biography").formatted(Formatting.GOLD),
                x+PAD, contentY+2, COL_TEXT_GOLD, false);
        ctx.drawText(textRenderer,
                Text.literal("Optional. Tell your character's story.").formatted(Formatting.DARK_GRAY),
                x+PAD, contentY+12, COL_TEXT_GRAY, false);

        // Bio text field background
        int fieldY = contentY + 24;
        fill(ctx, x+PAD, fieldY, x+W-PAD, fieldY+14, COL_DARK_PNL);
        hborder(ctx, x+PAD, fieldY,    x+W-PAD, COL_DIM);
        hborder(ctx, x+PAD, fieldY+13, x+W-PAD, COL_DIM);
        vborder(ctx, x+PAD,    fieldY, fieldY+14, COL_DIM);
        vborder(ctx, x+W-PAD-1, fieldY, fieldY+14, COL_DIM);

        if (bioField != null) {
            bioField.setX(x+PAD+2);
            bioField.setY(fieldY+1);
            bioField.setWidth(W-PAD*2-4);
        }

        // Character count
        int bioLen = bioField != null ? bioField.getText().length() : 0;
        int remaining = SaveCharacterCreationC2SPacket.MAX_BIO_LENGTH - bioLen;
        int countCol  = remaining < 50 ? COL_RED : COL_TEXT_GRAY;
        ctx.drawText(textRenderer,
                Text.literal(remaining + " characters remaining"),
                x+PAD, fieldY+18, countCol, false);

        // Preview of what's been typed so far
        if (bioField != null && !bioField.getText().isBlank()) {
            int previewY = fieldY + 32;
            fill(ctx, x+PAD, previewY, x+W-PAD, previewY+50, COL_BG2);
            hborder(ctx, x+PAD, previewY,    x+W-PAD, COL_DIM2);
            hborder(ctx, x+PAD, previewY+49, x+W-PAD, COL_DIM2);
            ctx.drawText(textRenderer, Text.literal("Preview:").formatted(Formatting.DARK_GRAY),
                    x+PAD+3, previewY+2, COL_TEXT_GRAY, false);
            drawWrappedText(ctx, bioField.getText(), x+PAD+3, previewY+12, W-PAD*2-6, COL_TEXT_WHITE);
        }
    }

    // ── Step 2: Confirm ───────────────────────────────────────────────────────

    private void drawConfirmStep(DrawContext ctx, int mx, int my) {
        int x = ox, y = oy;
        int contentY = y + 32;

        ctx.drawText(textRenderer,
                Text.literal("Review your character before confirming.")
                        .formatted(Formatting.DARK_GRAY),
                x+PAD, contentY, COL_TEXT_GRAY, false);

        contentY += 14;

        String name    = nameField != null ? nameField.getText().trim() : "";
        String species = speciesList.isEmpty() ? "" : speciesList.get(selectedSpeciesIndex).displayName();
        String bio     = bioField  != null ? bioField.getText().trim() : "";

        drawLabeledRow(ctx, x, contentY,      "Name",    name.isBlank()    ? "[not set]" : name,    name.isBlank());
        drawLabeledRow(ctx, x, contentY + 18, "Species", species.isBlank() ? "[not set]" : species, species.isBlank());

        int bioY = contentY + 36;
        ctx.drawText(textRenderer, Text.literal("Biography").formatted(Formatting.GOLD),
                x+PAD, bioY, COL_TEXT_GOLD, false);
        fill(ctx, x+PAD, bioY+10, x+W-PAD, bioY+52, COL_BG2);
        hborder(ctx, x+PAD, bioY+10, x+W-PAD, COL_DIM2);
        hborder(ctx, x+PAD, bioY+51, x+W-PAD, COL_DIM2);
        drawWrappedText(ctx,
                bio.isBlank() ? "No biography provided." : bio,
                x+PAD+3, bioY+13, W-PAD*2-6,
                bio.isBlank() ? COL_TEXT_GRAY : COL_TEXT_WHITE);

        // Warning if required fields missing
        if (name.isBlank() || species.isBlank()) {
            ctx.drawText(textRenderer,
                    Text.literal("⚠ Fill in all required fields before confirming.")
                            .formatted(Formatting.RED),
                    x+PAD, bioY+56, COL_RED, false);
        } else {
            ctx.drawText(textRenderer,
                    Text.literal("✔ Ready to confirm. This cannot be undone easily.")
                            .formatted(Formatting.GREEN),
                    x+PAD, bioY+56, COL_GREEN, false);
        }
    }

    private void drawLabeledRow(DrawContext ctx, int x, int y,
                                String label, String value, boolean missing) {
        fill(ctx, x+PAD, y, x+W-PAD, y+14, COL_DARK_PNL);
        hborder(ctx, x+PAD, y,    x+W-PAD, COL_DIM);
        hborder(ctx, x+PAD, y+13, x+W-PAD, COL_DIM);
        ctx.drawText(textRenderer, Text.literal(label).formatted(Formatting.GOLD),
                x+PAD+3, y+3, COL_TEXT_GOLD, false);
        ctx.drawText(textRenderer, Text.literal(value),
                x+PAD+58, y+3, missing ? COL_RED : COL_TEXT_WHITE, false);
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private void drawFooter(DrawContext ctx, int mx, int my) {
        int x = ox, y = oy;
        int footY = y + H - 22;

        fill(ctx, x+2, footY, x+W-2, y+H-2, COL_HEADER_BG);
        hborder(ctx, x+2, footY, x+W-2, COL_BORDER);

        // Back button (not on first step)
        if (currentStep > 0) {
            int backCol = hoverBack ? COL_ACCENT : COL_DIM;
            fill(ctx, x+PAD, footY+4, x+PAD+60, footY+16, hoverBack ? COL_BG2 : COL_DARK_PNL);
            hborder(ctx, x+PAD, footY+4, x+PAD+60, backCol);
            hborder(ctx, x+PAD, footY+15, x+PAD+60, backCol);
            vborder(ctx, x+PAD,    footY+4, footY+16, backCol);
            vborder(ctx, x+PAD+59, footY+4, footY+16, backCol);
            ctx.drawText(textRenderer, Text.literal("← Back"),
                    x+PAD+8, footY+7, hoverBack ? COL_TEXT_WHITE : COL_TEXT_GRAY, false);
        }

        // Next / Confirm button
        String btnLabel = currentStep == 2 ? "✔ Confirm" : "Next →";
        int btnX = x + W - PAD - 70;
        boolean canConfirm = currentStep < 2 || canSubmit();
        int btnBorderCol = canConfirm ? (hoverNext ? COL_GREEN : COL_ACCENT) : COL_DIM;
        fill(ctx, btnX, footY+4, btnX+68, footY+16,
                hoverNext && canConfirm ? COL_BG2 : COL_DARK_PNL);
        hborder(ctx, btnX, footY+4, btnX+68, btnBorderCol);
        hborder(ctx, btnX, footY+15, btnX+68, btnBorderCol);
        vborder(ctx, btnX,    footY+4, footY+16, btnBorderCol);
        vborder(ctx, btnX+67, footY+4, footY+16, btnBorderCol);
        ctx.drawText(textRenderer, Text.literal(btnLabel),
                btnX + (68 - textRenderer.getWidth(btnLabel)) / 2,
                footY+7,
                canConfirm ? (hoverNext ? COL_TEXT_WHITE : COL_TEXT_GOLD) : COL_TEXT_GRAY,
                false);

        // Step indicator dots
        for (int i = 0; i < 3; i++) {
            int dotX = x + W/2 - 12 + i*10;
            fill(ctx, dotX, footY+8, dotX+6, footY+12,
                    i == currentStep ? COL_ACCENT : COL_DIM);
        }
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);

        int x = ox, y = oy;
        int footY = y + H - 22;

        // Back
        if (currentStep > 0 && hoverBack) {
            currentStep--;
            updateFieldVisibility();
            return true;
        }

        // Next / Confirm
        if (hoverNext) {
            if (currentStep < 2) {
                if (validateCurrentStep()) {
                    currentStep++;
                    updateFieldVisibility();
                }
            } else {
                // Confirm — submit
                submit();
            }
            return true;
        }

        // Species arrows (Step 0)
        if (currentStep == 0 && !speciesLocked && speciesList.size() > 1) {
            int arrowY = y + 30 + 34; // boxY
            int arrowX = x + W - PAD - 34;
            if ((int)mx >= arrowX && (int)mx <= arrowX+32) {
                if ((int)my >= arrowY && (int)my <= arrowY+12) {
                    selectedSpeciesIndex = (selectedSpeciesIndex - 1 + speciesList.size()) % speciesList.size();
                    return true;
                }
                if ((int)my >= arrowY+13 && (int)my <= arrowY+25) {
                    selectedSpeciesIndex = (selectedSpeciesIndex + 1) % speciesList.size();
                    return true;
                }
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        if (currentStep == 0 && !speciesLocked && speciesList.size() > 1) {
            if (v > 0) selectedSpeciesIndex = (selectedSpeciesIndex - 1 + speciesList.size()) % speciesList.size();
            else       selectedSpeciesIndex = (selectedSpeciesIndex + 1) % speciesList.size();
            return true;
        }
        return super.mouseScrolled(mx, my, h, v);
    }

    // ── Validation and submission ─────────────────────────────────────────────

    private boolean validateCurrentStep() {
        return switch (currentStep) {
            case 0 -> nameField != null && !nameField.getText().trim().isBlank()
                    && !speciesList.isEmpty();
            case 1 -> true; // bio is optional
            default -> canSubmit();
        };
    }

    private boolean canSubmit() {
        String name = nameField != null ? nameField.getText().trim() : "";
        return !name.isBlank() && !speciesList.isEmpty();
    }

    private void submit() {
        if (!canSubmit()) return;

        String name    = nameField.getText().trim();
        String species = speciesList.get(selectedSpeciesIndex).id();
        String bio     = bioField != null ? bioField.getText().trim() : "";

        ClientPlayNetworking.send(new SaveCharacterCreationC2SPacket(name, species, bio));
        this.close();
    }

    // ── Draw helpers ──────────────────────────────────────────────────────────

    private void drawWrappedText(DrawContext ctx, String text, int x, int y, int maxWidth, int color) {
        if (text == null || text.isBlank()) return;
        List<net.minecraft.client.font.TextRenderer.TextLayerType> dummy = List.of();
        var lines = textRenderer.wrapLines(Text.literal(text), maxWidth);
        for (int i = 0; i < Math.min(lines.size(), 4); i++) {
            ctx.drawText(textRenderer, lines.get(i), x, y + i * 10, color, false);
        }
    }

    private void fill(DrawContext ctx, int x0, int y0, int x1, int y1, int col) {
        if (x1 > x0 && y1 > y0) ctx.fill(x0, y0, x1, y1, col);
    }

    private void hborder(DrawContext ctx, int x0, int y, int x1, int col) {
        ctx.fill(x0, y, x1, y+1, col);
    }

    private void vborder(DrawContext ctx, int x, int y0, int y1, int col) {
        ctx.fill(x, y0, x+1, y1, col);
    }
}