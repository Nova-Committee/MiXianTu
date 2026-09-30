package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.artifact.ForgingResultComponent;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMaterial;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.network.payload.ForgingActionC2SPayload;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.forging.ForgingSurface;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.screen.AuiContainerScreen;
import com.iafenvoy.mxt.screen.AuiPages;
import com.iafenvoy.mxt.screen.menu.ForgingMenu;
import com.iafenvoy.mxt.util.TooltipText;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The forge table surface, drawn by one page. Everything the old screen painted with
 * {@code graphics.text/fill/blit} is now a DOM element the Java side only writes state into: the two
 * selector grids, the meter, the two step rows and the three action buttons.
 * <p>
 * The selector grids have no menu slots, so they stay Java hit-tested. The page carries the old
 * coordinates and this class reads {@link ForgingMenu}'s constants for the same rectangles - the two must
 * stay in step, which is why the page repeats them in comments next to each element.
 */
public final class ForgingScreen extends AuiContainerScreen<ForgingMenu> {
    private static final int PANEL_WIDTH = 322, PANEL_HEIGHT = 234;
    private static final int CELLS = ForgingMenu.CELLS;
    private static final int GRID_CELLS = CELLS * CELLS;
    private static final int STEPS = ForgingMenu.SUFFIX_STEPS;
    private static final int BLUEPRINT_X = ForgingMenu.BLUEPRINT_GRID_X;
    private static final int METHOD_X = ForgingMenu.METHOD_GRID_X;
    private static final int GRID_Y = ForgingMenu.GRID_Y;
    private static final int CELL = ForgingMenu.CELL;
    private static final int CELL_PITCH = ForgingMenu.CELL_PITCH;
    private static final int RECESS_Y = ForgingMenu.RECESS_Y;
    private static final int RECESS_W = ForgingMenu.RECESS_W;
    private static final int RECESS_H = ForgingMenu.RECESS_H;
    // The scroller's own numbers, kept from the vanilla stonecutter the old screen copied.
    private static final int SCROLLER_WIDTH = 12, SCROLLER_HEIGHT = 15;
    private static final int SCROLL_TOP = ForgingMenu.RECESS_Y;
    private static final int SCROLL_TRAVEL = 39;
    // Step rows: two rows of six 18px cells, the second one STEP_ROW_PITCH below the first.
    // The metre's interior; the frame drawn around it is one pixel wider on every side.
    private static final int METER_X = 80, METER_W = 162;
    private static final int METER_INNER_X = METER_X + 1;
    private static final int METER_INNER_W = METER_W - 2;
    // An unset target cell draws a barrier: a row is always six cells wide, so a hole would leave the
    // alignment to be guessed.
    private static final IconReference EMPTY_STEP =
            IconReference.item(ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.BARRIER)));

    private final List<IconBox> blueprintCells = new ArrayList<>(GRID_CELLS);
    private final List<IconBox> methodCells = new ArrayList<>(GRID_CELLS);
    private final List<IconBox> targetCells = new ArrayList<>(STEPS);
    private final List<IconBox> historyCells = new ArrayList<>(STEPS);
    @Nullable
    private Element blueprintBar, methodBar, meterBand, meterZero, meterValueMark, meterPredicted;
    @Nullable
    private Element meterValue, stepsText, qualityText, useBlueprint, useMethod, cancel;

    // Scroll offset as a fraction of the list, as the stonecutter keeps it: the first visible cell is derived.
    private float blueprintOffs, methodOffs;
    // Which scrollbar is being dragged: 0 none, 1 blueprint, 2 method.
    private int dragging;
    // Picks are ids, not list positions, because a position is a fact about a list that moves. A pick is
    // never sent on its own; only the buttons turn one into a request.
    @Nullable
    private Identifier selectedBlueprint, selectedMethod;

    // The last state written into the page; every write re-runs its style pass.
    private int hoveredBlueprint = -1, hoveredMethod = -1;
    // The scrollbar flags start "already applied" as disabled: a list short enough to need no scrolling is the
    // common case, and starting at false meant the first refresh saw no change and never dimmed the thumb.
    private boolean shownBlueprintScrollbar = true, shownMethodScrollbar = true;
    private int shownBlueprintScroller = Integer.MIN_VALUE, shownMethodScroller = Integer.MIN_VALUE;
    private int shownMeterValue = Integer.MIN_VALUE;
    private int shownSteps = Integer.MIN_VALUE;
    @Nullable
    private String shownQuality;
    private boolean blueprintReady, methodReady, cancelReady;
    private boolean shownBlueprintReady, shownMethodReady, shownCancelReady;

    public ForgingScreen(ForgingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    protected String pagePath() {
        return AuiPages.page(AuiPages.FORGING, "forging");
    }

    @Override
    protected String pageName() {
        return "forging";
    }

    @Override
    protected boolean bindPage(Document document) {
        Element panel = document.getElementById("panel");
        if (panel == null) return this.fail("panel");
        Element title = document.getElementById("title");
        if (title == null) return this.fail("title");
        Element inventoryLabel = document.getElementById("inventory_label");
        if (inventoryLabel == null) return this.fail("inventory_label");
        Element targetCaption = document.getElementById("target_caption");
        if (targetCaption == null) return this.fail("target_caption");
        Element currentCaption = document.getElementById("current_caption");
        if (currentCaption == null) return this.fail("current_caption");
        this.panel = panel;
        // Menu slot order: 3 blueprints, 3 tools, 12 inputs, the output, then the player inventory.
        if (!this.bindCells(document, "blueprints", ForgingSurface.BLUEPRINT_START)) return false;
        if (!this.bindCells(document, "tools", ForgingSurface.TOOL_START)) return false;
        if (!this.bindCells(document, "inputs", ForgingSurface.INPUT_START)) return false;
        if (!this.bindCells(document, "output", ForgingSurface.OUTPUT_SLOT)) return false;
        if (!this.bindInventoryCells(document, "inventory")) return false;
        if (!this.bindGrid(document, "bp", GRID_CELLS, this.blueprintCells)) return false;
        if (!this.bindGrid(document, "mt", GRID_CELLS, this.methodCells)) return false;
        if (!this.bindGrid(document, "target", STEPS, this.targetCells)) return false;
        if (!this.bindGrid(document, "current", STEPS, this.historyCells)) return false;
        Element blueprintBar = document.getElementById("bp_bar");
        if (blueprintBar == null) return this.fail("bp_bar");
        Element methodBar = document.getElementById("mt_bar");
        if (methodBar == null) return this.fail("mt_bar");
        Element meterBand = document.getElementById("meter_band");
        if (meterBand == null) return this.fail("meter_band");
        Element meterZero = document.getElementById("meter_zero");
        if (meterZero == null) return this.fail("meter_zero");
        Element meterValueMark = document.getElementById("meter_value_mark");
        if (meterValueMark == null) return this.fail("meter_value_mark");
        Element meterPredicted = document.getElementById("meter_predicted");
        if (meterPredicted == null) return this.fail("meter_predicted");
        Element meterValue = document.getElementById("meter_value");
        if (meterValue == null) return this.fail("meter_value");
        Element stepsText = document.getElementById("steps");
        if (stepsText == null) return this.fail("steps");
        Element qualityText = document.getElementById("quality");
        if (qualityText == null) return this.fail("quality");
        Element useBlueprint = document.getElementById("use_blueprint");
        if (useBlueprint == null) return this.fail("use_blueprint");
        Element useMethod = document.getElementById("use_method");
        if (useMethod == null) return this.fail("use_method");
        Element cancel = document.getElementById("cancel");
        if (cancel == null) return this.fail("cancel");
        this.blueprintBar = blueprintBar;
        this.methodBar = methodBar;
        this.meterBand = meterBand;
        this.meterZero = meterZero;
        this.meterValueMark = meterValueMark;
        this.meterPredicted = meterPredicted;
        this.meterValue = meterValue;
        this.stepsText = stepsText;
        this.qualityText = qualityText;
        this.useBlueprint = useBlueprint;
        this.useMethod = useMethod;
        this.cancel = cancel;
        this.click(useBlueprint, this::useBlueprint);
        this.click(useMethod, this::useMethod);
        this.click(cancel, this::cancel);
        // A cell draws an icon and nothing else, so its tooltip is the only place a method's name appears.
        for (int index = 0; index < GRID_CELLS; index++) {
            int cell = index;
            this.tooltip(this.blueprintCells.get(index).root, TOOLTIP_OPTIONS, () -> this.blueprintTooltip(cell));
            this.tooltip(this.methodCells.get(index).root, TOOLTIP_OPTIONS, () -> this.methodTooltip(cell));
        }
        for (int index = 0; index < STEPS; index++) {
            int position = index;
            this.tooltip(this.targetCells.get(index).root, TOOLTIP_OPTIONS, () -> this.stepTooltip(position, true));
            this.tooltip(this.historyCells.get(index).root, TOOLTIP_OPTIONS, () -> this.stepTooltip(position, false));
        }
        this.text(title, this.getTitle());
        this.text(inventoryLabel, Component.translatable("container.inventory"));
        this.text(targetCaption, Component.translatable("screen.mxt.forging.target"));
        this.text(currentCaption, Component.translatable("screen.mxt.forging.current"));
        return true;
    }

    /**
     * A grid of icon boxes: {@code prefix-N} is the cell, {@code prefix_item-N} and {@code prefix_tex-N}
     * its two icon elements.
     */
    private boolean bindGrid(Document document, String prefix, int count, List<IconBox> target) {
        List<Element> roots = this.byIdPrefix(document, prefix + "-", count);
        if (roots == null) return this.fail(prefix + "-*");
        List<Element> items = this.byIdPrefix(document, prefix + "_item-", count);
        if (items == null) return this.fail(prefix + "_item-*");
        List<Element> textures = this.byIdPrefix(document, prefix + "_tex-", count);
        if (textures == null) return this.fail(prefix + "_tex-*");
        for (int index = 0; index < count; index++) {
            Element item = items.get(index);
            if (!(item instanceof Item icon)) return this.fail(prefix + "_item-" + index + " (item)");
            target.add(new IconBox(roots.get(index), icon, textures.get(index)));
        }
        return true;
    }

    @Override
    protected void onBindingsCleared() {
        this.blueprintCells.clear();
        this.methodCells.clear();
        this.targetCells.clear();
        this.historyCells.clear();
        this.blueprintBar = null;
        this.methodBar = null;
        this.meterBand = null;
        this.meterZero = null;
        this.meterValueMark = null;
        this.meterPredicted = null;
        this.meterValue = null;
        this.stepsText = null;
        this.qualityText = null;
        this.useBlueprint = null;
        this.useMethod = null;
        this.cancel = null;
        this.hoveredBlueprint = -1;
        this.hoveredMethod = -1;
        this.shownBlueprintScrollbar = false;
        this.shownMethodScrollbar = false;
        this.shownBlueprintScroller = Integer.MIN_VALUE;
        this.shownMethodScroller = Integer.MIN_VALUE;
        this.shownMeterValue = Integer.MIN_VALUE;
        this.shownSteps = Integer.MIN_VALUE;
        this.shownQuality = null;
        this.shownBlueprintReady = false;
        this.shownMethodReady = false;
        this.shownCancelReady = false;
    }

    @Override
    protected void onPageBound() {
        this.refresh();
    }

    // ------------------------------------------------------------------ state

    // A pick is a local click rather than a packet, so the buttons and the grids are re-derived every tick.
    @Override
    protected void refresh() {
        List<Entry> blueprints = this.blueprintEntries();
        List<Entry> methods = this.methodEntries();
        Identifier pickedBlueprint = this.picked(blueprints, this.selectedBlueprint);
        Identifier pickedMethod = this.picked(methods, this.selectedMethod);
        this.showGrid(this.blueprintCells, blueprints, this.blueprintOffs, pickedBlueprint);
        this.showGrid(this.methodCells, methods, this.methodOffs, pickedMethod);
        this.showScrollbar(true, this.blueprintBar, this.blueprintOffs, blueprints.size());
        this.showScrollbar(false, this.methodBar, this.methodOffs, methods.size());
        this.showSteps(this.targetCells, true);
        this.showSteps(this.historyCells, false);
        this.showMeter(pickedBlueprint, this.deltaOf(pickedMethod));
        this.showReadouts();
    }

    private void showGrid(List<IconBox> cells, List<Entry> entries, float offs, @Nullable Identifier selected) {
        int start = this.startIndex(offs, entries.size());
        for (int slot = 0; slot < cells.size(); slot++) {
            IconBox cell = cells.get(slot);
            int index = start + slot;
            Entry entry = index < entries.size() ? entries.get(index) : null;
            if (entry == null) {
                cell.clear();
                flag(cell.root, "selected", false);
                continue;
            }
            cell.show(entry.icon());
            flag(cell.root, "selected", entry.id().equals(selected));
        }
    }

    private void showScrollbar(boolean blueprint, @Nullable Element bar, float offs, int entries) {
        if (bar == null) return;
        boolean active = this.isScrollBarActive(entries);
        // Page coordinates: the thumb is a child of the panel, not of the track, so this is the same space as
        // the CSS default (top: 18px) and as the drag maths below. Writing it track-relative put both thumbs on
        // the panel's top edge, beside the title, instead of on the track beside their grid.
        int top = SCROLL_TOP + 1 + Math.round(SCROLL_TRAVEL * offs);
        if (blueprint) {
            if (active != this.shownBlueprintScrollbar) {
                flag(bar, "disabled", !active);
                this.shownBlueprintScrollbar = active;
            }
            if (top == this.shownBlueprintScroller) return;
            this.shownBlueprintScroller = top;
        } else {
            if (active != this.shownMethodScrollbar) {
                flag(bar, "disabled", !active);
                this.shownMethodScrollbar = active;
            }
            if (top == this.shownMethodScroller) return;
            this.shownMethodScroller = top;
        }
        put(bar, "top", top + "px");
    }

    private void showSteps(List<IconBox> cells, boolean target) {
        List<IconReference> icons = this.stepIcons(target);
        for (int position = 0; position < cells.size(); position++) {
            IconBox cell = cells.get(position);
            IconReference icon = position < icons.size() ? icons.get(position) : null;
            // Only the target row fills its empty cells; the history row leaves them blank.
            if (icon == null && !target) {
                cell.clear();
                continue;
            }
            cell.show(icon == null ? EMPTY_STEP : icon);
        }
    }

    private void showMeter(@Nullable Identifier pickedBlueprint, @Nullable Integer predictedDelta) {
        MeterScale scale = this.scale(pickedBlueprint);
        if (this.meterBand == null) return;
        if (scale == null) {
            this.hideMeter();
            return;
        }
        boolean active = this.menu.active();
        if (active) {
            int from = this.meterX(scale, this.menu.targetMin());
            int to = this.meterX(scale, this.menu.targetMax());
            put(this.meterBand, "left", from + "px");
            put(this.meterBand, "width", (to - from + 1) + "px");
            put(this.meterBand, "display", "block");
        } else {
            put(this.meterBand, "display", "none");
        }
        // Every mark is positioned in the panel's own coordinates: the scale's left end is the trough's interior.
        this.showMark(this.meterZero, true, this.meterX(scale, 0));
        boolean predicted = active && predictedDelta != null;
        this.showMark(this.meterPredicted, predicted,
                predicted ? this.meterX(scale, this.menu.meterValue() + predictedDelta) : 0);
        this.showMark(this.meterValueMark, active, active ? this.meterX(scale, this.menu.meterValue()) : 0);
    }

    private void showMark(@Nullable Element mark, boolean visible, int left) {
        if (mark == null) return;
        put(mark, "display", visible ? "block" : "none");
        if (visible) put(mark, "left", left + "px");
    }

    private void hideMeter() {
        for (Element mark : new Element[]{this.meterBand, this.meterZero, this.meterValueMark, this.meterPredicted}) {
            put(mark, "display", "none");
        }
    }

    private void showReadouts() {
        if (this.meterValue != null && this.menu.meterValue() != this.shownMeterValue) {
            this.shownMeterValue = this.menu.meterValue();
            this.text(this.meterValue, Component.translatable("screen.mxt.forging.meter.value", this.shownMeterValue));
        }
        if (this.stepsText != null && this.menu.steps() != this.shownSteps) {
            this.shownSteps = this.menu.steps();
            this.text(this.stepsText, Component.translatable("screen.mxt.forging.steps", this.shownSteps));
        }
        // What the piece came out as is the server's own answer and only exists once the result is in the
        // output slot, so nothing here predicts it: the line appears with the finished piece and goes with it.
        ForgingResultComponent result =
                this.menu.getSlot(ForgingSurface.OUTPUT_SLOT).getItem().get(MxtDataComponents.FORGING_RESULT);
        if (result == null) {
            this.showQuality("", "#FFFFFF");
            return;
        }
        Component name = ItemQualityService.coloredName(result.quality(), result.quality().value().name());
        TextColor color = name.getStyle().getColor();
        this.showQuality(Component.translatable("screen.mxt.forging.quality", name).getString(),
                color == null ? "#FFFFFF" : String.format("#%06X", color.getValue()));
    }

    /**
     * The tier's own colour covers the whole line: the page draws one text node, so the rich component the
     * old screen painted is flattened, and the tier colour is the part worth keeping.
     */
    private void showQuality(String text, String color) {
        if (text.equals(this.shownQuality)) return;
        this.shownQuality = text;
        if (this.qualityText == null) return;
        this.qualityText.setTextContent(text);
        put(this.qualityText, "color", color);
    }

    // A button is enabled exactly when pressing it would name something the server accepts: a blueprint needs
    // a live pick, no running session and its materials; a method needs a live pick and a running session.
    private void refreshButtons() {
        Identifier blueprint = this.picked(this.blueprintEntries(), this.selectedBlueprint);
        this.blueprintReady = !this.menu.active() && blueprint != null && this.menu.materialsCovered(blueprint);
        this.methodReady = this.menu.active() && this.picked(this.methodEntries(), this.selectedMethod) != null;
        // With no session nothing is locked, so there is nothing to cancel.
        this.cancelReady = this.menu.active();
        if (this.blueprintReady != this.shownBlueprintReady) {
            this.shownBlueprintReady = this.blueprintReady;
            if (this.useBlueprint != null) this.useBlueprint.setDisabled(!this.blueprintReady);
        }
        if (this.methodReady != this.shownMethodReady) {
            this.shownMethodReady = this.methodReady;
            if (this.useMethod != null) this.useMethod.setDisabled(!this.methodReady);
        }
        if (this.cancelReady != this.shownCancelReady) {
            this.shownCancelReady = this.cancelReady;
            if (this.cancel != null) this.cancel.setDisabled(!this.cancelReady);
        }
    }

    // ------------------------------------------------------------------ buttons

    private void useBlueprint() {
        // A disabled div still receives its click listener, so the same rule the old button's active flag held
        // is checked here before anything is sent.
        if (!this.blueprintReady) return;
        Identifier id = this.picked(this.blueprintEntries(), this.selectedBlueprint);
        if (id == null) return;
        ClientPacketDistributor.sendToServer(ForgingActionC2SPayload.select(id));
    }

    private void useMethod() {
        if (!this.methodReady) return;
        Identifier id = this.picked(this.methodEntries(), this.selectedMethod);
        if (id == null) return;
        ClientPacketDistributor.sendToServer(ForgingActionC2SPayload.strike(id));
    }

    // The cancel payload names nothing: there is one session per table, resolved from the open menu.
    private void cancel() {
        if (!this.cancelReady) return;
        ClientPacketDistributor.sendToServer(ForgingActionC2SPayload.cancel());
    }

    // ------------------------------------------------------------------ lists

    // A pick outlives the entry it names, so it is never read raw: this is the one place that question is
    // asked, for both the page state and the presses.
    @Nullable
    private Identifier picked(List<Entry> entries, @Nullable Identifier id) {
        if (id == null) return null;
        for (Entry entry : entries)
            if (entry.id().equals(id)) return id;
        return null;
    }

    private record Entry(Identifier id, @Nullable IconReference icon) {
    }

    // Every offered id yields an entry even with no resolvable icon: dropping one would shift every later position.
    private List<Entry> blueprintEntries() {
        if (this.minecraft == null || this.minecraft.level == null) return List.of();
        List<Entry> entries = new ArrayList<>();
        for (Identifier id : this.menu.blueprints()) {
            IconReference icon = MxtDatapackRegistries.get(this.minecraft.level.registryAccess(),
                            MxtResourceKeys.FORGING_BLUEPRINT, id)
                    .flatMap(blueprint -> BuiltInRegistries.ITEM.getOptional(blueprint.result()))
                    .map(ItemStack::new).flatMap(IconReference::of).orElse(null);
            entries.add(new Entry(id, icon));
        }
        return entries;
    }

    // The blueprint pick is re-resolved first: a manual taken out of its slot removes its blueprint from the
    // list, and a pick no longer offered must restrict nothing.
    private List<Entry> methodEntries() {
        if (this.minecraft == null || this.minecraft.level == null) return List.of();
        Identifier blueprint = this.picked(this.blueprintEntries(), this.selectedBlueprint);
        List<Entry> entries = new ArrayList<>();
        for (Identifier id : this.menu.methods(blueprint)) {
            IconReference icon = MxtDatapackRegistries.get(this.minecraft.level.registryAccess(),
                            MxtResourceKeys.FORGING_METHOD, id)
                    .flatMap(ForgingMethod::icon).orElse(null);
            entries.add(new Entry(id, icon));
        }
        return entries;
    }

    private List<IconReference> stepIcons(boolean target) {
        List<IconReference> icons = new ArrayList<>(STEPS);
        boolean usable = this.menu.active() && this.minecraft != null && this.minecraft.level != null;
        Registry<ForgingMethod> registry = usable
                ? this.minecraft.level.registryAccess().lookupOrThrow(MxtResourceKeys.FORGING_METHOD) : null;
        for (int position = 0; position < STEPS; position++) {
            if (!usable) {
                icons.add(null);
                continue;
            }
            int id = target ? this.menu.targetStep(position) : this.menu.historyStep(position);
            icons.add(ForgingMenu.isNone(id) ? null
                    : registry.get(id).flatMap(holder -> holder.value().icon()).orElse(null));
        }
        return icons;
    }

    // The method's own value_delta rather than a session table: the yellow line is drawn before the strike.
    @Nullable
    private Integer deltaOf(@Nullable Identifier method) {
        if (method == null || this.minecraft == null || this.minecraft.level == null) return null;
        return MxtDatapackRegistries.get(this.minecraft.level.registryAccess(), MxtResourceKeys.FORGING_METHOD, method)
                .map(ForgingMethod::valueDelta).orElse(null);
    }

    // ------------------------------------------------------------------ tooltips

    private String blueprintTooltip(int cell) {
        if (this.minecraft == null || this.minecraft.level == null) return "";
        Entry entry = this.cellEntry(this.blueprintCells, cell, this.blueprintEntries(), this.blueprintOffs);
        if (entry == null) return "";
        ForgingBlueprint blueprint = this.menu.blueprint(entry.id());
        if (blueprint == null) return "";
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip.mxt.forging.materials").withStyle(ChatFormatting.GOLD));
        boolean covered = true;
        for (ForgingMaterial requirement : blueprint.input()) {
            int have = this.menu.inputCount(requirement);
            boolean met = have >= requirement.count();
            covered &= met;
            lines.add(Component.literal(met ? "✔ " : "✖ ")
                    .append(Component.translatable("tooltip.mxt.forging.materials.line",
                            new ItemStack(requirement.item()).getHoverName(), have, requirement.count()))
                    .withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        if (!covered)
            lines.add(Component.translatable("tooltip.mxt.forging.materials.insufficient").withStyle(ChatFormatting.DARK_GRAY));
        // A blueprint with a step limit can be failed by taking too many strikes, and nothing else on this
        // screen says so.
        if (blueprint.hasStepLimit())
            lines.add(Component.translatable("tooltip.mxt.forging.step_limit", blueprint.maxSteps()).withStyle(ChatFormatting.GRAY));
        // The whole ladder, in the order the settlement reads it, so the strike count is worth something before a
        // run is committed to. Each row is named in its own tier's colour.
        lines.add(Component.translatable("tooltip.mxt.forging.quality_tiers").withStyle(ChatFormatting.GOLD));
        for (ForgingBlueprint.QualityThreshold tier : blueprint.qualityByExtraSteps()) {
            Component name = ItemQualityService.coloredName(tier.quality(), tier.quality().value().name());
            lines.add(tier.maxExtraSteps() == Integer.MAX_VALUE
                    ? Component.translatable("tooltip.mxt.forging.quality_tier.unbounded", name)
                    : Component.translatable("tooltip.mxt.forging.quality_tier", name, tier.maxExtraSteps()));
        }
        return join(lines);
    }

    private String methodTooltip(int cell) {
        if (this.minecraft == null || this.minecraft.level == null) return "";
        Entry entry = this.cellEntry(this.methodCells, cell, this.methodEntries(), this.methodOffs);
        if (entry == null) return "";
        ForgingMethod method = MxtDatapackRegistries.get(this.minecraft.level.registryAccess(),
                MxtResourceKeys.FORGING_METHOD, entry.id()).orElse(null);
        if (method == null) return "";
        List<Component> lines = new ArrayList<>();
        lines.add(method.displayName(entry.id()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("tooltip.mxt.forging.method.delta", TooltipText.signed(method.valueDelta()))
                .withStyle(ChatFormatting.BLUE));
        return join(lines);
    }

    private String stepTooltip(int position, boolean target) {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null) return "";
        int registryId = target ? this.menu.targetStep(position) : this.menu.historyStep(position);
        if (ForgingMenu.isNone(registryId)) return "";
        Identifier methodId = ForgingMenu.methodId(this.minecraft.player, registryId);
        ForgingMethod method = methodId == null ? null
                : MxtDatapackRegistries.get(this.minecraft.level.registryAccess(), MxtResourceKeys.FORGING_METHOD, methodId).orElse(null);
        if (method == null) return "";
        List<Component> lines = new ArrayList<>();
        lines.add(method.displayName(methodId).withStyle(ChatFormatting.GOLD));
        // The id, exactly as the item tooltips show it: for a datapack author, and only when they asked.
        if (this.minecraft.options.advancedItemTooltips)
            lines.add(Component.literal(methodId.toString()).withStyle(ChatFormatting.DARK_GRAY));
        return join(lines);
    }

    @Nullable
    private Entry cellEntry(List<IconBox> cells, int cell, List<Entry> entries, float offs) {
        if (cell < 0 || cell >= cells.size()) return null;
        int index = this.startIndex(offs, entries.size()) + cell;
        return index < entries.size() ? entries.get(index) : null;
    }

    private static String join(List<Component> lines) {
        StringBuilder builder = new StringBuilder();
        for (Component line : lines) {
            if (!builder.isEmpty()) builder.append('\n');
            builder.append(line.getString());
        }
        return builder.toString();
    }

    // ------------------------------------------------------------------ geometry

    private int startIndex(float offs, int entries) {
        return (int) (offs * this.getOffscreenRows(entries) + 0.5D) * CELLS;
    }

    private int getOffscreenRows(int entries) {
        return Math.max(0, (entries + CELLS - 1) / CELLS - CELLS);
    }

    private boolean isScrollBarActive(int entries) {
        return entries > GRID_CELLS;
    }

    private record MeterScale(int min, int max) {
    }

    // A running session supplies its bounds from the plan snapshotted at start, so a datapack reload cannot
    // move a bar being read.
    @Nullable
    private MeterScale scale(@Nullable Identifier pickedBlueprint) {
        if (!this.menu.active()) {
            ForgingBlueprint blueprint = this.menu.blueprint(pickedBlueprint);
            if (blueprint == null) return null;
            return new MeterScale(blueprint.meter().min(), blueprint.meter().max());
        }
        MeterScale running = new MeterScale(this.menu.meterMin(), this.menu.meterMax());
        return running.max() > running.min() ? running : null;
    }

    // The interior's last column belongs to the scale maximum, so the span is one pixel short of the interior.
    private int meterX(MeterScale scale, int value) {
        int span = scale.max() - scale.min();
        if (span <= 0) return METER_INNER_X;
        float fraction = Mth.clamp((value - scale.min()) / (float) span, 0.0F, 1.0F);
        return METER_INNER_X + Math.round(fraction * (METER_INNER_W - 1));
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.updateHover(mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * The grids have no menu slots, so their hover is Java state written as a class; only a change is written.
     */
    private void updateHover(double mouseX, double mouseY) {
        if (!this.slotsDrawn()) return;
        int blueprint = this.hoveredCell(mouseX, mouseY, BLUEPRINT_X);
        if (blueprint != this.hoveredBlueprint) {
            this.hoveredBlueprint = blueprint;
            for (int index = 0; index < this.blueprintCells.size(); index++) {
                flag(this.blueprintCells.get(index).root, "hover", index == blueprint);
            }
        }
        int method = this.hoveredCell(mouseX, mouseY, METHOD_X);
        if (method != this.hoveredMethod) {
            this.hoveredMethod = method;
            for (int index = 0; index < this.methodCells.size(); index++) {
                flag(this.methodCells.get(index).root, "hover", index == method);
            }
        }
    }

    private int hoveredCell(double mouseX, double mouseY, int gridX) {
        for (int slot = 0; slot < GRID_CELLS; slot++) {
            int x = gridX + slot % CELLS * CELL_PITCH;
            int y = GRID_Y + slot / CELLS * CELL_PITCH;
            if (this.inRect(mouseX, mouseY, x, y, CELL, CELL)) return slot;
        }
        return -1;
    }

    private boolean inRect(double mouseX, double mouseY, int x, int y, int width, int height) {
        double left = this.panelLeft + x, top = this.panelTop + y;
        return mouseX >= left && mouseX < left + width && mouseY >= top && mouseY < top + height;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (!this.slotsDrawn()) return true;
        if (this.scrollbarClicked(event.x(), event.y())) return true;
        if (this.selectorClicked(event.x(), event.y())) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.dragging != 0) {
            this.dragScrollbar(event.y());
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        this.dragging = 0;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.overGrid(mouseX, mouseY, BLUEPRINT_X)
                && this.isScrollBarActive(this.blueprintEntries().size())) {
            this.blueprintOffs = this.scrolled(this.blueprintOffs, scrollY, this.blueprintEntries().size());
            this.refresh();
            return true;
        }
        if (this.overGrid(mouseX, mouseY, METHOD_X)
                && this.isScrollBarActive(this.methodEntries().size())) {
            this.methodOffs = this.scrolled(this.methodOffs, scrollY, this.methodEntries().size());
            this.refresh();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private float scrolled(float offs, double delta, int entries) {
        int rows = this.getOffscreenRows(entries);
        if (rows <= 0) return offs;
        return Mth.clamp(offs - (float) delta / (float) rows, 0.0F, 1.0F);
    }

    // The whole recess scrolls, gutter included, hence the one pixel back-off by the grid inset.
    private boolean overGrid(double mouseX, double mouseY, int gridX) {
        return this.inRect(mouseX, mouseY, gridX - 1, RECESS_Y, RECESS_W + 1, RECESS_H);
    }

    private boolean scrollbarClicked(double mouseX, double mouseY) {
        if (this.inBar(mouseX, mouseY, ForgingMenu.SCROLLBAR_X)
                && this.isScrollBarActive(this.blueprintEntries().size())) {
            this.dragging = 1;
            this.dragScrollbar(mouseY);
            return true;
        }
        if (this.inBar(mouseX, mouseY, ForgingMenu.SCROLLBAR_X_RIGHT)
                && this.isScrollBarActive(this.methodEntries().size())) {
            this.dragging = 2;
            this.dragScrollbar(mouseY);
            return true;
        }
        return false;
    }

    // The same rectangle the scroller is drawn into: only its own column counts, not the whole track.
    private boolean inBar(double mouseX, double mouseY, int barX) {
        return this.inRect(mouseX, mouseY, barX + 1, SCROLL_TOP, SCROLLER_WIDTH, SCROLLER_HEIGHT + SCROLL_TRAVEL);
    }

    // The stonecutter's drag maths unchanged: the scroller's centre follows the cursor over SCROLL_TRAVEL.
    private void dragScrollbar(double mouseY) {
        if (SCROLL_TRAVEL <= 0) return;
        double top = this.panelTop + SCROLL_TOP;
        float offs = (float) (Mth.clamp((float) mouseY - top - SCROLLER_HEIGHT / 2.0F, 0.0F, (float) SCROLL_TRAVEL)
                / (float) SCROLL_TRAVEL);
        if (this.dragging == 1) this.blueprintOffs = offs;
        else if (this.dragging == 2) this.methodOffs = offs;
        this.refresh();
    }

    // Picking is local: nothing is sent, nothing can fail, and the two grids do not consult each other.
    private boolean selectorClicked(double mouseX, double mouseY) {
        if (this.clickCell(mouseX, mouseY, BLUEPRINT_X, this.blueprintOffs, this.blueprintEntries(), true)) return true;
        return this.clickCell(mouseX, mouseY, METHOD_X, this.methodOffs, this.methodEntries(), false);
    }

    // The blueprint pick is frozen while a session runs, since the table has locked a blueprint; the method
    // pick stays live.
    private boolean clickCell(double mouseX, double mouseY, int gridX, float offs, List<Entry> entries, boolean blueprint) {
        int slot = this.hoveredCell(mouseX, mouseY, gridX);
        if (slot < 0) return false;
        int index = this.startIndex(offs, entries.size()) + slot;
        if (index >= entries.size()) return false;
        Entry entry = entries.get(index);
        if (blueprint) {
            if (!this.menu.active()) this.selectedBlueprint = entry.id();
        } else {
            this.selectedMethod = entry.id();
        }
        this.refresh();
        return true;
    }

    /**
     * One icon box: an 18px cell that draws whichever of its two icon elements the entry resolved to.
     */
    private static final class IconBox {
        private final Element root;
        private final Item item;
        private final Element texture;
        private ItemStack shownStack = ItemStack.EMPTY;
        private String shownTexture = "";
        private boolean shownEmpty = true;

        private IconBox(Element root, Item item, Element texture) {
            this.root = root;
            this.item = item;
            this.texture = texture;
        }

        private void show(@Nullable IconReference icon) {
            ItemStack stack = icon == null ? ItemStack.EMPTY : icon.stack().orElse(ItemStack.EMPTY);
            if (!ItemStack.matches(stack, this.shownStack)) {
                if (stack.isEmpty()) this.item.clearDrivenState(Item.Source.INGREDIENT);
                else this.item.setIngredientStack(stack);
                this.shownStack = stack;
            }
            String textureKey = icon == null ? "" : icon.texture().map(Identifier::toString).orElse("");
            if (!textureKey.equals(this.shownTexture)) {
                if (textureKey.isEmpty()) this.texture.removeAttribute("src");
                else this.texture.setAttribute("src", textureKey);
                this.shownTexture = textureKey;
            }
            flag(this.root, "icon-item", !stack.isEmpty());
            flag(this.root, "icon-texture", !textureKey.isEmpty());
            if (!this.shownEmpty) return;
            flag(this.root, "empty", false);
            this.shownEmpty = false;
        }

        private void clear() {
            if (!this.shownStack.isEmpty()) {
                this.item.clearDrivenState(Item.Source.INGREDIENT);
                this.shownStack = ItemStack.EMPTY;
            }
            if (!this.shownTexture.isEmpty()) {
                this.texture.removeAttribute("src");
                this.shownTexture = "";
            }
            flag(this.root, "icon-item", false);
            flag(this.root, "icon-texture", false);
            if (this.shownEmpty) return;
            flag(this.root, "empty", true);
            this.shownEmpty = true;
        }
    }

    private static void flag(Element element, String token, boolean present) {
        if (element.getClassList().contains(token) == present) return;
        element.getClassList().toggle(token, present);
    }

    /**
     * {@code style} is already a static helper on the host class, and it is protected there.
     */
    private static void put(@Nullable Element element, String property, String value) {
        if (element == null) return;
        if (value.equals(element.getInlineStylePropertyValue(property))) return;
        element.setInlineStyleProperty(property, value);
    }
}
