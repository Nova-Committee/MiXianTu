package com.iafenvoy.mxt.screen.multiblock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout, the layer-by-layer reveal and the input hit tests. The scene itself is a picture-in-picture state, so this
 * class only decides what goes where and from which angle. Every rectangle here is relative to the given frame, which
 * is what lets the owning screen hand it the whole window.
 */
final class MultiblockStructureView {
    private static final int EDGE_PADDING = 20;
    private static final int TOP_OVERLAY_HEIGHT = 58;
    private static final int BOTTOM_OVERLAY_HEIGHT = 64;
    private static final int CONTROL_COUNT = 4;
    private static final int CONTROL_HEIGHT = 20;
    private static final int CONTROL_PADDING = 10;
    private static final int CONTROL_MIN_WIDTH = 30;
    private static final int CONTROL_GAP = 8;
    private static final int TIMELINE_HEIGHT = 4;
    private static final int TIMELINE_MARGIN = 16;
    private static final int TIMELINE_HIT_SLACK = 4;
    private static final int TIMELINE_HIT_HEIGHT = 14;
    private static final int KNOB_HALF_WIDTH = 2;
    private static final int KNOB_HALF_HEIGHT = 3;
    private static final long STEP_MILLIS = 850L;
    private static final float DEFAULT_YAW = -35.0F;
    private static final float DEFAULT_PITCH = 28.0F;
    private static final float PITCH_LIMIT = 75.0F;
    private static final float DRAG_YAW_SPEED = 0.55F;
    private static final float DRAG_PITCH_SPEED = 0.45F;
    private static final float MIN_SCENE_SCALE = 8.0F;
    private static final float MAX_SCENE_SCALE = 42.0F;
    private static final int SCENE_BACKDROP = 0x60000000;
    private static final int OVERLAY_BACKGROUND = 0xB00C1014;
    private static final int BUTTON_FILL = 0x80202836;
    private static final int BUTTON_FILL_HOVERED = 0x60FFD24A;
    private static final int BUTTON_OUTLINE = 0xFF5A6B7A;
    private static final int BUTTON_OUTLINE_HOVERED = 0xFFFFD24A;
    private static final int BUTTON_TEXT = 0xFFE6EAF0;
    private static final int TITLE_TEXT = 0xFFFFFFFF;
    private static final int MUTED_TEXT = 0xFF9AA4B2;
    private static final int TIMELINE_TRACK = 0x772E3742;
    private static final int TIMELINE_ACTIVE = 0xFFFFD24A;
    private static final int TIMELINE_KNOB = 0xFFFFF3C4;

    private boolean paused;
    private int manualStep = 1;
    private long startedAtMillis;
    private float viewYaw = DEFAULT_YAW;
    private float viewPitch = DEFAULT_PITCH;
    private boolean dragging;
    private MultiblockSceneRenderState.@Nullable SceneBlock hovered;

    void open() {
        this.paused = false;
        this.manualStep = 1;
        this.startedAtMillis = System.currentTimeMillis();
        this.viewYaw = DEFAULT_YAW;
        this.viewPitch = DEFAULT_PITCH;
        this.dragging = false;
        this.hovered = null;
    }

    void render(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY,
                int x, int y, int width, int height, MultiblockStructure structure) {
        int maxStep = layerCount(structure);
        int currentStep = this.currentStep(maxStep);
        this.extractScene(graphics, structure, mouseX, mouseY, x, y, width, height, currentStep);
        this.extractTopOverlay(graphics, font, structure, mouseX, mouseY, x, y, width, currentStep, maxStep);
        this.extractBottomOverlay(graphics, font, mouseX, mouseY, x, y, width, height, currentStep, maxStep);
        this.extractTooltip(graphics, font, mouseX, mouseY);
    }

