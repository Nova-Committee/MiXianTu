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
 * The layer-by-layer reveal, the camera, the boxes the page draws the chrome in and the input hit tests. The scene
 * itself is a picture-in-picture state, so this class decides what goes where and from which angle, and the screen
 * writes those rectangles into the page.
 * <p>
 * The page covers the whole window, so every rectangle here is in window coordinates.
 */
final class MultiblockStructureView {
    private static final int EDGE_PADDING = 20;
    private static final int TOP_OVERLAY_HEIGHT = 58;
    private static final int BOTTOM_OVERLAY_HEIGHT = 64;
    static final int CONTROL_COUNT = 4;
    private static final int CONTROL_HEIGHT = 20;
    private static final int CONTROL_PADDING = 10;
    private static final int CONTROL_MIN_WIDTH = 30;
    private static final int CONTROL_GAP = 8;
    /**
     * Where the four controls sit, measured from the bottom bar's top edge.
     */
    private static final int CONTROL_TOP = 14;
    /**
     * A text box is one vanilla line tall, which is what puts the bitmap font back where the old screen drew it.
     */
    private static final int TEXT_HEIGHT = 9;
    private static final int TITLE_TOP = 8;
    private static final int LAYER_TOP = 24;
    private static final int HINT_TOP = 40;
    private static final int BACK_TOP = 13;
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
    /**
     * The dark plate under the scene. It stays on this side on purpose: it has to be painted immediately before the
     * scene's picture-in-picture state, and the page cannot order itself against that. The alpha matches the page's
     * two overlay bars, so the whole window reads as one plate.
     */
    private static final int SCENE_BACKDROP = 0xB0000000;

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

    // ------------------------------------------------------------------ the boxes the page is written with

    /** One box of the page, in window coordinates. */
    record Rect(int x, int y, int width, int height) {
    }

    /** One of the four bottom controls; the label is rebuilt every frame because play and pause share one key. */
    record StructureControl(Rect box, Component label) {
    }

    Rect topBar(int width) {
        return new Rect(0, 0, width, TOP_OVERLAY_HEIGHT);
    }

    Rect bottomBar(int width, int height) {
        return new Rect(0, height - BOTTOM_OVERLAY_HEIGHT, width, BOTTOM_OVERLAY_HEIGHT);
    }

    /**
     * The scene spans the whole width: the two bars and this box are the only things on the screen, so nothing of
     * the world shows through next to it. Its own dark plate is painted by {@link #extractScene}.
     */
    Rect scene(int width, int height) {
        return new Rect(0, TOP_OVERLAY_HEIGHT, Math.max(1, width),
                Math.max(1, height - TOP_OVERLAY_HEIGHT - BOTTOM_OVERLAY_HEIGHT));
    }

    /** The title box: the whole width, centred by the page's {@code .t-center}. */
    Rect title(int width) {
        return new Rect(0, TITLE_TOP, width, TEXT_HEIGHT);
    }

    Rect layer(int width) {
        return new Rect(0, LAYER_TOP, width, TEXT_HEIGHT);
    }

    Rect hint(int width) {
        return new Rect(0, HINT_TOP, width, TEXT_HEIGHT);
    }

    Rect back(Font font) {
        return new Rect(EDGE_PADDING, BACK_TOP, this.backButtonWidth(font), CONTROL_HEIGHT);
    }

    Rect timeline(int width, int height) {
        return new Rect(this.timelineX(width), this.timelineBarY(height), this.timelineWidth(width), TIMELINE_HEIGHT);
    }

    /** The played part of the timeline, relative to the track box ({@link #timeline}). */
    Rect timelineFill(int width, int maxStep) {
        return new Rect(0, 0, this.timelineActiveWidth(width, maxStep), TIMELINE_HEIGHT);
    }

    Rect timelineKnob(int width, int height, int maxStep) {
        int knobX = this.timelineX(width) + this.timelineActiveWidth(width, maxStep);
        int knobY = this.timelineBarY(height);
        return new Rect(knobX - KNOB_HALF_WIDTH, knobY - KNOB_HALF_HEIGHT,
                KNOB_HALF_WIDTH * 2, TIMELINE_HEIGHT + KNOB_HALF_HEIGHT * 2);
    }

    /**
     * The four controls, laid out left to right and centred on the window: the page sizes a key to its own text,
     * which keeps a translated label from being clipped.
     */
    List<StructureControl> controls(Font font, int width, int height) {
        Component previous = Component.translatable("screen.mxt.multiblock.previous");
        Component restart = Component.translatable("screen.mxt.multiblock.restart");
        Component next = Component.translatable("screen.mxt.multiblock.next");
        // The play label swaps with the pause label, so the button is sized for the wider of the two: a button that
        // jumps sideways on every toggle is worse than one that is a little too wide.
        int toggleWidth = Math.max(buttonWidth(font, Component.translatable("screen.mxt.multiblock.play")),
                buttonWidth(font, Component.translatable("screen.mxt.multiblock.pause")));

        List<Component> labels = List.of(previous, restart,
                Component.translatable(this.paused ? "screen.mxt.multiblock.play" : "screen.mxt.multiblock.pause"), next);
        List<Integer> widths = List.of(buttonWidth(font, previous), buttonWidth(font, restart), toggleWidth,
                buttonWidth(font, next));
        int totalWidth = CONTROL_GAP * (labels.size() - 1);
        for (int controlWidth : widths) totalWidth += controlWidth;
        int cursor = (width - totalWidth) / 2;
        int controlY = height - BOTTOM_OVERLAY_HEIGHT + CONTROL_TOP;
        List<StructureControl> placed = new ArrayList<>(labels.size());
        for (int index = 0; index < labels.size(); index++) {
            int controlWidth = widths.get(index);
            placed.add(new StructureControl(new Rect(cursor, controlY, controlWidth, CONTROL_HEIGHT), labels.get(index)));
            cursor += controlWidth + CONTROL_GAP;
        }
        return placed;
    }

