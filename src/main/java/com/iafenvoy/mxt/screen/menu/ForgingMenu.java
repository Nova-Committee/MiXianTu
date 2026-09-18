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
 * The forge table's menu: the nineteen machine slots of {@link ForgingSurface}, then the player inventory, at
 * the coordinates the screen paints against. The session has no container, so it is mirrored onto
 * {@link DataSlot}s by {@link #broadcastChanges()}: the client's {@link ContainerLevelAccess} answers empty to
 * every lookup, so every session value must be published first.
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
    // Session state only. Neither selector's highlight is here: a pick is the player's own cursor over a
    // list they can see, and only the press of a button turns it into a request. See ForgingScreen.
    private static final int IDX_ACTIVE = 0;
    private static final int IDX_VALUE = 1;
    private static final int IDX_STEPS = 2;

    /**
     * The shortest run the session's frozen plan allows, mirrored to the client: the plan's
     * {@code optimal_steps}, which the quality formula subtracts from the player's step count. Reserved, but
     * deleting it would renumber every index the finish-pattern rows read from.
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
     */
    public static final int RECESS_Y = 17;
    public static final int RECESS_W = 49;
    public static final int RECESS_H = 76;

    /**
     * Where the scrollbar sprite's top-left corner goes. These are not derived from the recess: the texture
     * draws the bar in the gutter right of it, so the sprite origin is four pixels past the recess's wall.
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
        // The surface's own container on the server, reached through the access; a stand-in on the client,
        // which the container content packet fills through the slots - see Slot#set.
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
     * One slot on the machine surface. It exists because a plain {@code Slot} answers yes to everything, so
     * the filter was only consulted by hoppers and shift-clicks; the rule is asked here too, through
     * {@link ForgingSurface#canPlace}, because two copies would be free to disagree.
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
         * The surface is not a chest: what it holds is locked in for as long as a session runs, and its
         * snapshot is what a cancel or a failure returns.
         */
        @Override
        public boolean mayPickup(@NonNull Player player) {
            return ForgingSurface.canTake(this.getContainerSlot(), ForgingMenu.this.sessionLocked())
                    && super.mayPickup(player);
        }

    }

    /**
     * Runs one read against the forge table, or returns {@code fallback} when there is none to read. It always
     * goes through the {@link ContainerLevelAccess}, so a table broken under an open menu yields
     * {@code fallback} instead of a stale object.
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
     * The table this menu is acting on, resolved on demand, or null when there is none: resolving it from the
     * menu lets a request be keyed by "the table I am standing at" rather than a client-named position.
     */
    public ForgingTableBlockEntity table() {
        return this.fromTable(table -> table, null);
    }

    /**
     * Whether the surface would accept a stack in a slot, by that slot's own rule —
     * {@link ForgingSurface#canPlace}, which sits with the layout it describes, so the menu and the hopper path
     * cannot enforce two different filters.
     */
    public boolean accepts(int index, ItemStack stack) {
        return ForgingSurface.canPlace(index, stack, this.active(),
                this.fromTable(ForgingTableBlockEntity::selectedBlueprint, null));
    }

    /**
     * Whether a session has locked the surface, read from the synced flag so both sides answer alike: a slot
     * that allowed a pickup the server would refuse is a desync the player sees as an item bouncing back.
     */
    public boolean sessionLocked() {
        return this.synced[IDX_ACTIVE].get() != 0;
    }


    // ------------------------------------------------------------------ the two lists

    /**
     * The blueprints offered: what the blueprint items in the blueprint slots provide, derived through the
     * same rule the server validates against.
     */
    public List<Identifier> blueprints() {
        return ForgingWorkstationService.selectableBlueprintIds(this.machine);
    }

    /**
     * The methods offered: what the tools unlock, narrowed by what the given blueprint allows. The blueprint is
     * passed in so the list exists before a session does.
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
     * Whether the inputs cover a blueprint's whole material list, the same rule {@code start} applies on the
     * server, so a button disabled by it means a request that would have been refused.
     */
    public boolean materialsCovered(Identifier blueprintId) {
        ForgingBlueprint blueprint = this.blueprint(blueprintId);
        return blueprint != null && ForgingWorkstationService.materialsCovered(this.machine, blueprint.input());
    }

    /**
     * A blueprint as this side's registries see it, or null when nothing is picked or the datapack no longer
     * has it.
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
     * The shortest run the plan allows, or {@code 0} when no session is running. Nothing reads it yet.
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
     * Resolves a synced registry id back to its entry, through whichever registry access this side has.
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
        // Server only: everything below is the server's answer and reaches the client through these very
        // slots, so a client that recomputed it would overwrite what the packet had just put there.
        if (this.player.level().isClientSide()) return;
        ForgingTableBlockEntity surface = this.table();
        if (surface == null) return;

        boolean active = surface.forgingState().active();
        Snapshot snapshot = surface.forgingState().session().orElse(null);
        ForgingPlan plan = surface.forgingState().plan().orElse(null);
        this.synced[IDX_ACTIVE].set(active ? 1 : 0);
        this.synced[IDX_VALUE].set(snapshot == null ? 0 : snapshot.value());
        this.synced[IDX_STEPS].set(snapshot == null ? 0 : snapshot.steps());
        // From the plan, which is where the shortest run is computed and stored; the session only carries
        // its own progress. Both are dropped together, so this is zero exactly when `snapshot` is.
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
            // most recent step in both.
            this.synced[IDX_TARGET_START + index].set(requiredStep(pattern, index, required));
            this.synced[IDX_HISTORY_START + index].set(historyStep(history, index));
        }
    }

    /**
     * The registry id of the required step at display position {@code position}, or {@link #NONE}: the last
     * {@code required} entries of the six-step pattern, not the window the history row uses.
     */
    static int requiredStep(List<Identifier> pattern, int position, int required) {
        if (required <= 0 || pattern.size() != SUFFIX_STEPS) return NONE;
        int first = SUFFIX_STEPS - required;
        return position < first ? NONE : registryId(pattern.get(position));
    }

    /**
     * The registry id of the step taken at display position {@code position}, or {@link #NONE}. The window is
     * the last {@code history.size()} entries, so a short session shows its steps against the right-hand end.
     */
    static int historyStep(List<Identifier> history, int position) {
        int source = position - (SUFFIX_STEPS - history.size());
        if (source < 0 || source >= history.size()) return NONE;
        return registryId(history.get(source));
    }

    /**
     * The integer registry id the client will resolve the icon from, sent as a number because a data slot
     * carries an int — only a signed short on the wire, which is why a registry id is safe here.
     */
    private static int registryId(Identifier id) {
        if (id == null) return NONE;
        Registry<ForgingMethod> registry = MxtDatapackRegistries.registry(MxtResourceKeys.FORGING_METHOD);
        return registry.get(id).map(holder -> registry.getId(holder.value())).orElse(NONE);
    }

    // ------------------------------------------------------------------ interaction

    /**
     * No menu buttons: a pick is local to {@code ForgingScreen} and the two presses name their entry's own id
     * through {@code ForgingActionC2SPayload}, which the server re-resolves.
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
