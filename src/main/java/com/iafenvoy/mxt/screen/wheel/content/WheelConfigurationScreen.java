package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.registry.MxtKeyMappings;
import com.iafenvoy.mxt.render.IconRenderer;
import com.iafenvoy.mxt.runtime.wheel.WheelEntryKinds;
import com.iafenvoy.mxt.runtime.wheel.WheelLayout;
import com.iafenvoy.mxt.runtime.wheel.WheelSlot;
import com.iafenvoy.mxt.screen.AuiPages;
import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.AuiLinkedScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The wheel editor: two scrollable pools - auras on the left, everything else on the right, sharing one row so
 * any kind fits any sector - over the twelve sectors of the first page. It edits a draft and sends the whole
 * layout on close, {@code Escape} included, so the two sides cannot disagree about a sector.
 * <p>
 * The page draws; this class keeps the geometry, the hit testing and the draft. The panel shrinks with the
 * window, so the panel, the two pool origins, the sector row, the scrollbars and the divider are written into
 * the page as inline styles every frame - each write compares first, so a still frame writes nothing.
 */
public final class WheelConfigurationScreen extends Screen implements AuiLinkedScreen {
    private static final int SLOT_SIZE = 22;
    private static final int SLOT_GAP = 2;
    private static final int GRID_STEP = SLOT_SIZE + SLOT_GAP;
    private static final int POOL_COLUMNS = 6;
    private static final int POOL_WIDTH = POOL_COLUMNS * GRID_STEP - SLOT_GAP;
    private static final int POOL_GAP = 16;
    private static final int CONTENT_WIDTH = POOL_WIDTH * 2 + POOL_GAP;
    private static final int SLOT_ROW_WIDTH = WheelLayout.SLOTS * GRID_STEP - SLOT_GAP;
    private static final int PANEL_MARGIN = 12;
    private static final int HEADER_HEIGHT = 30;
    private static final int POOL_HEADING_HEIGHT = 12;
    // Divider, numbers, cells and the key line under them: the band stops just below the keys, so nothing is
    // left empty under the row and the pools get the rest of the panel. The divider is what anchors the band,
    // so the three offsets below it are the only way to nudge the row without moving the line.
    private static final int SLOT_ROW_HEIGHT = 56, SLOT_NUMBER_OFFSET = 6, SLOT_TOP_OFFSET = 17, SLOT_KEY_OFFSET = 42;
    private static final int PANEL_HEIGHT = 268;
    /**
     * The rows a full-height band can show: the page's cell grid is fixed, the visible window slides over it.
     */
    private static final int POOL_ROWS = 7;
    private static final int POOL_CELLS = POOL_COLUMNS * POOL_ROWS;
    /**
     * The name drawn in a cell is cut to the cell width minus this inset, as {@link IconRenderer} does it.
     */
    private static final int NAME_INSET = 2;
    private static final int AURA_POOL = 0, ABILITY_POOL = 1;

    private final List<WheelMenuEntry> auras, options;
    private final List<Cell> poolCells = new ArrayList<>(POOL_CELLS * 2);
    private final List<Cell> sectorCells = new ArrayList<>(WheelLayout.SLOTS);
    private final double[] scroll = new double[2];
    private final int[] poolLeft = new int[2];
    private WheelLayout draft;
    private int selectedPool = -1, selectedOption = -1;
    private int panelLeft, panelTop, panelWidth, panelHeight;
    private int poolsTop, poolsBottom;
    private int slotRowLeft, slotRowTop;

    @Nullable
    private Document document;
    @Nullable
    private Component pageError;
    private final AuiPages.StyleHold styleHold = new AuiPages.StyleHold();
    private List<FormattedCharSequence> errorLines = List.of();
    private int errorWidth = -1;
    private long boundGeneration = Long.MIN_VALUE;
    @Nullable
    private Element panel, title, headingAura, headingOption, emptyAura, emptyOption, divider;
    @Nullable
    private Element poolAura, poolOption, sectorRow, trackAura, trackOption, thumbAura, thumbOption;

    private WheelConfigurationScreen(Player player) {
        super(Component.translatable("screen.mxt.wheel_configuration"));
        this.auras = WheelContent.auras(player);
        this.options = WheelContent.options(player);
        this.draft = WheelContent.layoutFor(player);
    }