    int maxStep(MultiblockStructure structure) {
        return layerCount(structure);
    }

    int currentStep(MultiblockStructure structure) {
        return this.currentStep(layerCount(structure));
    }

    // ------------------------------------------------------------------ the scene itself

    void extractScene(GuiGraphicsExtractor graphics, MultiblockStructure structure, int mouseX, int mouseY,
                      int width, int height) {
        Rect scene = this.scene(width, height);
        graphics.fill(scene.x(), scene.y(), scene.x() + scene.width(), scene.y() + scene.height(), SCENE_BACKDROP);
        StructureBounds bounds = StructureBounds.from(structure);
        MultiblockSceneRenderState.SceneBounds sceneBounds = bounds.sceneBounds();
        MultiblockSceneCamera camera = MultiblockSceneCamera.of(this.viewYaw, this.viewPitch,
                sceneScale(bounds, scene.width(), scene.height()), sceneBounds);
        List<MultiblockSceneRenderState.SceneBlock> blocks = this.visibleBlocks(structure, this.currentStep(layerCount(structure)), bounds);
        this.hovered = isInside(mouseX, mouseY, scene.x(), scene.y(), scene.width(), scene.height())
                ? camera.pick(blocks, mouseX, mouseY, scene.x(), scene.y(), scene.x() + scene.width(), scene.y() + scene.height())
                : null;
        graphics.submitPictureInPictureRenderState(new MultiblockSceneRenderState(blocks, camera,
                scene.x(), scene.y(), scene.x() + scene.width(), scene.y() + scene.height(),
                this.hovered == null ? -1 : this.hovered.index(), graphics.peekScissorStack()));
    }

    // The hovered cell's own item tooltip, so what is written is whatever that stack would say anywhere else.
    void extractTooltip(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        if (this.hovered == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        List<Component> lines = this.hovered.stack().getTooltipLines(Item.TooltipContext.of(minecraft.level),
                minecraft.player, minecraft.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
        if (!lines.isEmpty()) graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ input

    /** A click inside the timeline's hit band pauses the replay and jumps to the layer under the pointer. */
    boolean seekTimeline(double mouseX, double mouseY, int width, int height, MultiblockStructure structure) {
        int maxStep = layerCount(structure);
        if (maxStep <= 0) return false;
        int timelineX = this.timelineX(width);
        int timelineWidth = this.timelineWidth(width);
        int hitY = this.timelineBarY(height) - TIMELINE_HIT_SLACK;
        if (!isInside(mouseX, mouseY, timelineX, hitY, timelineWidth, TIMELINE_HIT_HEIGHT)) return false;
        this.paused = true;
        if (maxStep == 1) {
            this.manualStep = 1;
            return true;
        }
        float progress = (float) ((mouseX - timelineX) / Math.max(1.0D, timelineWidth));
        this.manualStep = Math.round(clamp(progress, 0.0F, 1.0F) * (maxStep - 1)) + 1;
        return true;
    }

    /** A press anywhere in the scene starts an orbit drag; the buttons below are the page's own, not this. */
    boolean beginDrag(double mouseX, double mouseY, int width, int height) {
        Rect scene = this.scene(width, height);
        if (!isInside(mouseX, mouseY, scene.x(), scene.y(), scene.width(), scene.height())) return false;
        this.dragging = true;
        return true;
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

    // The scene has no zoom of its own: a scroll over it is consumed rather than handed to whatever is behind.
    boolean scrollOverScene(double mouseX, double mouseY, int width, int height) {
        Rect scene = this.scene(width, height);
        return isInside(mouseX, mouseY, scene.x(), scene.y(), scene.width(), scene.height());
    }

    // The step is read before the pause is written: pausing first would read the stored step instead of the one on
    // screen, which is the step the player meant to walk away from.
    void previous(MultiblockStructure structure) {
        int step = this.currentStep(layerCount(structure));
        this.paused = true;
        this.manualStep = Math.max(1, step - 1);
    }

    void next(MultiblockStructure structure) {
        int maxStep = layerCount(structure);
        int step = this.currentStep(maxStep);
        this.paused = true;
        this.manualStep = Math.min(maxStep, step + 1);
    }

    void toggle(MultiblockStructure structure) {
        int step = this.currentStep(layerCount(structure));
        if (this.paused) {
            // Resuming rewinds the clock to where the paused step was, so play continues from there.
            this.paused = false;
            this.startedAtMillis = System.currentTimeMillis() - (long) Math.max(0, step - 1) * STEP_MILLIS;
        } else {
            this.paused = true;
            this.manualStep = step;
        }
    }

    // ------------------------------------------------------------------ the scene's contents

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

    private int timelineActiveWidth(int width, int maxStep) {
        int timelineWidth = this.timelineWidth(width);
        if (maxStep <= 0) return 0;
        if (maxStep == 1) return timelineWidth;
        return Math.round((this.currentStep(maxStep) - 1) / (float) (maxStep - 1) * timelineWidth);
    }

    private int backButtonWidth(Font font) {
        return buttonWidth(font, Component.translatable("screen.mxt.multiblock.back"));
    }

    private static int buttonWidth(Font font, Component label) {
        return Math.max(CONTROL_MIN_WIDTH, font.width(label) + CONTROL_PADDING * 2);
    }

    private int timelineX(int width) {
        return Math.max(EDGE_PADDING, width / 5);
    }

    private int timelineWidth(int width) {
        return width - this.timelineX(width) * 2;
    }

    private int timelineBarY(int height) {
        return height - TIMELINE_MARGIN;
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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
