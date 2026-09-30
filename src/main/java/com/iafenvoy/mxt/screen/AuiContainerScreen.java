package com.iafenvoy.mxt.screen;

import com.iafenvoy.mxt.MiXianTu;
import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.element.Container;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.screen.AuiLinkedScreen;
import com.sighs.apricityui.ui.Tooltip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * Hosts one bundled ApricityUI page over a vanilla menu: the page owns the panel and the cell geometry,
 * while slot items, hover, clicks and tooltips stay vanilla.
 * <p>
 * The page must not set {@code aui-mouse-events=intercept}: that makes ApricityUI cancel the native
 * click, and the menu would never see a slot click again.
 * <p>
 * Slot geometry is read back from the page every frame instead of being repeated in Java. ApricityUI
 * commits element offsets during painting and memoises them until a style or layout change, so a read
 * taken before that commit mixes a stale cell offset with the panel's real position - writing it would
 * park every item in the screen corner. The panel's committed rect is the layout that was actually
 * painted, so a cell that reads outside it is a read from before that layout: nothing is written, the
 * previous validated geometry is kept, and the next frame tries again.
 */
public abstract class AuiContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T>
        implements AuiLinkedScreen {
    /**
     * An item is 16x16 inside an 18x18 cell.
     */
    protected static final int ITEM_INSET = 1;
    /**
     * Off-panel x/y for menu slots that have no page geometry behind them yet.
     */
    protected static final int PARKED_SLOT = -1000;
    /**
     * Rounding slack, in document pixels, for "this slot lies inside the painted panel".
     */
    private static final double SLOT_SLACK = 2.0D;
    /**
     * Failing sync passes, before the page has ever yielded a usable layout, after which the screen
     * reports once that it cannot read the page.
     */
    private static final int GEOMETRY_WARN_FRAMES = 20;
    /**
     * ApricityUI's own tooltip mounts an 11px web-scale style of its own; {@link AuiStyles#TOOLTIP} is
     * appended after it inside the same inline block, so it wins without depending on the stylesheet cascade.
     */
    protected static final Tooltip.Options TOOLTIP_OPTIONS = AuiStyles.TOOLTIP;

    @Nullable
    private Document document;
    @Nullable
    private Component pageError;
    private List<FormattedCharSequence> errorLines = List.of();
    private int errorWidth = -1;
    private boolean pageBound;
    private boolean geometryReady;
    private boolean badCellReported;
    private int layoutWait;
    private long boundGeneration = Long.MIN_VALUE;

    @Nullable
    protected Element panel;
    /**
     * Menu slot index to the page cell that draws behind it.
     */
    protected final Map<Integer, Element> cells = new LinkedHashMap<>();
    private final List<Tooltip.Binding> tooltips = new ArrayList<>();
    private final AuiPages.StyleHold styleHold = new AuiPages.StyleHold();

    protected int panelLeft, panelTop;
    protected int panelWidth, panelHeight;

    protected AuiContainerScreen(T menu, Inventory inventory, Component title, int width, int height) {
        super(menu, inventory, title, width, height);
        this.panelWidth = width;
        this.panelHeight = height;
    }

    // ------------------------------------------------------------------ page contract

    /**
     * Document path of the page, e.g. {@code AuiPages.page(AuiPages.FORGING, "forging")}.
     */
    protected abstract String pagePath();

    /**
     * Page name as it appears in the fallback error line.
     */
    protected abstract String pageName();

    /**
     * Resolves every id the page contract names and binds the slot cells; answers false after calling
     * {@link #fail} with the missing piece.
     */
    protected abstract boolean bindPage(Document document);

    /**
     * Runs after a successful bind and after every rebind; the place to push the first state.
     */
    protected void onPageBound() {
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
        return this.boundGeneration;
    }

    // ------------------------------------------------------------------ lifecycle

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
            boolean stylesPrepared = AuiPages.warmUpStyles(this.pagePath());
            this.document = Document.create(this.pagePath());
            if (this.document == null) {
                this.pageError = Component.translatable("screen.mxt.page.missing", this.pageName());
                return;
            }
            this.styleHold.restart(stylesPrepared);
            this.rebind();
            return;
        }
        // A window resize re-enters init() with the same DOM, and ApricityUI appends listeners without ever
        // deduping them: rebinding here would stack a second click handler - and a second tooltip - on every
        // element the page has, so one click would act twice. Only the viewport changed, and a document that was
        // really rebuilt is caught by the generation check in syncPage.
        this.document.applyViewport(true);
    }

    /**
     * Resolves the page contract again; a refresh (hot reload) replaces every element, which is when the page's
     * listeners have to be bound again.
     */
    private void rebind() {
        this.clearBindings();
        this.pageError = null;
        Document current = this.document;
        if (current == null) return;
        if (!this.bindPage(current)) return;
        this.pageBound = true;
        this.boundGeneration = current.getRefreshGeneration();
        this.onPageBound();
    }

    /**
     * Reports a contract violation once and keeps the generation so it is not retried every frame.
     */
    protected boolean fail(String missing) {
        Document current = this.document;
        long generation = current == null ? Long.MIN_VALUE : current.getRefreshGeneration();
        this.clearBindings();
        this.boundGeneration = generation;
        this.pageError = Component.translatable("screen.mxt.page.invalid", this.pageName(), missing);
        return false;
    }

    private void clearBindings() {
        for (Tooltip.Binding binding : this.tooltips) binding.close();
        this.tooltips.clear();
        this.cells.clear();
        this.pageBound = false;
        this.geometryReady = false;
        this.badCellReported = false;
        this.layoutWait = 0;
        this.parkSlots();
        this.boundGeneration = Long.MIN_VALUE;
        this.panel = null;
        this.onBindingsCleared();
    }

    /**
     * Drops the state a subclass caches about the page; every element it remembered is dead after a rebind.
     */
    protected void onBindingsCleared() {
    }

    /**
     * The menu builds every slot at (0, 0); parking them keeps that from drawing under the panel.
     */
    private void parkSlots() {
        for (Slot slot : this.menu.slots) {
            slot.x = PARKED_SLOT;
            slot.y = PARKED_SLOT;
        }
    }

    // ------------------------------------------------------------------ binding helpers

    /**
     * Resolves a container by id and answers its own slot children in slot-index order.
     */
    @Nullable
    protected List<Element> cellsOf(Document document, String containerId) {
        Element element = document.getElementById(containerId);
        if (!(element instanceof Container container)) return null;
        List<Element> found = new ArrayList<>();
        for (Element candidate : document.getElements()) {
            if (!(candidate instanceof com.sighs.apricityui.element.Slot slot)) continue;
            if (slot.findAncestor(Container.class) != container) continue;
            found.add(slot);
        }
        found.sort(Comparator.comparingInt(AuiContainerScreen::slotIndexOf));
        return found;
    }

    /**
     * Binds a container whose slot-index is the menu slot index.
     */
    protected boolean bindCells(Document document, String containerId, int menuBase) {
        return this.bindCells(document, containerId, localIndex -> menuBase + localIndex);
    }

    /**
     * Binds a container whose slot-index is a local index, through an explicit local-to-menu mapping;
     * a negative answer fails the bind.
     */
    protected boolean bindCells(Document document, String containerId, IntUnaryOperator menuIndex) {
        List<Element> found = this.cellsOf(document, containerId);
        if (found == null) return this.fail(containerId + " (container)");
        if (found.isEmpty()) return this.fail(containerId + " (no slots)");
        for (Element cell : found) {
            int local = slotIndexOf(cell);
            int index = local < 0 ? -1 : menuIndex.applyAsInt(local);
            if (index < 0 || index >= this.menu.slots.size()) {
                return this.fail(containerId + " (slot-index " + local + ")");
            }
            this.cells.put(index, cell);
        }
        return true;
    }

    /**
     * Binds the player inventory: slot-index is the vanilla inventory index (0..35).
     */
    protected boolean bindInventoryCells(Document document, String containerId) {
        return this.bindCells(document, containerId, this::inventoryMenuIndex);
    }

    /**
     * The menu slot holding one player inventory stack; -1 when the menu has no such slot.
     */
    protected int inventoryMenuIndex(int inventoryIndex) {
        Inventory inventory = this.minecraft.player == null
                ? null : this.minecraft.player.getInventory();
        for (int index = 0; index < this.menu.slots.size(); index++) {
            Slot slot = this.menu.slots.get(index);
            if (slot.container != inventory) continue;
            if (slot.getContainerSlot() == inventoryIndex) return index;
        }
        return -1;
    }

    /**
     * Resolves {@code count} elements named {@code prefix + 0..count-1}; null when one is missing.
     */
    @Nullable
    protected List<Element> byIdPrefix(Document document, String prefix, int count) {
        List<Element> found = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Element element = document.getElementById(prefix + index);
            if (element == null) return null;
            found.add(element);
        }
        return found;
    }

    protected static int slotIndexOf(Element element) {
        return element instanceof com.sighs.apricityui.element.Slot slot ? slot.getSlotIndex() : -1;
    }

    /**
     * Applies text and its colour only when either changed; the page is written to, never read back.
     */
    protected void text(@Nullable Element element, Component text) {
        if (element == null) return;
        String value = text.getString();
        if (!value.equals(element.getTextContent())) element.setTextContent(value);
        TextColor color = text.getStyle().getColor();
        if (color == null) return;
        String hex = String.format(Locale.ROOT, "#%06X", color.getValue());
        if (!hex.equalsIgnoreCase(element.getInlineStylePropertyValue("color"))) {
            element.setInlineStyleProperty("color", hex);
        }
    }

    protected void click(Element element, Runnable action) {
        element.addEventListener("click", event -> action.run());
    }

    protected void tooltip(Element element, Tooltip.Options options, Supplier<String> text) {
        this.tooltips.add(Tooltip.bind(element, text, options));
    }

    protected void tooltipTranslation(Element element, String key, Tooltip.Options options) {
        this.tooltips.add(Tooltip.bindTranslation(element, key, options));
    }

    /**
     * Writes an inline property only when it differs; every write re-runs the style pass.
     */
    protected static void style(@Nullable Element element, String property, String value) {
        if (element == null) return;
        if (value.equals(element.getInlineStylePropertyValue(property))) return;
        element.setInlineStyleProperty(property, value);
    }

    protected static boolean hasClass(Element element, String token) {
        return element.getClassList().contains(token);
    }

    /**
     * {@code toggle} rewrites the whole class attribute on every call, so the state is compared first.
     */
    protected static void setClass(Element element, String token, boolean present) {
        if (hasClass(element, token) == present) return;
        element.getClassList().toggle(token, present);
    }

    // ------------------------------------------------------------------ frame

    /**
     * Runs before the vanilla pass so slot geometry is current and the page sits under the items.
     */
    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Nothing at all is drawn while the page's stylesheet is still in flight: without it the page has no
        // layout, so this frame would put every cell, and every item behind one, in the top-left corner.
        if (this.pageError == null && this.styleHold.held()) return;
        this.syncPage();
        ApricityGuiLayers.submitUi(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void syncPage() {
        Document current = this.document;
        if (current == null) return;
        if (current.getRefreshGeneration() != this.boundGeneration) {
            this.rebind();
            return;
        }
        if (!this.pageBound) return;
        // A failed read keeps the geometry that was already validated instead of parking the slots:
        // ApricityUI invalidates the committed rects of a whole route whenever anything on it is marked
        // for relayout, and its tooltip does that on every mouse move, so parking here blinks the items
        // (and drops hover/clicks) for as long as the pointer keeps moving.
        if (this.syncGeometry(current)) {
            this.geometryReady = true;
            this.layoutWait = 0;
        } else if (!this.geometryReady) {
            this.waitForGeometry(current);
        }
    }

    /**
     * Writes the menu slots from the page and answers whether the page could be read at all.
     * <p>
     * Containment is checked against the panel's <em>live</em> rectangle. The failure this guards against is a
     * cell whose position is a memoised leftover from an earlier layout - such a cell still reads from the
     * document origin while the panel is already centred, so it falls outside the panel. The panel's committed
     * (painted) rect is deliberately not used: ApricityUI only re-commits a rect when that element's dependency
     * changes, so a panel first committed before the flex centring ran would fail this test forever and every
     * slot on that screen would stay parked (observed on the station pages: {@code panelCommitted=true} while
     * the page was unusable).
     */
    private boolean syncGeometry(Document current) {
        if (this.panel == null || this.cells.isEmpty()) return false;
        Position origin = Position.of(this.panel);
        Size size = Size.of(this.panel);
        if (size.width() <= 0 || size.height() <= 0) return false;
        for (Map.Entry<Integer, Element> entry : this.cells.entrySet()) {
            Position at = Position.of(entry.getValue());
            if (at.x < origin.x - SLOT_SLACK || at.x > origin.x + size.width() + SLOT_SLACK
                    || at.y < origin.y - SLOT_SLACK || at.y > origin.y + size.height() + SLOT_SLACK) {
                this.reportBadCell(entry.getKey(), at, origin, size);
                return false;
            }
        }
        Position screen = current.documentToScreenPosition(origin);
        this.panelLeft = (int) Math.round(screen.x);
        this.panelTop = (int) Math.round(screen.y);
        this.panelWidth = Math.max(1, (int) Math.round(size.width() * current.getViewportScaleX()));
        this.panelHeight = Math.max(1, (int) Math.round(size.height() * current.getViewportScaleY()));
        this.leftPos = this.panelLeft;
        this.topPos = this.panelTop;
        for (Map.Entry<Integer, Element> entry : this.cells.entrySet()) {
            int index = entry.getKey();
            if (index < 0 || index >= this.menu.slots.size()) continue;
            Position cellScreen = current.documentToScreenPosition(Position.of(entry.getValue()));
            Slot slot = this.menu.slots.get(index);
            slot.x = (int) Math.round(cellScreen.x) + ITEM_INSET - this.leftPos;
            slot.y = (int) Math.round(cellScreen.y) + ITEM_INSET - this.topPos;
        }
        this.onGeometry();
        return true;
    }

    /**
     * Reports the first cell that sits outside the panel, once per bind: the line names the menu slot and both
     * rectangles, which is what a page whose grid put a cell in the wrong place looks like from here.
     */
    private void reportBadCell(int index, Position at, Position origin, Size size) {
        if (this.badCellReported) return;
        this.badCellReported = true;
        MiXianTu.LOGGER.warn(
                "[MXT] {} page cell for menu slot {} is outside the panel: cell=({},{}) panel=({},{}) size=({},{})",
                this.pageName(), index, Math.round(at.x), Math.round(at.y), Math.round(origin.x),
                Math.round(origin.y), Math.round(size.width()), Math.round(size.height())
        );
    }

    /**
     * Runs once per frame with the panel rectangle and the slot geometry current; the place to lay out
     * vanilla widgets that sit on the page.
     */
    protected void onGeometry() {
    }

    /**
     * Keeps the slots parked until the page yields a usable layout for the first time, and says so once.
     */
    private void waitForGeometry(Document current) {
        this.layoutWait++;
        this.parkSlots();
        if (this.layoutWait != GEOMETRY_WARN_FRAMES) return;
        Position origin = this.panel == null ? null : Position.of(this.panel);
        Size size = this.panel == null ? null : Size.of(this.panel);
        MiXianTu.LOGGER.warn(
                "[MXT] {} page geometry is unusable after {} passes: path={} panelCommitted={} panel={} size={} slots={}",
                this.pageName(),
                this.layoutWait,
                current.getPath(),
                this.panel != null && this.panel.getRenderer().getCommittedRectIfValid() != null,
                origin == null ? "none" : origin.toString(),
                size == null ? "none" : size.toString(),
                this.cells.size()
        );
    }

    /**
     * Items may only be drawn once the slot geometry behind them has actually been read.
     */
    protected boolean slotsDrawn() {
        return this.pageBound && this.geometryReady;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.styleHold.tick();
        this.refresh();
    }

    // ------------------------------------------------------------------ vanilla pass

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Deliberately no super: the panel dims itself and a translucent plate over a dimmed world reads as an
        // almost opaque one, which is not the look these pages were matched to.
        if (this.pageError == null) return;
        if (this.errorWidth != this.width) {
            this.errorLines = this.font.split(this.pageError, Math.max(40, this.width - 40));
            this.errorWidth = this.width;
        }
        int y = this.height / 2 - this.errorLines.size() * 5;
        for (FormattedCharSequence line : this.errorLines) {
            graphics.text(this.font, line, (this.width - this.font.width(line)) / 2, y, 0xFFFF5555, false);
            y += 10;
        }
    }

    @Override
    protected void extractSlot(@NonNull GuiGraphicsExtractor graphics, @NonNull Slot slot, int mouseX, int mouseY) {
        if (!this.slotsDrawn()) return;
        super.extractSlot(graphics, slot, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    /**
     * Without read geometry the cells are parked: they must not hover, take clicks or find slots.
     */
    @Override
    protected boolean isHovering(int left, int top, int width, int height, double mouseX, double mouseY) {
        return this.slotsDrawn() && super.isHovering(left, top, width, height, mouseX, mouseY);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        if (!this.slotsDrawn()) return true;
        return mouseX < this.panelLeft || mouseY < this.panelTop
                || mouseX >= this.panelLeft + this.panelWidth || mouseY >= this.panelTop + this.panelHeight;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!this.slotsDrawn()) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (!this.slotsDrawn()) return true;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double deltaX, double deltaY) {
        if (!this.slotsDrawn()) return true;
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (!this.pageBound) return event.isEscape() && super.keyPressed(event);
        return super.keyPressed(event);
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
}