    ClickResult mouseClicked(Font font, MultiblockStructure structure, double mouseX, double mouseY,
                             int x, int y, int width, int height) {
        if (isInside(mouseX, mouseY, x + EDGE_PADDING, y + 13, this.backButtonWidth(font), CONTROL_HEIGHT))
            return ClickResult.BACK;

        StructureControlAction control = this.selectControlAt(font, mouseX, mouseY, x, y, width, height);
        if (control != null) {
            this.applyControl(control, layerCount(structure));
            return ClickResult.HANDLED;
        }

        Integer timelineStep = this.selectTimelineAt(mouseX, mouseY, x, y, width, height, layerCount(structure));
        if (timelineStep != null) {
            this.paused = true;
            this.manualStep = timelineStep;
            return ClickResult.HANDLED;
        }

        if (isInside(mouseX, mouseY, this.sceneX(x), this.sceneY(y), this.sceneWidth(width), this.sceneHeight(height))) {
            this.dragging = true;
            return ClickResult.HANDLED;
        }
        return ClickResult.NONE;
    }

    boolean mouseReleased(int button) {
        if (button != 0 || !this.dragging) return false;
        this.dragging = false;
        return true;
    }

    // The mouse handler hands over this frame's movement, so the camera needs no anchor of its own.
    boolean mouseDragged(double deltaX, double deltaY) {
        if (!this.dragging) return false;
        this.viewYaw += (float) deltaX * DRAG_YAW_SPEED;
        this.viewPitch = clamp(this.viewPitch + (float) deltaY * DRAG_PITCH_SPEED, -PITCH_LIMIT, PITCH_LIMIT);
        return true;
    }

    boolean mouseScrolled(double mouseX, double mouseY, int x, int y, int width, int height) {
        return isInside(mouseX, mouseY, this.sceneX(x), this.sceneY(y), this.sceneWidth(width), this.sceneHeight(height));
    }

    boolean step(int delta, MultiblockStructure structure) {
        this.applyControl(delta < 0 ? StructureControlAction.PREVIOUS : StructureControlAction.NEXT, layerCount(structure));
        return true;
    }

    boolean toggle(MultiblockStructure structure) {
        this.applyControl(StructureControlAction.TOGGLE, layerCount(structure));
        return true;
    }

    private void extractScene(GuiGraphicsExtractor graphics, MultiblockStructure structure,
                              int mouseX, int mouseY, int x, int y, int width, int height, int visibleStep) {
        int sceneX = this.sceneX(x);
        int sceneY = this.sceneY(y);
        int sceneWidth = this.sceneWidth(width);
        int sceneHeight = this.sceneHeight(height);
        graphics.fill(sceneX, sceneY, sceneX + sceneWidth, sceneY + sceneHeight, SCENE_BACKDROP);
        StructureBounds bounds = StructureBounds.from(structure);
        MultiblockSceneRenderState.SceneBounds scene = bounds.sceneBounds();
        MultiblockSceneCamera camera = MultiblockSceneCamera.of(this.viewYaw, this.viewPitch,
                sceneScale(bounds, sceneWidth, sceneHeight), scene);
        List<MultiblockSceneRenderState.SceneBlock> blocks = this.visibleBlocks(structure, visibleStep, bounds);
        this.hovered = isInside(mouseX, mouseY, sceneX, sceneY, sceneWidth, sceneHeight)
                ? camera.pick(blocks, mouseX, mouseY, sceneX, sceneY, sceneX + sceneWidth, sceneY + sceneHeight)
                : null;
        graphics.submitPictureInPictureRenderState(new MultiblockSceneRenderState(blocks, camera,
                sceneX, sceneY, sceneX + sceneWidth, sceneY + sceneHeight,
                this.hovered == null ? -1 : this.hovered.index(), graphics.peekScissorStack()));
    }

