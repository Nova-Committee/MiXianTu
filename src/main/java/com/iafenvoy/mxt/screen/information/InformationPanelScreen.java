package com.iafenvoy.mxt.screen.information;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueProgress;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueProgress.Entry;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueProgress.Mode;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueProgress.Progress;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.screen.aui.*;
import com.iafenvoy.mxt.screen.aui.AuiScrollList.Cell;
import com.iafenvoy.mxt.screen.information.InformationCollector.InformationEntry;
import com.iafenvoy.mxt.screen.information.InformationHelper.Columns;
import com.iafenvoy.mxt.screen.information.InformationManager.Side;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.*;

/**
 * The player information panel, drawn by one page. Two pages share the panel - the attribute lists and the
 * technique table - switched by the two tabs in its top-left corner.
 * <p>
 * Unlike the container screens this one has no menu, and its panel shrinks with the window, so the geometry
 * is computed here (the same formulas the old screens used) and written into the page as inline styles. The
 * page carries no coordinates of its own beyond the 510x315 defaults, and nothing is ever read back from the
 * DOM: the rectangles this class writes are the ones it positions the player preview against.
 * <p>
 * Each page's rows are DOM elements driven by {@link AuiScrollList}: the visible window is written on every
 * layout and the scroll offset stays a pixel value, like the vanilla lists both old screens used. Rows that
 * would fall outside the list are hidden instead of clipped, because clipping a box in ApricityUI paints a
 * dark artefact.
 */
public final class InformationPanelScreen extends AuiScreen {
    public enum Page {
        INFO,
        TECHNIQUES
    }

    private static final int PANEL_WIDTH = 510, PANEL_HEIGHT = 315;
    private static final int PLAYER_RENDER_WIDTH = 120, PLAYER_RENDER_SCALE = 30;
    private static final int EQUIPMENT_SLOT_SIZE = 24, EQUIPMENT_SLOT_COUNT = 4;
    private static final int CONTENT_LEFT = 20, CONTENT_TOP = 36;
    /**
     * A caption to its list, and the panel's bottom margin under both lists.
     */
    private static final int CAPTION_GAP = 16;
    private static final int LIST_BOTTOM = 12;
    private static final int PLAYER_PREVIEW_HEIGHT = EQUIPMENT_SLOT_SIZE * EQUIPMENT_SLOT_COUNT;
    private static final int ROW_HEIGHT = 18;
    /**
     * The gap between the name column and the value column, and the row's right margin - the same gap
     * {@link InformationHelper} keeps when it splits a row into its two columns.
     */
    private static final int TEXT_INSET = 8;
    private static final int TECHNIQUE_ROWS = 16, TECHNIQUE_ROW_HEIGHT = 34;
    private static final int TECHNIQUE_COLUMNS = 2, TECHNIQUE_COLUMN_GAP = 10;
    private static final int TECHNIQUE_ICON_SIZE = 24, TECHNIQUE_ICON_GAP = 9;
    private static final int TECHNIQUE_NAME_GAP = 4;
    private static final int TECHNIQUE_BAR_TOP = 19;
    private static final int TECHNIQUE_SEPARATOR_TOP = TECHNIQUE_ROW_HEIGHT - 3;
    private static final int TAB_TOP = 10, TAB_LEFT = 18;
    private static final int TAB_HEIGHT = 15, TAB_GAP = 6;
    /**
     * Room kept free of the tabs on the right; the boxes give way together when the panel is narrower.
     */
    private static final int TAB_MARGIN = 14;
    /**
     * The tab boxes are roomier than their labels: the page keeps its 9px base font, so the size comes from
     * {@link #TAB_HEIGHT} and this much padding rather than from a larger font. The label is measured with the
     * vanilla font, exactly as ApricityUI measures it.
     */
    private static final int TAB_PADDING = 24;
    private static final int PANEL_MARGIN = 20;
    /**
     * The 习得功法 page has no caption line, so its list starts right under the tab band - the same 6px gap the
     * two tabs keep between themselves - instead of one caption below it. The page carries the same defaults.
     */
    private static final int TECHNIQUE_TOP = TAB_TOP + TAB_HEIGHT + TAB_GAP, TECHNIQUE_EMPTY_TOP = TECHNIQUE_TOP + 6;
    private static final int LABEL_COLOR = 0xFFFFFFFF, MUTED_COLOR = 0xFFC0C0C0, UNKNOWN_COLOR = 0xFF8A8A8A;
    private static final int BAR_FALLBACK_COLOR = 0xFFCFCFCF;
    private static final EquipmentSlot[] EQUIPMENT_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    @Nullable
    private Element panel, cultivationCaption, basicCaption, preview, tabInfo, tabTechnique, techniquesEmpty;
    private final List<Item> equipment = new ArrayList<>(EQUIPMENT_SLOT_COUNT);
    @Nullable
    private AuiScrollList<InformationEntry> cultivation, basic;
    @Nullable
    private AuiScrollList<TechniqueRow> techniques;
    @Nullable
    private TechniqueBinding techniqueBinding;
    private final Set<String> overflowReports = new HashSet<>();
    private Page page;
    private int panelLeft, panelTop;
    private int previewLeft, previewWidth;
    private int refreshTicks;
    /**
     * The geometry the page was last written with; a resize is the only thing that changes it.
     */
    private long shownGeometry = Long.MIN_VALUE;
    @Nullable
    private Page shownPage;

