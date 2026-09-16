package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.IconReference;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMaterial;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.network.payload.ForgingActionC2SPayload;
import com.iafenvoy.mxt.render.IconRenderer;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
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
 * The forge table surface: it blits {@code textures/gui/forging_table.png}, which already draws every panel,
 * slot well, the meter and the two step rows, and adds live icons on top. The slot coordinates come from
 * {@link ForgingMenu}, measured off the same image, so a well and the hitbox in it cannot drift apart.
 */
public final class ForgingScreen extends AbstractContainerScreen<ForgingMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/forging_table.png");

    /**
     * The selector options, drawn as the vanilla stonecutter draws its recipe buttons. The sprite is taken to
     * the full {@link ForgingMenu#CELL} width, since at 16 it would leave a stripe of the recess showing.
     */
    private static final int OPTION_W = ForgingMenu.CELL;
    private static final int OPTION_H = 18;
    /**
     * An item icon is 16x16, whatever the cell around it measures.
     */
    private static final int ICON = 16;
    private static final Identifier OPTION = Identifier.withDefaultNamespace("container/stonecutter/recipe");
    private static final Identifier OPTION_SELECTED = Identifier.withDefaultNamespace("container/stonecutter/recipe_selected");
    private static final Identifier OPTION_HIGHLIGHTED = Identifier.withDefaultNamespace("container/stonecutter/recipe_highlighted");
    private static final int TEXT = 0xFF404040;

    /**
     * The selector scrollbars, taken from the vanilla stonecutter. The sprite size, travel and drag maths are
     * the stonecutter's unchanged; the scroller's track is the 54px one - see {@link #scrollbar}.
     */
    private static final int SCROLLER_WIDTH = 12;
    private static final int SCROLLER_HEIGHT = 15;
    /**
     * The track the scroller slides in: 54, the stonecutter's number rather than the recess's 76, because
     * {@code blitSprite} scales the 12x15 sprite to the rectangle it is handed.
     */
    private static final int SCROLLER_FULL_HEIGHT = 54;
    /**
     * The scroller's track: the recess's inner top, and the span it slides over.
     */
    private static final int SCROLL_TOP = ForgingMenu.RECESS_Y;
    private static final int SCROLL_TRAVEL = SCROLLER_FULL_HEIGHT - SCROLLER_HEIGHT;
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/stonecutter/scroller");
    private static final Identifier SCROLLER_DISABLED = Identifier.withDefaultNamespace("container/stonecutter/scroller_disabled");
    /**
     * The meter trough, whose interior the texture draws as one pixel of black border around 160x4 of
     * {@code C6C6C6}. METER_X is the left border's own column, so the usable span is what is left.
     */
    private static final int METER_X = 80;
    private static final int METER_Y = 76;
    private static final int METER_W = 162;
    private static final int METER_INNER_X = METER_X + 1;
    private static final int METER_INNER_W = METER_W - 2;
    private static final int METER_H = 4;
    /**
     * How far a value mark reaches past the trough, so it reads as a mark on a scale rather than as
     * something the trough clips. The frame plate is the same {@code C6C6C6} as the interior.
     */
    private static final int METER_MARK_OVERHANG = 2;
    /**
     * The four colours, chosen against that {@code C6C6C6} interior.
     */
    private static final int METER_TARGET = 0xFF3B8D3B;
    private static final int METER_ZERO = 0xFF6E6E6E;
    private static final int METER_VALUE = 0xFFD63A3A;
    private static final int METER_PREDICTED = 0xFFE8D44D;
    /**
     * The readout sits in the grey band between the meter and the first step row, not on the meter's own
     * line: that band is only three pixels tall, and text on it runs into the step cells underneath.
     */
    private static final int READOUT_Y = 85;
    private static final int STEP_X = 135;
    private static final int STEP_Y = 98;
    private static final int STEP_ROW_PITCH = 22;
    private static final int STEP_CELL_PITCH = 18;
    /**
     * What an unset cell of a step row is drawn as. A row is always six cells wide, so a barrier says the cell
     * is part of the row and deliberately has no value, where a hole would leave the alignment to be guessed.
     */
    private static final IconReference EMPTY_STEP = IconReference.item(ItemStackTemplate.fromNonEmptyStack(new ItemStack(Items.BARRIER)));
    /**
     * How far the first cell column starts inside its recess: the left recess starts at column 7 and its
     * cells at 8.
     */
    private static final int GRID_INSET = 1;
    /**
     * The action buttons, and the cells in the row under them; named because three things have to agree on
     * them: the two buttons, the cancel button below the first and the step count below the second.
     */
    private static final int ACTION_LEFT_X = 7;
    private static final int ACTION_RIGHT_X = 250;
    private static final int ACTION_Y = 100;
    private static final int ACTION_W = 65;
    private static final int ACTION_H = 16;
    private static final int ACTION_SECOND_ROW_Y = 120;
    /**
     * Where a vanilla button draws its label, and so where a label that stands in for one goes.
     */
    private static final int ACTION_TEXT_INSET = 4;

    /**
     * The scroll offset of each grid, as a fraction of the list, exactly as the stonecutter keeps it: the
     * first visible cell is derived from this rather than stored.
     */
    private float blueprintOffs;
    private float methodOffs;
    /**
     * Which scrollbar is being dragged: 0 none, 1 blueprint, 2 method.
     */
    private int dragging;
    private Button useBlueprint, useMethod, cancel;

    /**
     * The two picks, stored as ids rather than list positions because a position is a fact about a list that
     * moves. Neither is sent on its own; only the buttons turn a pick into a request.
     */
    private Identifier selectedBlueprint;
    private Identifier selectedMethod;

    public ForgingScreen(ForgingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 322, 234);
        this.titleLabelX = 7;
        this.titleLabelY = 5;
        this.inventoryLabelX = ForgingMenu.INVENTORY_X;
        this.inventoryLabelY = 141;
    }

    /**
     * The action buttons. Built here rather than drawn by hand so the press, the hover state and
     * the disabled look all stay vanilla's.
     */
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

    /**
     * The buttons follow both the session and the picks; since a pick is a click rather than a packet, they
     * are re-checked every tick.
     */
    @Override
    protected void containerTick() {
        super.containerTick();
        this.refreshButtons();
    }

    /**
     * Asks the server to open a session for the blueprint picked in the left grid. The pick is re-resolved
     * against the list so the button can never name something the grid has stopped showing.
     */
    private void useBlueprint() {
        Identifier id = this.picked(this.blueprintEntries(), this.selectedBlueprint);
        if (id == null) return;
        ClientPacketDistributor.sendToServer(ForgingActionC2SPayload.select(id));
    }

    /**
     * Asks the server to strike once with the method picked in the right grid.
     */
    private void useMethod() {
        Identifier id = this.picked(this.methodEntries(), this.selectedMethod);
        if (id == null) return;
        ClientPacketDistributor.sendToServer(ForgingActionC2SPayload.strike(id));
    }

    /**
     * Asks the server to abandon the session. It names nothing, because there is only ever one session per
     * table and the server resolves the table from the open menu.
     */
    private void cancel() {
        ClientPacketDistributor.sendToServer(ForgingActionC2SPayload.cancel());
    }

    /**
     * The pick, if the list still offers it, otherwise null. A pick outlives the entry it names, so it is
     * never read raw; this is the one place that question is asked, on both the render and press path.
     */
    private Identifier picked(List<Entry> entries, Identifier id) {
        if (id == null) return null;
        for (Entry entry : entries)
            if (entry.id().equals(id)) return id;
        return null;
    }

    /**
     * A button is enabled exactly when pressing it would name something the server accepts: a blueprint needs
     * a live pick, no running session and its materials in the input slots, a method a live pick and a session.
     */
    private void refreshButtons() {
        Identifier blueprint = this.picked(this.blueprintEntries(), this.selectedBlueprint);
        if (this.useBlueprint != null)
            this.useBlueprint.active = !this.menu.active() && blueprint != null && this.menu.materialsCovered(blueprint);
        if (this.useMethod != null)
            this.useMethod.active = this.menu.active() && this.picked(this.methodEntries(), this.selectedMethod) != null;
        // With no session nothing is locked, so there is nothing to cancel and nothing a cancel could cost.
        if (this.cancel != null) this.cancel.active = this.menu.active();
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        // Screen space: this runs before extractContents moves the origin.
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 512, 256);
    }

    /**
     * Everything the frame draws, in the frame's own coordinates, with the picks resolved once each so a
     * highlight and the prediction beside it cannot disagree. Only the bar and the two grids need no session.
     */
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

    /**
     * The meter's yellow line, and the only reader of this: the method's own {@code value_delta} rather than
     * any session table, because the point is to draw it before the strike has happened.
     */
    private Integer deltaOf(Identifier method) {
        if (method == null || this.minecraft.level == null) return null;
        return MxtDatapackRegistries.get(this.minecraft.level.registryAccess(), MxtResourceKeys.FORGING_METHOD, method)
                .map(ForgingMethod::valueDelta).orElse(null);
    }

    /**
     * The two grids' tooltips, which no slot provides: the blueprint one says what the piece will cost, the
     * method one what a strike would do to the value. A slot tooltip still wins, since {@code super} first.
     */
    @Override
    protected void extractTooltip(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        this.blueprintTooltip(graphics, this.blueprintEntries(), mouseX, mouseY);
        this.methodTooltip(graphics, this.methodEntries(), mouseX, mouseY);
        this.stepTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Which method one of the step cells holds. A cell draws an icon and nothing else, so it names a method
     * only to someone who knows the icons; the id comes from the same calls the cells are drawn from.
     */
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

    /**
     * The registry id in the step cell under the cursor, or {@link ForgingMenu#NONE}. The hitbox width is the
     * cell pitch, not the icon's 16, so the cells tile with no dead strip between them to hover over.
     */
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

    /**
     * The hovered blueprint's material list: what it is about to take, and which of it the input slots are
     * short of. A satisfied entry is green with a tick, a missing one red with a cross, each line with a count.
     */
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
        graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }

    /**
     * The hovered method's name and what it does to the value, neither of which the cell can say: the icon
     * identifies the method only to someone who knows the icons, and the delta is invisible before the strike.
     */
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

    /**
     * One selector island's live layer: the options, the hover, the scrollbar.
     */
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

    /**
     * One option: its background and its item together, being one widget - a button whose label is an item.
     * The width is the stonecutter's sprite taken to {@link ForgingMenu#CELL}, so no recess stripe shows.
     */
    private void option(GuiGraphicsExtractor graphics, int x, int y, @Nullable IconReference icon,
                        boolean selected, boolean hovered) {
        Identifier sprite = selected ? OPTION_SELECTED : hovered ? OPTION_HIGHLIGHTED : OPTION;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y - (OPTION_H - ForgingMenu.CELL) / 2, OPTION_W, OPTION_H);
        if (icon == null) return;
        IconRenderer.render(graphics, icon, x, y, ForgingMenu.CELL);
    }

    /**
     * The index of the first cell the grid shows, from the scroll fraction: the stonecutter's line, with
     * {@link ForgingMenu#CELLS} where it writes a hard-coded four.
     */
    private int startIndex(float offs, int entries) {
        return (int) (offs * this.getOffscreenRows(entries) + 0.5D) * ForgingMenu.CELLS;
    }

    /**
     * How many rows of the list do not fit, clamped at zero because the stonecutter only ever asks once it
     * knows the bar is active.
     */
    private int getOffscreenRows(int entries) {
        return Math.max(0, (entries + ForgingMenu.CELLS - 1) / ForgingMenu.CELLS - ForgingMenu.CELLS);
    }

    /**
     * The scrollbar is active only when the list does not fit, which is also when it is drawn at all:
     * a list that fits gets the greyed sprite in the same place, not a full-height thumb.
     */
    private boolean isScrollBarActive(int entries) {
        return entries > ForgingMenu.CELLS * ForgingMenu.CELLS;
    }

    /**
     * The vanilla stonecutter scroller, except that the column is the measured {@link ForgingMenu#SCROLLBAR_X}
     * rather than its magic 119, and the track is anchored to the recess.
     */
    private void scrollbar(GuiGraphicsExtractor graphics, int barX, float offs, int entries) {
        int offset = (int) (SCROLL_TRAVEL * offs);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.isScrollBarActive(entries) ? SCROLLER : SCROLLER_DISABLED, barX + 1, SCROLL_TOP + 1 + offset, SCROLLER_WIDTH, SCROLLER_HEIGHT);
    }

    /**
     * One six-step row, right-aligned because a finish pattern describes the last steps of a session: the
     * last cell is the most recent step in both rows. Only the required row fills its gaps, with a barrier.
     */
    private void stepIcons(GuiGraphicsExtractor graphics, int y, boolean target) {
        List<IconReference> icons = this.stepIcons(target);
        for (int position = 0; position < ForgingMenu.SUFFIX_STEPS && position < icons.size(); position++) {
            IconReference icon = icons.get(position);
            if (icon == null && !target) continue;
            IconRenderer.render(graphics, icon == null ? EMPTY_STEP : icon,
                    STEP_X + position * STEP_CELL_PITCH, y, ICON);
        }
    }

    /**
     * The captions for the two step rows, in the grey margin the texture leaves to their left.
     */
    private void stepCaptions(GuiGraphicsExtractor graphics) {
        graphics.text(this.font, Component.translatable("screen.mxt.forging.target"), ForgingMenu.INVENTORY_X, STEP_Y + 5, TEXT, false);
        graphics.text(this.font, Component.translatable("screen.mxt.forging.current"), ForgingMenu.INVENTORY_X, STEP_Y + 5 + STEP_ROW_PITCH, TEXT, false);
    }

    /**
     * The two numbers: the value under the meter, and the step count under the method button, because the
     * count is how much of the session's budget got the value there and so reads as a counter.
     */
    private void readouts(GuiGraphicsExtractor graphics) {
        graphics.text(this.font, Component.translatable("screen.mxt.forging.meter.value", this.menu.meterValue()), ForgingMenu.INVENTORY_X, READOUT_Y, TEXT, false);
        Component steps = Component.translatable("screen.mxt.forging.steps", this.menu.steps());
        graphics.text(this.font, steps, ACTION_RIGHT_X + (ACTION_W - this.font.width(steps)) / 2, ACTION_SECOND_ROW_Y + ACTION_TEXT_INSET, TEXT, false);
    }

    /**
     * The meter: the texture draws the trough and its border, so this adds the target band and three marks —
     * the gray zero, the red value and the yellow prediction.
     */
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

    /**
     * The two ends of the bar.
     */
    private record MeterScale(int min, int max) {
    }

    /**
     * The bounds the bar is drawn against, or null when nothing defines them. A running session supplies
     * them from its plan, snapshotted at start, so a datapack reload cannot move a bar being read.
     */
    private MeterScale scale(Identifier pickedBlueprint) {
        if (!this.menu.active()) {
            ForgingBlueprint blueprint = this.menu.blueprint(pickedBlueprint);
            if (blueprint == null) return null;
            return new MeterScale(blueprint.meter().min(), blueprint.meter().max());
        }
        MeterScale running = new MeterScale(this.menu.meterMin(), this.menu.meterMax());
        return running.max() > running.min() ? running : null;
    }

    /**
     * Where a value sits on the trough, clamped to its interior. The interior's last column belongs to the
     * scale's maximum, so the span is one pixel shorter than the interior.
     */
    private int meterX(MeterScale scale, int value) {
        int span = scale.max() - scale.min();
        if (span <= 0) return METER_INNER_X;
        float fraction = Mth.clamp((value - scale.min()) / (float) span, 0.0F, 1.0F);
        return METER_INNER_X + Math.round(fraction * (METER_INNER_W - 1));
    }

    /**
     * One value mark: a single column, reaching past the trough on both sides.
     */
    private void mark(GuiGraphicsExtractor graphics, int x, int colour) {
        graphics.fill(x, METER_Y - METER_MARK_OVERHANG, x + 1, METER_Y + METER_H + METER_MARK_OVERHANG, colour);
    }

    /**
     * One grid entry: the id it stands for, and the stack that draws it, travelling together because the grid
     * is indexed by position and a click has to name the entry on screen there.
     */
    private record Entry(Identifier id, @Nullable IconReference icon) {
    }

    /**
     * Every offered blueprint, with the stack that stands for it. An id always yields an entry, even when its
     * result item cannot be resolved, because dropping it would shift every later position.
     */
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

    /**
     * Every offered method, with its icon. The pick is resolved first rather than read raw: a manual taken out
     * of its slot removes its blueprint from the list, and a pick no longer offered must restrict nothing.
     */
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

    /**
     * The wheel scrolls the grid under it, as it does on the stonecutter.
     */
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

    /**
     * One wheel notch moves one row, expressed as a fraction of the whole travel.
     */
    private float scrolled(float offs, double delta, int entries) {
        int rows = this.getOffscreenRows(entries);
        if (rows <= 0) return offs;
        return Mth.clamp(offs - (float) delta / (float) rows, 0.0F, 1.0F);
    }

    /**
     * The whole recess scrolls, not just the cells, so the wheel works over the gutter too. The cells
     * start {@link #GRID_INSET} inside the recess, hence the back-off.
     */
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

    /**
     * Only the scroller column counts, so a click on the cells is not swallowed as a scroll. This is
     * the same rectangle {@link #scrollbar} draws the sprite into.
     */
    private boolean inBar(double mouseX, double mouseY, int barX) {
        return this.isHovering(barX + 1, SCROLL_TOP, SCROLLER_WIDTH, SCROLLER_FULL_HEIGHT, mouseX, mouseY);
    }

    /**
     * The stonecutter's drag maths, unchanged: the scroller's centre follows the cursor over the
     * travel between the top of the track and its bottom minus the scroller's own height.
     */
    private void dragScrollbar(double mouseY) {
        if (SCROLL_TRAVEL <= 0) return;
        float top = this.topPos + SCROLL_TOP;
        float offs = Mth.clamp((float) mouseY - top - SCROLLER_HEIGHT / 2.0F, 0.0F, (float) SCROLL_TRAVEL) / (float) SCROLL_TRAVEL;
        if (this.dragging == 1) this.blueprintOffs = offs;
        else if (this.dragging == 2) this.methodOffs = offs;
    }

    /**
     * A click on a selector cell. It picks, and picks are local: nothing is sent, nothing can fail, and
     * the two grids do not consult each other.
     */
    private boolean selectorClicked(double mouseX, double mouseY) {
        if (this.clickCell(mouseX, mouseY, ForgingMenu.BLUEPRINT_GRID_X, this.blueprintOffs,
                this.blueprintEntries(), true)) return true;
        return this.clickCell(mouseX, mouseY, ForgingMenu.METHOD_GRID_X, this.methodOffs,
                this.methodEntries(), false);
    }

    /**
     * Picks whatever is under the cursor. The blueprint pick is frozen while a session runs, because the table
     * has locked a blueprint; the method pick is not, since the tools decide what may be struck.
     */
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

    /**
     * The entry under the cursor in one grid, or null when the cursor is outside it or over a cell the list
     * does not reach. One definition for both the click and the tooltip.
     */
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
