package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.network.payload.AlchemyActionC2SPayload;
import com.iafenvoy.mxt.network.payload.AlchemyActionC2SPayload.Action;
import com.iafenvoy.mxt.screen.AlchemyUiPages;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceMenu;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceMenu.TemperatureAck;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceView;
import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.element.Container;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.render.AABB;
import com.sighs.apricityui.render.Rect;
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
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Hosts one bundled ApricityUI page over the vanilla furnace menu: the page owns the panel art and the
 * cell geometry, while slot items, hover, clicks and tooltips stay vanilla.
 * <p>
 * The page must not set {@code aui-mouse-events=intercept}: that makes ApricityUI cancel the native
 * click, and the menu would never see a slot click again.
 */
public final class AlchemyFurnaceScreen extends AbstractContainerScreen<AlchemyFurnaceMenu> implements AuiLinkedScreen {
    private static final int ITEM_INSET = 1;
    private static final int PANEL_WIDTH = 202;
    private static final int PANEL_HEIGHT_MONITOR = 225;
    private static final int PANEL_HEIGHT_PART = 160;
    /** Off-panel x/y for menu slots that have no page geometry behind them yet. */
    private static final int PARKED_SLOT = -1000;
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
     * ApricityUI's own tooltip mounts an 11px web-scale style of its own; {@code Options.style()} is
     * appended after it inside the same inline block, so these declarations win without depending on the
     * stylesheet cascade (a stylesheet {@code !important} rule is the other way round and did not stick).
     */
    private static final Tooltip.Options TOOLTIP_OPTIONS = new Tooltip.Options(
            null,
            "font-family:initial;font-size:9px;line-height:9px;font-weight:400;min-width:0;"
                    + "padding:4px 6px;border:1px solid #1a1a1a;border-left:2px solid #8b5cf6;"
                    + "box-shadow:2px 2px 0 rgba(139,92,246,0.25);",
            12, 16, 200);

    @Nullable
    private Document document;
    @Nullable
    private Component pageError;
    private List<FormattedCharSequence> errorLines = List.of();
    private int errorWidth = -1;
    private boolean slotsBound;
    private boolean geometryReady;
    private int layoutWait;
    private long boundGeneration = Long.MIN_VALUE;

    @Nullable
    private Element panel, title, temperature, limit, status, progress, target,apply,start,abort;
    private final Map<Integer, Element> cells = new LinkedHashMap<>();
    private final List<Tooltip.Binding> tooltips = new ArrayList<>();

    private int panelLeft, panelTop;
    private int panelWidth = PANEL_WIDTH, panelHeight = PANEL_HEIGHT_PART;

    private boolean draftDirty;
    private boolean writingField;
    @Nullable
    private String fieldText;
    private int observedEpoch;
    private int inflight;
    private double submitted = Double.NaN;
    @Nullable
    private Component rejection;

    private boolean startDisabled,abortDisabled;
    private float shownProgress = Float.NaN;
    private long shownTemperatureBits = Long.MIN_VALUE;
    private long shownLimitBits = Long.MIN_VALUE;
    @Nullable
    private Component shownTitle, shownQuality,shownStatus;