    public InformationPanelScreen() {
        this(Page.INFO);
    }

    public InformationPanelScreen(Page page) {
        super(AuiPages.page(AuiPages.INFORMATION, "information"));
        this.page = page;
    }

    @Override
    public void onPageBound() {
        this.refreshInformation();
    }

    @Override
    public void bindPage() {
        this.panel = this.getOrThrow("panel");
        this.cultivationCaption = this.getOrThrow("cultivation_caption");
        this.basicCaption = this.getOrThrow("basic_caption");
        this.preview = this.getOrThrow("preview");
        this.tabInfo = this.getOrThrow("tab_info");
        this.tabTechnique = this.getOrThrow("tab_technique");
        this.techniquesEmpty = this.getOrThrow("techniques_empty");

        AuiScrollList<InformationEntry> cultivation = this.scrollList("cult", "cultivation", 14, new InfoBinding());
        AuiScrollList<InformationEntry> basic = this.scrollList("basic", "basic", 8, new InfoBinding());
        TechniqueBinding techniqueBinding = new TechniqueBinding();
        AuiScrollList<TechniqueRow> techniques =
                this.scrollList("tech", "techniques", TECHNIQUE_ROWS, techniqueBinding);
        for (int index = 0; index < EQUIPMENT_SLOT_COUNT; index++) {
            this.equipment.add(this.getOrThrow("equip-" + index, Item.class));
        }
        this.cultivation = cultivation;
        this.basic = basic;
        this.techniques = techniques;
        this.techniqueBinding = techniqueBinding;
        this.text(this.cultivationCaption, Component.translatable("info.mxt.cultivation"));
        this.text(this.basicCaption, Component.translatable("info.mxt.basic"));
        this.text(this.tabInfo, Component.translatable("screen.mxt.information_panel.tab.info"));
        this.text(this.tabTechnique, Component.translatable("screen.mxt.technique_panel"));
        this.text(this.techniquesEmpty, Component.translatable("screen.mxt.technique_panel.empty"));
        this.tabInfo.addEventListener("click", event -> this.setPage(Page.INFO));
        this.tabTechnique.addEventListener("click", event -> this.setPage(Page.TECHNIQUES));
        // A row click is a local pick, as the old list's selection was.
        for (AuiScrollList<InformationEntry> list : List.of(cultivation, basic)) {
            for (Cell cell : list.cells) {
                cell.click(() -> this.select(list, cell));
            }
        }
        this.shownGeometry = Long.MIN_VALUE;
        this.shownPage = null;
    }

    @Override
    public void onBindingsCleared() {
        this.techniqueBinding = null;
        this.panel = null;
        this.cultivationCaption = null;
        this.basicCaption = null;
        this.preview = null;
        this.tabInfo = null;
        this.tabTechnique = null;
        this.techniquesEmpty = null;
        this.equipment.clear();
        this.cultivation = null;
        this.basic = null;
        this.techniques = null;
        this.shownGeometry = Long.MIN_VALUE;
        this.shownPage = null;
    }

