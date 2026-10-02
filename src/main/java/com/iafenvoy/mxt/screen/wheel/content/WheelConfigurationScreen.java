package com.iafenvoy.mxt.screen.wheel.content;

import com.iafenvoy.mxt.api.WheelMenuEntry;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.registry.MxtKeyMappings;
import com.iafenvoy.mxt.render.IconRenderer;
import com.iafenvoy.mxt.runtime.wheel.WheelEntryKinds;
import com.iafenvoy.mxt.runtime.wheel.WheelLayout;
import com.iafenvoy.mxt.runtime.wheel.WheelSlot;
import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.iafenvoy.mxt.screen.aui.AuiPages;
import com.iafenvoy.mxt.screen.aui.AuiScreen;
import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
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
public final class WheelConfigurationScreen extends AuiScreen {
    private static final int SLOT_SIZE = 22, SLOT_GAP = 2;
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
    private static final int SLOT_ROW_HEIGHT = 56, SLOT_TOP_OFFSET = 17;
    private static final int PANEL_HEIGHT = 268;
    /**
     * The rows a full-height band can show: the page's cell grid is fixed, the visible window slides over it.
     */
    private static final int POOL_ROWS = 7, POOL_CELLS = POOL_COLUMNS * POOL_ROWS;
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
    protected String pagePath() {
        return AuiPages.wheelConfigPage();
    }

    @Override
    protected String pageName() {
        return "wheel_config";
    }

    @Override
    public void onPageBound() {
        // The panel's geometry is computed from the window, so it is written before the page is told about it.
        this.layout();
        this.refreshPage();
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        this.layout();
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
     * Runs before the vanilla pass so the page sits under the tooltips this class draws itself.
     */
    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Nothing at all is drawn while the page's stylesheet is still in flight: this screen's panel and pool
        // geometry are written into the page, but without the stylesheet there is no box to lay them out in.
        if (!this.auiReadyToDraw()) return;
        if (this.auiPageWritable()) this.refreshPage();
        ApricityGuiLayers.submitUi(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.extractTooltip(graphics, mouseX, mouseY, Minecraft.getInstance().player);
        this.extractPageError(graphics, this.font, this.width, this.height);
    }

    @Override
    public void onBindingsCleared() {
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
    }

    @Override
    public void bindPage() {
        this.panel = this.getOrThrow("panel");
        this.title = this.getOrThrow("title");
        this.headingAura = this.getOrThrow("heading_aura");
        this.headingOption = this.getOrThrow("heading_option");
        this.emptyAura = this.getOrThrow("empty_aura");
        this.emptyOption = this.getOrThrow("empty_option");
        this.divider = this.getOrThrow("divider");
        this.poolAura = this.getOrThrow("pool_aura");
        this.poolOption = this.getOrThrow("pool_option");
        this.sectorRow = this.getOrThrow("sector_row");
        this.trackAura = this.getOrThrow("track_aura");
        this.trackOption = this.getOrThrow("track_option");
        this.thumbAura = this.getOrThrow("thumb_aura");
        this.thumbOption = this.getOrThrow("thumb_option");

        for (int pool = 0; pool < 2; pool++) {
            String prefix = pool == AURA_POOL ? "aura" : "option";
            for (int index = 0; index < POOL_CELLS; index++) {
                this.poolCells.add(this.cell(prefix, index));
            }
        }
        for (int sector = 0; sector < WheelLayout.SLOTS; sector++) {
            this.sectorCells.add(this.cell("sector", sector));
        }
        AuiElements.setText(this.title, this.getTitle().getString());
        AuiElements.setText(this.headingAura, Component.translatable("wheel.mxt.pool.aura").getString());
        AuiElements.setText(this.headingOption, Component.translatable("wheel.mxt.pool.ability").getString());
        AuiElements.setText(this.emptyAura, Component.translatable("wheel.mxt.pool.empty").getString());
        AuiElements.setText(this.emptyOption, Component.translatable("wheel.mxt.pool.empty").getString());
    }

    /**
     * One cell of the page: the root plus the four (five, for a sector) elements it writes into. The number, key
     * and stale marks are optional, so only the parts every cell carries are required.
     */
    private Cell cell(String prefix, int index) {
        return new Cell(this.getOrThrow(prefix + "-" + index),
                this.getOrThrow(prefix + "_item-" + index, Item.class),
                this.getOrThrow(prefix + "_tex-" + index),
                this.getOrThrow(prefix + "_name-" + index),
                this.getOrThrow(prefix + "_accent-" + index), this.font,
                this.find(prefix + "_number-" + index),
                this.find(prefix + "_key-" + index));
    }

    // ------------------------------------------------------------------ frame

    private void refreshPage() {
        Element panel = this.panel;
        if (panel == null) return;
        AuiElements.style(panel, "left", this.panelLeft + "px");
        AuiElements.style(panel, "top", this.panelTop + "px");
        AuiElements.style(panel, "width", this.panelWidth + "px");
        AuiElements.style(panel, "height", this.panelHeight + "px");
        AuiElements.style(this.title, "left", "10px");
        AuiElements.style(this.title, "top", "10px");

        int band = Math.max(1, this.poolsBottom - this.poolsTop);
        for (int pool = 0; pool < 2; pool++) {
            int left = this.poolLeft[pool] - this.panelLeft;
            int top = this.poolsTop - this.panelTop;
            Element heading = pool == AURA_POOL ? this.headingAura : this.headingOption;
            Element empty = pool == AURA_POOL ? this.emptyAura : this.emptyOption;
            Element cells = pool == AURA_POOL ? this.poolAura : this.poolOption;
            Element track = pool == AURA_POOL ? this.trackAura : this.trackOption;
            AuiElements.style(heading, "left", left + "px");
            AuiElements.style(heading, "top", HEADER_HEIGHT + "px");
            AuiElements.style(empty, "left", left + "px");
            AuiElements.style(empty, "top", top + "px");
            AuiElements.setClass(empty, "hidden", !this.pool(pool).isEmpty());
            // Scrolling moves the whole grid rather than each cell: one write, and the cells keep their own
            // static offsets inside the pool. The offset is rounded once and used by the window below too.
            int offset = (int) Math.round(this.scroll[pool]);
            AuiElements.style(cells, "left", left + "px");
            AuiElements.style(cells, "top", (top - offset) + "px");
            AuiElements.style(track, "left", (left + POOL_WIDTH + 2) + "px");
            AuiElements.style(track, "top", top + "px");
            AuiElements.style(track, "height", band + "px");
            this.showPool(pool, offset, band);
            this.showScrollBar(pool, band);
        }

        AuiElements.style(this.divider, "left", "6px");
        AuiElements.style(this.divider, "top", (this.poolsBottom - this.panelTop + 1) + "px");
        AuiElements.style(this.divider, "width", Math.max(0, this.panelWidth - 12) + "px");

        AuiElements.style(this.sectorRow, "left", (this.slotRowLeft - this.panelLeft) + "px");
        AuiElements.style(this.sectorRow, "top", (this.slotRowTop - this.panelTop) + "px");
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
            AuiElements.setText(cell.number, Integer.toString(sector + 1));
            Component key = this.slotKey(sector);
            AuiElements.setText(cell.key, key == null ? "" : key.getString());
        }
    }

