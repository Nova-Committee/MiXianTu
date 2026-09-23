package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMaterial;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.network.payload.ForgingActionC2SPayload;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.render.IconRenderer;
import com.iafenvoy.mxt.screen.menu.ForgingMenu;
import com.iafenvoy.mxt.util.TooltipText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The forge table surface: the texture draws the panels, and the slot coordinates come from
 * {@link ForgingMenu}, measured off the same image, so a well and its hitbox cannot drift apart.
 */
public final class ForgingScreen extends AbstractContainerScreen<ForgingMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/forging_table.png");

    // The option sprite runs the full cell width, as at 16 it would leave a stripe of the recess showing.
    private static final int OPTION_W = ForgingMenu.CELL;
    private static final int OPTION_H = 18;
    // An item icon is 16x16 whatever the cell around it measures.
    private static final int ICON = 16;
    private static final Identifier OPTION = Identifier.withDefaultNamespace("container/stonecutter/recipe");
    private static final Identifier OPTION_SELECTED = Identifier.withDefaultNamespace("container/stonecutter/recipe_selected");
    private static final Identifier OPTION_HIGHLIGHTED = Identifier.withDefaultNamespace("container/stonecutter/recipe_highlighted");
    private static final int TEXT = 0xFF404040;

    // The vanilla stonecutter's scrollbar: sprite size, travel and drag maths are its own unchanged.
    private static final int SCROLLER_WIDTH = 12;
    private static final int SCROLLER_HEIGHT = 15;
    // The track the scroller slides in: 54, the stonecutter's number rather than the recess's 76, because
    // blitSprite scales the 12x15 sprite to the rectangle it is handed.
    private static final int SCROLLER_FULL_HEIGHT = 54;
    private static final int SCROLL_TOP = ForgingMenu.RECESS_Y;
    private static final int SCROLL_TRAVEL = SCROLLER_FULL_HEIGHT - SCROLLER_HEIGHT;
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/stonecutter/scroller");
    private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace("container/stonecutter/scroller_disabled");
    // The meter trough: the texture draws one pixel of black border around 160x4 of C6C6C6, and METER_X is
    // the left border's own column, so the usable interior is what is left after it.
    private static final int METER_X = 80;
    private static final int METER_Y = 76;
    private static final int METER_W = 162;
    private static final int METER_INNER_X = METER_X + 1;
    private static final int METER_INNER_W = METER_W - 2;
    private static final int METER_H = 4;
    // Marks reach past the trough so they read on a scale rather than being clipped by it; the frame plate
    // is the same C6C6C6 as the interior.
    private static final int METER_MARK_OVERHANG = 2;
    // Chosen to read against that C6C6C6 interior.
    private static final int METER_TARGET = 0xFF3B8D3B;
    private static final int METER_ZERO = 0xFF6E6E6E;
    private static final int METER_VALUE = 0xFFD63A3A;
    private static final int METER_PREDICTED = 0xFFE8D44D;
    // The readout sits in the grey band between the meter and the first step row: that band is three pixels
    // tall and text on the meter's own line runs into the step cells underneath.
    private static final int READOUT_Y = 85;
    private static final int STEP_X = 135;
    private static final int STEP_Y = 98;
    private static final int STEP_ROW_PITCH = 22;
    private static final int STEP_CELL_PITCH = 18;
    // An unset cell draws a barrier: a row is always six cells wide, so a hole would leave the alignment to be guessed.
    private static final IconReference EMPTY_STEP = IconReference.item(ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.BARRIER)));
    // The first cell column starts this far inside its recess (recess at 7, its cells at 8).
    private static final int GRID_INSET = 1;
    // The action buttons and the row under them: the two buttons, the cancel button and the step count
    // readout all have to agree on these.
    private static final int ACTION_LEFT_X = 7;
    private static final int ACTION_RIGHT_X = 250;
    private static final int ACTION_Y = 100;
    private static final int ACTION_W = 65;
    private static final int ACTION_H = 16;
    private static final int ACTION_SECOND_ROW_Y = 120;
    // Where a vanilla button draws its label; the step count stands in for one.
    private static final int ACTION_TEXT_INSET = 4;

    // Scroll offset as a fraction of the list, as the stonecutter keeps it: the first visible cell is derived.
    private float blueprintOffs;
    private float methodOffs;
    // Which scrollbar is being dragged: 0 none, 1 blueprint, 2 method.
    private int dragging;
    private Button useBlueprint, useMethod, cancel;

    // Picks are ids, not list positions, because a position is a fact about a list that moves. A pick is
    // never sent on its own; only the buttons turn one into a request.
    private Identifier selectedBlueprint;
    private Identifier selectedMethod;

    public ForgingScreen(ForgingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 322, 234);
        this.titleLabelX = 7;
        this.titleLabelY = 5;
        this.inventoryLabelX = ForgingMenu.INVENTORY_X;
        this.inventoryLabelY = 141;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.useBlueprint = this.addRenderableWidget(Button.builder(Component.translatable("screen.mxt.forging.use_blueprint"), _1 -> this.useBlueprint())
                .bounds(this.leftPos + ACTION_LEFT_X, this.topPos + ACTION_Y, ACTION_W, ACTION_H).build());
        this.cancel = this.addRenderableWidget(Button.builder(Component.translatable("screen.mxt.forging.cancel"), _ -> this.cancel())
                .bounds(this.leftPos + ACTION_LEFT_X, this.topPos + ACTION_SECOND_ROW_Y, ACTION_W, ACTION_H).build());
        this.useMethod = this.addRenderableWidget(Button.builder(Component.translatable("screen.mxt.forging.use_method"), _ -> this.useMethod())
                .bounds(this.leftPos + ACTION_RIGHT_X, this.topPos + ACTION_Y, ACTION_W, ACTION_H).build());
    }

    // A pick is a local click rather than a packet, so the buttons are re-derived every tick.
    @Override
    protected void containerTick() {
        super.containerTick();
        this.refreshButtons();
    }

    private void useBlueprint() {
        Identifier id = this.picked(this.blueprintEntries(), this.selectedBlueprint);
        if (id == null) return;
        ClientPacketDistributor.sendToServer(ForgingActionC2SPayload.select(id));
    }

    private void useMethod() {
        Identifier id = this.picked(this.methodEntries(), this.selectedMethod);
        if (id == null) return;
        ClientPacketDistributor.sendToServer(ForgingActionC2SPayload.strike(id));
    }

    // The cancel payload names nothing: there is one session per table, resolved from the open menu.
    private void cancel() {
        ClientPacketDistributor.sendToServer(ForgingActionC2SPayload.cancel());
    }

    // A pick outlives the entry it names, so it is never read raw: this is the one place that question is
    // asked, for both rendering and presses.
    private Identifier picked(List<Entry> entries, Identifier id) {
        if (id == null) return null;
        for (Entry entry : entries)
            if (entry.id().equals(id)) return id;
        return null;
    }

    // A button is enabled exactly when pressing it would name something the server accepts: a blueprint needs
    // a live pick, no running session and its materials; a method needs a live pick and a running session.
    private void refreshButtons() {
        Identifier blueprint = this.picked(this.blueprintEntries(), this.selectedBlueprint);
        if (this.useBlueprint != null)
            this.useBlueprint.active = !this.menu.active() && blueprint != null && this.menu.materialsCovered(blueprint);
        if (this.useMethod != null)
            this.useMethod.active = this.menu.active() && this.picked(this.methodEntries(), this.selectedMethod) != null;
        // With no session nothing is locked, so there is nothing to cancel.
        if (this.cancel != null) this.cancel.active = this.menu.active();
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        // Screen space: this runs before extractContents moves the origin.
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 512, 256);
    }

    // Everything the frame draws, in the frame's own coordinates; the picks are resolved once each so a
    // highlight and the prediction beside it cannot disagree.
    @Override
    protected void extractLabels(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractLabels(graphics, mouseX, mouseY);
        List<Entry> blueprints = this.blueprintEntries();
        List<Entry> methods = this.methodEntries();
        Identifier pickedBlueprint = this.picked(blueprints, this.selectedBlueprint);
        Identifier pickedMethod = this.picked(methods, this.selectedMethod);
        this.selector(graphics, ForgingMenu.BLUEPRINT_GRID_X, ForgingMenu.SCROLLBAR_X, blueprints, pickedBlueprint, true, mouseX, mouseY);
        this.selector(graphics, ForgingMenu.METHOD_GRID_X, ForgingMenu.SCROLLBAR_X_RIGHT, methods, pickedMethod, false, mouseX, mouseY);
        if (this.menu.active()) {
            this.readouts(graphics);
            this.stepCaptions(graphics);
            this.stepIcons(graphics, STEP_Y, true);
            this.stepIcons(graphics, STEP_Y + STEP_ROW_PITCH, false);
        }
        this.meter(graphics, pickedBlueprint, this.deltaOf(pickedMethod));
    }

    // The method's own value_delta rather than a session table: the yellow line is drawn before the strike.
    private Integer deltaOf(Identifier method) {
        if (method == null || this.minecraft.level == null) return null;
        return MxtDatapackRegistries.get(this.minecraft.level.registryAccess(), MxtResourceKeys.FORGING_METHOD, method)
                .map(ForgingMethod::valueDelta).orElse(null);
    }

    // The grids have no slots, so their tooltips come from here; super runs first, so a slot tooltip wins.
    @Override
    protected void extractTooltip(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        this.blueprintTooltip(graphics, this.blueprintEntries(), mouseX, mouseY);
        this.methodTooltip(graphics, this.methodEntries(), mouseX, mouseY);
        this.stepTooltip(graphics, mouseX, mouseY);
    }

    // A cell draws an icon and nothing else, so its tooltip is the only place the method's name appears.
    private void stepTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.minecraft.level == null || this.minecraft.player == null) return;
        int registryId = this.hoveredStep(mouseX, mouseY);
        if (ForgingMenu.isNone(registryId)) return;
        Identifier methodId = ForgingMenu.methodId(this.minecraft.player, registryId);
        ForgingMethod method = methodId == null ? null
                : MxtDatapackRegistries.get(this.minecraft.level.registryAccess(), MxtResourceKeys.FORGING_METHOD, methodId).orElse(null);
        if (method == null) return;

        List<Component> lines = new ArrayList<>();
        lines.add(method.displayName(methodId).withStyle(ChatFormatting.GOLD));
        // The id, exactly as the item tooltips show it: for a datapack author, and only when they asked.
        if (this.minecraft.options.advancedItemTooltips)
            lines.add(Component.literal(methodId.toString()).withStyle(ChatFormatting.DARK_GRAY));
        graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    // Hitboxes use the cell pitch, not the icon's 16, so the cells tile with no dead strip between them.
    private int hoveredStep(double mouseX, double mouseY) {
        if (!this.menu.active()) return ForgingMenu.NONE;
        for (int row = 0; row < 2; row++) {
            int y = STEP_Y + row * STEP_ROW_PITCH;
            for (int position = 0; position < ForgingMenu.SUFFIX_STEPS; position++) {
                if (!this.isHovering(STEP_X + position * STEP_CELL_PITCH, y, STEP_CELL_PITCH, ICON, mouseX, mouseY))
                    continue;
                return row == 0 ? this.menu.targetStep(position) : this.menu.historyStep(position);
            }
        }
        return ForgingMenu.NONE;
    }

    private void blueprintTooltip(GuiGraphicsExtractor graphics, List<Entry> blueprints, int mouseX, int mouseY) {
        if (this.minecraft.level == null) return;
        Entry hovered = this.hoveredCell(mouseX, mouseY, ForgingMenu.BLUEPRINT_GRID_X, this.blueprintOffs, blueprints);
        if (hovered == null) return;
        ForgingBlueprint blueprint = this.menu.blueprint(hovered.id());
        if (blueprint == null) return;

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
        graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    private void methodTooltip(GuiGraphicsExtractor graphics, List<Entry> methods, int mouseX, int mouseY) {
        if (this.minecraft.level == null) return;
        Entry hovered = this.hoveredCell(mouseX, mouseY, ForgingMenu.METHOD_GRID_X, this.methodOffs, methods);
        if (hovered == null) return;
        ForgingMethod method = MxtDatapackRegistries.get(this.minecraft.level.registryAccess(),
                MxtResourceKeys.FORGING_METHOD, hovered.id()).orElse(null);
        if (method == null) return;

        List<Component> lines = new ArrayList<>();
        lines.add(method.displayName(hovered.id()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("tooltip.mxt.forging.method.delta", TooltipText.signed(method.valueDelta())).withStyle(ChatFormatting.BLUE));
        graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    private void selector(GuiGraphicsExtractor graphics, int gridX, int barX, List<Entry> entries,
                          Identifier selected, boolean blueprint, int mouseX, int mouseY) {
        float offs = blueprint ? this.blueprintOffs : this.methodOffs;
        int start = this.startIndex(offs, entries.size());
        int visible = ForgingMenu.CELLS * ForgingMenu.CELLS;

        for (int slot = 0; slot < visible; slot++) {
            int x = gridX + slot % ForgingMenu.CELLS * ForgingMenu.CELL;
            int y = ForgingMenu.GRID_Y + slot / ForgingMenu.CELLS * ForgingMenu.CELL_PITCH;
            int index = start + slot;
            if (index >= entries.size()) continue;
            Entry entry = entries.get(index);
            this.option(graphics, x, y, entry.icon(), entry.id().equals(selected),
                    this.isHovering(x, y, ForgingMenu.CELL, ForgingMenu.CELL, mouseX, mouseY));
        }
        this.scrollbar(graphics, barX, offs, entries.size());
    }

    private void option(GuiGraphicsExtractor graphics, int x, int y, @Nullable IconReference icon,
                        boolean selected, boolean hovered) {
        Identifier sprite = selected ? OPTION_SELECTED : hovered ? OPTION_HIGHLIGHTED : OPTION;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y - (OPTION_H - ForgingMenu.CELL) / 2, OPTION_W, OPTION_H);
        if (icon == null) return;
        IconRenderer.render(graphics, icon, x, y, ForgingMenu.CELL);
    }

    // The stonecutter's line, with CELLS where it hard-codes a four.
    private int startIndex(float offs, int entries) {
        return (int) (offs * this.getOffscreenRows(entries) + 0.5D) * ForgingMenu.CELLS;
    }

    // Clamped at zero: the stonecutter only asks once the bar is known to be active.
    private int getOffscreenRows(int entries) {
        return Math.max(0, (entries + ForgingMenu.CELLS - 1) / ForgingMenu.CELLS - ForgingMenu.CELLS);
    }

    private boolean isScrollBarActive(int entries) {
        return entries > ForgingMenu.CELLS * ForgingMenu.CELLS;
    }

    // The stonecutter's scroller with the measured SCROLLBAR_X column instead of its magic 119.
    private void scrollbar(GuiGraphicsExtractor graphics, int barX, float offs, int entries) {
        int offset = (int) (SCROLL_TRAVEL * offs);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.isScrollBarActive(entries) ? SCROLLER : SCROLLER_DISABLED, barX + 1, SCROLL_TOP + 1 + offset, SCROLLER_WIDTH, SCROLLER_HEIGHT);
    }

    // Only the target row fills its empty cells (with EMPTY_STEP); the history row skips them.
    private void stepIcons(GuiGraphicsExtractor graphics, int y, boolean target) {
        List<IconReference> icons = this.stepIcons(target);
        for (int position = 0; position < ForgingMenu.SUFFIX_STEPS && position < icons.size(); position++) {
            IconReference icon = icons.get(position);
            if (icon == null && !target) continue;
            IconRenderer.render(graphics, icon == null ? EMPTY_STEP : icon,
                    STEP_X + position * STEP_CELL_PITCH, y, ICON);
        }
    }

    private void stepCaptions(GuiGraphicsExtractor graphics) {
        graphics.text(this.font, Component.translatable("screen.mxt.forging.target"), ForgingMenu.INVENTORY_X, STEP_Y + 5, TEXT, false);
        graphics.text(this.font, Component.translatable("screen.mxt.forging.current"), ForgingMenu.INVENTORY_X, STEP_Y + 5 + STEP_ROW_PITCH, TEXT, false);
    }

    private void readouts(GuiGraphicsExtractor graphics) {
        graphics.text(this.font, Component.translatable("screen.mxt.forging.meter.value", this.menu.meterValue()), ForgingMenu.INVENTORY_X, READOUT_Y, TEXT, false);
        Component steps = Component.translatable("screen.mxt.forging.steps", this.menu.steps());
        graphics.text(this.font, steps, ACTION_RIGHT_X + (ACTION_W - this.font.width(steps)) / 2, ACTION_SECOND_ROW_Y + ACTION_TEXT_INSET, TEXT, false);
    }

    private void meter(GuiGraphicsExtractor graphics, Identifier pickedBlueprint, Integer predictedDelta) {
        MeterScale scale = this.scale(pickedBlueprint);
        if (scale == null) return;
        boolean active = this.menu.active();
        if (active)
            graphics.fill(this.meterX(scale, this.menu.targetMin()), METER_Y,
                    this.meterX(scale, this.menu.targetMax()) + 1, METER_Y + METER_H, METER_TARGET);
        this.mark(graphics, this.meterX(scale, 0), METER_ZERO);
        if (!active) return;
        if (predictedDelta != null)
            this.mark(graphics, this.meterX(scale, this.menu.meterValue() + predictedDelta), METER_PREDICTED);
        this.mark(graphics, this.meterX(scale, this.menu.meterValue()), METER_VALUE);
    }

    private record MeterScale(int min, int max) {
    }

    // A running session supplies its bounds from the plan snapshotted at start, so a datapack reload cannot
    // move a bar being read.
    private MeterScale scale(Identifier pickedBlueprint) {
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

    private void mark(GuiGraphicsExtractor graphics, int x, int colour) {
        graphics.fill(x, METER_Y - METER_MARK_OVERHANG, x + 1, METER_Y + METER_H + METER_MARK_OVERHANG, colour);
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
            IconReference icon = MxtDatapackRegistries.get(this.minecraft.level.registryAccess(),
                            MxtResourceKeys.FORGING_METHOD, id)
                    .flatMap(ForgingMethod::icon).orElse(null);
            entries.add(new Entry(id, icon));
        }
        return entries;
    }

    private List<IconReference> stepIcons(boolean target) {
        List<IconReference> icons = new ArrayList<>(ForgingMenu.SUFFIX_STEPS);
        boolean usable = this.menu.active() && this.minecraft.level != null;
        Registry<ForgingMethod> registry = usable ? this.minecraft.level.registryAccess().lookupOrThrow(MxtResourceKeys.FORGING_METHOD) : null;
        for (int position = 0; position < ForgingMenu.SUFFIX_STEPS; position++) {
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

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
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
        if (this.overGrid(mouseX, mouseY, ForgingMenu.BLUEPRINT_GRID_X)
                && this.isScrollBarActive(this.blueprintEntries().size())) {
            this.blueprintOffs = this.scrolled(this.blueprintOffs, scrollY, this.blueprintEntries().size());
            return true;
        }
        if (this.overGrid(mouseX, mouseY, ForgingMenu.METHOD_GRID_X)
                && this.isScrollBarActive(this.methodEntries().size())) {
            this.methodOffs = this.scrolled(this.methodOffs, scrollY, this.methodEntries().size());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private float scrolled(float offs, double delta, int entries) {
        int rows = this.getOffscreenRows(entries);
        if (rows <= 0) return offs;
        return Mth.clamp(offs - (float) delta / (float) rows, 0.0F, 1.0F);
    }

    // The whole recess scrolls, gutter included, hence the back-off by GRID_INSET.
    private boolean overGrid(double mouseX, double mouseY, int gridX) {
        return this.isHovering(gridX - GRID_INSET, ForgingMenu.RECESS_Y,
                ForgingMenu.RECESS_W + GRID_INSET, ForgingMenu.RECESS_H, mouseX, mouseY);
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

    // The same rectangle scrollbar() draws into: only the scroller column counts, not the whole track.
    private boolean inBar(double mouseX, double mouseY, int barX) {
        return this.isHovering(barX + 1, SCROLL_TOP, SCROLLER_WIDTH, SCROLLER_FULL_HEIGHT, mouseX, mouseY);
    }

    // The stonecutter's drag maths unchanged: the scroller's centre follows the cursor over SCROLL_TRAVEL.
    private void dragScrollbar(double mouseY) {
        if (SCROLL_TRAVEL <= 0) return;
        float top = this.topPos + SCROLL_TOP;
        float offs = Mth.clamp((float) mouseY - top - SCROLLER_HEIGHT / 2.0F, 0.0F, (float) SCROLL_TRAVEL) / (float) SCROLL_TRAVEL;
        if (this.dragging == 1) this.blueprintOffs = offs;
        else if (this.dragging == 2) this.methodOffs = offs;
    }

    // Picking is local: nothing is sent, nothing can fail, and the two grids do not consult each other.
    private boolean selectorClicked(double mouseX, double mouseY) {
        if (this.clickCell(mouseX, mouseY, ForgingMenu.BLUEPRINT_GRID_X, this.blueprintOffs,
                this.blueprintEntries(), true)) return true;
        return this.clickCell(mouseX, mouseY, ForgingMenu.METHOD_GRID_X, this.methodOffs,
                this.methodEntries(), false);
    }

    // The blueprint pick is frozen while a session runs, since the table has locked a blueprint; the method
    // pick stays live.
    private boolean clickCell(double mouseX, double mouseY, int gridX, float offs, List<Entry> entries, boolean blueprint) {
        Entry entry = this.hoveredCell(mouseX, mouseY, gridX, offs, entries);
        if (entry == null) return false;
        if (blueprint) {
            if (!this.menu.active()) this.selectedBlueprint = entry.id();
        } else {
            this.selectedMethod = entry.id();
        }
        return true;
    }

    private Entry hoveredCell(double mouseX, double mouseY, int gridX, float offs, List<Entry> entries) {
        int start = this.startIndex(offs, entries.size());
        for (int slot = 0; slot < ForgingMenu.CELLS * ForgingMenu.CELLS; slot++) {
            int x = gridX + slot % ForgingMenu.CELLS * ForgingMenu.CELL;
            int y = ForgingMenu.GRID_Y + slot / ForgingMenu.CELLS * ForgingMenu.CELL_PITCH;
            if (!this.isHovering(x, y, ForgingMenu.CELL, ForgingMenu.CELL, mouseX, mouseY)) continue;
            int index = start + slot;
            return index < entries.size() ? entries.get(index) : null;
        }
        return null;
    }
}