    private void select(AuiScrollList<InformationEntry> list, Cell cell) {
        if (cell.key() == null) return;
        for (AuiScrollList<InformationEntry> other : List.of(list, list == this.cultivation ? this.basic : this.cultivation))
            if (other != null)
                other.markSelected(cell.key());
    }

    private void setPage(Page next) {
        if (this.page == next) return;
        this.page = next;
        this.showPage();
        // The hidden page's rows were not laid out, so the new one is written now.
        if (next == Page.TECHNIQUES) this.refreshTechniques();
        this.layout();
    }

    /**
     * Switches the page class on the panel and the selected tab; the page blocks themselves are CSS.
     */
    private void showPage() {
        if (this.panel == null) return;
        if (this.shownPage == this.page) return;
        this.shownPage = this.page;
        boolean techniques = this.page == Page.TECHNIQUES;
        AuiElements.setClass(this.panel, "page-technique", techniques);
        if (this.tabInfo != null) AuiElements.setClass(this.tabInfo, "selected", !techniques);
        if (this.tabTechnique != null) AuiElements.setClass(this.tabTechnique, "selected", techniques);
    }

    @Override
    public void tick() {
        super.tick();
        int interval = MxtClientConfig.INSTANCE.information.refreshInterval.getValue();
        if (++this.refreshTicks < interval) return;
        this.refreshTicks = 0;
        this.refreshInformation();
    }

    private void refreshInformation() {
        if (this.minecraft.player == null) return;
        if (this.cultivation != null) {
            this.cultivation.rebuild(this.font,
                    InformationManager.collectEntries(this.minecraft.player, Side.CULTIVATION));
        }
        if (this.basic != null) {
            this.basic.rebuild(this.font, InformationManager.collectEntries(this.minecraft.player, Side.BASIC));
        }
        this.refreshTechniques();
    }

    private void refreshTechniques() {
        AuiScrollList<TechniqueRow> list = this.techniques;
        if (list == null || this.minecraft.player == null) return;
        SpiritIdentityAttachment spirit = this.minecraft.player.getData(MxtAttachments.SPIRIT_IDENTITY);
        ResourceHolderAttachment resources = this.minecraft.player.getData(MxtAttachments.RESOURCE_HOLDER);
        Mode mode = MxtClientConfig.INSTANCE.techniques.progressMode.getValue();
        List<Entry> rows = TechniqueProgress.rows(spirit,
                this.minecraft.player.getData(MxtAttachments.PROGRESSION), resources,
                FormulaContexts.forEntity(this.minecraft.player));
        List<TechniqueRow> entries = new ArrayList<>(rows.size());
        for (Entry row : rows) entries.add(new TechniqueRow(row, TechniqueProgress.progress(row, mode)));
        list.rebuild(this.font, entries);
        if (this.techniquesEmpty != null) {
            AuiElements.style(this.techniquesEmpty, "display", entries.isEmpty() ? "block" : "none");
        }
    }

    // ------------------------------------------------------------------ layout