    public AlchemyFurnaceScreen(AlchemyFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, panelHeight(menu.view()));
    }

    private static int panelHeight(AlchemyFurnaceMenu.View view) {
        return view == AlchemyFurnaceMenu.View.MONITOR ? PANEL_HEIGHT_MONITOR : PANEL_HEIGHT_PART;
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
            AlchemyUiPages.seedMissing();
            this.document = Document.create(AlchemyUiPages.path(this.menu.view()));
            if (this.document == null) {
                this.pageError = Component.translatable("screen.mxt.alchemy.template_missing", AlchemyUiPages.name(this.menu.view()));
                return;
            }
        } else {
            this.document.applyViewport(true);
        }
        this.rebind();
        this.refresh();
    }

    /** Resolves the page contract again; a refresh (hot reload or resize) replaces every element. */
    private void rebind() {
        this.clearBindings();
        this.pageError = null;
        Document current = this.document;
        if (current == null) return;
        if (!this.bindDocument(current)) return;
        this.slotsBound = true;
        this.boundGeneration = current.getRefreshGeneration();
    }

    private boolean bindDocument(Document current) {
        Element panel = current.getElementById("panel");
        if (panel == null) return this.fail("panel");
        Element title = current.getElementById("title");
        if (title == null) return this.fail("title");
        if (!(current.getElementById("machine") instanceof Container machine)) return this.fail("machine (container)");
        if (!(current.getElementById("player_inventory") instanceof Container inventory)) return this.fail("player_inventory (container)");
        List<Element> machineCells = slotsOf(current, machine);
        if (machineCells.size() != this.menu.view().machineSlots()) return this.fail("machine (" + machineCells.size() + " slots)");
        List<Element> playerCells = slotsOf(current, inventory);
        if (playerCells.size() != 36) return this.fail("player_inventory (" + playerCells.size() + " slots)");
        this.panel = panel;
        this.title = title;
        if (!this.bindMachine(machineCells)) return false;
        if (!this.bindPlayer(playerCells)) return false;
        if (this.menu.view() == AlchemyFurnaceMenu.View.MONITOR && !this.bindMonitor(current)) return false;
        this.text(title, this.getTitle());
        this.text(current.getElementById("inventory_label"), Component.translatable("container.inventory"));
        return true;
    }

    private boolean bindMachine(List<Element> machineCells) {
        for (Element cell : machineCells) {
            int index = slotIndexOf(cell);
            if (index < 0 || index >= machineCells.size()) return this.fail("machine (slot-index " + index + ")");
            this.cells.put(index, cell);
            this.tooltips.add(Tooltip.bindTranslation(cell, this.machineTooltip(index), TOOLTIP_OPTIONS));
        }
        return true;
    }

    private boolean bindPlayer(List<Element> playerCells) {
        for (Element cell : playerCells) {
            int vanilla = slotIndexOf(cell);
            int index = vanilla < 0 || vanilla > 35 ? -1 : this.menu.menuIndex("inventory_" + vanilla);
            if (index < 0) return this.fail("player_inventory (slot-index " + vanilla + ")");
            this.cells.put(index, cell);
        }
        return true;
    }

    private boolean bindMonitor(Document current) {
        Element temperature = current.getElementById("temperature");
        if (temperature == null) return this.fail("temperature");
        Element limit = current.getElementById("limit");
        if (limit == null) return this.fail("limit");
        Element status = current.getElementById("status");
        if (status == null) return this.fail("status");
        Element progress = current.getElementById("progress_fill");
        if (progress == null) return this.fail("progress_fill");
        if (!(current.getElementById("target") instanceof Input target)) return this.fail("target (input)");
        Element apply = current.getElementById("apply");
        if (apply == null) return this.fail("apply");
        Element start = current.getElementById("start");
        if (start == null) return this.fail("start");
        Element abort = current.getElementById("abort");
        if (abort == null) return this.fail("abort");
        this.temperature = temperature;
        this.limit = limit;
        this.status = status;
        this.progress = progress;
        this.target = target;
        this.apply = apply;
        this.start = start;
        this.abort = abort;
        this.click(apply, this::applyTemperature);
        this.click(start, () -> this.send(Action.START, 0));
        this.click(abort, () -> this.send(Action.ABORT, 0));
        this.tooltips.add(Tooltip.bind(title, () -> this.titleTooltip(), TOOLTIP_OPTIONS));
        this.tooltips.add(Tooltip.bind(temperature, this::heatTooltip, TOOLTIP_OPTIONS));
        this.tooltips.add(Tooltip.bind(limit, this::limitTooltip, TOOLTIP_OPTIONS));
        this.tooltips.add(Tooltip.bind(target, this::fieldTooltip, TOOLTIP_OPTIONS));
        this.tooltips.add(Tooltip.bind(apply, this::fieldTooltip, TOOLTIP_OPTIONS));
        return true;
    }

    private boolean fail(String missing) {
        Document current = this.document;
        long generation = current == null ? Long.MIN_VALUE : current.getRefreshGeneration();
        this.clearBindings();
        // Keep the failed generation so a broken page is reported once instead of every frame.
        this.boundGeneration = generation;
        this.pageError = Component.translatable("screen.mxt.alchemy.template_invalid", AlchemyUiPages.name(this.menu.view()), missing);
        return false;
    }

    private void clearBindings() {
        for (Tooltip.Binding binding : this.tooltips) binding.close();
        this.tooltips.clear();
        this.cells.clear();
        this.slotsBound = false;
        this.geometryReady = false;
        this.layoutWait = 0;
        this.parkSlots();
        this.boundGeneration = Long.MIN_VALUE;
        this.panel = null;
        this.title = null;
        this.temperature = null;
        this.limit = null;
        this.status = null;
        this.progress = null;
        this.target = null;
        this.apply = null;
        this.start = null;
        this.abort = null;
        this.fieldText = null;
        this.draftDirty = false;
        this.writingField = false;
        this.rejection = null;
        this.resetShownState();
    }

    private void resetShownState() {
        this.startDisabled = false;
        this.abortDisabled = false;
        this.shownProgress = Float.NaN;
        this.shownTemperatureBits = Long.MIN_VALUE;
        this.shownLimitBits = Long.MIN_VALUE;
        this.shownTitle = null;
        this.shownQuality = null;
        this.shownStatus = null;
    }

    /** The menu builds every slot at (0, 0); parking them keeps that from drawing under the panel. */
    private void parkSlots() {
        for (Slot slot : this.menu.slots) {
            slot.x = PARKED_SLOT;
            slot.y = PARKED_SLOT;
        }
    }

    private static List<Element> slotsOf(Document document, Container container) {
        List<Element> cells = new ArrayList<>();
        for (Element element : document.getElements()) {
            if (!(element instanceof com.sighs.apricityui.element.Slot slot)) continue;
            if (slot.findAncestor(Container.class) != container) continue;
            cells.add(slot);
        }
        cells.sort((left, right) -> Integer.compare(slotIndexOf(left), slotIndexOf(right)));
        return cells;
    }

    private static int slotIndexOf(Element element) {
        return element instanceof com.sighs.apricityui.element.Slot slot ? slot.getSlotIndex() : -1;
    }

    private String machineTooltip(int index) {
        return switch (this.menu.view()) {
            case MONITOR -> "tooltip.mxt.alchemy.fire";
            case MAIN -> "screen.mxt.alchemy.main";
            case AUXILIARY -> index == 2 ? "screen.mxt.alchemy.catalyst" : "screen.mxt.alchemy.auxiliary";
            case OUTPUT -> "screen.mxt.alchemy.output";
        };
    }

    /** Runs before the vanilla pass so slot geometry is current and the page sits under the items. */
    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.syncPage();
        ApricityGuiLayers.submitUi(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void syncPage() {
        Document current = this.document;
        if (current == null) return;
        if (current.getRefreshGeneration() != this.boundGeneration) {
            this.rebind();
            this.refresh();
        }
        if (!this.slotsBound) return;
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
     * ApricityUI memoises each element's offset and only clears it on a style or layout change, and the
     * geometry this method runs against is committed in the paint pass — which happens <em>after</em> the
     * frame that first shows the page. A read taken before that commit therefore mixes a stale slot
     * offset with the panel's real position, and writing it parks every item in the screen corner. The
     * panel's committed rect is the layout ApricityUI actually paints, so a slot that reads outside it is
     * a read from before that layout: nothing is written, the caller keeps the geometry it validated
     * earlier (or, before the first good read, parks the slots), and the next frame tries again.
     * {@link #GEOMETRY_WARN_FRAMES} only decides when to report that a usable layout never arrived, it
     * never forces a write.
     */
    private boolean syncGeometry(Document current) {
        if (this.panel == null || this.cells.isEmpty()) return false;
        Rect panelRect = this.panel.getRenderer().getCommittedRectIfValid();
        if (panelRect == null) return false;
        AABB painted = panelRect.getVisualBounds();
        if (!painted.isValid()) return false;
        for (Element cell : this.cells.values()) {
            Position at = Position.of(cell);
            if (at.x < painted.x() - SLOT_SLACK || at.x > painted.maxX() + SLOT_SLACK
                    || at.y < painted.y() - SLOT_SLACK || at.y > painted.maxY() + SLOT_SLACK) {
                return false;
            }
        }
        Position origin = current.documentToScreenPosition(Position.of(this.panel));
        Size size = Size.of(this.panel);
        this.panelLeft = (int) Math.round(origin.x);
        this.panelTop = (int) Math.round(origin.y);
        this.panelWidth = Math.max(1, (int) Math.round(size.width() * current.getViewportScaleX()));
        this.panelHeight = Math.max(1, (int) Math.round(size.height() * current.getViewportScaleY()));
        this.leftPos = this.panelLeft;
        this.topPos = this.panelTop;
        for (Map.Entry<Integer, Element> entry : this.cells.entrySet()) {
            int index = entry.getKey();
            if (index < 0 || index >= this.menu.slots.size()) continue;
            Position screen = current.documentToScreenPosition(Position.of(entry.getValue()));
            Slot slot = this.menu.slots.get(index);
            slot.x = (int) Math.round(screen.x) + ITEM_INSET - this.leftPos;
            slot.y = (int) Math.round(screen.y) + ITEM_INSET - this.topPos;
        }
        return true;
    }

    /**
     * Keeps the slots parked until the page yields a usable layout for the first time, and says so once.
     */
    private void waitForGeometry(Document current) {
        this.layoutWait++;
        this.parkSlots();
        if (this.layoutWait != GEOMETRY_WARN_FRAMES) return;
        MiXianTu.LOGGER.warn(
                "[MXT] alchemy page geometry is unusable after {} passes: path={} panelCommitted={} panel={} slots={}",
                this.layoutWait,
                current.getPath(),
                this.panel != null && this.panel.getRenderer().getCommittedRectIfValid() != null,
                this.panel == null ? "none" : Position.of(this.panel).toString(),
                this.cells.size()
        );
    }

    /** Items may only be drawn once the slot geometry behind them has actually been read. */
    private boolean slotsDrawn() {
        return this.slotsBound && this.geometryReady;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.refresh();
    }

    private void refresh() {
        if (!this.slotsBound || this.menu.view() != AlchemyFurnaceMenu.View.MONITOR) return;
        // A rebuilt DOM is stale until syncPage rebinds it.
        Document current = this.document;
        if (current != null && current.getRefreshGeneration() != this.boundGeneration) return;
        this.pollTemperature();
        AlchemyFurnaceView view = this.menu.viewSnapshot();
        AlchemyFurnaceView.Numbers numbers = view.numbers();
        this.showTitle(view.status().furnace(), view.status().quality());
        this.showExact(this.temperature, "screen.mxt.alchemy.temperature", numbers.temperature(), true);
        this.showExact(this.limit, "screen.mxt.alchemy.maximum", numbers.maximum(), false);
        this.showStatus(view.status().message());
        long total = numbers.total();
        float progress = total > 0 && view.status().locked()
                ? (float) (total - numbers.remaining()) / total : 0;
        if (this.progress != null && Float.floatToIntBits(progress) != Float.floatToIntBits(this.shownProgress)) {
            this.progress.setInlineStyleProperty("width", Math.round(progress * 100) + "%");
            this.shownProgress = progress;
        }
        boolean canStart = view.status().canStart();
        if (this.start != null && !canStart != this.startDisabled) {
            this.start.setDisabled(!canStart);
            this.startDisabled = !canStart;
        }
        boolean canAbort = "WARMING".equals(view.status().phase()) || "RUNNING".equals(view.status().phase());
        if (this.abort != null && !canAbort != this.abortDisabled) {
            this.abort.setDisabled(!canAbort);
            this.abortDisabled = !canAbort;
        }
        this.syncDraft(numbers.target());
    }

    private void showTitle(Component furnace, Component quality) {
        if (this.title == null || furnace.equals(this.shownTitle) && quality.equals(this.shownQuality)) return;
        this.text(this.title, furnace);
        this.shownTitle = furnace;
        this.shownQuality = quality;
    }

    private void showStatus(Component message) {
        if (this.status == null || message.equals(this.shownStatus)) return;
        this.text(this.status, message);
        this.shownStatus = message;
    }

    private void showExact(@Nullable Element label, String key, double value, boolean heat) {
        if (label == null) return;
        long bits = Double.doubleToLongBits(value);
        if (bits == (heat ? this.shownTemperatureBits : this.shownLimitBits)) return;
        this.text(label, Component.translatable(key, roundTrip(value)));
        if (heat) this.shownTemperatureBits = bits;
        else this.shownLimitBits = bits;
    }

    private String titleTooltip() {
        AlchemyFurnaceView.Status status = this.menu.viewSnapshot().status();
        return status.furnace().getString() + " / " + status.quality().getString();
    }

    private String heatTooltip() {
        AlchemyFurnaceView.Numbers numbers = this.menu.viewSnapshot().numbers();
        return Component.translatable("screen.mxt.alchemy.temperature_exact",
                roundTrip(numbers.temperature()), roundTrip(numbers.target()), roundTrip(numbers.maximum())).getString();
    }

    private String limitTooltip() {
        AlchemyFurnaceView.Numbers numbers = this.menu.viewSnapshot().numbers();
        return Component.translatable("screen.mxt.alchemy.limit_exact",
                roundTrip(numbers.maximum()), roundTrip(numbers.recipeTarget()), roundTrip(numbers.tolerance())).getString();
    }

    private String fieldTooltip() {
        AlchemyFurnaceView.Numbers numbers = this.menu.viewSnapshot().numbers();
        Component text = this.rejection != null ? this.rejection
                : this.inflight > 0 ? Component.translatable("screen.mxt.alchemy.temperature_pending", roundTrip(this.submitted))
                : Component.translatable("screen.mxt.alchemy.temperature_draft", roundTrip(numbers.target()), roundTrip(numbers.maximum()));
        return text.getString();
    }

    private void syncDraft(double authoritative) {
        if (this.target == null) return;
        String raw = this.target.getValue();
        if (!this.writingField && this.fieldText != null && !raw.equals(this.fieldText)) {
            this.draftDirty = true;
            this.rejection = null;
        }
        if (this.draftDirty || this.inflight > 0 || this.fieldFocused()) return;
        String exact = roundTrip(authoritative);
        if (!exact.equals(raw)) this.writeField(exact);
    }

    private boolean fieldFocused() {
        return this.document != null && this.document.getFocusedElement() == this.target;
    }

    private void pollTemperature() {
        TemperatureAck ack = this.menu.temperatureAck();
        if (ack.epoch() == this.observedEpoch) return;
        int steps = epochSteps(this.observedEpoch, ack.epoch());
        this.observedEpoch = ack.epoch();
        if (this.inflight <= 0) return;
        this.inflight = Math.max(0, this.inflight - steps);
        if (this.inflight > 0) return;
        this.finishTemperature(ack);
    }

    private void finishTemperature(TemperatureAck ack) {
        boolean diverged = this.draftDiverged();
        if (ack.accepted() && !diverged) {
            this.rejection = null;
            this.draftDirty = false;
            this.writeField(roundTrip(ack.target()));
        } else if (!ack.accepted() && !diverged) {
            this.rejection = Component.translatable("screen.mxt.alchemy.temperature_rejected", roundTrip(this.submitted), roundTrip(ack.target()));
        }
        this.submitted = Double.NaN;
    }

    private boolean draftDiverged() {
        if (this.target == null || Double.isNaN(this.submitted)) return true;
        Optional<Double> parsed = parseFinite(this.target.getValue());
        return parsed.isEmpty() || Double.doubleToLongBits(parsed.get()) != Double.doubleToLongBits(this.submitted);
    }

    private void writeField(String text) {
        if (this.target == null) return;
        // Remember what Java wrote even when the value already matches, or the next frame reads it as a user edit.
        this.fieldText = text;
        if (text.equals(this.target.getValue())) return;
        this.writingField = true;
        this.target.setValue(text);
        this.writingField = false;
    }

    private void applyTemperature() {
        if (this.target == null) return;
        String draft = this.target.getValue();
        Optional<Double> parsed = parseFinite(draft);
        if (parsed.isEmpty()) {
            this.rejection = Component.translatable("screen.mxt.alchemy.temperature_invalid", draft);
            return;
        }
        this.submitted = parsed.get();
        this.inflight++;
        this.draftDirty = true;
        this.rejection = null;
        this.send(Action.TEMPERATURE, this.submitted);
    }

    private static Optional<Double> parseFinite(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static String roundTrip(double value) {
        return Double.toString(value);
    }

    private static int epochSteps(int from, int to) {
        return (to - from) & 0xFFFF;
    }

    private void text(@Nullable Element element, Component text) {
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

    private void click(Element element, Runnable action) {
        element.addEventListener("click", event -> action.run());
    }

    private void send(Action action, double temperature) {
        ClientPacketDistributor.sendToServer(new AlchemyActionC2SPayload(this.menu.containerId, action, temperature));
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The vanilla pass owns the in-game dim gradient behind the panel; skipping super drops it.
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
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
    protected void extractSlot(@NonNull GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        if (!this.slotsDrawn()) return;
        super.extractSlot(graphics, slot, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    }

    /** Without read geometry the cells are parked: they must not hover, take clicks or find slots. */
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
        if (!this.slotsBound) return event.isEscape() && super.keyPressed(event);
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
