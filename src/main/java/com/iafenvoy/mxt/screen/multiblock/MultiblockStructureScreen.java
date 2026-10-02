package com.iafenvoy.mxt.screen.multiblock;

import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.iafenvoy.mxt.screen.aui.AuiPages;
import com.iafenvoy.mxt.screen.aui.AuiScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The formation structure preview: the page draws the whole window - the two overlay bars, the title, the layer
 * line, the hint, the five keys and the timeline - while the scene, its dark plate and the hovered cell's tooltip
 * stay Java, the scene as a picture-in-picture state inside the box the page holds open for it.
 * <p>
 * The page carries no coordinates: {@link #writeLayout()} writes every box from {@link MultiblockStructureView}'s
 * numbers whenever the window size changes, the way the wheel writes its ring. The page must not set
 * {@code aui-mouse-events=intercept}: the timeline seek and the scene drag are still hit-tested here, while the
 * keys are ordinary DOM clicks.
 */
public final class MultiblockStructureScreen extends AuiScreen {
    private final MultiblockStructure structure;
    private final MultiblockStructureView view = new MultiblockStructureView();
    @Nullable
    private Element topBar, bottomBar, scene, back, timeline, timelineFill, timelineKnob;
    @Nullable
    private Label title, layer, hint, backLabel;
    private final List<Element> controlKeys = new ArrayList<>(MultiblockStructureView.CONTROL_COUNT);
    private final List<Label> controlLabels = new ArrayList<>(MultiblockStructureView.CONTROL_COUNT);
    /**
     * The window size the page was last written with; a resize is the only thing that changes the boxes.
     */
    private int shownWidth = -1, shownHeight = -1;

    private MultiblockStructureScreen(MultiblockStructure structure) {
        super(structure.title());
        this.structure = structure;
        this.view.open();
    }

    public static void open(MultiblockStructure structure) {
        Minecraft.getInstance().setScreen(new MultiblockStructureScreen(structure));
    }

    @Override
    protected String pagePath() {
        return AuiPages.multiblockPage();
    }

    @Override
    protected String pageName() {
        return "structure";
    }

    /**
     * Runs before the vanilla pass, so the page is extracted under the scene's picture-in-picture state and both
     * are under the hovered cell's tooltip.
     */
    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Nothing is drawn while the page's stylesheet is still in flight: without it there is no box for the
        // scene, and the bars would sit at the document origin for two ticks.
        if (!this.auiReadyToDraw()) return;
        if (this.auiPageWritable()) this.syncPage();
        ApricityGuiLayers.submitUi(graphics);
        this.view.extractScene(graphics, this.structure, mouseX, mouseY, this.width, this.height);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.view.extractTooltip(graphics, this.font, mouseX, mouseY);
        this.extractPageError(graphics, this.font, this.width, this.height);
    }

    private void syncPage() {
        if (this.shownWidth != this.width || this.shownHeight != this.height) this.writeLayout();
        this.writeState();
    }

    // ------------------------------------------------------------------ the page contract

    /**
     * Resolves the page contract; a refresh (hot reload) replaces every element, which is also when the click
     * handlers have to be bound again, so every bind binds them.
     */
    @Override
    public void bindPage() {
        this.topBar = this.getOrThrow("top");
        this.bottomBar = this.getOrThrow("bottom");
        this.scene = this.getOrThrow("scene");
        this.back = this.getOrThrow("back");
        this.timeline = this.getOrThrow("timeline");
        this.timelineFill = this.getOrThrow("timeline_fill");
        this.timelineKnob = this.getOrThrow("timeline_knob");
        this.title = new Label(this.getOrThrow("title"));
        this.layer = new Label(this.getOrThrow("layer"));
        this.hint = new Label(this.getOrThrow("hint"));
        this.backLabel = new Label(this.back);

        this.controlKeys.clear();
        for (String id : List.of("previous", "restart", "toggle", "next"))
            this.controlKeys.add(this.getOrThrow(id));
        // The back key is the only fixed label on the page: the four controls are rewritten because play and
        // pause share one key.
        this.backLabel.show(Component.translatable("screen.mxt.multiblock.back").getString());
        this.controlLabels.clear();
        for (Element key : this.controlKeys) this.controlLabels.add(new Label(key));
        // The keys are the page's; only the two things that need the pointer's position stay here.
        this.click(this.back, this::onClose);
        this.click(this.controlKeys.get(0), () -> this.view.previous(this.structure));
        this.click(this.controlKeys.get(1), this.view::open);
        this.click(this.controlKeys.get(2), () -> this.view.toggle(this.structure));
        this.click(this.controlKeys.get(3), () -> this.view.next(this.structure));
    }

    @Override
    public void onPageBound() {
        this.writeLayout();
        this.writeState();
    }

    @Override
    public void onBindingsCleared() {
        this.topBar = null;
        this.bottomBar = null;
        this.scene = null;
        this.back = null;
        this.timeline = null;
        this.timelineFill = null;
        this.timelineKnob = null;
        this.title = null;
        this.layer = null;
        this.hint = null;
        this.backLabel = null;
        this.controlKeys.clear();
        this.controlLabels.clear();
        this.shownWidth = -1;
        this.shownHeight = -1;
    }

    /**
     * Writes every box of the page: the two bars, the scene, the three text lines, the five keys and the timeline
     * track. Called on a resize and on every rebind, never per frame.
     */
    private void writeLayout() {
        box(this.topBar, this.view.topBar(this.width));
        box(this.bottomBar, this.view.bottomBar(this.width, this.height));
        box(this.scene, this.view.scene(this.width, this.height));
        box(this.back, this.view.back(this.font));
        // A line's own box: the page centres the text inside it with `.t-center`.
        if (this.title != null) box(this.title.element(), this.view.title(this.width));
        if (this.layer != null) box(this.layer.element(), this.view.layer(this.width));
        if (this.hint != null) box(this.hint.element(), this.view.hint(this.width));
        box(this.timeline, this.view.timeline(this.width, this.height));
        List<MultiblockStructureView.StructureControl> controls = this.view.controls(this.font, this.width, this.height);
        for (int index = 0; index < this.controlKeys.size() && index < controls.size(); index++) {
            box(this.controlKeys.get(index), controls.get(index).box());
        }
        this.shownWidth = this.width;
        this.shownHeight = this.height;
    }

    /**
     * The per-frame part: the three lines, the four key labels and how far the timeline has run. Every write
     * compares first, so a frame that changes nothing touches the page not at all.
     */
    private void writeState() {
        if (this.title != null) this.title.show(this.structure.title().getString());
        if (this.hint != null)
            this.hint.show(Component.translatable("screen.mxt.multiblock.hint").getString());
        int maxStep = this.view.maxStep(this.structure);
        int step = this.view.currentStep(this.structure);
        if (this.layer != null) this.layer.show(maxStep > 0
                ? Component.translatable("screen.mxt.multiblock.layer", step, maxStep).getString() : "");
        List<MultiblockStructureView.StructureControl> controls = this.view.controls(this.font, this.width, this.height);
        for (int index = 0; index < this.controlLabels.size() && index < controls.size(); index++) {
            this.controlLabels.get(index).show(controls.get(index).label().getString());
        }
        box(this.timelineFill, this.view.timelineFill(this.width, maxStep));
        box(this.timelineKnob, this.view.timelineKnob(this.width, this.height, maxStep));
    }

    // ------------------------------------------------------------------ input

    // A left click first tries the timeline's hit band, then the scene's orbit; the keys are DOM clicks and never
    // reach this. The right button does nothing here, as before.
    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;
        if (this.view.seekTimeline(event.x(), event.y(), this.width, this.height, this.structure)) return true;
        return this.view.beginDrag(event.x(), event.y(), this.width, this.height);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        return this.view.mouseReleased(event.button());
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double deltaX, double deltaY) {
        return this.view.mouseDragged(deltaX, deltaY);
    }

    // Any scroll over the scene counts as a drag: the scene has no zoom of its own here.
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollY != 0.0D && this.view.scrollOverScene(mouseX, mouseY, this.width, this.height);
    }

    // Arrow keys are the two step keys; the space bar is the play/pause key.
    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_LEFT) {
            this.view.previous(this.structure);
            return true;
        }
        if (key == InputConstants.KEY_RIGHT) {
            this.view.next(this.structure);
            return true;
        }
        if (key == InputConstants.KEY_SPACE) {
            this.view.toggle(this.structure);
            return true;
        }
        return super.keyPressed(event);
    }

    // ------------------------------------------------------------------ the vanilla pass

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    // ------------------------------------------------------------------ page writes

    private static void box(@Nullable Element element, MultiblockStructureView.Rect rect) {
        AuiElements.style(element, "left", rect.x() + "px");
        AuiElements.style(element, "top", rect.y() + "px");
        AuiElements.style(element, "width", rect.width() + "px");
        AuiElements.style(element, "height", rect.height() + "px");
    }
}