    /**
     * Writes the panel and the content rectangles; the layout only changes with the window, so the whole
     * write is skipped while the signature holds.
     */
    private void layout() {
        if (this.panel == null) return;
        // The page class is (re)applied here, so a screen opened straight onto the second page shows it.
        this.showPage();
        int width = Math.max(1, Math.min(PANEL_WIDTH, this.width - 12));
        int height = Math.max(1, Math.min(PANEL_HEIGHT, this.height - 12));
        int left = (this.width - width) / 2;
        int top = (this.height - height) / 2;
        int renderWidth = Math.min(PLAYER_RENDER_WIDTH, Math.max(60, (width - 250) / 2));
        // The frame sits right of the equipment column and the right column right of the frame: 20 + 24 + 8 = 52
        // and 52 + renderWidth + 12, both of which are the page's own defaults. Leaving the margin out of the sum
        // moved the frame 20px left (the recess covered the four equipment cells) and the right column with it.
        int previewLeft = CONTENT_LEFT + EQUIPMENT_SLOT_SIZE + 8;
        int rightX = previewLeft + renderWidth + 12;
        int rightWidth = Math.max(1, width - rightX - PANEL_MARGIN);
        // The 人物信息 column's list starts one caption below the content top and ends at the panel's bottom
        // margin; the 习得功法 list has no caption above it, so it starts higher (see TECHNIQUE_TOP).
        int listTop = CONTENT_TOP + CAPTION_GAP;
        int rightHeight = Math.max(1, height - listTop - LIST_BOTTOM);
        int techniqueHeight = Math.max(1, height - TECHNIQUE_TOP - LIST_BOTTOM);
        // The 基本信息 list spans the left column: from its own left edge to the frame's right edge.
        int playerWidth = previewLeft + renderWidth - CONTENT_LEFT;
        int basicTop = CONTENT_TOP + PLAYER_PREVIEW_HEIGHT + 14;
        int basicListTop = basicTop + CAPTION_GAP;
        int basicHeight = Math.max(1, height - LIST_BOTTOM - basicListTop);
        int listWidth = Math.max(1, width - PANEL_MARGIN * 2);
        long signature = ((long) width << 40) ^ ((long) height << 20) ^ (renderWidth * 31L);
        this.panelLeft = left;
        this.panelTop = top;
        this.previewLeft = previewLeft;
        this.previewWidth = renderWidth;
        if (signature != this.shownGeometry) {
            this.shownGeometry = signature;
            AuiElements.style(this.panel, "left", left + "px");
            AuiElements.style(this.panel, "top", top + "px");
            AuiElements.style(this.panel, "width", width + "px");
            AuiElements.style(this.panel, "height", height + "px");
            AuiElements.style(this.preview, "left", this.previewLeft + "px");
            AuiElements.style(this.preview, "width", renderWidth + "px");
            AuiElements.style(this.cultivationCaption, "left", rightX + "px");
            AuiElements.style(this.basicCaption, "top", basicTop + "px");
            if (this.cultivation != null) {
                AuiElements.style(this.cultivation.root, "left", rightX + "px");
                AuiElements.style(this.cultivation.root, "width", rightWidth + "px");
                AuiElements.style(this.cultivation.root, "height", rightHeight + "px");
            }
            if (this.basic != null) {
                AuiElements.style(this.basic.root, "top", basicListTop + "px");
                AuiElements.style(this.basic.root, "width", playerWidth + "px");
                AuiElements.style(this.basic.root, "height", basicHeight + "px");
            }
            if (this.techniques != null) {
                AuiElements.style(this.techniques.root, "top", TECHNIQUE_TOP + "px");
                AuiElements.style(this.techniques.root, "width", listWidth + "px");
                AuiElements.style(this.techniques.root, "height", techniqueHeight + "px");
            }
            if (this.techniquesEmpty != null)
                AuiElements.style(this.techniquesEmpty, "top", TECHNIQUE_EMPTY_TOP + "px");
            this.layoutTabs(width);
        }
        // The hidden page keeps the geometry it had: laying it out would write rows nothing can see.
        if (this.page == Page.INFO) {
            if (this.cultivation != null) this.cultivation.layout(this.font, rightWidth, rightHeight);
            if (this.basic != null) this.basic.layout(this.font, playerWidth, basicHeight);
        } else if (this.techniques != null) {
            this.techniques.layout(this.font, listWidth, techniqueHeight);
        }
    }

    /**
     * The two tabs sit in the panel's top-left corner, on the line the old title occupied; the first one is the
     * panel's own name, so the page carries no separate title node.
     */
    private void layoutTabs(int width) {
        if (this.tabInfo == null || this.tabTechnique == null) return;
        int techniqueWidth = this.tabWidth("screen.mxt.technique_panel");
        int infoWidth = this.tabWidth("screen.mxt.information_panel.tab.info");
        int room = Math.max(2, width - TAB_LEFT - TAB_MARGIN - TAB_GAP);
        if (infoWidth + techniqueWidth > room) {
            int total = infoWidth + techniqueWidth;
            infoWidth = Math.max(1, room * infoWidth / total);
            techniqueWidth = Math.max(1, room - infoWidth);
        }
        AuiElements.style(this.tabInfo, "left", TAB_LEFT + "px");
        AuiElements.style(this.tabInfo, "top", TAB_TOP + "px");
        AuiElements.style(this.tabInfo, "width", infoWidth + "px");
        AuiElements.style(this.tabInfo, "height", TAB_HEIGHT + "px");
        AuiElements.style(this.tabTechnique, "left", (TAB_LEFT + infoWidth + TAB_GAP) + "px");
        AuiElements.style(this.tabTechnique, "top", TAB_TOP + "px");
        AuiElements.style(this.tabTechnique, "width", techniqueWidth + "px");
        AuiElements.style(this.tabTechnique, "height", TAB_HEIGHT + "px");
    }

