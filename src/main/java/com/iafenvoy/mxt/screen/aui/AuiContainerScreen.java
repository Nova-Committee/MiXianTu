package com.iafenvoy.mxt.screen.aui;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.screen.ApricityContainerMenu;
import com.sighs.apricityui.screen.ApricityContainerScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Hosts one bundled ApricityUI page over a vanilla menu: the page owns the panel and the cell geometry,
 * while every slot - its position, its item, its hover, its click and its tooltip - is ApricityUI's
 * {@link ApricityContainerScreen} plus its slot binder.
 * <p>
 * The page must not set {@code aui-mouse-events=intercept}: that makes ApricityUI cancel the native
 * click, and the menu would never see a slot click again.
 * <p>
 * What is left here is what ApricityUI does not do for us: the page contract ({@link AuiWrappedScreen}),
 * the vanilla grey plate, tooltips for elements that are neither a slot nor an item, and
 * the panel's live rectangle - the screen's own hit tests and "clicked outside the panel" are written
 * against it, because the AUI host is a whole-window screen and would otherwise never call a click
 * outside.
 */
public abstract class AuiContainerScreen<T extends ApricityContainerMenu> extends ApricityContainerScreen
        implements AuiWrappedScreen {
    /**
     * The page's own menu. {@code AbstractContainerScreen.menu} is typed as ApricityUI's base menu, and this
     * is the same object under the page's own type; a subclass reads {@code this.menu} as before.
     */
    protected final T menu;
    private final AuiWrappedScreen.State state = new AuiWrappedScreen.State();
    private final List<TooltipAnchor> tooltipAnchors = new ArrayList<>();
    /**
     * Set once the panel's rectangle has been read; until then the screen's own hit tests have no frame of
     * reference and the page is treated as not drawn yet.
     */
    private boolean panelRead;

    @Nullable
    protected Element panel;
    protected int panelLeft, panelTop;
    protected int panelWidth, panelHeight;

    protected AuiContainerScreen(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.menu = menu;
    }

    // ------------------------------------------------------------------ page contract

    @Override
    public AuiWrappedScreen.State auiState() {
        return this.state;
    }

    /**
     * Runs once per container tick, after the menu has synced its data slots.
     */
    protected void refresh() {
    }

    /**
     * The page generation the current bindings came from; a different one means the DOM was rebuilt.
     */
    protected long boundGeneration() {
        return this.state.boundGeneration;
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * ApricityUI builds the document from the menu's page path, so that page's stylesheets are prepared here
     * - before {@code super.init()} creates it.
     */
    @Override
    protected void init() {
        this.auiPreparePage(this.menu.getTemplatePath());
        super.init();
        this.auiPageOpened();
    }

    /**
     * Drops the bindings and everything this host cached about them; the state a subclass caches is dropped by
     * {@link #onBindingsCleared()}, which the base call reaches last.
     */
    @Override
    public void auiClearBindings() {
        this.tooltipAnchors.clear();
        this.panel = null;
        this.panelRead = false;
        AuiWrappedScreen.super.auiClearBindings();
    }

    /**
     * Drops the state a subclass caches about the page; every element it remembered is dead after a rebind.
     */
    @Override
    public void onBindingsCleared() {
    }

    // ------------------------------------------------------------------ binding helpers

    /**
     * Resolves {@code count} elements named {@code prefix + 0..count-1}; the first missing id is the fault.
     */
    protected List<Element> byIdPrefix(String prefix, int count) {
        List<Element> found = new ArrayList<>(count);
        for (int index = 0; index < count; index++) found.add(this.getOrThrow(prefix + index));
        return found;
    }

    /**
     * Registers a hover tooltip for one page element. The lines go through the vanilla renderer like every other
     * tooltip in the mod, and unlike a slot's own tooltip they have to be registered by this host: ApricityUI only
     * answers for slots and for item/texture elements.
     */
    protected void tooltip(Element element, Supplier<List<Component>> lines) {
        this.tooltipAnchors.add(new TooltipAnchor(element, lines));
    }

    // ------------------------------------------------------------------ frame

    /**
     * Items, slot geometry, hover and the SLOT tooltips are all ApricityUI's pass; this only keeps the panel
     * rectangle current and adds the page's own tooltips after it.
     */
    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.readPanelRectangle();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.extractPageTooltips(graphics, mouseX, mouseY);
    }

    /**
     * Reads the panel's live rectangle and publishes it as the screen's frame of reference: its origin becomes
     * {@code leftPos/topPos} - the pair ApricityUI's slot binder subtracts and its hover test adds back - and the
     * rest is what this screen's own hit tests are written against.
     * <p>
     * The live rectangle is the one ApricityUI keeps current; the committed (painted) one is only re-committed when
     * that element's own dependencies change, so it can be a layout behind.
     */
    private void readPanelRectangle() {
        Element current = this.panel;
        if (current == null) return;
        Size size = Size.of(current);
        if (size.width() <= 0 || size.height() <= 0) return;
        Position origin = Position.of(current);
        Document document = this.getLinkedDocument();
        if (document == null) return;
        Position screen = document.documentToScreenPosition(origin);
        this.panelLeft = (int) Math.round(screen.x);
        this.panelTop = (int) Math.round(screen.y);
        this.panelWidth = Math.max(1, (int) Math.round(size.width() * document.getViewportScaleX()));
        this.panelHeight = Math.max(1, (int) Math.round(size.height() * document.getViewportScaleY()));
        this.leftPos = this.panelLeft;
        this.topPos = this.panelTop;
        this.panelRead = true;
    }

    /**
     * The registered page tooltips, in the vanilla renderer. ApricityUI has already queued the hovered slot's own
     * tooltip, and a tooltip already queued for this frame wins over a later one, so an item under the pointer
     * keeps its own box (which is why a cell's own hint has to check whether the slot is occupied).
     */
    private void extractPageTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Document current = this.getLinkedDocument();
        if (current == null || !this.panelRead || this.tooltipAnchors.isEmpty()) return;
        Position pointer = current.screenToDocumentPosition(new Position(mouseX, mouseY));
        for (TooltipAnchor anchor : this.tooltipAnchors) {
            if (!inside(anchor.anchor(), pointer)) continue;
            List<Component> lines = anchor.lines().get();
            if (lines == null || lines.isEmpty()) return;
            graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
            return;
        }
    }

    /**
     * Whether a pointer in the page's own coordinates is inside an element's live rectangle. The committed rect is
     * deliberately not used: ApricityUI re-commits one only when that element's own dependencies change.
     */
    private static boolean inside(Element element, Position pointer) {
        Size size = Size.of(element);
        if (size.width() <= 0 || size.height() <= 0) return false;
        Position at = Position.of(element);
        return pointer.x >= at.x && pointer.x < at.x + size.width()
                && pointer.y >= at.y && pointer.y < at.y + size.height();
    }

    /**
     * The screen's own hit tests may only run once the panel rectangle has been read.
     */
    protected boolean slotsDrawn() {
        return this.auiBound() && this.panelRead;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.refresh();
    }

    // ------------------------------------------------------------------ vanilla pass

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The vanilla plate, so the page is submitted over a grey the player already reads as a GUI.
        AuiStyles.extract(this, graphics);
    }

    /**
     * The AUI host is a whole-window screen, so the vanilla test - "the pointer is off the screen" - would never
     * fire and a carried stack could never be dropped by clicking the page's own background. The panel is the
     * container here, exactly as the player sees it.
     */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        if (!this.slotsDrawn()) return true;
        return mouseX < this.panelLeft || mouseY < this.panelTop
                || mouseX >= this.panelLeft + this.panelWidth || mouseY >= this.panelTop + this.panelHeight;
    }

    @Override
    public void removed() {
        this.auiClose();
        super.removed();
    }

    /**
     * One page element that answers tooltip lines while the pointer is inside it.
     */
    private record TooltipAnchor(Element anchor, Supplier<List<Component>> lines) {
    }
}
