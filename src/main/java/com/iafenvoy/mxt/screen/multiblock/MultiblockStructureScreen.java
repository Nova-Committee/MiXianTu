package com.iafenvoy.mxt.screen.multiblock;

import com.iafenvoy.mxt.screen.AuiPages;
import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.AuiLinkedScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
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
public final class MultiblockStructureScreen extends Screen implements AuiLinkedScreen {
    /**
     * The page name in the fallback error line, and what {@code /formation show} resets to if it is missing.
     */
    private static final String PAGE = "structure";

    private final MultiblockStructure structure;
    private final MultiblockStructureView view = new MultiblockStructureView();

    @Nullable
    private Document document;
    @Nullable
    private Component pageError;
    private final AuiPages.StyleHold styleHold = new AuiPages.StyleHold();
    private List<FormattedCharSequence> errorLines = List.of();
    private int errorWidth = -1;
    private boolean pageBound;
    private long boundGeneration = Long.MIN_VALUE;

    @Nullable
    private Element topBar, bottomBar, scene, back, timeline, timelineFill, timelineKnob;
    @Nullable
    private Label title, layer, hint, backLabel;
    private final List<Element> controlKeys = new ArrayList<>(MultiblockStructureView.CONTROL_COUNT);
    private final List<Label> controlLabels = new ArrayList<>(MultiblockStructureView.CONTROL_COUNT);
    /**
     * The window size the page was last written with; a resize is the only thing that changes the boxes.
     */
    private int shownWidth = -1;
    private int shownHeight = -1;

    private MultiblockStructureScreen(MultiblockStructure structure) {
        super(structure.title());
        this.structure = structure;
        this.view.open();
    }

    public static void open(MultiblockStructure structure) {
        Minecraft.getInstance().setScreen(new MultiblockStructureScreen(structure));
    }

    @Override
    @Nullable
    public Document getLinkedDocument() {
        return this.document;
    }

    @Override
    protected void init() {
        super.init();
        if (this.document == null) {
            AuiPages.seedAll();
            boolean stylesPrepared = AuiPages.warmUpStyles(AuiPages.multiblockPage());
            this.document = Document.create(AuiPages.multiblockPage());
            if (this.document == null) {
                this.pageError = Component.translatable("screen.mxt.page.missing", PAGE);
                return;
            }
            this.styleHold.restart(stylesPrepared);
            this.rebind(true);
            return;
        }
        // A resize re-enters init() with the same DOM, so the five keys keep the handlers they already have:
        // binding them again would stack a second click on every one of them. ApricityUI does not rebuild the
        // document for a viewport change, and the boxes are rewritten by syncPage below.
        this.document.applyViewport(true);
        this.shownWidth = -1;
        this.shownHeight = -1;
        this.syncPage();
    }

    @Override
    public void tick() {
        super.tick();
        this.styleHold.tick();
    }

