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
import com.iafenvoy.mxt.screen.AuiPages;
import com.iafenvoy.mxt.screen.AuiStyles;
import com.iafenvoy.mxt.screen.information.InformationCollector.InformationEntry;
import com.iafenvoy.mxt.screen.information.InformationHelper.Columns;
import com.iafenvoy.mxt.screen.information.InformationManager.Side;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.sighs.apricityui.client.gui.ApricityGuiLayers;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.screen.AuiLinkedScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
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
 * Each page's rows are DOM elements driven by {@link ScrollList}: the visible window is written on every
 * layout and the scroll offset stays a pixel value, like the vanilla lists both old screens used. Rows that
 * would fall outside the list are hidden instead of clipped, because clipping a box in ApricityUI paints a
 * dark artefact.
 */
public final class InformationPanelScreen extends Screen implements AuiLinkedScreen {
    public enum Page {
        INFO,
        TECHNIQUES
    }

    private static final int PANEL_WIDTH = 510;
    private static final int PANEL_HEIGHT = 315;
    private static final int PLAYER_RENDER_WIDTH = 120;
    private static final int PLAYER_RENDER_SCALE = 30;
    private static final int EQUIPMENT_SLOT_SIZE = 24;
    private static final int EQUIPMENT_SLOT_COUNT = 4;
    /**
     * The page's content margin: the equipment column and the 基本信息 list both start here. The page's own
     * defaults are the old screen's numbers, so Java has to reproduce them.
     */
    private static final int CONTENT_LEFT = 20;
    /**
     * Where the content starts, below the tab band: the tabs occupy y=10..25 (see information.css), so the
     * captions and the player frame begin at 36. The page's HTML defaults carry the same numbers.
     */
    private static final int CONTENT_TOP = 36;
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
    private static final int TECHNIQUE_ROWS = 16;
    private static final int TECHNIQUE_ROW_HEIGHT = 34;
    private static final int TECHNIQUE_COLUMNS = 2;
    private static final int TECHNIQUE_COLUMN_GAP = 10;
    private static final int TECHNIQUE_ICON_SIZE = 24;
    private static final int TECHNIQUE_ICON_GAP = 9;
    private static final int TECHNIQUE_NAME_GAP = 4;
    private static final int TECHNIQUE_BAR_TOP = 19;
    private static final int TECHNIQUE_SEPARATOR_TOP = TECHNIQUE_ROW_HEIGHT - 3;
    private static final int TAB_TOP = 10;
    private static final int TAB_LEFT = 18;
    private static final int TAB_HEIGHT = 15;
    private static final int TAB_GAP = 6;
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
    private static final int TECHNIQUE_TOP = TAB_TOP + TAB_HEIGHT + TAB_GAP;
    private static final int TECHNIQUE_EMPTY_TOP = TECHNIQUE_TOP + 6;
    private static final int LABEL_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFC0C0C0;
    private static final int UNKNOWN_COLOR = 0xFF8A8A8A;
    private static final int BAR_FALLBACK_COLOR = 0xFFCFCFCF;
    private static final EquipmentSlot[] EQUIPMENT_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    @Nullable
    private Document document;
    @Nullable
    private Component pageError;
    private final AuiPages.StyleHold styleHold = new AuiPages.StyleHold();
    private List<FormattedCharSequence> errorLines = List.of();
    private int errorWidth = -1;
    private long boundGeneration = Long.MIN_VALUE;

