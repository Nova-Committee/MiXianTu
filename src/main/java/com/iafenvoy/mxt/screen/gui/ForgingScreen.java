package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.network.payload.ForgingActionC2SPayload;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.forging.ForgingSurface;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.mxt.screen.aui.AuiContainerScreen;
import com.iafenvoy.mxt.screen.aui.AuiElements;
import com.iafenvoy.mxt.screen.menu.ForgingMenu;
import com.iafenvoy.mxt.util.TooltipText;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.init.Element;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
    private static final int CELLS = ForgingMenu.CELLS, GRID_CELLS = CELLS * CELLS;
    private static final int STEPS = ForgingMenu.SUFFIX_STEPS;
    private static final int BLUEPRINT_X = ForgingMenu.BLUEPRINT_GRID_X;
    private static final int METHOD_X = ForgingMenu.METHOD_GRID_X;
    private static final int GRID_Y = ForgingMenu.GRID_Y;
    private static final int CELL = ForgingMenu.CELL, CELL_PITCH = ForgingMenu.CELL_PITCH;
    private static final int RECESS_Y = ForgingMenu.RECESS_Y, RECESS_W = ForgingMenu.RECESS_W, RECESS_H = ForgingMenu.RECESS_H;
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
    private static final IconReference EMPTY_STEP = IconReference.item(ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.BARRIER)));

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
    // The cell the pointer is over; the page is told only when that changes.
    private int hoveredBlueprint = -1, hoveredMethod = -1;
    // Whether a press would name something the server accepts, shown as the button's disabled attribute. Nothing
    // else is remembered about the page: AuiElements compares every write with the element it writes, so a second
    // copy of "what the page was last told" is only a chance to disagree with it.
    private boolean blueprintReady, methodReady, cancelReady;

    public ForgingScreen(ForgingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void bindPage() {
        this.panel = this.getOrThrow("panel");
        this.blueprintBar = this.getOrThrow("bp_bar");
        this.methodBar = this.getOrThrow("mt_bar");
        this.meterBand = this.getOrThrow("meter_band");
        this.meterZero = this.getOrThrow("meter_zero");
        this.meterValueMark = this.getOrThrow("meter_value_mark");
        this.meterPredicted = this.getOrThrow("meter_predicted");
        this.meterValue = this.getOrThrow("meter_value");
        this.stepsText = this.getOrThrow("steps");
        this.qualityText = this.getOrThrow("quality");
        this.useBlueprint = this.getOrThrow("use_blueprint");
        this.useMethod = this.getOrThrow("use_method");
        this.cancel = this.getOrThrow("cancel");
        // The page's slot containers - blueprints, tools, inputs, output and the player inventory - are declared
        // by ForgingMenu's layout, which is what ApricityUI binds these cells against.
        this.bindGrid("bp", GRID_CELLS, this.blueprintCells);
        this.bindGrid("mt", GRID_CELLS, this.methodCells);
        this.bindGrid("target", STEPS, this.targetCells);
        this.bindGrid("current", STEPS, this.historyCells);
        this.click(this.useBlueprint, this::useBlueprint);
        this.click(this.useMethod, this::useMethod);
        this.click(this.cancel, this::cancel);
        // The labels are Java's: the page's three buttons are empty boxes, and the theme's `.button` already
        // centres whatever text they carry.
        this.text(this.useBlueprint, Component.translatable("screen.mxt.forging.use_blueprint"));
        this.text(this.useMethod, Component.translatable("screen.mxt.forging.use_method"));
        this.text(this.cancel, Component.translatable("screen.mxt.forging.cancel"));
        // A cell draws an icon and nothing else, so its tooltip is the only place a method's name appears.
        for (int index = 0; index < GRID_CELLS; index++) {
            int cell = index;
            this.tooltip(this.blueprintCells.get(index).root, () -> this.blueprintTooltip(cell));
            this.tooltip(this.methodCells.get(index).root, () -> this.methodTooltip(cell));
        }
        for (int index = 0; index < STEPS; index++) {
            int position = index;
            this.tooltip(this.targetCells.get(index).root, () -> this.stepTooltip(position, true));
            this.tooltip(this.historyCells.get(index).root, () -> this.stepTooltip(position, false));
        }
        this.text(this.getOrThrow("title"), this.getTitle());
        this.text(this.getOrThrow("inventory_label"), Component.translatable("container.inventory"));
        this.text(this.getOrThrow("target_caption"), Component.translatable("screen.mxt.forging.target"));
        this.text(this.getOrThrow("current_caption"), Component.translatable("screen.mxt.forging.current"));
    }

    /**
     * A grid of icon boxes: {@code prefix-N} is the cell, {@code prefix_item-N} and {@code prefix_tex-N}
     * its two icon elements.
     */
    private void bindGrid(String prefix, int count, List<IconBox> target) {
        List<Element> roots = this.byIdPrefix(prefix + "-", count);
        List<Element> textures = this.byIdPrefix(prefix + "_tex-", count);
        for (int index = 0; index < count; index++) {
            Item icon = this.getOrThrow(prefix + "_item-" + index, Item.class);
            target.add(new IconBox(roots.get(index), icon, textures.get(index)));
        }
    }

    @Override
    public void onBindingsCleared() {
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
        this.blueprintReady = false;
        this.methodReady = false;
        this.cancelReady = false;
    }

    @Override
    public void onPageBound() {
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
        this.showScrollbar(this.blueprintBar, this.blueprintOffs, blueprints.size());
        this.showScrollbar(this.methodBar, this.methodOffs, methods.size());
        this.showSteps(this.targetCells, true);
        this.showSteps(this.historyCells, false);
        this.showMeter(pickedBlueprint, this.deltaOf(pickedMethod));
        this.showButtons(pickedBlueprint, pickedMethod);
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
                AuiElements.setClass(cell.root, "selected", false);
                continue;
            }
            cell.show(entry.icon());
            AuiElements.setClass(cell.root, "selected", entry.id().equals(selected));
        }
    }

    private void showScrollbar(@Nullable Element bar, float offs, int entries) {
        if (bar == null) return;
        AuiElements.setClass(bar, "disabled", !this.isScrollBarActive(entries));
        // Page coordinates: the thumb is a child of the panel, not of the track, so this is the same space as
        // the CSS default (top: 18px) and as the drag maths below. Writing it track-relative put both thumbs on
        // the panel's top edge, beside the title, instead of on the track beside their grid.
        AuiElements.style(bar, "top", SCROLL_TOP + 1 + Math.round(SCROLL_TRAVEL * offs) + "px");
    }

    /**
     * A press is accepted exactly when its request would name something the server takes: a blueprint needs a live
     * pick, no running session and its materials; a method needs a live pick while a session runs; cancel needs a
     * session at all. The attribute also makes ApricityUI drop the click, so the guards in the three presses only
     * cover a press that lands before the next refresh.
     */
    private void showButtons(@Nullable Identifier pickedBlueprint, @Nullable Identifier pickedMethod) {
        this.blueprintReady = !this.menu.active() && pickedBlueprint != null && this.menu.materialsCovered(pickedBlueprint);
        this.methodReady = this.menu.active() && pickedMethod != null;
        // With no session nothing is locked, so there is nothing to cancel.
        this.cancelReady = this.menu.active();
        AuiElements.setDisabled(this.useBlueprint, !this.blueprintReady);
        AuiElements.setDisabled(this.useMethod, !this.methodReady);
        AuiElements.setDisabled(this.cancel, !this.cancelReady);
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
            AuiElements.style(this.meterBand, "left", from + "px");
            AuiElements.style(this.meterBand, "width", (to - from + 1) + "px");
            AuiElements.style(this.meterBand, "display", "block");
        } else {
            AuiElements.style(this.meterBand, "display", "none");
        }
        // Every mark is positioned in the panel's own coordinates: the scale's left end is the trough's interior.
        this.showMark(this.meterZero, true, this.meterX(scale, 0));
        boolean predicted = active && predictedDelta != null;
        this.showMark(this.meterPredicted, predicted,
                predicted ? this.meterX(scale, this.menu.meterValue() + predictedDelta) : 0);
        this.showMark(this.meterValueMark, active, active ? this.meterX(scale, this.menu.meterValue()) : 0);
    }

    private void showMark(@Nullable Element mark, boolean visible, int left) {
        AuiElements.style(mark, "display", visible ? "block" : "none");
        if (visible) AuiElements.style(mark, "left", left + "px");
    }

    private void hideMeter() {
        this.showMark(this.meterBand, false, 0);
        this.showMark(this.meterZero, false, 0);
        this.showMark(this.meterValueMark, false, 0);
        this.showMark(this.meterPredicted, false, 0);
    }

    private void showReadouts() {
        this.text(this.meterValue, Component.translatable("screen.mxt.forging.meter.value", this.menu.meterValue()));
        this.text(this.stepsText, Component.translatable("screen.mxt.forging.steps", this.menu.steps()));
        // What the piece came out as is the server's own answer and only exists once the result is in the
        // output slot, so nothing here predicts it: the line appears with the finished piece and goes with it.
        // The tier itself is read off the piece's quality component, the one every writer stamps.
        ItemStack output = this.menu.getSlot(ForgingSurface.OUTPUT_SLOT).getItem();
        Holder<ItemQuality> quality = output.get(MxtDataComponents.QUALITY);
        if (output.get(MxtDataComponents.FORGING_RESULT) == null || quality == null) {
            this.showQuality("", "#FFFFFF");
            return;
        }
        Component name = QualityService.coloredName(quality, quality.value().name());
        TextColor color = name.getStyle().getColor();
        this.showQuality(Component.translatable("screen.mxt.forging.quality", name).getString(),
                color == null ? "#FFFFFF" : String.format("#%06X", color.getValue()));
    }

    /**
     * The tier's own colour covers the whole line: the page draws one text node, so the rich component the
     * old screen painted is flattened, and the tier colour is the part worth keeping.
     */
    private void showQuality(String text, String color) {
        AuiElements.setText(this.qualityText, text);
        AuiElements.style(this.qualityText, "color", color);
    }

    // ------------------------------------------------------------------ buttons

    private void useBlueprint() {
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
        if (this.minecraft.level == null) return List.of();
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
        if (this.minecraft.level == null) return List.of();
        Identifier blueprint = this.picked(this.blueprintEntries(), this.selectedBlueprint);
        List<Entry> entries = new ArrayList<>();
        for (Identifier id : this.menu.methods(blueprint)) {
            IconReference icon = MxtDatapackRegistries.get(this.minecraft.level.registryAccess(), MxtResourceKeys.FORGING_METHOD, id)
                    .flatMap(ForgingMethod::icon).orElse(null);
            entries.add(new Entry(id, icon));
        }
        return entries;
    }

    private List<IconReference> stepIcons(boolean target) {
        List<IconReference> icons = new ArrayList<>(STEPS);
        boolean usable = this.menu.active() && this.minecraft.level != null;
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
        if (method == null || this.minecraft.level == null) return null;
        return MxtDatapackRegistries.get(this.minecraft.level.registryAccess(), MxtResourceKeys.FORGING_METHOD, method)
                .map(ForgingMethod::valueDelta).orElse(null);
    }

    // ------------------------------------------------------------------ tooltips

    private List<Component> blueprintTooltip(int cell) {
        if (this.minecraft.level == null) return List.of();
        Entry entry = this.cellEntry(this.blueprintCells, cell, this.blueprintEntries(), this.blueprintOffs);
        if (entry == null) return List.of();
        ForgingBlueprint blueprint = this.menu.blueprint(entry.id());
        if (blueprint == null) return List.of();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip.mxt.forging.materials").withStyle(ChatFormatting.GOLD));
        boolean covered = true;
        for (SizedIngredient requirement : blueprint.input()) {
            ItemStack preview = this.menu.inputPreview(requirement);
            int have = this.menu.inputCount(requirement);
            boolean met = have >= requirement.count();
            covered &= met;
            lines.add(Component.literal(met ? "✔ " : "✖ ")
                    .append(Component.translatable("tooltip.mxt.forging.materials.line",
                            preview.isEmpty() ? Component.literal("?") : preview.getHoverName(), have, requirement.count()))
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
            Component name = QualityService.coloredName(tier.quality(), tier.quality().value().name());
            lines.add(tier.maxExtraSteps() == Integer.MAX_VALUE
                    ? Component.translatable("tooltip.mxt.forging.quality_tier.unbounded", name)
                    : Component.translatable("tooltip.mxt.forging.quality_tier", name, tier.maxExtraSteps()));
        }
        return lines;
    }

    private List<Component> methodTooltip(int cell) {
        if (this.minecraft.level == null) return List.of();
        Entry entry = this.cellEntry(this.methodCells, cell, this.methodEntries(), this.methodOffs);
        if (entry == null) return List.of();
        ForgingMethod method = MxtDatapackRegistries.get(this.minecraft.level.registryAccess(),
                MxtResourceKeys.FORGING_METHOD, entry.id()).orElse(null);
        if (method == null) return List.of();
        List<Component> lines = new ArrayList<>();
        lines.add(method.displayName(entry.id()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("tooltip.mxt.forging.method.delta", TooltipText.signed(method.valueDelta()))
                .withStyle(ChatFormatting.BLUE));
        return lines;
    }

    private List<Component> stepTooltip(int position, boolean target) {
        if (this.minecraft.level == null || this.minecraft.player == null) return List.of();
        int registryId = target ? this.menu.targetStep(position) : this.menu.historyStep(position);
        if (ForgingMenu.isNone(registryId)) return List.of();
        Identifier methodId = ForgingMenu.methodId(this.minecraft.player, registryId);
        ForgingMethod method = methodId == null ? null
                : MxtDatapackRegistries.get(this.minecraft.level.registryAccess(), MxtResourceKeys.FORGING_METHOD, methodId).orElse(null);
        if (method == null) return List.of();
        List<Component> lines = new ArrayList<>();
        lines.add(method.displayName(methodId).withStyle(ChatFormatting.GOLD));
        // The id, exactly as the item tooltips show it: for a datapack author, and only when they asked.
        if (this.minecraft.options.advancedItemTooltips)
            lines.add(Component.literal(methodId.toString()).withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Nullable
    private Entry cellEntry(List<IconBox> cells, int cell, List<Entry> entries, float offs) {
        if (cell < 0 || cell >= cells.size()) return null;
        int index = this.startIndex(offs, entries.size()) + cell;
        return index < entries.size() ? entries.get(index) : null;
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
                AuiElements.setClass(this.blueprintCells.get(index).root, "hover", index == blueprint);
            }
        }
        int method = this.hoveredCell(mouseX, mouseY, METHOD_X);
        if (method != this.hoveredMethod) {
            this.hoveredMethod = method;
            for (int index = 0; index < this.methodCells.size(); index++) {
                AuiElements.setClass(this.methodCells.get(index).root, "hover", index == method);
            }
        }
    }

    private int hoveredCell(double mouseX, double mouseY, int gridX) {
        for (int slot = 0; slot < GRID_CELLS; slot++) {
            // Columns sit one CELL apart, rows one CELL_PITCH apart, as the page and the old texture both have it.
            int x = gridX + slot % CELLS * CELL;
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
        // The page's cell carries no `empty` class to begin with, so it starts out drawn - and this flag has to
        // agree, because clear() early-returns while it is set. Starting it at true meant the first clear() of a
        // cell that had never held an entry wrote nothing, so an emptied grid kept showing boxes it did not have.
        private boolean shownEmpty = false;

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
            AuiElements.setClass(this.root, "icon-item", !stack.isEmpty());
            AuiElements.setClass(this.root, "icon-texture", !textureKey.isEmpty());
            if (!this.shownEmpty) return;
            AuiElements.setClass(this.root, "empty", false);
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
            AuiElements.setClass(this.root, "icon-item", false);
            AuiElements.setClass(this.root, "icon-texture", false);
            if (this.shownEmpty) return;
            AuiElements.setClass(this.root, "empty", true);
            this.shownEmpty = true;
        }
    }
}