    // The hovered cell's own item tooltip, so what is written is whatever that stack would say anywhere else.
    private void extractTooltip(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        if (this.hovered == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        List<Component> lines = this.hovered.stack().getTooltipLines(Item.TooltipContext.of(minecraft.level),
                minecraft.player, minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
        if (!lines.isEmpty()) graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    private void extractTopOverlay(GuiGraphicsExtractor graphics, Font font, MultiblockStructure structure,
                                   int mouseX, int mouseY, int x, int y, int width,
                                   int currentStep, int maxStep) {
        graphics.fill(x, y, x + width, y + TOP_OVERLAY_HEIGHT, OVERLAY_BACKGROUND);
        int buttonX = x + EDGE_PADDING;
        int buttonY = y + 13;
        int backWidth = this.backButtonWidth(font);
        this.extractButton(graphics, font, buttonX, buttonY, backWidth, CONTROL_HEIGHT,
                Component.translatable("screen.mxt.multiblock.back"),
                isInside(mouseX, mouseY, buttonX, buttonY, backWidth, CONTROL_HEIGHT));
        graphics.centeredText(font, structure.title(), x + width / 2, y + 8, TITLE_TEXT);
        if (maxStep > 0) {
            graphics.centeredText(font, Component.translatable("screen.mxt.multiblock.layer", currentStep, maxStep),
                    x + width / 2, y + 24, TITLE_TEXT);
        }
        graphics.centeredText(font, Component.translatable("screen.mxt.multiblock.hint"), x + width / 2, y + 40, MUTED_TEXT);
    }

    private void extractBottomOverlay(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY,
                                      int x, int y, int width, int height, int currentStep, int maxStep) {
        int overlayY = y + height - BOTTOM_OVERLAY_HEIGHT;
        graphics.fill(x, overlayY, x + width, y + height, OVERLAY_BACKGROUND);
        for (StructureControl control : this.controls(font, x, y, width, height)) {
            this.extractButton(graphics, font, control.x(), control.y(), control.width(), control.height(), control.label(),
                    isInside(mouseX, mouseY, control.x(), control.y(), control.width(), control.height()));
        }

        int timelineX = this.timelineX(x, width);
        int timelineWidth = this.timelineWidth(x, width);
        int timelineY = this.timelineBarY(y, height);
        graphics.fill(timelineX, timelineY, timelineX + timelineWidth, timelineY + TIMELINE_HEIGHT, TIMELINE_TRACK);
        int activeWidth = maxStep <= 0 ? 0
                : maxStep == 1 ? timelineWidth
                : Math.round((currentStep - 1) / (float) (maxStep - 1) * timelineWidth);
        graphics.fill(timelineX, timelineY, timelineX + activeWidth, timelineY + TIMELINE_HEIGHT, TIMELINE_ACTIVE);
        int knobX = timelineX + activeWidth;
        graphics.fill(knobX - KNOB_HALF_WIDTH, timelineY - KNOB_HALF_HEIGHT,
                knobX + KNOB_HALF_WIDTH, timelineY + TIMELINE_HEIGHT + KNOB_HALF_HEIGHT, TIMELINE_KNOB);
    }

    private void extractButton(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                               Component label, boolean hovered) {
        graphics.fill(x, y, x + width, y + height, hovered ? BUTTON_FILL_HOVERED : BUTTON_FILL);
        graphics.outline(x, y, width, height, hovered ? BUTTON_OUTLINE_HOVERED : BUTTON_OUTLINE);
        graphics.centeredText(font, label, x + width / 2, y + (height - font.lineHeight) / 2, BUTTON_TEXT);
    }

    private List<MultiblockSceneRenderState.SceneBlock> visibleBlocks(MultiblockStructure structure, int visibleStep,
                                                                      StructureBounds bounds) {
        List<MultiblockSceneRenderState.SceneBlock> blocks = new ArrayList<>();
        if (visibleStep <= 0 || structure.blocks().isEmpty()) return blocks;
        int topLayer = bounds.minY() + visibleStep - 1;
        List<MultiblockStructure.BlockEntry> entries = structure.blocks();
        for (int index = 0; index < entries.size(); index++) {
            MultiblockStructure.BlockEntry entry = entries.get(index);
            if (entry.y() > topLayer) continue;
            ItemStack icon = entry.icon();
            if (icon.isEmpty()) continue;
            blocks.add(new MultiblockSceneRenderState.SceneBlock(entry.state(), icon,
                    entry.x(), entry.y(), entry.z(), index));
        }
        return blocks;
    }

    // Big enough to read, small enough to leave the scene inside its frame: the footprint is measured across both
    // horizontal axes because the camera turns, and the height is counted at its worst case too.
    private static float sceneScale(StructureBounds bounds, int width, int height) {
        int spanX = bounds.maxX() - bounds.minX() + 1;
        int spanY = bounds.maxY() - bounds.minY() + 1;
        int spanZ = bounds.maxZ() - bounds.minZ() + 1;
        float footprint = Math.max(spanX, spanZ) + Math.min(spanX, spanZ) * 0.6F;
        float vertical = spanY + Math.max(spanX, spanZ) * 0.45F;
        float byWidth = width / Math.max(1.0F, footprint * 1.7F);
        float byHeight = height / Math.max(1.0F, vertical * 1.8F);
        return clamp(Math.min(byWidth, byHeight), MIN_SCENE_SCALE, MAX_SCENE_SCALE);
    }

    private static int layerCount(MultiblockStructure structure) {
        if (structure.blocks().isEmpty()) return 0;
        return StructureBounds.from(structure).layerCount();
    }

    // A paused view keeps its step; a running one walks the layers and wraps, so the replay needs no timer of its own.
    private int currentStep(int maxStep) {
        if (maxStep <= 0) return 0;
        if (this.paused) return Mth.clamp(this.manualStep, 1, maxStep);
        long elapsed = Math.max(0L, System.currentTimeMillis() - this.startedAtMillis);
        return (int) (elapsed / STEP_MILLIS % maxStep) + 1;
    }

    private @Nullable StructureControlAction selectControlAt(Font font, double mouseX, double mouseY,
                                                             int x, int y, int width, int height) {
        for (StructureControl control : this.controls(font, x, y, width, height)) {
            if (isInside(mouseX, mouseY, control.x(), control.y(), control.width(), control.height()))
                return control.action();
        }
        return null;
    }

    private @Nullable Integer selectTimelineAt(double mouseX, double mouseY,
                                               int x, int y, int width, int height, int maxStep) {
        if (maxStep <= 0) return null;
        int timelineX = this.timelineX(x, width);
        int timelineWidth = this.timelineWidth(x, width);
        int timelineY = this.timelineBarY(y, height) - TIMELINE_HIT_SLACK;
        if (!isInside(mouseX, mouseY, timelineX, timelineY, timelineWidth, TIMELINE_HIT_HEIGHT)) return null;
        if (maxStep == 1) return 1;
        float progress = (float) ((mouseX - timelineX) / Math.max(1.0D, timelineWidth));
        return Math.round(clamp(progress, 0.0F, 1.0F) * (maxStep - 1)) + 1;
    }

    private void applyControl(StructureControlAction action, int maxStep) {
        switch (action) {
            // The step is read before the pause is written: pausing first would read the stored step instead of the
            // one on screen, which is the step the player meant to walk away from.
            case PREVIOUS -> {
                int step = this.currentStep(maxStep);
                this.paused = true;
                this.manualStep = Math.max(1, step - 1);
            }
            case RESTART -> this.open();
            case TOGGLE -> {
                int step = this.currentStep(maxStep);
                if (this.paused) {
                    // Resuming rewinds the clock to where the paused step was, so play continues from there.
                    this.paused = false;
                    this.startedAtMillis = System.currentTimeMillis() - (long) Math.max(0, step - 1) * STEP_MILLIS;
                } else {
                    this.paused = true;
                    this.manualStep = step;
                }
            }
            case NEXT -> {
                int step = this.currentStep(maxStep);
                this.paused = true;
                this.manualStep = Math.min(maxStep, step + 1);
            }
        }
    }

    private List<StructureControl> controls(Font font, int x, int y, int width, int height) {
        Component previous = Component.translatable("screen.mxt.multiblock.previous");
        Component restart = Component.translatable("screen.mxt.multiblock.restart");
        Component next = Component.translatable("screen.mxt.multiblock.next");
        // The play label swaps with the pause label, so the button is sized for the wider of the two: a button that
        // jumps sideways on every toggle is worse than one that is a little too wide.
        int toggleWidth = Math.max(buttonWidth(font, Component.translatable("screen.mxt.multiblock.play")),
                buttonWidth(font, Component.translatable("screen.mxt.multiblock.pause")));

        List<StructureControl> controls = new ArrayList<>(CONTROL_COUNT);
        controls.add(new StructureControl(0, 0, buttonWidth(font, previous), CONTROL_HEIGHT, previous, StructureControlAction.PREVIOUS));
        controls.add(new StructureControl(0, 0, buttonWidth(font, restart), CONTROL_HEIGHT, restart, StructureControlAction.RESTART));
        controls.add(new StructureControl(0, 0, toggleWidth, CONTROL_HEIGHT,
                Component.translatable(this.paused ? "screen.mxt.multiblock.play" : "screen.mxt.multiblock.pause"),
                StructureControlAction.TOGGLE));
        controls.add(new StructureControl(0, 0, buttonWidth(font, next), CONTROL_HEIGHT, next, StructureControlAction.NEXT));

        int totalWidth = CONTROL_GAP * (controls.size() - 1);
        for (StructureControl control : controls) totalWidth += control.width();
        int cursor = x + (width - totalWidth) / 2;
        int controlY = y + height - BOTTOM_OVERLAY_HEIGHT + 14;
        List<StructureControl> placed = new ArrayList<>(controls.size());
        for (StructureControl control : controls) {
            placed.add(new StructureControl(cursor, controlY, control.width(), CONTROL_HEIGHT, control.label(), control.action()));
            cursor += control.width() + CONTROL_GAP;
        }
        return placed;
    }

    private int backButtonWidth(Font font) {
        return buttonWidth(font, Component.translatable("screen.mxt.multiblock.back"));
    }

    private static int buttonWidth(Font font, Component label) {
        return Math.max(CONTROL_MIN_WIDTH, font.width(label) + CONTROL_PADDING * 2);
    }

    private int sceneX(int x) {
        return x + EDGE_PADDING;
    }

    private int sceneY(int y) {
        return y + TOP_OVERLAY_HEIGHT;
    }

    private int sceneWidth(int width) {
        return Math.max(1, width - EDGE_PADDING * 2);
    }

    private int sceneHeight(int height) {
        return Math.max(1, height - TOP_OVERLAY_HEIGHT - BOTTOM_OVERLAY_HEIGHT);
    }

    private int timelineX(int x, int width) {
        return x + Math.max(EDGE_PADDING, width / 5);
    }

    private int timelineWidth(int x, int width) {
        return width - (this.timelineX(x, width) - x) * 2;
    }

    private int timelineBarY(int y, int height) {
        return y + height - TIMELINE_MARGIN;
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    enum ClickResult {
        NONE,
        HANDLED,
        BACK
    }

    private enum StructureControlAction {
        PREVIOUS,
        RESTART,
        TOGGLE,
        NEXT
    }

    private record StructureControl(int x, int y, int width, int height, Component label,
                                    StructureControlAction action) {
    }

    private record StructureBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        static StructureBounds from(MultiblockStructure structure) {
            if (structure.blocks().isEmpty()) return new StructureBounds(0, 0, 0, 0, 0, 0);
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (MultiblockStructure.BlockEntry entry : structure.blocks()) {
                minX = Math.min(minX, entry.x());
                minY = Math.min(minY, entry.y());
                minZ = Math.min(minZ, entry.z());
                maxX = Math.max(maxX, entry.x());
                maxY = Math.max(maxY, entry.y());
                maxZ = Math.max(maxZ, entry.z());
            }
            return new StructureBounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        int layerCount() {
            return this.maxY - this.minY + 1;
        }

        MultiblockSceneRenderState.SceneBounds sceneBounds() {
            return new MultiblockSceneRenderState.SceneBounds(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
        }
    }
}