    public static boolean open() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        Minecraft.getInstance().setScreen(new WheelConfigurationScreen(player));
        return true;
    }

    // ------------------------------------------------------------------ page contract

    @Override
    @Nullable
    public Document getLinkedDocument() {
        return this.document;
    }

    @Override
    protected void init() {
        super.init();
        if (this.document == null) {
            AuiPages.seed(AuiPages.WHEEL, AuiPages.WHEEL_FILES);
            boolean stylesPrepared = AuiPages.warmUpStyles(AuiPages.wheelConfigPage());
            this.document = Document.create(AuiPages.wheelConfigPage());
            if (this.document == null) {
                this.pageError = Component.translatable("screen.mxt.page.missing", "wheel_config");
                return;
            }
            this.styleHold.restart(stylesPrepared);
        } else {
            this.document.applyViewport(true);
        }
        this.layout();
        this.rebind();
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        this.layout();
    }

    @Override
    public void tick() {
        super.tick();
        this.styleHold.tick();
    }

    /**
     * The panel and everything the page is told: the panel shrinks with the window, so the pools are centred
     * inside it rather than pinned to a fixed width.
     */
    private void layout() {
        this.panelWidth = Math.max(1, Math.min(this.width - PANEL_MARGIN * 2, CONTENT_WIDTH + 18));
        this.panelHeight = Math.max(1, Math.min(this.height - PANEL_MARGIN * 2, PANEL_HEIGHT));
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = (this.height - this.panelHeight) / 2;
        // Pools are centred as one block, so a narrow panel squeezes both rather than clipping the right one.
        int contentLeft = this.panelLeft + Math.max(6, (this.panelWidth - CONTENT_WIDTH) / 2);
        this.poolLeft[AURA_POOL] = contentLeft;
        this.poolLeft[ABILITY_POOL] = contentLeft + POOL_WIDTH + POOL_GAP;
        this.poolsTop = this.panelTop + HEADER_HEIGHT + POOL_HEADING_HEIGHT;
        this.poolsBottom = this.panelTop + this.panelHeight - SLOT_ROW_HEIGHT;
        this.slotRowLeft = this.panelLeft + Math.max(6, (this.panelWidth - SLOT_ROW_WIDTH) / 2);
        this.slotRowTop = this.poolsBottom + SLOT_TOP_OFFSET;
        this.clampScroll();
    }

    /**
     * No vanilla dim gradient: as on every other page, the panel is the only thing that darkens the world.
     */
    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    }

    /**
     * Runs before the vanilla pass so the page sits under the tooltips this class draws itself.
     */
    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Nothing at all is drawn while the page's stylesheet is still in flight: this screen's panel and pool
        // geometry are written into the page, but without the stylesheet there is no box to lay them out in.
        if (this.pageError == null && this.styleHold.held()) return;
        this.syncPage();
        ApricityGuiLayers.submitUi(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.extractTooltip(graphics, mouseX, mouseY, Minecraft.getInstance().player);
        this.extractPageError(graphics);
    }

    private void syncPage() {
        Document current = this.document;
        if (current == null) return;
        if (current.getRefreshGeneration() != this.boundGeneration) {
            this.rebind();
            return;
        }
        if (this.panel == null) return;
        this.refreshPage();
    }

    private void rebind() {
        Document current = this.document;
        this.clearBindings();
        this.pageError = null;
        if (current == null) return;
        if (!this.bindPage(current)) return;
        this.boundGeneration = current.getRefreshGeneration();
        this.refreshPage();
    }

    private void clearBindings() {
        this.poolCells.clear();
        this.sectorCells.clear();
        this.panel = null;
        this.title = null;
        this.headingAura = null;
        this.headingOption = null;
        this.emptyAura = null;
        this.emptyOption = null;
        this.divider = null;
        this.poolAura = null;
        this.poolOption = null;
        this.sectorRow = null;
        this.trackAura = null;
        this.trackOption = null;
        this.thumbAura = null;
        this.thumbOption = null;
        this.boundGeneration = Long.MIN_VALUE;
    }

    private boolean bindPage(Document document) {
        Element panel = document.getElementById("panel");
        if (panel == null) return this.fail("panel");
        Element title = document.getElementById("title");
        if (title == null) return this.fail("title");
        Element headingAura = document.getElementById("heading_aura");
        if (headingAura == null) return this.fail("heading_aura");
        Element headingOption = document.getElementById("heading_option");
        if (headingOption == null) return this.fail("heading_option");
        Element emptyAura = document.getElementById("empty_aura");
        if (emptyAura == null) return this.fail("empty_aura");
        Element emptyOption = document.getElementById("empty_option");
        if (emptyOption == null) return this.fail("empty_option");
        Element divider = document.getElementById("divider");
        if (divider == null) return this.fail("divider");
        Element poolAura = document.getElementById("pool_aura");
        if (poolAura == null) return this.fail("pool_aura");
        Element poolOption = document.getElementById("pool_option");
        if (poolOption == null) return this.fail("pool_option");
        Element sectorRow = document.getElementById("sector_row");
        if (sectorRow == null) return this.fail("sector_row");
        Element trackAura = document.getElementById("track_aura");
        if (trackAura == null) return this.fail("track_aura");
        Element trackOption = document.getElementById("track_option");
        if (trackOption == null) return this.fail("track_option");
        Element thumbAura = document.getElementById("thumb_aura");
        if (thumbAura == null) return this.fail("thumb_aura");
        Element thumbOption = document.getElementById("thumb_option");
        if (thumbOption == null) return this.fail("thumb_option");
        this.panel = panel;
        this.title = title;
        this.headingAura = headingAura;
        this.headingOption = headingOption;
        this.emptyAura = emptyAura;
        this.emptyOption = emptyOption;
        this.divider = divider;
        this.poolAura = poolAura;
        this.poolOption = poolOption;
        this.sectorRow = sectorRow;
        this.trackAura = trackAura;
        this.trackOption = trackOption;
        this.thumbAura = thumbAura;
        this.thumbOption = thumbOption;
        for (int pool = 0; pool < 2; pool++) {
            String prefix = pool == AURA_POOL ? "aura" : "option";
            for (int index = 0; index < POOL_CELLS; index++) {
                Cell cell = cell(document, this.font, prefix, index);
                if (cell == null) return this.fail(prefix + "-" + index);
                this.poolCells.add(cell);
            }
        }
        for (int sector = 0; sector < WheelLayout.SLOTS; sector++) {
            Cell cell = cell(document, this.font, "sector", sector);
            if (cell == null) return this.fail("sector-" + sector);
            this.sectorCells.add(cell);
        }
        text(title, this.getTitle().getString());
        text(headingAura, Component.translatable("wheel.mxt.pool.aura").getString());
        text(headingOption, Component.translatable("wheel.mxt.pool.ability").getString());
        text(emptyAura, Component.translatable("wheel.mxt.pool.empty").getString());
        text(emptyOption, Component.translatable("wheel.mxt.pool.empty").getString());
        return true;
    }

    /**
     * One cell of the page: the root plus the four (five, for a sector) elements it writes into.
     */
    @Nullable
    private static Cell cell(Document document, Font font, String prefix, int index) {
        Element root = document.getElementById(prefix + "-" + index);
        Element icon = document.getElementById(prefix + "_item-" + index);
        Element texture = document.getElementById(prefix + "_tex-" + index);
        Element name = document.getElementById(prefix + "_name-" + index);
        Element accent = document.getElementById(prefix + "_accent-" + index);
        if (root == null || !(icon instanceof Item item) || texture == null || name == null || accent == null)
            return null;
        return new Cell(root, item, texture, name, accent, font,
                document.getElementById(prefix + "_stale-" + index),
                document.getElementById(prefix + "_number-" + index),
                document.getElementById(prefix + "_key-" + index));
    }

    private boolean fail(String missing) {
        Document current = this.document;
        this.pageError = Component.translatable("screen.mxt.page.invalid", "wheel_config", missing);
        this.boundGeneration = current == null ? Long.MIN_VALUE : current.getRefreshGeneration();
        return false;
    }

    // ------------------------------------------------------------------ frame

    private void refreshPage() {
        Element panel = this.panel;
        if (panel == null) return;
        put(panel, "left", this.panelLeft + "px");
        put(panel, "top", this.panelTop + "px");
        put(panel, "width", this.panelWidth + "px");
        put(panel, "height", this.panelHeight + "px");
        put(this.title, "left", "10px");
        put(this.title, "top", "10px");

        int band = Math.max(1, this.poolsBottom - this.poolsTop);
        for (int pool = 0; pool < 2; pool++) {
            int left = this.poolLeft[pool] - this.panelLeft;
            int top = this.poolsTop - this.panelTop;
            Element heading = pool == AURA_POOL ? this.headingAura : this.headingOption;
            Element empty = pool == AURA_POOL ? this.emptyAura : this.emptyOption;
            Element cells = pool == AURA_POOL ? this.poolAura : this.poolOption;
            Element track = pool == AURA_POOL ? this.trackAura : this.trackOption;
            put(heading, "left", left + "px");
            put(heading, "top", HEADER_HEIGHT + "px");
            put(empty, "left", left + "px");
            put(empty, "top", top + "px");
            setClass(empty, "hidden", !this.pool(pool).isEmpty());
            // Scrolling moves the whole grid rather than each cell: one write, and the cells keep their own
            // static offsets inside the pool. The offset is rounded once and used by the window below too.
            int offset = (int) Math.round(this.scroll[pool]);
            put(cells, "left", left + "px");
            put(cells, "top", (top - offset) + "px");
            put(track, "left", (left + POOL_WIDTH + 2) + "px");
            put(track, "top", top + "px");
            put(track, "height", band + "px");
            this.showPool(pool, offset, band);
            this.showScrollBar(pool, band);
        }

        put(this.divider, "left", "6px");
        put(this.divider, "top", (this.poolsBottom - this.panelTop + 1) + "px");
        put(this.divider, "width", Math.max(0, this.panelWidth - 12) + "px");

        put(this.sectorRow, "left", (this.slotRowLeft - this.panelLeft) + "px");
        put(this.sectorRow, "top", (this.slotRowTop - this.panelTop) + "px");
        this.showSectors();
    }

    /**
     * The window over one pool's fixed cell grid: a row is drawn when it is inside the band, and the last row
     * is drawn to its end so a pool reads as one grid instead of scattered frames.
     */
    private void showPool(int pool, int offset, int band) {
        List<WheelMenuEntry> options = this.pool(pool);
        int rows = rows(options.size());
        int firstRow = Math.max(0, offset / GRID_STEP);
        int lastRow = Math.min(rows, (int) Math.ceil((double) (offset + band) / GRID_STEP) + 1);
        for (int cell = 0; cell < POOL_CELLS; cell++) {
            int row = cell / POOL_COLUMNS;
            int index = row * POOL_COLUMNS + cell % POOL_COLUMNS;
            int y = row * GRID_STEP - offset;
            boolean visible = row >= firstRow && row < lastRow && y >= 0 && y + SLOT_SIZE <= band;
            WheelMenuEntry entry = visible && index < options.size() ? options.get(index) : null;
            boolean selected = visible && pool == this.selectedPool && index == this.selectedOption;
            this.poolCells.get(pool * POOL_CELLS + cell).show(entry, !visible, selected, false);
        }
    }

    private void showSectors() {
        for (int sector = 0; sector < WheelLayout.SLOTS; sector++) {
            WheelSlot slot = this.draft.slot(sector);
            WheelMenuEntry entry = this.entryOf(slot);
            Cell cell = this.sectorCells.get(sector);
            // A sector whose id no longer resolves keeps its place but is marked, so it can still be cleared.
            cell.show(entry, false, false, entry == null && !slot.isEmpty());
            text(cell.number, Integer.toString(sector + 1));
            Component key = this.slotKey(sector);
            text(cell.key, key == null ? "" : key.getString());
        }
    }

    private void showScrollBar(int pool, int band) {
        Element track = pool == AURA_POOL ? this.trackAura : this.trackOption;
        Element thumb = pool == AURA_POOL ? this.thumbAura : this.thumbOption;
        int rows = rows(this.pool(pool).size());
        int maxScroll = Math.max(0, rows * GRID_STEP - band);
        setClass(track, "hidden", maxScroll == 0);
        if (maxScroll == 0) return;
        int thumbHeight = Math.max(12, (int) ((double) band * band / (rows * GRID_STEP)));
        int thumbY = (int) ((band - thumbHeight) * (this.scroll[pool] / maxScroll));
        put(thumb, "top", thumbY + "px");
        put(thumb, "height", thumbHeight + "px");
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

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        int[] option = this.optionAt(event.x(), event.y());
        if (option != null) {
            boolean same = this.selectedPool == option[0] && this.selectedOption == option[1];
            this.selectedPool = same ? -1 : option[0];
            this.selectedOption = same ? -1 : option[1];
            return true;
        }
        int sector = this.slotAt(event.x(), event.y());
        if (sector >= 0) {
            WheelMenuEntry selected = this.selectedEntry();
            this.draft = this.draft.with(sector, selected == null
                    ? WheelSlot.EMPTY : WheelSlot.of(selected.kind(), selected.id()));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY < this.poolsTop || mouseY >= this.poolsBottom)
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int pool = mouseX >= this.poolLeft[ABILITY_POOL] ? ABILITY_POOL : AURA_POOL;
        if (pool == AURA_POOL && mouseX >= this.poolLeft[AURA_POOL] + POOL_WIDTH)
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        this.scroll[pool] -= scrollY * GRID_STEP * 2.0D;
        this.clampScroll();
        return true;
    }

    @Override
    public void onClose() {
        WheelContent.save(this.draft);
        super.onClose();
    }

    // The page outlives the screen unless it is unregistered here: the renderer draws every document, so a
    // leaked one keeps drawing the panel over the game and each reopen stacks another copy on top.
    @Override
    public void removed() {
        this.clearBindings();
        if (this.document != null) {
            this.document.remove();
            this.document = null;
        }
        super.removed();
    }

    // ------------------------------------------------------------------ entries and geometry

    private List<WheelMenuEntry> pool(int pool) {
        return pool == ABILITY_POOL ? this.options : this.auras;
    }

    private @Nullable WheelMenuEntry selectedEntry() {
        if (this.selectedPool < 0 || this.selectedOption < 0) return null;
        List<WheelMenuEntry> options = this.pool(this.selectedPool);
        return this.selectedOption < options.size() ? options.get(this.selectedOption) : null;
    }

    // The key bound to one sector, drawn under its cell as [Z]; an unbound slot shows nothing, since vanilla's
    // "unknown" placeholder would read as a binding that is not there. A name longer than the 24px step is cut.
    private @Nullable Component slotKey(int sector) {
        KeyMapping key = MxtKeyMappings.WHEEL_SLOTS.get(sector).get();
        if (key.isUnbound()) return null;
        String label = "[" + key.getTranslatedKeyMessage().getString() + "]";
        return Component.literal(IconRenderer.fit(this.font, label, GRID_STEP - 2));
    }

    // Resolved the same way the wheel does, so a cell shows what that sector will actually draw.
    private @Nullable WheelMenuEntry entryOf(WheelSlot slot) {
        if (slot.isEmpty()) return null;
        for (WheelMenuEntry entry : this.pool(slot.kind() == WheelEntryKinds.AURA ? AURA_POOL : ABILITY_POOL))
            if (entry.id().equals(slot.id())) return entry;
        return null;
    }

    private int @Nullable [] optionAt(double mouseX, double mouseY) {
        if (mouseY < this.poolsTop || mouseY >= this.poolsBottom) return null;
        for (int pool = 0; pool < 2; pool++) {
            int col = (int) ((mouseX - this.poolLeft[pool]) / GRID_STEP);
            int row = (int) ((mouseY - this.poolsTop + this.scroll[pool]) / GRID_STEP);
            if (col < 0 || col >= POOL_COLUMNS || row < 0) continue;
            int x = this.poolLeft[pool] + col * GRID_STEP;
            int y = this.poolsTop + row * GRID_STEP - (int) this.scroll[pool];
            if (y < this.poolsTop || y + SLOT_SIZE > this.poolsBottom) continue;
            if (!inside(mouseX, mouseY, x, y)) continue;
            int index = row * POOL_COLUMNS + col;
            if (index < this.pool(pool).size()) return new int[]{pool, index};
        }
        return null;
    }

    private int slotAt(double mouseX, double mouseY) {
        int sector = (int) ((mouseX - this.slotRowLeft) / GRID_STEP);
        if (sector < 0 || sector >= WheelLayout.SLOTS) return -1;
        return inside(mouseX, mouseY, this.slotRowLeft + sector * GRID_STEP, this.slotRowTop) ? sector : -1;
    }

    private void clampScroll() {
        int contentHeight = Math.max(0, this.poolsBottom - this.poolsTop);
        for (int pool = 0; pool < 2; pool++) {
            double max = Math.max(0, rows(this.pool(pool).size()) * GRID_STEP - contentHeight);
            this.scroll[pool] = Math.max(0, Math.min(max, this.scroll[pool]));
        }
    }

    private static int rows(int size) {
        return size / POOL_COLUMNS + (size % POOL_COLUMNS == 0 ? 0 : 1);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
    }

    // ------------------------------------------------------------------ tooltips

    // The entry tooltips are vanilla's own (multi-line, the way the wheel itself shows them): the pointer is
    // tested against the same rectangles the page is given, so there is no per-cell tooltip binding to keep.
    private void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, @Nullable Player player) {
        WheelMenuEntry entry;
        int[] option = this.optionAt(mouseX, mouseY);
        if (option != null) {
            entry = this.pool(option[0]).get(option[1]);
        } else {
            int sector = this.slotAt(mouseX, mouseY);
            if (sector < 0) return;
            WheelSlot slot = this.draft.slot(sector);
            entry = this.entryOf(slot);
            if (entry == null) {
                if (slot.isEmpty()) return;
                graphics.setComponentTooltipForNextFrame(this.font,
                        List.of(Component.translatable("wheel.mxt.tooltip.stale", slot.id().toString())), mouseX, mouseY);
                return;
            }
        }
        if (entry == null) return;
        List<Component> lines = entry.tooltip(player);
        if (!lines.isEmpty()) graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    // ------------------------------------------------------------------ page writing

    private static void put(@Nullable Element element, String property, String value) {
        if (element == null) return;
        if (value.equals(element.getInlineStylePropertyValue(property))) return;
        element.setInlineStyleProperty(property, value);
    }

    private static void text(@Nullable Element element, String value) {
        if (element == null) return;
        if (value.equals(element.getTextContent())) return;
        element.setTextContent(value);
    }

    private static void setClass(Element element, String token, boolean present) {
        if (element.getClassList().contains(token) == present) return;
        element.getClassList().toggle(token, present);
    }

    private static void flag(@Nullable Element element, String token, boolean present) {
        if (element == null) return;
        setClass(element, token, present);
    }

    /**
     * One page cell and the state it was last given, so a frame that changes nothing writes nothing.
     */
    private static final class Cell {
        private final Element root, name, accent;
        private final Item item;
        private final Element texture;
        private final Font font;
        @Nullable
        private final Element stale, number, key;
        @Nullable
        private IconReference shownIcon;
        @Nullable
        private WheelMenuEntry shownEntry;
        @Nullable
        private String shownLabel;
        private int shownAccent = Integer.MIN_VALUE;
        private int shownFlags = -1;

        private Cell(Element root, Item item, Element texture, Element name, Element accent, Font font,
                     @Nullable Element stale, @Nullable Element number, @Nullable Element key) {
            this.root = root;
            this.item = item;
            this.texture = texture;
            this.name = name;
            this.accent = accent;
            this.font = font;
            this.stale = stale;
            this.number = number;
            this.key = key;
        }

        private void show(@Nullable WheelMenuEntry entry, boolean hidden, boolean selected, boolean stale) {
            int flags = (hidden ? 1 : 0) | (selected ? 2 : 0) | (stale ? 4 : 0);
            if (flags != this.shownFlags) {
                this.shownFlags = flags;
                flag(this.root, "hidden", hidden);
                flag(this.root, "selected", selected);
                flag(this.root, "stale", stale);
            }
            IconReference icon = entry == null ? null : entry.icon().orElse(null);
            boolean asItem = icon != null && icon.item().isPresent();
            boolean asTexture = icon != null && icon.texture().isPresent();
            // The three ways a cell can carry something: an item, a texture, or the entry's name.
            flag(this.root, "icon-item", asItem);
            flag(this.root, "icon-texture", asTexture);
            flag(this.root, "plain", entry != null && !asItem && !asTexture);
            if (!Objects.equals(this.shownIcon, icon)) {
                this.shownIcon = icon;
                if (asItem) this.item.setIngredientStack(icon.item().orElseThrow().create());
                else this.item.clearDrivenState(Item.Source.INGREDIENT);
                if (asTexture) this.texture.setAttribute("src", icon.texture().orElseThrow().toString());
                else this.texture.removeAttribute("src");
            }
            // The label is keyed on the entry, not on the icon: two entries without one share a null icon, and
            // a name left over from the first of them would be the one thing the cell shows.
            if (!Objects.equals(this.shownEntry, entry)) {
                this.shownEntry = entry;
                String label = entry == null ? ""
                        : IconRenderer.fit(this.font, entry.title().getString(), SLOT_SIZE - NAME_INSET);
                if (!label.equals(this.shownLabel)) {
                    this.shownLabel = label;
                    this.name.setTextContent(label);
                }
            }
            int accent = entry == null ? 0 : entry.accentColor();
            if (accent == this.shownAccent) return;
            this.shownAccent = accent;
            this.accent.setInlineStyleProperty("background-color", css(accent));
        }

        private static String css(int argb) {
            if ((argb >>> 24) == 0) return "transparent";
            return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)",
                    (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, ((argb >>> 24) & 0xFF) / 255.0D);
        }
    }
}