    /**
     * Runs before the vanilla pass, so the page is extracted under the scene's picture-in-picture state and both
     * are under the hovered cell's tooltip.
     */
    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Nothing is drawn while the page's stylesheet is still in flight: without it there is no box for the
        // scene, and the bars would sit at the document origin for two ticks.
        if (this.pageError == null && this.styleHold.held()) return;
        this.syncPage();
        ApricityGuiLayers.submitUi(graphics);
        this.view.extractScene(graphics, this.structure, mouseX, mouseY, this.width, this.height);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.view.extractTooltip(graphics, this.font, mouseX, mouseY);
        if (this.pageError != null) this.extractPageError(graphics);
    }

    private void syncPage() {
        Document current = this.document;
        if (current == null) return;
        if (current.getRefreshGeneration() != this.boundGeneration) {
            this.rebind(true);
            return;
        }
        if (!this.pageBound) return;
        if (this.shownWidth != this.width || this.shownHeight != this.height) this.writeLayout();
        this.writeState();
    }

    // ------------------------------------------------------------------ the page contract

    /**
     * Resolves the page contract again; a refresh (hot reload) replaces every element, which is also when the
     * click handlers have to be bound again.
     */
    private void rebind(boolean bindHandlers) {
        this.clearBindings();
        this.pageError = null;
        Document current = this.document;
        if (current == null) return;
        if (!this.bindPage(current, bindHandlers)) return;
        this.pageBound = true;
        this.boundGeneration = current.getRefreshGeneration();
        this.writeLayout();
        this.writeState();
    }

    private boolean bindPage(Document document, boolean bindHandlers) {
        Element topBar = document.getElementById("top");
        if (topBar == null) return this.fail("top");
        Element bottomBar = document.getElementById("bottom");
        if (bottomBar == null) return this.fail("bottom");
        Element scene = document.getElementById("scene");
        if (scene == null) return this.fail("scene");
        Element title = document.getElementById("title");
        if (title == null) return this.fail("title");
        Element layer = document.getElementById("layer");
        if (layer == null) return this.fail("layer");
        Element hint = document.getElementById("hint");
        if (hint == null) return this.fail("hint");
        Element back = document.getElementById("back");
        if (back == null) return this.fail("back");
        Element timeline = document.getElementById("timeline");
        if (timeline == null) return this.fail("timeline");
        Element timelineFill = document.getElementById("timeline_fill");
        if (timelineFill == null) return this.fail("timeline_fill");
        Element timelineKnob = document.getElementById("timeline_knob");
        if (timelineKnob == null) return this.fail("timeline_knob");
        this.controlKeys.clear();
        for (String id : List.of("previous", "restart", "toggle", "next")) {
            Element key = document.getElementById(id);
            if (key == null) return this.fail(id);
            this.controlKeys.add(key);
        }
        this.topBar = topBar;
        this.bottomBar = bottomBar;
        this.scene = scene;
        this.back = back;
        this.timeline = timeline;
        this.timelineFill = timelineFill;
        this.timelineKnob = timelineKnob;
        this.title = new Label(title);
        this.layer = new Label(layer);
        this.hint = new Label(hint);
        // The back key is the only fixed label on the page: the four controls are rewritten because play and
        // pause share one key.
        this.backLabel = new Label(back);
        this.backLabel.show(Component.translatable("screen.mxt.multiblock.back").getString());
        this.controlLabels.clear();
        for (Element key : this.controlKeys) this.controlLabels.add(new Label(key));
        // The keys are the page's; only the two things that need the pointer's position stay here.
        if (bindHandlers) {
            this.click(back, this::onClose);
            this.click(this.controlKeys.get(0), () -> this.view.previous(this.structure));
            this.click(this.controlKeys.get(1), this.view::open);
            this.click(this.controlKeys.get(2), () -> this.view.toggle(this.structure));
            this.click(this.controlKeys.get(3), () -> this.view.next(this.structure));
        }
        return true;
    }

    private boolean fail(String missing) {
        Document current = this.document;
        long generation = current == null ? Long.MIN_VALUE : current.getRefreshGeneration();
        this.clearBindings();
        // Keep the failed generation so a broken page is reported once instead of every frame.
        this.boundGeneration = generation;
        this.pageError = Component.translatable("screen.mxt.page.invalid", PAGE, missing);
        return false;
    }

    private void clearBindings() {
        this.pageBound = false;
        this.boundGeneration = Long.MIN_VALUE;
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
        if (this.title != null) this.title.box(this.view.title(this.width));
        if (this.layer != null) this.layer.box(this.view.layer(this.width));
        if (this.hint != null) this.hint.box(this.view.hint(this.width));
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

    // No background of its own: the view fills the frame it was given, and the default layer would blur behind it.
    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void removed() {
        this.clearBindings();
        if (this.document != null) {
            this.document.remove();
            this.document = null;
        }
        super.removed();
    }

    private void extractPageError(GuiGraphicsExtractor graphics) {
        Component error = this.pageError;
        if (error == null) return;
        if (this.errorWidth != this.width) {
            this.errorLines = this.font.split(error, Math.max(40, this.width - 40));
            this.errorWidth = this.width;
        }
        int y = this.height / 2 - this.errorLines.size() * 5;
        for (FormattedCharSequence line : this.errorLines) {
            graphics.text(this.font, line, (this.width - this.font.width(line)) / 2, y, 0xFFFF5555, false);
            y += 10;
        }
    }

    // ------------------------------------------------------------------ page writes

    private void click(Element element, Runnable action) {
        element.addEventListener("click", event -> action.run());
    }

    private static void box(@Nullable Element element, MultiblockStructureView.Rect rect) {
        style(element, "left", rect.x() + "px");
        style(element, "top", rect.y() + "px");
        style(element, "width", rect.width() + "px");
        style(element, "height", rect.height() + "px");
    }

    /** Writes an inline property only when it differs; every write re-runs the page's style pass. */
    private static void style(@Nullable Element element, String property, String value) {
        if (element == null) return;
        if (value.equals(element.getInlineStylePropertyValue(property))) return;
        element.setInlineStyleProperty(property, value);
    }

    /**
     * Writes a text node only when it changed, for the same reason.
     */
    private static final class Label {
        private final Element element;
        @Nullable
        private String shown;

        private Label(Element element) {
            this.element = element;
        }

        private void show(String value) {
            if (value.equals(this.shown)) return;
            this.shown = value;
            this.element.setTextContent(value);
        }

        /** The line's own box: the page centres the text inside it with {@code .t-center}. */
        private void box(MultiblockStructureView.Rect rect) {
            MultiblockStructureScreen.box(this.element, rect);
        }
    }
}