    private int tabWidth(String key) {
        int label = this.font.width(Component.translatable(key));
        return Math.max(24, label + TAB_PADDING);
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean bound = this.auiPageWritable();
        if (bound && this.page == Page.INFO) this.showEquipment();
        if (bound) this.layout();
        // The page is submitted by super (ApricityScreen); the player preview is extracted after it so it lands
        // over the page's transparent preview box.
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (bound && this.page == Page.INFO) this.extractPlayer(graphics, mouseX, mouseY);
        this.extractTooltip(graphics, mouseX, mouseY);
    }

    /**
     * The page's own tooltips, through the vanilla renderer instead of ApricityUI's: the lines are components with
     * their own colours, which a page element's flattened text cannot carry. Submitted after {@code super}, like
     * the multi-block page's cell tooltips, so they land over the page.
     */
    private void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.page == Page.INFO) this.extractEquipmentTooltip(graphics, mouseX, mouseY);
        else this.extractTechniqueTooltip(graphics, mouseX, mouseY);
    }

    // The four cells carry the player's real stacks, so what is drawn is what that stack would say anywhere else.
    private void extractEquipmentTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int index = this.equipmentSlotAt(mouseX, mouseY);
        if (index < 0 || this.minecraft.player == null) return;
        ItemStack stack = this.minecraft.player.getItemBySlot(EQUIPMENT_SLOTS[index]);
        if (!stack.isEmpty()) graphics.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
    }

    /**
     * The equipment cell under the pointer, or -1. The column is fixed geometry - four cells of
     * {@link #EQUIPMENT_SLOT_SIZE} down from the content's top-left corner, the same numbers the page carries -
     * so the hit test needs no geometry read back from it.
     */
    private int equipmentSlotAt(double mouseX, double mouseY) {
        Document current = this.getLinkedDocument();
        Position pointer = current == null ? new Position(mouseX, mouseY)
                : current.screenToDocumentPosition(new Position(mouseX, mouseY));
        int left = this.panelLeft + CONTENT_LEFT;
        int top = this.panelTop + CONTENT_TOP;
        if (pointer.x < left || pointer.x >= left + EQUIPMENT_SLOT_SIZE) return -1;
        int offset = (int) (pointer.y - top);
        if (offset < 0 || offset >= EQUIPMENT_SLOT_SIZE * EQUIPMENT_SLOT_COUNT) return -1;
        return offset / EQUIPMENT_SLOT_SIZE;
    }

    // A hovered technique row's tooltip; the row spells out the level id and the tier no row has room for.
    private void extractTechniqueTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.techniqueBinding == null) return;
        AuiScrollList<TechniqueRow> list = this.techniques;
        Cell cell = list == null ? null : list.hoveredCell(mouseX, mouseY);
        if (cell == null || !(cell.entry() instanceof TechniqueRow entry)) return;
        List<Component> lines = this.techniqueBinding.tooltipLines(entry);
        if (!lines.isEmpty()) graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    private void showEquipment() {
        if (this.minecraft.player == null) return;
        for (int index = 0; index < this.equipment.size(); index++) {
            ItemStack stack = this.minecraft.player.getItemBySlot(EQUIPMENT_SLOTS[index]);
            Item item = this.equipment.get(index);
            if (stack.isEmpty()) item.clearDrivenState(Item.Source.INGREDIENT);
            else item.setIngredientStack(stack);
        }
    }

    // The entity render is the one thing AUI cannot draw, so it stays on top of the page at the rectangle
    // the page's preview frame occupies.
    private void extractPlayer(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.minecraft.player == null) return;
        int x1 = this.panelLeft + this.previewLeft;
        int y1 = this.panelTop + CONTENT_TOP;
        int x2 = x1 + this.previewWidth;
        int y2 = y1 + PLAYER_PREVIEW_HEIGHT;
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x1, y1, x2, y2, PLAYER_RENDER_SCALE, 0,
                mouseX, mouseY, this.minecraft.player);
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        this.shownGeometry = Long.MIN_VALUE;
        this.layout();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        AuiScrollList<?> target = null;
        if (this.page == Page.INFO) {
            if (this.cultivation != null && this.cultivation.contains(mouseX, mouseY)) target = this.cultivation;
            else if (this.basic != null && this.basic.contains(mouseX, mouseY)) target = this.basic;
        } else if (this.techniques != null && this.techniques.contains(mouseX, mouseY)) {
            target = this.techniques;
        }
        if (target == null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        target.scrollBy(-scrollY * target.rowHeight(), this.font);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String abbreviate(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        String suffix = "...";
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width(suffix))) + suffix;
    }

    private static String format(double value) {
        return Math.abs(value - Math.rint(value)) < 1.0E-6D
                ? Long.toString(Math.round(value)) : String.format("%.1f", value);
    }

    /**
     * One scrolled entry of the technique table.
     */
    private record TechniqueRow(Entry row, Progress progress) {
    }

    /**
     * The attribute rows: a name and a value, each cut to its own column.
     */
    private final class InfoBinding implements AuiScrollList.Binding<InformationEntry> {
        private Columns columns = new Columns(0, 1);

        @Override
        public List<Cell> bind(AuiWrappedScreen screen, String prefix, int count) {
            List<Cell> cells = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                Element row = screen.getOrThrow(prefix + "_row-" + index);
                Element name = screen.getOrThrow(prefix + "_name-" + index);
                Element value = screen.getOrThrow(prefix + "_value-" + index);
                cells.add(new Cell(row, new Element[]{name, value}));
            }
            return cells;
        }

        @Override
        public int rowHeight() {
            return ROW_HEIGHT;
        }

        /**
         * One pair of columns for the whole block, measured from the widest name and the widest value in it.
         * Per-row numbers only line the values up while every row carries a name of the same width: a
         * continuation row (a second resource of the same chain carries no name at all) put its value at the
         * left edge instead of in the value column. The same pass warns once per distinct entry that would
         * need more than one line.
         * <p>
         * The name column starts at the row's own left edge, which is the list's left edge - the same x the
         * caption above the list sits at.
         */
        @Override
        public void prepare(Font font, List<InformationEntry> entries, int width) {
            int available = Math.max(1, width - TEXT_INSET);
            int widestName = 0, widestValue = 0;
            for (InformationEntry entry : entries) {
                if (entry.name() != null) widestName = Math.max(widestName, font.width(entry.name()));
                widestValue = Math.max(widestValue, font.width(entry.value()));
            }
            this.columns = InformationHelper.columns(available, widestName, widestValue);
            if (width <= 1 || FMLEnvironment.isProduction()) return;
            for (InformationEntry entry : entries) {
                int nameLines = entry.name() == null ? 0
                        : font.split(entry.name(), Math.max(1, this.columns.nameWidth())).size();
                int valueLines = font.split(entry.value(), Math.max(1, this.columns.valueWidth())).size();
                if (nameLines <= 1 && valueLines <= 1) continue;
                String key = (entry.name() == null ? "" : entry.name().getString()) + "=" + entry.value().getString();
                if (!InformationPanelScreen.this.overflowReports.add(key)) continue;
                MiXianTu.LOGGER.error("Information entry contains more than one line (width={}): {}",
                        available, key);
            }
        }

        @Override
        public void clear(Cell cell) {
            cell.hide();
        }

        @Override
        public void show(Font font, Cell cell, InformationEntry entry, int index, int left, int top, int width) {
            String fullName = entry.name() == null ? "" : entry.name().getString();
            String fullValue = entry.value().getString();
            cell.show(fullName + "=" + fullValue, entry, left, top, width);
            // The value column belongs to the block (see prepare), not to this row, so a continuation row lines
            // up with the named ones instead of starting at the left edge.
            AuiElements.style(cell.part(1), "left", (this.columns.nameWidth() + TEXT_INSET) + "px");
            AuiElements.setText(cell.part(0), abbreviate(font, fullName, Math.max(1, this.columns.nameWidth())));
            AuiElements.setText(cell.part(1), abbreviate(font, fullValue, Math.max(1, this.columns.valueWidth())));
            AuiElements.style(cell.part(0), "color", AuiStyles.hex(entry.color()));
            AuiElements.style(cell.part(1), "color", AuiStyles.hex(entry.color()));
        }
    }

    /**
     * The technique rows: icon, name in its tier's colour, level, mastery and the mastery bar.
     */
    private static final class TechniqueBinding implements AuiScrollList.Binding<TechniqueRow> {
        @Override
        public List<Cell> bind(AuiWrappedScreen screen, String prefix, int count) {
            List<Cell> cells = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                Element row = screen.getOrThrow(prefix + "-" + index);
                Item item = screen.getOrThrow(prefix + "_item-" + index, Item.class);
                Element texture = screen.getOrThrow(prefix + "_tex-" + index);
                Element name = screen.getOrThrow(prefix + "_name-" + index);
                Element level = screen.getOrThrow(prefix + "_level-" + index);
                Element value = screen.getOrThrow(prefix + "_value-" + index);
                Element bar = screen.getOrThrow(prefix + "_bar-" + index);
                Element fill = screen.getOrThrow(prefix + "_fill-" + index);
                Element separator = screen.getOrThrow(prefix + "_sep-" + index);
                cells.add(new Cell(row, new Element[]{item, texture, name, level, value, bar, fill, separator}));
            }
            return cells;
        }

        @Override
        public int rowHeight() {
            return TECHNIQUE_ROW_HEIGHT;
        }

        @Override
        public int columns() {
            return TECHNIQUE_COLUMNS;
        }

        @Override
        public int columnGap() {
            return TECHNIQUE_COLUMN_GAP;
        }

        @Override
        public void clear(Cell cell) {
            // A technique row paints an icon frame, a meter and a separator of its own (see information.css), so
            // unlike an attribute row it leaves visible residue when a never-shown cell is left in the DOM.
            cell.hide();
        }

        @Override
        public void show(Font font, Cell cell, TechniqueRow entry, int index, int left, int top, int width) {
            Entry row = entry.row();
            cell.show(HolderHelper.id(row.technique()).toString(), entry, left, top, width);
            this.showIcon(cell, row);
            int textX = TECHNIQUE_ICON_SIZE + TECHNIQUE_ICON_GAP;
            Component value = this.valueText(entry);
            int valueWidth = font.width(value);
            int valueLeft = Math.max(textX, width - valueWidth);
            int room = Math.max(0, valueLeft - TECHNIQUE_NAME_GAP - textX);
            Component level = this.levelText(row);
            int levelWidth = font.width(level);
            // The name gives way first: a long one is cut with an ellipsis, while the level keeps its own width,
            // because a name that ate the level would hide the one thing the row is for.
            Component full = this.nameText(row);
            Component name = Component.literal(abbreviate(font, full.getString(),
                    Math.max(0, room - levelWidth - TECHNIQUE_NAME_GAP))).withStyle(full.getStyle());
            int nameWidth = font.width(name);
            if (nameWidth + TECHNIQUE_NAME_GAP + levelWidth > room) {
                level = Component.literal(abbreviate(font, level.getString(),
                        Math.max(0, room - nameWidth - TECHNIQUE_NAME_GAP)));
                levelWidth = font.width(level);
            }
            AuiElements.setText(cell.part(2), name.getString());
            AuiElements.setText(cell.part(3), levelWidth > 0 ? level.getString() : "");
            AuiElements.setText(cell.part(4), value.getString());
            AuiElements.style(cell.part(2), "color", styleColor(full, LABEL_COLOR));
            AuiElements.style(cell.part(3), "left", (textX + nameWidth + TECHNIQUE_NAME_GAP) + "px");
            AuiElements.style(cell.part(3), "color", AuiStyles.hex(row.hasLevel() ? MUTED_COLOR : UNKNOWN_COLOR));
            AuiElements.style(cell.part(4), "left", valueLeft + "px");
            AuiElements.style(cell.part(4), "color", AuiStyles.hex(MUTED_COLOR));
            int barWidth = Math.max(1, width - textX);
            AuiElements.style(cell.part(5), "left", textX + "px");
            AuiElements.style(cell.part(5), "top", TECHNIQUE_BAR_TOP + "px");
            AuiElements.style(cell.part(5), "width", barWidth + "px");
            int filled = (int) Math.round((barWidth - 2) * entry.progress().fraction());
            AuiElements.style(cell.part(6), "width", Math.max(0, Math.min(filled, barWidth - 2)) + "px");
            AuiElements.style(cell.part(6), "background-color", AuiStyles.hex(this.fillColor(row)));
            AuiElements.style(cell.part(7), "top", TECHNIQUE_SEPARATOR_TOP + "px");
            AuiElements.style(cell.part(7), "width", width + "px");
        }

        private void showIcon(Cell cell, Entry row) {
            Optional<IconReference> icon = row.technique().value().icon();
            ItemStack stack = icon.flatMap(IconReference::stack).orElse(ItemStack.EMPTY);
            String textureKey = icon.flatMap(IconReference::texture).map(Identifier::toString).orElse("");
            String key = stack.isEmpty() ? textureKey : stack.getItem() + "x" + stack.getCount();
            if (!key.equals(cell.iconKey())) {
                cell.iconKey(key);
                Item item = (Item) cell.part(0);
                if (stack.isEmpty()) item.clearDrivenState(Item.Source.INGREDIENT);
                else item.setIngredientStack(stack);
                if (textureKey.isEmpty()) cell.part(1).removeAttribute("src");
                else cell.part(1).setAttribute("src", textureKey);
            }
            cell.setClass("icon-item", !stack.isEmpty());
            cell.setClass("icon-texture", !textureKey.isEmpty());
        }

        // The technique's own name in its tier's colour; the caller cuts the text to the room it has.
        private Component nameText(Entry row) {
            Component name = DefinitionText.name(row.technique(), "technique");
            Optional<Holder<ItemQuality>> quality = row.technique().value().quality();
            return quality.map(holder -> ItemQualityService.coloredName(holder, name)).orElse(name);
        }

        // The level's own display name when the pack provides one, its rank otherwise.
        private Component levelText(Entry row) {
            Holder<Progression> level = row.level();
            if (!row.hasLevel() || level == null)
                return Component.translatable("screen.mxt.technique_panel.level_unknown");
            Component name = DefinitionText.name(level, "progression");
            Component label = DefinitionText.resolved(name)
                    ? name : Component.literal(Integer.toString(row.rank() + 1));
            return Component.translatable("screen.mxt.technique_panel.level", label, row.rank() + 1, row.total());
        }

        private Component valueText(TechniqueRow entry) {
            Entry row = entry.row();
            if (!row.hasLevel() || !row.hasMastery())
                return Component.translatable("screen.mxt.technique_panel.value_unknown");
            if (!row.hasNextLevel()) return Component.translatable("screen.mxt.technique_panel.value_max");
            return Component.translatable("screen.mxt.technique_panel.value",
                    format(entry.progress().done()), format(entry.progress().span()));
        }

        // Tinted with the mastery resource's own particle colour, like the crafting progress bar; the dark skin
        // needs it lifted or a dark tint vanishes into the track.
        private int fillColor(Entry row) {
            Holder<Resource> mastery = row.technique().value().masteryResource().orElse(null);
            return mastery == null ? BAR_FALLBACK_COLOR : 0xFF000000 | AuiStyles.readableOnDark(mastery.value().particleColor());
        }

        // A row has no room for the level id or the tier, so the tooltip spells both out. Its lines keep their own
        // colours here: the vanilla renderer draws the components, unlike a page element's flattened text.
        private List<Component> tooltipLines(TechniqueRow entry) {
            Entry row = entry.row();
            MutableComponent line = this.nameText(row).copy();
            if (row.hasLevel() && row.level() != null)
                line.append(" ").append(Component.literal(HolderHelper.id(row.level()).toString())
                        .withStyle(ChatFormatting.DARK_GRAY));
            return List.of(line, Component.translatable("screen.mxt.technique_panel.grade", this.gradeText(row))
                    .withStyle(ChatFormatting.GRAY));
        }

        // The tier's own name and colour. A technique that declares no tier says so, instead of printing a
        // free-form grade word no language file can be asked for.
        private Component gradeText(Entry row) {
            Optional<Holder<ItemQuality>> quality = row.technique().value().quality();
            return quality.isEmpty() ? Component.translatable("screen.mxt.technique_panel.grade_none")
                    : ItemQualityService.coloredName(quality.orElseThrow(), DefinitionText.name(quality.orElseThrow()));
        }
    }

    private static String styleColor(Component component, int fallback) {
        TextColor style = component.getStyle().getColor();
        return AuiStyles.hex(style == null ? fallback : style.getValue());
    }
}