    private void showScrollBar(int pool, int band) {
        Element track = pool == AURA_POOL ? this.trackAura : this.trackOption;
        Element thumb = pool == AURA_POOL ? this.thumbAura : this.thumbOption;
        int rows = rows(this.pool(pool).size());
        int maxScroll = Math.max(0, rows * GRID_STEP - band);
        AuiElements.setClass(track, "hidden", maxScroll == 0);
        if (maxScroll == 0) return;
        int thumbHeight = Math.max(12, (int) ((double) band * band / (rows * GRID_STEP)));
        int thumbY = (int) ((band - thumbHeight) * (this.scroll[pool] / maxScroll));
        AuiElements.style(thumb, "top", thumbY + "px");
        AuiElements.style(thumb, "height", thumbHeight + "px");
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

    /**
     * One page cell and the state it was last given, so a frame that changes nothing writes nothing.
     */
    private static final class Cell {
        private final Element root, name, accent, texture;
        private final Item item;
        private final Font font;
        @Nullable
        private final Element number, key;
        @Nullable
        private IconReference shownIcon;
        @Nullable
        private WheelMenuEntry shownEntry;
        @Nullable
        private String shownLabel;
        private int shownAccent = Integer.MIN_VALUE, shownFlags = -1;

        private Cell(Element root, Item item, Element texture, Element name, Element accent, Font font, @Nullable Element number, @Nullable Element key) {
            this.root = root;
            this.item = item;
            this.texture = texture;
            this.name = name;
            this.accent = accent;
            this.font = font;
            this.number = number;
            this.key = key;
        }

        private void show(@Nullable WheelMenuEntry entry, boolean hidden, boolean selected, boolean stale) {
            int flags = (hidden ? 1 : 0) | (selected ? 2 : 0) | (stale ? 4 : 0);
            if (flags != this.shownFlags) {
                this.shownFlags = flags;
                AuiElements.setClass(this.root, "hidden", hidden);
                AuiElements.setClass(this.root, "selected", selected);
                AuiElements.setClass(this.root, "stale", stale);
            }
            IconReference icon = entry == null ? null : entry.icon().orElse(null);
            boolean asItem = icon != null && icon.item().isPresent();
            boolean asTexture = icon != null && icon.texture().isPresent();
            // The three ways a cell can carry something: an item, a texture, or the entry's name.
            AuiElements.setClass(this.root, "icon-item", asItem);
            AuiElements.setClass(this.root, "icon-texture", asTexture);
            AuiElements.setClass(this.root, "plain", entry != null && !asItem && !asTexture);
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
                    AuiElements.setText(this.name, label);
                }
            }
            int accent = entry == null ? 0 : entry.accentColor();
            if (accent == this.shownAccent) return;
            this.shownAccent = accent;
            AuiElements.style(this.accent, "background-color", css(accent));
        }

        private static String css(int argb) {
            if ((argb >>> 24) == 0) return "transparent";
            return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.3f)",
                    (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, ((argb >>> 24) & 0xFF) / 255.0D);
        }
    }
}