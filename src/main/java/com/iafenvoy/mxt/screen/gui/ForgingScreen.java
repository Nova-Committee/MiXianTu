package com.iafenvoy.mxt.screen.gui;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMaterial;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.network.payload.ForgingActionC2SPayload;
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
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The forge table surface.
 *
 * <h2>The texture is the layout</h2>
 * Every panel, slot well, the meter and the two step rows are already drawn in
 * {@code textures/gui/forging_table.png}; this class blits that, and everything it draws on top is
 * either a live icon or a live highlight. No coordinate here is invented: the slot positions come
 * from {@link ForgingMenu}, which measured them off the same image, so a well and the hitbox sitting
 * in it cannot drift apart.
 *
 * <h2>Two passes, two coordinate systems</h2>
 * Vanilla gives a container screen three hooks worth using, and each is in a different space:
 * <ul>
 *   <li>{@link #extractBackground} runs before anything else and is in <b>screen</b> space, so the frame
 *       texture is blitted at {@code leftPos/topPos}.</li>
 *   <li>{@link #extractLabels} runs inside {@code AbstractContainerScreen.extractContents}'s
 *       {@code pose().translate(leftPos, topPos)}, which it pops on the way out. Everything drawn there is
 *       in <b>frame</b> space - the same space {@code Slot.x/y} is written in - so no coordinate below adds
 *       the frame offset. Vanilla calls the hook "labels" because two labels are all it draws; the whole
 *       frame is the same kind of content and belongs in the same space.</li>
 *   <li>{@link #extractTooltip} runs after that translate has been popped, in <b>screen</b> space. A
 *       tooltip is positioned from the cursor and flushed in a later pass, so its coordinates are screen
 *       coordinates wherever it is queued from - which is what lets the grid tooltips live here beside the
 *       slot tooltip vanilla already sets.</li>
 * </ul>
 * Mixing the spaces is what makes a surface look shifted - and it is easy to do, because both use the same
 * numbers. Drawing the frame from {@code extractRenderState}, as this screen used to, means every single
 * coordinate there has to add the frame offset by hand.
 */
public final class ForgingScreen extends AbstractContainerScreen<ForgingMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/forging_table.png");

    /**
     * The selector options, drawn as the vanilla stonecutter draws its recipe buttons.
     *
     * <p>Three states, three sprites, and one of them is always drawn - a plain option included.
     *
     * <p>The stonecutter's sprite is 16 wide and is drawn 16 apart, because its own cell pitch <em>is</em>
     * 16. Here the cell is {@link ForgingMenu#CELL}, so the sprite is taken to the full cell width
     * instead: at 16 it would leave a two pixel stripe of the recess showing down every option. The
     * height and the one-pixel rise are the stonecutter's, which is what puts a 16x16 icon in the
     * middle of the button.
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
     * The selector scrollbars, taken from the vanilla stonecutter.
     *
     * <p>Stonecutter and this surface differ in two numbers, and both are the layout's:
     * <ul>
     *   <li>it lists four recipes per row, this lists three. {@link ForgingMenu#CELLS} replaces its
     *       hard-coded four everywhere the column count appears - the visible count, the offscreen
     *       row count, and the stride between one scroll row and the next.</li>
     *   <li>its scroller is drawn <em>inside</em> a 54px track at x=119; here the texture draws the
     *       bar in the gutter to the <em>right of</em> the recess, a full recess-width further out.
     *       See {@link #scrollbar}.</li>
     * </ul>
     * Everything else - the sprite size, the travel, the drag maths - is the stonecutter's unchanged.
     */
    private static final int SCROLLER_WIDTH = 12;
    private static final int SCROLLER_HEIGHT = 15;
    /**
     * The track the scroller slides in, and how far it travels.
     *
     * <p>54, the stonecutter's own number - not the recess's 76. The sprite is 12x15 and is drawn at
     * its natural size; giving it the whole recess instead would <em>stretch</em> it, because
     * {@code blitSprite} scales the sprite to the rectangle it is handed.
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
     * {@code C6C6C6}. Measured off the image rather than derived: METER_X is the left border's own
     * column, so the usable span is what is left after both of them.
     */
    private static final int METER_X = 80;
    private static final int METER_Y = 76;
    private static final int METER_W = 162;
    private static final int METER_INNER_X = METER_X + 1;
    private static final int METER_INNER_W = METER_W - 2;
    private static final int METER_H = 4;
    /**
     * How far a value mark reaches past the trough.
     *
     * <p>A mark that stopped at the trough's border would read as something the trough clips; crossing it
     * makes it read as a mark on a scale. The frame plate is the same {@code C6C6C6} as the interior, so
     * there is nothing else out there to collide with.</p>
     */
    private static final int METER_MARK_OVERHANG = 2;
    /**
     * The four colours, chosen against that {@code C6C6C6} interior: the green is the one the fill used
     * before, so it is already known to read on this texture, and the gray is dark enough to be a mark
     * rather than a shading.
     */
    private static final int METER_TARGET = 0xFF3B8D3B;
    private static final int METER_ZERO = 0xFF6E6E6E;
    private static final int METER_VALUE = 0xFFD63A3A;
    private static final int METER_PREDICTED = 0xFFE8D44D;
    /**
     * The readout sits in the grey band between the meter and the first step row, not on the meter's
     * own line: that band is only three pixels tall, and text drawn on it runs into the step cells
     * underneath. The two captions below it start at {@link #STEP_Y}.
     */
    private static final int READOUT_Y = 85;
    private static final int STEP_X = 135;
    private static final int STEP_Y = 98;
    private static final int STEP_ROW_PITCH = 22;
    private static final int STEP_CELL_PITCH = 18;
    /**
     * What an unset cell of a step row is drawn as.
     *
     * <p>A row is always six cells wide, so an empty one is not nothing to show - it is a step that has
     * not been taken yet, or a position the pattern does not ask for. Drawing a hole leaves the row's
     * alignment to be guessed at; the barrier says the cell is part of the row and deliberately has no
     * value.</p>
     */
    private static final ItemStack EMPTY_STEP = new ItemStack(Items.BARRIER);
    /**
     * How far the first cell column starts inside its recess: the left recess starts at column 7 and
     * its cells at 8.
     */
    private static final int GRID_INSET = 1;
    /**
     * The action buttons, and the cells in the row under them.
     *
     * <p>Named rather than repeated because three things have to agree on them: the two buttons, the
     * cancel button directly below the first, and the step count directly below the second.</p>
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
     * The scroll offset of each grid, as a fraction of the list, exactly as the stonecutter keeps it.
     * The first visible cell is derived from this rather than stored, so the pixel position and the
     * content can never disagree.
     */
    private float blueprintOffs;
    private float methodOffs;
    /**
     * Which scrollbar is being dragged: 0 none, 1 blueprint, 2 method.
     */
    private int dragging;
    private Button useBlueprint, useMethod, cancel;

    /**
     * The two picks, and the whole of the selection model.
     *
     * <p>They are ids rather than list positions, because a position is a fact about a list that moves:
     * placing a second manual shifts every index after it, and a stored index would silently come to
     * mean a different blueprint. An id either is still offered or is not, which is a question this
     * screen can answer from the list it is already drawing.</p>
     *
     * <p>Neither one is sent anywhere on its own. A pick costs nothing, sends nothing and cannot be
     * refused; only the two buttons turn a pick into a request. That is what keeps blueprints and
     * methods independent - picking a blueprint is not starting a session, so the method list does not
     * have to wait for one.</p>
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
     * The buttons follow both the session and the picks, and a pick is made by a click rather than by a
     * packet, so they are re-checked every tick rather than only when something arrives.
     */
    @Override
    protected void containerTick() {
        super.containerTick();
        this.refreshButtons();
    }

    /**
     * Asks the server to open a session for the blueprint picked in the left grid.
     *
     * <p>These are requests, not calls: the screen only ever runs on the client, where there is no
     * {@code ServerPlayer} to act on. {@code ForgingActionC2SPayload} is the existing channel for
     * exactly this, and the server re-checks the id against its own list before it does anything, so
     * nothing here is trusted.
     *
     * <p>The pick is re-resolved against the list rather than sent raw, so the button can never name
     * something the grid has stopped showing - a manual removed from its slot takes its blueprint out
     * of the list, and pressing with a stale id would just be refused.
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
     * Asks the server to abandon the session.
     *
     * <p>It names nothing, because there is only ever one session per table and the server resolves the
     * table from the menu this player has open. What cancelling <em>costs</em> is the server's decision,
     * not the screen's: today everything goes back, and the policy behind that is replaceable without the
     * client knowing.</p>
     */
    private void cancel() {
        ClientPacketDistributor.sendToServer(ForgingActionC2SPayload.cancel());
    }

    /**
     * The pick, if the list still offers it, otherwise null.
     *
     * <p>A pick outlives the entry it names - a hammer can be taken out of its slot - so it is never
     * read raw. This is the one place that question is asked, on both the render and the press path.
     */
    private Identifier picked(List<Entry> entries, Identifier id) {
        if (id == null) return null;
        for (Entry entry : entries)
            if (entry.id().equals(id)) return id;
        return null;
    }

    /**
     * A button is enabled exactly when pressing it would name something the server accepts.
     *
     * <p>The server checks all of this again - this only keeps the player from pressing something that
     * cannot work. A blueprint needs a live pick, no session already running, and its materials in the
     * input slots; a method needs a live pick and a session to strike. The material half is not a
     * convenience: starting consumes the declared amounts, so a button that lit up without them would
     * only ever produce a refusal.</p>
     */
    private void refreshButtons() {
        Identifier blueprint = this.picked(this.blueprintEntries(), this.selectedBlueprint);
        if (this.useBlueprint != null)
            this.useBlueprint.active = !this.menu.active() && blueprint != null && this.menu.materialsCovered(blueprint);
        if (this.useMethod != null)
            this.useMethod.active = this.menu.active() && this.picked(this.methodEntries(), this.selectedMethod) != null;
        // Nothing is locked and nothing has been consumed when no session runs, so there is nothing to
        // cancel and nothing a cancel could cost.
        if (this.cancel != null) this.cancel.active = this.menu.active();
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        // Screen space: this runs before extractContents moves the origin.
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 512, 256);
    }

    /**
     * Everything the frame draws, in the frame's own coordinates.
     *
     * <p>See the class comment for why nothing here adds the frame offset. The order is the order they stack:
     * the two labels with {@code super}, then the recessed grids and the step rows, then the meter on top.
     * The picks are resolved once each and handed to the two places that draw from them, so a highlight and
     * the prediction beside it can never disagree about which method is picked.</p>
     *
     * <p>Only the bar and the two grids are always drawn. Everything else - the two readouts, the step rows,
     * their captions - describes a session, so with none running the frame shows the trough with its zero mark
     * and nothing that would read as a measurement of something that is not happening.</p>
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
     * What the picked method would do to the value, or null when nothing is picked.
     *
     * <p>The meter's yellow line and nothing else reads this. It comes from the method's own
     * {@code value_delta} rather than from any table the session keeps, because the point of drawing it
     * before the strike is that the strike has not happened yet.</p>
     */
    private Integer deltaOf(Identifier method) {
        if (method == null || this.minecraft.level == null) return null;
        return MxtDatapackRegistries.get(this.minecraft.level.registryAccess(), MxtResourceKeys.FORGING_METHOD, method)
                .map(ForgingMethod::valueDelta).orElse(null);
    }

    /**
     * The two grids' tooltips, beside whatever slot tooltip vanilla found.
     *
     * <p>Nothing here is a slot, so neither grid gets a tooltip for free, and both are worth having: the
     * blueprint one says what the piece will cost before anything is spent, the method one says what a
     * strike would do to the value before the step is committed.</p>
     *
     * <p>{@code super} runs first and the queue keeps the first tooltip of a frame, so a slot's own tooltip
     * always wins. That is the right precedence and also an unreachable one - the grids sit in the recesses,
     * which are not over any slot.</p>
     */
    @Override
    protected void extractTooltip(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        this.blueprintTooltip(graphics, this.blueprintEntries(), mouseX, mouseY);
        this.methodTooltip(graphics, this.methodEntries(), mouseX, mouseY);
        this.stepTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Which method one of the step cells holds.
     *
     * <p>A cell draws an icon and nothing else - the same icon the method grid uses - so it names a method only
     * to someone who already knows the icons. Both rows are read to decide whether a session is finished, which
     * makes "what is that step" worth answering rather than leaving the player to match pictures by eye.</p>
     *
     * <p>The registry id comes out of the same two calls the cells are drawn from, so a cell cannot describe a
     * different method from the one it is showing. No placeholder answers anything: a barrier is the absence of
     * a step, not a step.</p>
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
     * The registry id in the step cell under the cursor, or {@link ForgingMenu#NONE}.
     *
     * <p>Row first, then position, so the two rows are one definition rather than two loops that have to agree
     * about where a cell is. The width is the cell pitch, not the icon's 16, so the cells tile with no dead
     * strip between them to hover over.</p>
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
     * The hovered blueprint's material list, as a tooltip on the grid.
     *
     * <p>The grid is not made of slots, so nothing shows this for free, and it is the one thing a player
     * wants before committing anything: what this blueprint is about to take, and which of it the input
     * slots are still short of. A satisfied entry is green with a tick, a missing one red with a cross -
     * the same markers {@code ItemBindingTooltipAppender} uses for the conditions it reports.</p>
     *
     * <p>Each line carries the count as well, because "missing" and "not enough" are different problems:
     * one iron where three are wanted is red, and the numbers are what say why.</p>
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
     * The hovered method's name and what it does to the value, as a tooltip on the grid.
     *
     * <p>Same reasoning as the blueprint tooltip - the grid is not made of slots, so nothing shows this
     * for free - but the two things it prints are the two the cell cannot say. The icon only identifies
     * the method to someone who already knows the icons, and the delta is invisible until the strike has
     * landed, at which point the step has already been spent and cannot be taken back.</p>
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
     * One option: its background and its item, drawn together.
     *
     * <p>They belong in one method because they are one widget - a button whose label happens to be an
     * item. Splitting them is how the two drift apart: the sprite's one-pixel rise is only correct
     * <em>because</em> the icon is placed against it, and nothing about either is meaningful alone.
     *
     * <p>The numbers are the stonecutter's. Its button sprite is 18 tall and is drawn one pixel above
     * the row it belongs to, so an icon at the row's own y sits in the middle of it; the icon is a
     * 16x16 sprite, so a cell of {@link ForgingMenu#CELL} centres it with one pixel either side. The
     * one place the width is not the stonecutter's: its sprites are 16 wide because its cells are 16,
     * and taking these to the cell width instead stops a two pixel stripe of the recess showing down
     * the right of every button.
     */
    private void option(GuiGraphicsExtractor graphics, int x, int y, ItemStack icon,
                        boolean selected, boolean hovered) {
        Identifier sprite = selected ? OPTION_SELECTED : hovered ? OPTION_HIGHLIGHTED : OPTION;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y - (OPTION_H - ForgingMenu.CELL) / 2, OPTION_W, OPTION_H);
        if (icon.isEmpty()) return;
        int inset = (ForgingMenu.CELL - ICON) / 2;
        graphics.item(icon, x + inset, y + inset);
    }

    /**
     * The index of the first cell the grid shows, from the scroll fraction.
     *
     * <p>The stonecutter's line, with {@link ForgingMenu#CELLS} where it writes a hard-coded four:
     * the offset picks a row, and the row is multiplied by the column count to get an index.
     */
    private int startIndex(float offs, int entries) {
        return (int) (offs * this.getOffscreenRows(entries) + 0.5D) * ForgingMenu.CELLS;
    }

    /**
     * How many rows of the list do not fit. The stonecutter writes {@code (n + 4 - 1) / 4 - 3} for
     * four columns and three visible rows; {@link ForgingMenu#CELLS} is both of those numbers here.
     *
     * <p>Clamped at zero, because the stonecutter only ever asks once it knows the bar is active.
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
     * The vanilla stonecutter scroller, verbatim: the sprite goes at the measured column and row, and the
     * scroller slides down that column by the scroll fraction.
     *
     * <p>Two things differ from the stonecutter, and only these two:
     * <ul>
     *   <li>its column is the magic 119, chosen to land on the track painted inside its own recipe
     *       list. Here the texture paints the bar in the gutter <em>right of</em> the recess, which is
     *       the measured column {@link ForgingMenu#SCROLLBAR_X}.</li>
     *   <li>its track is anchored to its own recipe row; here it is anchored to the recess the texture
     *       drew. The height is the same 54, because the visible list is three rows either way, and the
     *       sprite is drawn at its natural 12x15 rather than scaled to the track.</li>
     * </ul>
     */
    private void scrollbar(GuiGraphicsExtractor graphics, int barX, float offs, int entries) {
        int offset = (int) (SCROLL_TRAVEL * offs);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.isScrollBarActive(entries) ? SCROLLER : SCROLLER_DISABLED, barX + 1, SCROLL_TOP + 1 + offset, SCROLLER_WIDTH, SCROLLER_HEIGHT);
    }

    /**
     * One six-step row.
     *
     * <p>Right-aligned within the row, because a finish pattern describes the <em>last</em> steps of
     * a session: the last cell is the most recent step in both rows.</p>
     *
     * <p>Only the required row fills its gaps. A barrier there says something the icon cannot: this position
     * is part of the pattern's six and the pattern asks for nothing in it. The current row needs no such
     * statement - a step that has not been taken yet is simply not drawn, and a row of barriers under every
     * fresh session would read as six things that went wrong.</p>
     */
    private void stepIcons(GuiGraphicsExtractor graphics, int y, boolean target) {
        List<ItemStack> icons = this.stepIcons(target);
        for (int position = 0; position < ForgingMenu.SUFFIX_STEPS && position < icons.size(); position++) {
            ItemStack icon = icons.get(position);
            if (icon.isEmpty() && !target) continue;
            graphics.item(icon.isEmpty() ? EMPTY_STEP : icon, STEP_X + position * STEP_CELL_PITCH, y);
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
     * The two numbers: the value under the meter, and the step count in the cell under the method button.
     *
     * <p>They used to share a line, opposite each other. Two numbers side by side read as one readout and
     * they are not the same kind of thing: the value is where the piece is, the step count is how much of
     * the session's budget went into getting it there - so the count sits under the button that increments
     * it, where it reads as a counter rather than as a second gauge.</p>
     */
    private void readouts(GuiGraphicsExtractor graphics) {
        graphics.text(this.font, Component.translatable("screen.mxt.forging.meter.value", this.menu.meterValue()), ForgingMenu.INVENTORY_X, READOUT_Y, TEXT, false);
        Component steps = Component.translatable("screen.mxt.forging.steps", this.menu.steps());
        graphics.text(this.font, steps, ACTION_RIGHT_X + (ACTION_W - this.font.width(steps)) / 2, ACTION_SECOND_ROW_Y + ACTION_TEXT_INSET, TEXT, false);
    }

    /**
     * The meter: the texture draws the trough and its border, so this adds the target band and the three
     * marks that turn the trough into a scale.
     *
     * <p>The green block is the band the value has to land in. The gray line is zero - the one fixed place
     * on the bar: the trough's ends are limits rather than places, so without it "how far left am I" has no
     * answer until the value lands somewhere recognisable. The red line is where the value is; the yellow
     * one is where the picked method would put it, which is the whole reason the meter is worth drawing: a
     * strike is a decision, not a reveal.</p>
     *
     * <p>The zero mark is a property of the <em>scale</em>, not of the session, so it is drawn whenever there
     * is a scale to draw it on - which is as soon as a blueprint is picked, before anything is committed.
     * Everything else is a reading, and there is nothing to read without a session.</p>
     *
     * <p>Drawn band first and marks in order of importance, so when two land on the same column the one that
     * matters more is the one left visible - the value over the prediction, and both over zero. The value
     * landing exactly on zero is the one case where zero goes under, and it is the right way round: at that
     * moment the reading and the reference are the same number.</p>
     *
     * <p>{@code predictedDelta} is null when nothing is picked, and the yellow line is then simply absent. A
     * prediction that would leave the trough is clamped to its end rather than hidden: the mark pinned at the
     * edge is the honest picture of a step that would be refused, and a line that vanished would leave the
     * player guessing whether they still had a pick.</p>
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
     * The bounds the bar is drawn against, or null when nothing defines them.
     *
     * <p>While a session runs they come from its plan, which was snapshotted when it started: a datapack
     * reload must not move a bar a player is already reading. Before one starts there is no plan, and the
     * picked blueprint's own meter is what this bar is about to be - which is the whole of what the zero
     * mark needs to be placed.</p>
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
     * Where a value sits on the trough, clamped to its interior.
     *
     * <p>The interior's last column belongs to the scale's maximum, so the span is one pixel shorter than the
     * interior: mapping the maximum onto {@code METER_INNER_X + METER_INNER_W} would put the mark on the
     * texture's own right border.</p>
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
     * One grid entry: the id it stands for, and the stack that draws it.
     *
     * <p>The two travel together on purpose. The grid is indexed by position, and a click has to name
     * the entry that is on screen at that position - keeping a list of ids beside a list of icons
     * would make that a promise about two iterators agreeing, which is exactly the kind of promise
     * that breaks silently when one of them starts skipping entries.
     */
    private record Entry(Identifier id, ItemStack icon) {
    }

    /**
     * Every offered blueprint, with the stack that stands for it: what the manuals in the blueprint
     * slots provide, and nothing else.
     *
     * <p>An id always yields an entry, even when its result item cannot be resolved: the entry is what
     * a click resolves to, so dropping it would shift every later position and make the grid disagree
     * with itself. The icon is simply empty in that case, and the cell draws as a blank button.
     */
    private List<Entry> blueprintEntries() {
        if (this.minecraft.level == null) return List.of();
        List<Entry> entries = new ArrayList<>();
        for (Identifier id : this.menu.blueprints()) {
            ItemStack icon = MxtDatapackRegistries.get(this.minecraft.level.registryAccess(),
                            MxtResourceKeys.FORGING_BLUEPRINT, id)
                    .flatMap(blueprint -> BuiltInRegistries.ITEM.getOptional(blueprint.result()))
                    .map(ItemStack::new).orElse(ItemStack.EMPTY);
            entries.add(new Entry(id, icon));
        }
        return entries;
    }

    /**
     * Every offered method, with its icon: what the tools in the tool slots unlock, narrowed by what the
     * picked blueprint allows.
     *
     * <p>The pick is resolved first rather than read raw. A manual taken out of its slot takes its
     * blueprint out of the list, and a pick that is no longer offered must not go on filtering the method
     * grid - it has to fall back to "nothing is restricting this", which is the same state as having
     * picked nothing at all.</p>
     */
    private List<Entry> methodEntries() {
        if (this.minecraft.level == null) return List.of();
        Identifier blueprint = this.picked(this.blueprintEntries(), this.selectedBlueprint);
        List<Entry> entries = new ArrayList<>();
        for (Identifier id : this.menu.methods(blueprint)) {
            ItemStack icon = MxtDatapackRegistries.get(this.minecraft.level.registryAccess(),
                            MxtResourceKeys.FORGING_METHOD, id)
                    .map(ForgingMethod::iconStack).orElse(ItemStack.EMPTY);
            entries.add(new Entry(id, icon));
        }
        return entries;
    }

    private List<ItemStack> stepIcons(boolean target) {
        List<ItemStack> icons = new ArrayList<>(ForgingMenu.SUFFIX_STEPS);
        boolean usable = this.menu.active() && this.minecraft.level != null;
        Registry<ForgingMethod> registry = usable ? this.minecraft.level.registryAccess().lookupOrThrow(MxtResourceKeys.FORGING_METHOD) : null;
        for (int position = 0; position < ForgingMenu.SUFFIX_STEPS; position++) {
            if (!usable) {
                icons.add(ItemStack.EMPTY);
                continue;
            }
            int id = target ? this.menu.targetStep(position) : this.menu.historyStep(position);
            icons.add(ForgingMenu.isNone(id) ? ItemStack.EMPTY
                    : registry.get(id).map(holder -> holder.value().iconStack()).orElse(ItemStack.EMPTY));
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
     * Picks whatever is under the cursor.
     *
     * <p>A pick is not an action. It used to be: a click on a blueprint sent a session request and a
     * click on a method struck immediately, which made the two lists into two triggers and left nothing
     * that could simply be highlighted. Now the click moves a cursor and the buttons do the acting.</p>
     *
     * <p>The blueprint pick is frozen while a session runs, because the table has locked a blueprint and
     * highlighting a different one would claim something the server is not doing. The method pick is not
     * frozen: what may be struck is decided by the tools, so the pick is just a cursor and the server
     * re-checks it anyway.</p>
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
     * The entry under the cursor in one grid, or null when the cursor is outside it or over a cell the
     * list does not reach.
     *
     * <p>One definition for both the click and the tooltip, so a cell that highlights is a cell that can
     * be picked and a cell whose tooltip opens - three things that would otherwise be three chances to
     * place the same rectangle a pixel apart.</p>
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
