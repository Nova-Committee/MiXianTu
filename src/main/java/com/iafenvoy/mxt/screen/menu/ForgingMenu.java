package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMaterial;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.item.block.entity.ForgingTableBlockEntity;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.forging.ForgingPlan;
import com.iafenvoy.mxt.runtime.forging.ForgingSession.Snapshot;
import com.iafenvoy.mxt.runtime.forging.ForgingSurface;
import com.iafenvoy.mxt.runtime.forging.ForgingWorkstationService;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The forge table's vanilla container menu.
 *
 * <h2>Slots</h2>
 * The nineteen machine slots of {@link ForgingSurface}, then the player inventory. The coordinates
 * here are the screen-space values the surface is painted against; {@code ForgingScreen} owns the
 * frame those coordinates are measured in, and the two are kept in step by the shared constants at
 * the bottom of this file.
 *
 * <h2>State that is not a slot</h2>
 * The session - meter value, step count, the required steps and the steps taken - has no container
 * of its own. It is mirrored onto {@link DataSlot}s, which is the vanilla channel for exactly this:
 * the server writes them in {@link #broadcastChanges()} and the client reads the same indices
 * without knowing what produced them. The server never trusts a value that arrives from the client.
 * The only client input this menu accepts is a request naming one id, and the server re-resolves and
 * re-validates it against its own lists.
 *
 * <h2>What is not synced here</h2>
 * Neither selector's highlight. A pick is the player's cursor over a list they can already see, made
 * and unmade freely, and only pressing a button turns it into a request - so it lives in
 * {@code ForgingScreen} and never crosses the wire. Keeping it there is also what lets the method list
 * exist before a session does: it is filtered by the <em>picked</em> blueprint, not by a locked one.
 *
 * <h2>Reaching the table</h2>
 * Only for the session. The table is reached through {@link ContainerLevelAccess}, which is the vanilla
 * handle for "the block this menu belongs to" - one is created by
 * {@link ForgingTableBlockEntity#createMenu}. The server is handed a real access; the client is handed
 * {@link ContainerLevelAccess#NULL}, whose every lookup is empty. That asymmetry is deliberate and is the
 * whole reason this class holds no block entity: a client-side read of the server's table is impossible
 * by construction rather than by a null check, so every session value has to be published to a data slot
 * first. The two selector lists are the exception, and they do not need the table: both are pure
 * functions of what the client already has - its copy of the slot contents, plus this side's own
 * datapack registries and, for the method list, the blueprint the player has picked. See
 * {@link #blueprints()} and {@link #methods(Identifier)}.
 */
public final class ForgingMenu extends AbstractContainerMenu {
    /**
     * Machine slots on the surface, player slots in the menu.
     */
    public static final int MACHINE_SLOTS = ForgingSurface.TOTAL_SLOTS;
    public static final int PLAYER_START = MACHINE_SLOTS;


    /**
     * The six-step suffix is fixed size: a session keeps at most six history entries and a finish
     * pattern is either empty or exactly six long.
     */
    public static final int SUFFIX_STEPS = 6;

    /**
     * Sentinel for "no entry". Registry ids are never negative.
     */
    public static final int NONE = -1;

    // ---- synced indices
    //
    // Session state only. Neither selector's highlight is here, and that is deliberate: a pick is the
    // player's own cursor over a list they can see, made and unmade without telling anyone, and only
    // the press of a button turns it into a request. Keeping it on the client is what stops the two
    // lists from having to defer to the server about what "currently selected" means - and stops a
    // blueprint pick from having to be a session before the method list can exist. See ForgingScreen.
    private static final int IDX_ACTIVE = 0;
    private static final int IDX_VALUE = 1;
    private static final int IDX_STEPS = 2;

    /**
     * The shortest run the running session's frozen plan allows, mirrored to the client.
     *
     * <p>This is the plan's {@code optimal_steps}: the length of the best forge, computed by its breadth
     * first search at load time and frozen into the session's plan. It is the number the quality formula
     * subtracts from the player's own step count - {@code extra_steps = actual - optimal} - so it is what
     * "how far from a perfect craft was that" is measured in.</p>
     *
     * <p><b>Reserved, not yet read.</b> No screen code reads it today: the two readouts show the meter
     * value and the step count, and the number a player actually sees is on the finished item, in its
     * {@code mxt:forging_result} component. The field is kept wired end to end anyway - written in
     * {@link #syncFromTable()}, carried by the data slot, exposed by {@link #optimalSteps()} - as the
     * prepared place for a readout like "steps 4 / shortest 3", which is then one label and two lang keys
     * away rather than a new synced field. It is also the kind of number a designer may prefer to keep off
     * the screen, since it is the length of the shortest solution; that is a display decision, and this
     * comment is the note that the plumbing is already there whichever way it goes.</p>
     *
     * <p>Deleting it instead would renumber every index behind it, and the finish-pattern rows read their
     * positions from those indices - so a slot that costs a few bytes per session is the cheaper of the
     * two mistakes.</p>
     */
    private static final int IDX_OPTIMAL = 3;

    private static final int IDX_METER_MIN = 4;
    private static final int IDX_METER_MAX = 5;
    private static final int IDX_TARGET_MIN = 6;
    private static final int IDX_TARGET_MAX = 7;
    private static final int IDX_REQUIRED = 8;
    private static final int IDX_TARGET_START = 9;
    private static final int IDX_HISTORY_START = IDX_TARGET_START + SUFFIX_STEPS;
    private static final int SYNCED = IDX_HISTORY_START + SUFFIX_STEPS;

    // ---- frame geometry, shared with ForgingScreen
    //
    // Every number here is a pixel in assets/mxt/textures/gui/forging_table.png. The texture is the
    // layout: a slot's hitbox has to land on the cell already drawn for it, so the two cannot be
    // allowed to drift apart and the screen reads these instead of repeating them.

    /**
     * The two selector cell grids: 3 columns of 18px cells, 19px pitch, inside the recessed areas.
     */
    public static final int CELLS = 3;
    public static final int CELL = 18;
    public static final int CELL_PITCH = 19;
    public static final int BLUEPRINT_GRID_X = 7;
    public static final int METHOD_GRID_X = 250;
    public static final int GRID_Y = 17;

    /**
     * The two recessed selector areas, measured off the texture.
     *
     * <p>Left recess: content columns {@code 8..55}, brown right wall {@code 56}. Right recess:
     * content {@code 251..298}, wall {@code 299}. One column of plate grey follows each wall before
     * the scrollbar's gutter begins.
     */
    public static final int RECESS_Y = 17;
    public static final int RECESS_W = 49;
    public static final int RECESS_H = 76;

    /**
     * Where the scrollbar sprite's top-left corner goes.
     *
     * <p>These are not derived from the recess: the texture draws the bar in the gutter <em>right
     * of</em> the recess - a plate-grey column, then the bar's own dark edge - so the sprite origin
     * is four pixels past the recess's right wall. Asserting the measured column directly is what
     * keeps the sprite on the bar the texture already drew; subtracting a sprite width from the
     * recess, as the stonecutter does, would put it a full sprite width away, inside the recess.
     */
    public static final int SCROLLBAR_X = 58;
    public static final int SCROLLBAR_X_RIGHT = 301;

    /**
     * The three machine slot columns. The slot columns use an 18px pitch, the grids 19px.
     */
    public static final int MACHINE_PITCH = 18;
    public static final int SLOT_TOP = 18;

    /**
     * The 4x3 material grid and the result, which sits level with the middle row.
     */
    public static final int INPUT_X = 81;

    /**
     * The player inventory band, inside its own tab in the texture.
     */
    public static final int INVENTORY_X = 81;
    public static final int INVENTORY_Y = 152;
    public static final int HOTBAR_Y = 210;

    private final Container machine;
    private final ContainerLevelAccess access;
    /**
     * The opener. The client half needs it for one thing only: the registries it resolves the two
     * selector lists and the step icons against. It is never asked for the level's blocks.
     */
    private final Player player;

    private final DataSlot[] synced = new DataSlot[SYNCED];

    public ForgingMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public ForgingMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(MxtMenus.FORGING_TABLE.get(), containerId);
        this.access = access;
        this.player = inventory.player;
        // The surface's own container on the server, reached through the access rather than handed in;
        // a stand-in on the client, where the access is empty and where the container content packet
        // fills this container in as the slots receive their items - see Slot#set.
        this.machine = this.fromTable(ForgingTableBlockEntity::forgingContainer, new SimpleContainer(MACHINE_SLOTS));
        for (int index = 0; index < SYNCED; index++) {
            this.synced[index] = DataSlot.standalone();
            this.addDataSlot(this.synced[index]);
        }
        this.addMachineSlots();
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                this.addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * MACHINE_PITCH, INVENTORY_Y + row * MACHINE_PITCH));
        for (int column = 0; column < 9; column++)
            this.addSlot(new Slot(inventory, column, INVENTORY_X + column * MACHINE_PITCH, HOTBAR_Y));
    }

    private void addMachineSlots() {
        for (int row = 0; row < ForgingSurface.BLUEPRINT_SLOTS; row++)
            this.addSlot(new MachineSlot(ForgingSurface.BLUEPRINT_START + row, INVENTORY_X, SLOT_TOP + row * MACHINE_PITCH));
        for (int row = 0; row < ForgingSurface.TOOL_SLOTS; row++)
            this.addSlot(new MachineSlot(ForgingSurface.TOOL_START + row, 225, SLOT_TOP + row * MACHINE_PITCH));
        for (int index = 0; index < ForgingSurface.INPUT_SLOTS; index++)
            this.addSlot(new MachineSlot(ForgingSurface.INPUT_START + index,
                    INPUT_X + 27 + index % ForgingSurface.INPUT_COLUMNS * MACHINE_PITCH,
                    SLOT_TOP + index / ForgingSurface.INPUT_COLUMNS * MACHINE_PITCH));
        // The result is written by the service and never by hand.
        this.addSlot(new MachineSlot(ForgingSurface.OUTPUT_SLOT, 198, SLOT_TOP + MACHINE_PITCH));
    }

    /**
     * One slot on the machine surface.
     *
     * <p>This exists because {@code Container#canPlaceItem} is <em>not</em> what a player click asks.
     * A click goes to {@code AbstractContainerMenu#clicked}, which asks the {@link Slot}, and a plain
     * {@code Slot} answers yes to everything - so the block entity's filter was only ever consulted by
     * hoppers and shift-clicks, and the surface accepted anything a hand dragged in. The rule is
     * therefore asked here too, and it is the same rule object the block entity uses:
     * {@link ForgingSurface#canPlace}. Two copies would be free to disagree, and the disagreement would
     * look like a slot that takes an item and then spits it back.
     *
     * <p>The one thing this cannot delegate is the container: {@link #machine} is the surface's own on
     * the server and a stand-in on the client, and the client's is filled by the container content
     * packet. See the constructor.
     */
    private final class MachineSlot extends Slot {
        MachineSlot(int index, int x, int y) {
            super(ForgingMenu.this.machine, index, x, y);
        }

        @Override
        public boolean mayPlace(@NonNull ItemStack stack) {
            return ForgingMenu.this.accepts(this.getContainerSlot(), stack);
        }

        /**
         * The surface is not a chest: what it holds is locked in for as long as a session runs, and the
         * session's own snapshot is what a cancel or a failure returns. Taking something out mid-session
         * would leave the return with nothing to give back.
         */
        @Override
        public boolean mayPickup(@NonNull Player player) {
            return ForgingSurface.canTake(this.getContainerSlot(), ForgingMenu.this.sessionLocked())
                    && super.mayPickup(player);
        }

    }

    /**
     * Runs one read against the forge table, or returns {@code fallback} when there is none to read.
     *
     * <p>This is the only way this class touches the block, and it goes through the menu's
     * {@link ContainerLevelAccess} every time rather than through a stored reference. Two things follow
     * from that. A table that was broken while its menu was still open yields {@code fallback} instead
     * of a stale object, because the lookup is redone each time; and nothing here keeps a block entity
     * - with its container, its level and its session - alive after the block is gone.
     *
     * <p>The client's access answers empty, always: see the class comment for why that is the point
     * rather than a limitation.
     */
    private <T> T fromTable(Function<ForgingTableBlockEntity, T> reader, T fallback) {
        return this.access
                .evaluate((level, pos) -> level.getBlockEntity(pos) instanceof ForgingTableBlockEntity table
                        ? Optional.ofNullable(reader.apply(table))
                        : Optional.<T>empty())
                .flatMap(Function.identity())
                .orElse(fallback);
    }

    /**
     * The table this menu is acting on, resolved on demand, or null when there is none.
     *
     * <p>Server side only - the client's access is empty, so this is always null there. It exists for
     * the packet handler, which has a player and needs the block behind the menu that player has open.
     * Resolving it from the menu is what lets a forging request be keyed by "the table I am standing
     * at" rather than by a position the client names, which would mean publishing coordinates to the
     * client and then trusting them back.
     */
    public ForgingTableBlockEntity table() {
        return this.fromTable(table -> table, null);
    }

    /**
     * Whether the surface would accept a stack in a slot, by that slot's own rule.
     *
     * <p>The rule is {@link ForgingSurface#canPlace}, which sits with the layout it describes, so the
     * menu and the hopper path cannot enforce two different filters. It needs no block entity: the
     * locked blueprint is only consulted while the surface is unlocked, and those are the same
     * condition, so the server resolves it and the client simply has none. That is also what makes both
     * halves answer alike, which is what matters - {@code Slot#mayPlace} is the only thing a plain
     * click asks.
     */
    public boolean accepts(int index, ItemStack stack) {
        return ForgingSurface.canPlace(index, stack, this.active(),
                this.fromTable(ForgingTableBlockEntity::selectedBlueprint, null));
    }

    /**
     * Whether a session has locked the surface.
     *
     * <p>Read from the synced flag rather than from the block entity so it is the same answer on both
     * sides - the client half cannot reach the table, and a slot that allowed a pickup the server would
     * refuse is a desync the player sees as an item bouncing back.
     */
    public boolean sessionLocked() {
        return this.synced[IDX_ACTIVE].get() != 0;
    }


    // ------------------------------------------------------------------ the two lists
    /**
     * The blueprints offered: what the blueprint items in the blueprint slots provide, and nothing else.
     *
     * <p>Derived rather than published, on both halves. The input is the slot contents of the container
     * this side is showing, and the client has that - the container content packet fills its stand-in
     * container through the slots. It is derived through the same rule the server validates against
     * rather than reimplemented, so the two cannot disagree about what the items in the slots mean. A
     * client showing an id the server would refuse costs a refused request, never a wrong action.
     */
    public List<Identifier> blueprints() {
        return ForgingWorkstationService.selectableBlueprintIds(this.machine);
    }

    /**
     * The methods offered: what the tools in the tool slots unlock, narrowed by what the given blueprint
     * allows.
     *
     * <p>Two independent axes rather than two systems: the tools decide what this player can perform, the
     * blueprint decides what this piece may be made with, and a method has to be on both. A blueprint that
     * declares nothing - and a null id, which is what this is before anything is picked - restricts
     * nothing, so the grid fills from the tools alone.</p>
     *
     * <p>The blueprint is passed in rather than read from the session, so the list exists before a session
     * does. Both halves compute it the same way from the same two inputs - the container this side is
     * showing, and this side's registries - and the client has both.</p>
     */
    public List<Identifier> methods(Identifier blueprintId) {
        return ForgingWorkstationService.availableMethodIds(this.machine, this.player.level().registryAccess(), blueprintId);
    }

    /**
     * How many of one of a blueprint's declared materials the input slots hold, for the grid tooltip.
     */
    public int inputCount(ForgingMaterial entry) {
        return ForgingWorkstationService.availableCount(this.machine, entry);
    }

    /**
     * Whether the inputs cover a blueprint's whole material list.
     *
     * <p>The same rule {@code start} applies on the server, so a button disabled by this means a request
     * that would have been refused rather than a surprise. A blueprint that cannot be resolved counts as
     * uncovered: there is nothing to start.</p>
     */
    public boolean materialsCovered(Identifier blueprintId) {
        ForgingBlueprint blueprint = this.blueprint(blueprintId);
        return blueprint != null && ForgingWorkstationService.materialsCovered(this.machine, blueprint.input());
    }

    /**
     * A blueprint as this side's registries see it, or null when nothing is picked or the datapack no
     * longer has it.
     */
    public ForgingBlueprint blueprint(Identifier id) {
        return id == null ? null
                : MxtDatapackRegistries.get(this.player.level().registryAccess(), MxtResourceKeys.FORGING_BLUEPRINT, id).orElse(null);
    }

    public boolean active() {
        return this.synced[IDX_ACTIVE].get() != 0;
    }

    public int meterValue() {
        return this.synced[IDX_VALUE].get();
    }

    public int steps() {
        return this.synced[IDX_STEPS].get();
    }

    /**
     * The shortest run the plan allows, or {@code 0} when no session is running.
     *
     * <p>The client end of {@link #IDX_OPTIMAL}, which nothing reads yet - see that constant for what the
     * number is and why the field is kept.</p>
     */
    public int optimalSteps() {
        return this.synced[IDX_OPTIMAL].get();
    }

    public int meterMin() {
        return this.synced[IDX_METER_MIN].get();
    }

    public int meterMax() {
        return this.synced[IDX_METER_MAX].get();
    }

    public int targetMin() {
        return this.synced[IDX_TARGET_MIN].get();
    }

    public int targetMax() {
        return this.synced[IDX_TARGET_MAX].get();
    }

    public int requiredSuffixSteps() {
        return this.synced[IDX_REQUIRED].get();
    }

    /**
     * The registry id of the required step at one suffix position, or {@link #NONE}.
     */
    public int targetStep(int position) {
        return position < 0 || position >= SUFFIX_STEPS ? NONE : this.synced[IDX_TARGET_START + position].get();
    }

    /**
     * The registry id of the step taken at one suffix position, or {@link #NONE}.
     */
    public int historyStep(int position) {
        return position < 0 || position >= SUFFIX_STEPS ? NONE : this.synced[IDX_HISTORY_START + position].get();
    }

    public static boolean isNone(int registryId) {
        return registryId == NONE;
    }

    /**
     * Resolves a synced registry id back to its entry, through whichever registry access this side
     * has: the running server, or the client's own synchronised copy of the datapack registries.
     */
    public static Identifier methodId(Player player, int registryId) {
        if (registryId == NONE) return null;
        Registry<ForgingMethod> registry = player.level().registryAccess().lookupOrThrow(MxtResourceKeys.FORGING_METHOD);
        return registry.get(registryId).flatMap(holder -> holder.unwrapKey().map(ResourceKey::identifier)).orElse(null);
    }

    // ------------------------------------------------------------------ syncing

    /**
     * Pushes the session onto the data slots, then lets vanilla send whatever changed.
     */
    @Override
    public void broadcastChanges() {
        this.syncFromTable();
        super.broadcastChanges();
    }

    private void syncFromTable() {
        // Server only. Everything below is the server's answer, and it reaches the client through these
        // very slots, so a client that recomputed it here would overwrite what the packet had just put
        // there. The side check is the guard; the client's access being empty would stop it reaching the
        // table anyway, but it would happily write the fallbacks.
        if (this.player.level().isClientSide()) return;
        ForgingTableBlockEntity surface = this.table();
        if (surface == null) return;

        boolean active = surface.forgingState().active();
        Snapshot snapshot = surface.forgingState().session().orElse(null);
        ForgingPlan plan = surface.forgingState().plan().orElse(null);
        this.synced[IDX_ACTIVE].set(active ? 1 : 0);
        this.synced[IDX_VALUE].set(snapshot == null ? 0 : snapshot.value());
        this.synced[IDX_STEPS].set(snapshot == null ? 0 : snapshot.steps());
        // From the plan, because that is where the shortest run is computed and stored; the session only
        // carries its own progress. Both are dropped together, so this is zero exactly when `snapshot` is.
        this.synced[IDX_OPTIMAL].set(plan == null ? 0 : plan.optimalSteps());
        this.synced[IDX_METER_MIN].set(plan == null ? 0 : plan.meterMin());
        this.synced[IDX_METER_MAX].set(plan == null ? 0 : plan.meterMax());
        this.synced[IDX_TARGET_MIN].set(plan == null ? 0 : plan.targetMin());
        this.synced[IDX_TARGET_MAX].set(plan == null ? 0 : plan.targetMax());

        List<Identifier> pattern = plan == null ? List.of() : plan.finishPattern();
        int required = plan == null ? 0 : plan.requiredSuffixSteps();
        List<Identifier> history = snapshot == null ? List.of() : snapshot.history();
        this.synced[IDX_REQUIRED].set(required);
        for (int index = 0; index < SUFFIX_STEPS; index++) {
            // Both rows describe the same six positions and are right-aligned, so position five is the
            // most recent step in both and the two line up on it.
            this.synced[IDX_TARGET_START + index].set(requiredStep(pattern, index, required));
            this.synced[IDX_HISTORY_START + index].set(historyStep(history, index));
        }
    }

    /**
     * The registry id of the required step at display position {@code position}, or {@link #NONE}.
     *
     * <p>The rule is about the <em>last</em> {@code required} entries of the six-step pattern - see
     * {@code ForgingSession#canComplete} - so this shows exactly those, right-aligned, and leaves the
     * positions in front of them empty.</p>
     *
     * <p>It is deliberately not the window the history row uses. That one takes the last {@code count}
     * entries of a list that may be shorter than six; this one takes a suffix of a pattern that is always
     * six when it is used at all. Pushing the pattern through the history window shows its <em>first</em>
     * {@code required} steps instead, which is a sequence the server never asks for - and it lines up on
     * the same positions, so the row looks right while telling the player to strike the wrong steps.</p>
     */
    static int requiredStep(List<Identifier> pattern, int position, int required) {
        if (required <= 0 || pattern.size() != SUFFIX_STEPS) return NONE;
        int first = SUFFIX_STEPS - required;
        return position < first ? NONE : registryId(pattern.get(position));
    }

    /**
     * The registry id of the step taken at display position {@code position}, or {@link #NONE}.
     *
     * <p>The window is the <em>last</em> {@code history.size()} entries, so a session that has struck
     * fewer than six times shows its steps against the right-hand end of the row - the end the finish
     * pattern is compared against - rather than against the left.</p>
     */
    static int historyStep(List<Identifier> history, int position) {
        int source = position - (SUFFIX_STEPS - history.size());
        if (source < 0 || source >= history.size()) return NONE;
        return registryId(history.get(source));
    }

    /**
     * The integer registry id the client will resolve the icon from. Sent as a number rather than a
     * name because a data slot carries an int.
     *
     * <p>Server side only, which is worth stating because it is the one place on this path that still
     * uses a server-only registry handle: it is reached from {@link #syncFromTable}, which the client
     * returns from. A data slot is also only a signed short on the wire - see
     * {@code ClientboundContainerSetDataPacket} - which is why a registry id is safe here (they are
     * dense and small) and why a world coordinate would not have been.
     */
    private static int registryId(Identifier id) {
        if (id == null) return NONE;
        Registry<ForgingMethod> registry = MxtDatapackRegistries.registry(MxtResourceKeys.FORGING_METHOD);
        return registry.get(id).map(holder -> registry.getId(holder.value())).orElse(NONE);
    }

    // ------------------------------------------------------------------ interaction

    /**
     * No menu buttons.
     *
     * <p>There is nothing left for one to carry. A pick is local to {@code ForgingScreen} and is not
     * sent at all; the two presses name their entry's own id through {@code ForgingActionC2SPayload},
     * which the server re-resolves and re-validates. A button id would have had to be turned back into
     * an entry on the far side - and this menu declines every button on the client, so the press would
     * not even have been sent.
     */
    @Override
    public boolean clickMenuButton(@NonNull Player player, int id) {
        return false;
    }

    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        boolean moved = index < MACHINE_SLOTS
                ? this.moveItemStackTo(slot.getItem(), PLAYER_START, this.slots.size(), true)
                : this.moveItemStackTo(slot.getItem(), 0, MACHINE_SLOTS, false);
        if (!moved) return ItemStack.EMPTY;
        if (slot.getItem().isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return stillValid(this.access, player, MxtBlocks.FORGING_TABLE.get());
    }
}