    @Nullable
    private Element panel, cultivationCaption, basicCaption, preview, tabInfo, tabTechnique, techniquesEmpty;
    private final List<Item> equipment = new ArrayList<>(EQUIPMENT_SLOT_COUNT);
    @Nullable
    private ScrollList<InformationEntry> cultivation, basic;
    @Nullable
    private ScrollList<TechniqueRow> techniques;
    @Nullable
    private TechniqueBinding techniqueBinding;
    private final Set<String> overflowReports = new HashSet<>();
    @Nullable
    private String selectedKey;
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
        super(Component.translatable("screen.mxt.information_panel"));
        this.page = page;
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
            boolean stylesPrepared = AuiPages.warmUpStyles(AuiPages.page(AuiPages.INFORMATION, "information"));
            this.document = Document.create(AuiPages.page(AuiPages.INFORMATION, "information"));
            if (this.document == null) {
                this.pageError = Component.translatable("screen.mxt.page.missing", "information");
                return;
            }
            this.styleHold.restart(stylesPrepared);
            this.rebind();
            return;
        }
        // A window resize re-enters init() with the same DOM, and ApricityUI appends listeners without ever
        // deduping them: rebinding would stack a second click on both tabs and on every row of both lists. The
        // panel geometry is recomputed every frame, and a document that was really rebuilt is caught by the
        // generation check in extractRenderState.
        this.document.applyViewport(true);
    }

    private void rebind() {
        this.clearBindings();
        this.pageError = null;
        Document current = this.document;
        if (current == null) return;
        if (!this.bindPage(current)) return;
        this.boundGeneration = current.getRefreshGeneration();
        this.refreshInformation();
    }

    private boolean bindPage(Document document) {
        Element panel = document.getElementById("panel");
        if (panel == null) return this.fail("panel");
        Element cultivationCaption = document.getElementById("cultivation_caption");
        if (cultivationCaption == null) return this.fail("cultivation_caption");
        Element basicCaption = document.getElementById("basic_caption");
        if (basicCaption == null) return this.fail("basic_caption");
        Element preview = document.getElementById("preview");
        if (preview == null) return this.fail("preview");
        Element tabInfo = document.getElementById("tab_info");
        if (tabInfo == null) return this.fail("tab_info");
        Element tabTechnique = document.getElementById("tab_technique");
        if (tabTechnique == null) return this.fail("tab_technique");
        Element techniquesEmpty = document.getElementById("techniques_empty");
        if (techniquesEmpty == null) return this.fail("techniques_empty");
        ScrollList<InformationEntry> cultivation =
                ScrollList.bind(document, "cult", "cultivation", 14, new InfoBinding());
        if (cultivation == null) return this.fail("cult_row-*");
        ScrollList<InformationEntry> basic =
                ScrollList.bind(document, "basic", "basic", 8, new InfoBinding());
        if (basic == null) return this.fail("basic_row-*");
        TechniqueBinding techniqueBinding = new TechniqueBinding();
        ScrollList<TechniqueRow> techniques =
                ScrollList.bind(document, "tech", "techniques", TECHNIQUE_ROWS, techniqueBinding);
        if (techniques == null) return this.fail("tech-*");
        for (int index = 0; index < EQUIPMENT_SLOT_COUNT; index++) {
            Element element = document.getElementById("equip-" + index);
            if (!(element instanceof Item item)) return this.fail("equip-" + index + " (item)");
            this.equipment.add(item);
        }
        this.panel = panel;
        this.cultivationCaption = cultivationCaption;
        this.basicCaption = basicCaption;
        this.preview = preview;
        this.tabInfo = tabInfo;
        this.tabTechnique = tabTechnique;
        this.techniquesEmpty = techniquesEmpty;
        this.cultivation = cultivation;
        this.basic = basic;
        this.techniques = techniques;
        this.techniqueBinding = techniqueBinding;
        this.text(cultivationCaption, Component.translatable("info.mxt.cultivation"));
        this.text(basicCaption, Component.translatable("info.mxt.basic"));
        this.text(tabInfo, Component.translatable("screen.mxt.information_panel.tab.info"));
        this.text(tabTechnique, Component.translatable("screen.mxt.technique_panel"));
        this.text(techniquesEmpty, Component.translatable("screen.mxt.technique_panel.empty"));
        tabInfo.addEventListener("click", event -> this.setPage(Page.INFO));
        tabTechnique.addEventListener("click", event -> this.setPage(Page.TECHNIQUES));
        // A row click is a local pick, as the old list's selection was.
        for (ScrollList<InformationEntry> list : List.of(cultivation, basic)) {
            for (Cell cell : list.cells) {
                cell.root.addEventListener("click", event -> this.select(list, cell));
            }
        }
        this.shownGeometry = Long.MIN_VALUE;
        this.shownPage = null;
        return true;
    }

    private boolean fail(String missing) {
        Document current = this.document;
        this.boundGeneration = current == null ? Long.MIN_VALUE : current.getRefreshGeneration();
        this.clearBindings();
        this.pageError = Component.translatable("screen.mxt.page.invalid", "information", missing);
        return false;
    }

    private void clearBindings() {
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

    private void select(ScrollList<InformationEntry> list, Cell cell) {
        if (cell.key == null) return;
        this.selectedKey = cell.key;
        for (ScrollList<InformationEntry> other : List.of(list, list == this.cultivation ? this.basic : this.cultivation)) {
            if (other != null) other.markSelected(this.selectedKey);
        }
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
        flag(this.panel, "page-technique", techniques);
        if (this.tabInfo != null) flag(this.tabInfo, "selected", !techniques);
        if (this.tabTechnique != null) flag(this.tabTechnique, "selected", techniques);
    }

    @Override
    public void tick() {
        super.tick();
        this.styleHold.tick();
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
        ScrollList<TechniqueRow> list = this.techniques;
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
            put(this.techniquesEmpty, "display", entries.isEmpty() ? "block" : "none");
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
            put(this.panel, "left", left + "px");
            put(this.panel, "top", top + "px");
            put(this.panel, "width", width + "px");
            put(this.panel, "height", height + "px");
            put(this.preview, "left", this.previewLeft + "px");
            put(this.preview, "width", renderWidth + "px");
            put(this.cultivationCaption, "left", rightX + "px");
            put(this.basicCaption, "top", basicTop + "px");
            if (this.cultivation != null) {
                put(this.cultivation.root, "left", rightX + "px");
                put(this.cultivation.root, "width", rightWidth + "px");
                put(this.cultivation.root, "height", rightHeight + "px");
            }
            if (this.basic != null) {
                put(this.basic.root, "top", basicListTop + "px");
                put(this.basic.root, "width", playerWidth + "px");
                put(this.basic.root, "height", basicHeight + "px");
            }
            if (this.techniques != null) {
                put(this.techniques.root, "top", TECHNIQUE_TOP + "px");
                put(this.techniques.root, "width", listWidth + "px");
                put(this.techniques.root, "height", techniqueHeight + "px");
            }
            if (this.techniquesEmpty != null)
                put(this.techniquesEmpty, "top", TECHNIQUE_EMPTY_TOP + "px");
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
        put(this.tabInfo, "left", TAB_LEFT + "px");
        put(this.tabInfo, "top", TAB_TOP + "px");
        put(this.tabInfo, "width", infoWidth + "px");
        put(this.tabInfo, "height", TAB_HEIGHT + "px");
        put(this.tabTechnique, "left", (TAB_LEFT + infoWidth + TAB_GAP) + "px");
        put(this.tabTechnique, "top", TAB_TOP + "px");
        put(this.tabTechnique, "width", techniqueWidth + "px");
        put(this.tabTechnique, "height", TAB_HEIGHT + "px");
    }

    private int tabWidth(String key) {
        int label = this.font.width(Component.translatable(key));
        return Math.max(24, label + TAB_PADDING);
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Nothing at all is drawn while the page's stylesheet is still in flight: without it there is no layout
        // to lay the panel, the rows and the equipment cells out on.
        if (this.pageError == null && this.styleHold.held()) return;
        Document current = this.document;
        if (current != null && current.getRefreshGeneration() != this.boundGeneration) {
            this.rebind();
        }
        if (this.page == Page.INFO) this.showEquipment();
        this.layout();
        ApricityGuiLayers.submitUi(graphics);
        if (this.page == Page.INFO) this.extractPlayer(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
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
        Position pointer = this.document == null ? new Position(mouseX, mouseY)
                : this.document.screenToDocumentPosition(new Position(mouseX, mouseY));
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
        ScrollList<TechniqueRow> list = this.techniques;
        Cell cell = list == null ? null : list.hoveredCell(mouseX, mouseY);
        if (cell == null || !(cell.entry instanceof TechniqueRow entry)) return;
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
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // No vanilla dim gradient, for the same reason as AuiContainerScreen: only the panel dims the world.
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
    protected void repositionElements() {
        super.repositionElements();
        this.shownGeometry = Long.MIN_VALUE;
        this.layout();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        ScrollList<?> target = null;
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

    @Override
    public void removed() {
        this.clearBindings();
        if (this.document != null) {
            this.document.remove();
            this.document = null;
        }
        super.removed();
    }

    private void text(@Nullable Element element, Component text) {
        if (element == null) return;
        String value = text.getString();
        if (!value.equals(element.getTextContent())) element.setTextContent(value);
    }

    private static void put(@Nullable Element element, String property, String value) {
        if (element == null) return;
        if (value.equals(element.getInlineStylePropertyValue(property))) return;
        element.setInlineStyleProperty(property, value);
    }

    private static void setText(Element element, String text) {
        if (text.equals(element.getTextContent())) return;
        element.setTextContent(text);
    }

    /**
     * Puts a row cell at its rectangle and remembers that rectangle: the hover hit test reads the remembered one,
     * so it can only ever agree with what the page is really drawing.
     */
    private static void place(Cell cell, int left, int top, int width) {
        cell.left = left;
        cell.top = top;
        cell.width = width;
        put(cell.root, "left", left + "px");
        put(cell.root, "top", top + "px");
        put(cell.root, "width", width + "px");
    }

    private static void flag(Element element, String token, boolean present) {
        if (element.getClassList().contains(token) == present) return;
        element.getClassList().toggle(token, present);
    }

    private static String color(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
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
     * One row element of a {@link ScrollList}: its root plus whatever elements its binding named.
     */
    private static final class Cell {
        private final Element root;
        private final Element[] parts;
        /**
         * The selection identity the binding derives; null while the row is hidden.
         */
        @Nullable
        private String key;
        /**
         * The entry currently shown, for tooltips that have to recompute from live state.
         */
        @Nullable
        private Object entry;
        /**
         * Icon signature, so a stack is only pushed into the DOM when it changed.
         */
        @Nullable
        private String iconKey;
        private boolean visible;
        /**
         * The rectangle the last layout wrote into the page, in the list's own coordinates: the hit test reads
         * these instead of re-deriving the grid, so a hover can only land on a row that is really drawn.
         */
        private int left;
        private int top;
        private int width;

        private Cell(Element root, Element[] parts) {
            this.root = root;
            this.parts = parts;
        }
    }

    /**
     * One scrollable list: a fixed set of row elements, the entries behind them and the pixel offset the
     * vanilla lists kept.
     */
    private static final class ScrollList<E> {
        private final Element root;
        private final List<Cell> cells;
        private final Binding<E> binding;
        private List<E> entries = List.of();
        private double scroll;
        private int width;
        private int height;
        /**
         * The identity the picked row carries; re-applied after every layout, because the entry behind a cell
         * changes when the list scrolls or its contents are rebuilt.
         */
        @Nullable
        private String selectedKey;
        /**
         * The width the binding's columns were last prepared for; only a change re-runs that pass.
         */
        private int preparedWidth;

        private ScrollList(Element root, List<Cell> cells, Binding<E> binding) {
            this.root = root;
            this.cells = cells;
            this.binding = binding;
        }

        @Nullable
        private static <E> ScrollList<E> bind(Document document, String prefix, String rootId, int count, Binding<E> binding) {
            Element root = document.getElementById(rootId);
            if (root == null) return null;
            List<Cell> cells = binding.bind(document, prefix, count);
            if (cells == null) return null;
            return new ScrollList<>(root, cells, binding);
        }

        private void rebuild(Font font, List<E> entries) {
            this.entries = entries;
            this.prepare(font, this.width);
            this.scroll = Math.clamp(this.scroll, 0.0D, this.maxScroll());
            this.layout(font, this.width, this.height);
        }

        /**
         * Prepares the binding's columns for one width. The first rebuild runs before the panel has written its
         * geometry, so its width is still zero there and the columns come out degenerate - the rows then draw no
         * text until the next rebuild, which the refresh interval puts a second away. {@link #layout} therefore
         * prepares again as soon as the real width arrives.
         */
        private void prepare(Font font, int width) {
            this.preparedWidth = width;
            this.binding.prepare(font, this.entries, width);
        }

        private int rowHeight() {
            return this.binding.rowHeight();
        }

        private int columns() {
            return Math.max(1, this.binding.columns());
        }

        private double maxScroll() {
            int rows = (this.entries.size() + this.columns() - 1) / this.columns();
            return Math.max(0, rows * this.rowHeight() + 4 - this.height);
        }

        private void scrollBy(double delta, Font font) {
            this.scroll = Math.clamp(this.scroll + delta, 0.0D, this.maxScroll());
            this.layout(font, this.width, this.height);
        }

        private void layout(Font font, int width, int height) {
            this.width = width;
            this.height = height;
            if (width != this.preparedWidth) this.prepare(font, width);
            this.scroll = Math.clamp(this.scroll, 0.0D, this.maxScroll());
            int rowHeight = this.rowHeight();
            int columns = this.columns();
            int visibleRows = Math.max(0, height / rowHeight);
            int firstRow = (int) (this.scroll / rowHeight);
            int offset = (int) Math.round(this.scroll) - firstRow * rowHeight;
            int gap = this.binding.columnGap();
            int cellWidth = Math.max(1, (Math.max(1, width) - gap * (columns - 1)) / columns);
            int first = firstRow * columns;
            for (int index = 0; index < this.cells.size(); index++) {
                Cell cell = this.cells.get(index);
                int entryIndex = first + index;
                if (entryIndex >= this.entries.size() || index >= visibleRows * columns) {
                    this.binding.clear(cell);
                    continue;
                }
                int left = index % columns * (cellWidth + gap);
                int top = index / columns * rowHeight - offset;
                this.binding.show(font, cell, this.entries.get(entryIndex), entryIndex, left, top, cellWidth);
            }
            // The highlight belongs to the entry, not to the row element: a scrolled or refreshed list moves
            // entries between cells, so which cell carries it is only known after the window was written.
            this.applySelection();
        }

        private void markSelected(@Nullable String key) {
            this.selectedKey = key;
            this.applySelection();
        }

        private void applySelection() {
            for (Cell cell : this.cells) {
                flag(cell.root, "selected", this.selectedKey != null && this.selectedKey.equals(cell.key));
            }
        }

        private boolean contains(double mouseX, double mouseY) {
            Document document = this.root.document;
            if (document == null) return false;
            // The pointer arrives in screen coordinates while element positions are document coordinates.
            Position pointer = document.screenToDocumentPosition(new Position(mouseX, mouseY));
            Position position = Position.of(this.root);
            return pointer.x >= position.x && pointer.x < position.x + this.width
                    && pointer.y >= position.y && pointer.y < position.y + this.height;
        }

        /**
         * The row cell under the pointer, or null. The pointer is converted the same way {@link #contains} does,
         * then measured against the rectangles the last layout wrote, so only a drawn row can be hit.
         */
        @Nullable
        private Cell hoveredCell(double mouseX, double mouseY) {
            Document document = this.root.document;
            if (document == null) return null;
            Position pointer = document.screenToDocumentPosition(new Position(mouseX, mouseY));
            Position position = Position.of(this.root);
            double localX = pointer.x - position.x;
            double localY = pointer.y - position.y;
            if (localX < 0 || localY < 0 || localX >= this.width || localY >= this.height) return null;
            for (Cell cell : this.cells) {
                if (!cell.visible || cell.entry == null) continue;
                if (localX >= cell.left && localX < cell.left + cell.width
                        && localY >= cell.top && localY < cell.top + this.rowHeight()) {
                    return cell;
                }
            }
            return null;
        }

        /**
         * What one kind of row shows: {@code bind} collects the elements it needs, {@code prepare} runs once
         * per rebuild (column widths, diagnostics) and {@code show} writes the visible window.
         */
        private interface Binding<E> {
            @Nullable
            List<Cell> bind(Document document, String prefix, int count);

            default void prepare(Font font, List<E> entries, int width) {
            }

            default int rowHeight() {
                return ROW_HEIGHT;
            }

            /**
             * The technique page lays its rows out in two columns; everything else is one.
             */
            default int columns() {
                return 1;
            }

            default int columnGap() {
                return TECHNIQUE_COLUMN_GAP;
            }

            void clear(Cell cell);

            void show(Font font, Cell cell, E entry, int index, int left, int top, int width);
        }
    }

    /**
     * The attribute rows: a name and a value, each cut to its own column.
     */
    private final class InfoBinding implements ScrollList.Binding<InformationEntry> {
        private Columns columns = new Columns(0, 1);

        @Override
        @Nullable
        public List<Cell> bind(Document document, String prefix, int count) {
            List<Cell> cells = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                Element row = document.getElementById(prefix + "_row-" + index);
                Element name = document.getElementById(prefix + "_name-" + index);
                Element value = document.getElementById(prefix + "_value-" + index);
                if (row == null || name == null || value == null) return null;
                cells.add(new Cell(row, new Element[]{name, value}));
            }
            return cells;
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
            cell.visible = false;
            cell.key = null;
            cell.entry = null;
            // Written for a cell that was never shown as well: the row elements are in the page from the start,
            // so anything they paint on their own would otherwise stay in the list as residue.
            put(cell.root, "display", "none");
        }

        @Override
        public void show(Font font, Cell cell, InformationEntry entry, int index, int left, int top, int width) {
            String fullName = entry.name() == null ? "" : entry.name().getString();
            String fullValue = entry.value().getString();
            cell.key = fullName + "=" + fullValue;
            cell.entry = entry;
            place(cell, left, top, width);
            if (!cell.visible) {
                cell.visible = true;
                put(cell.root, "display", "block");
            }
            // The value column belongs to the block (see prepare), not to this row, so a continuation row lines
            // up with the named ones instead of starting at the left edge.
            put(cell.parts[1], "left", (this.columns.nameWidth() + TEXT_INSET) + "px");
            setText(cell.parts[0], abbreviate(font, fullName, Math.max(1, this.columns.nameWidth())));
            setText(cell.parts[1], abbreviate(font, fullValue, Math.max(1, this.columns.valueWidth())));
            put(cell.parts[0], "color", color(entry.color()));
            put(cell.parts[1], "color", color(entry.color()));
        }
    }

    /**
     * The technique rows: icon, name in its tier's colour, level, mastery and the mastery bar.
     */
    private static final class TechniqueBinding implements ScrollList.Binding<TechniqueRow> {
        @Override
        @Nullable
        public List<Cell> bind(Document document, String prefix, int count) {
            List<Cell> cells = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                Element row = document.getElementById(prefix + "-" + index);
                Element item = document.getElementById(prefix + "_item-" + index);
                Element texture = document.getElementById(prefix + "_tex-" + index);
                Element name = document.getElementById(prefix + "_name-" + index);
                Element level = document.getElementById(prefix + "_level-" + index);
                Element value = document.getElementById(prefix + "_value-" + index);
                Element bar = document.getElementById(prefix + "_bar-" + index);
                Element fill = document.getElementById(prefix + "_fill-" + index);
                Element separator = document.getElementById(prefix + "_sep-" + index);
                if (row == null || !(item instanceof Item) || texture == null || name == null
                        || level == null || value == null || bar == null || fill == null || separator == null) {
                    return null;
                }
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
        public void clear(Cell cell) {
            cell.visible = false;
            cell.key = null;
            cell.entry = null;
            // A technique row paints an icon frame, a meter and a separator of its own (see information.css), so
            // unlike an attribute row it leaves visible residue when a never-shown cell is left in the DOM.
            put(cell.root, "display", "none");
        }

        @Override
        public void show(Font font, Cell cell, TechniqueRow entry, int index, int left, int top, int width) {
            Entry row = entry.row();
            cell.key = HolderHelper.id(row.technique()).toString();
            cell.entry = entry;
            place(cell, left, top, width);
            if (!cell.visible) {
                cell.visible = true;
                put(cell.root, "display", "block");
            }
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
            setText(cell.parts[2], name.getString());
            setText(cell.parts[3], levelWidth > 0 ? level.getString() : "");
            setText(cell.parts[4], value.getString());
            put(cell.parts[2], "color", styleColor(full, LABEL_COLOR));
            put(cell.parts[3], "left", (textX + nameWidth + TECHNIQUE_NAME_GAP) + "px");
            put(cell.parts[3], "color", color(row.hasLevel() ? MUTED_COLOR : UNKNOWN_COLOR));
            put(cell.parts[4], "left", valueLeft + "px");
            put(cell.parts[4], "color", color(MUTED_COLOR));
            int barWidth = Math.max(1, width - textX);
            put(cell.parts[5], "left", textX + "px");
            put(cell.parts[5], "top", TECHNIQUE_BAR_TOP + "px");
            put(cell.parts[5], "width", barWidth + "px");
            int filled = (int) Math.round((barWidth - 2) * entry.progress().fraction());
            put(cell.parts[6], "width", Math.max(0, Math.min(filled, barWidth - 2)) + "px");
            put(cell.parts[6], "background-color", color(this.fillColor(row)));
            put(cell.parts[7], "top", TECHNIQUE_SEPARATOR_TOP + "px");
            put(cell.parts[7], "width", width + "px");
        }

        private void showIcon(Cell cell, Entry row) {
            Optional<IconReference> icon = row.technique().value().icon();
            ItemStack stack = icon.flatMap(IconReference::stack).orElse(ItemStack.EMPTY);
            String textureKey = icon.flatMap(IconReference::texture).map(Identifier::toString).orElse("");
            String key = stack.isEmpty() ? textureKey : stack.getItem() + "x" + stack.getCount();
            if (!key.equals(cell.iconKey)) {
                cell.iconKey = key;
                Item item = (Item) cell.parts[0];
                if (stack.isEmpty()) item.clearDrivenState(Item.Source.INGREDIENT);
                else item.setIngredientStack(stack);
                if (textureKey.isEmpty()) cell.parts[1].removeAttribute("src");
                else cell.parts[1].setAttribute("src", textureKey);
            }
            flag(cell.root, "icon-item", !stack.isEmpty());
            flag(cell.root, "icon-texture", !textureKey.isEmpty());
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
        return color(style == null ? fallback : style.getValue());
    }
}
