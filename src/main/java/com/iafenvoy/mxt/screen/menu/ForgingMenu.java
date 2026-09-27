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
 * The forge table's menu: the machine slots of {@link ForgingSurface} then the player inventory, at the
 * coordinates the screen paints against. The session has no container, so it is mirrored onto
 * {@link DataSlot}s; a client's {@link ContainerLevelAccess} answers every lookup with nothing.
 */
public final class ForgingMenu extends AbstractContainerMenu {
    public static final int MACHINE_SLOTS = ForgingSurface.TOTAL_SLOTS;
    public static final int PLAYER_START = MACHINE_SLOTS;
    // Fixed six: a session keeps at most six history entries and a finish pattern is empty or exactly six long.
    public static final int SUFFIX_STEPS = 6;
    // Sentinel for "no entry"; registry ids are never negative.
    public static final int NONE = -1;
    // ---- synced indices
    private static final int IDX_ACTIVE = 0, IDX_VALUE = 1, IDX_STEPS = 2;
    // The plan's optimal_steps, which the quality formula subtracts from the player's step count.
    // Reserved, but deleting it would renumber every index the finish-pattern rows read from.
    private static final int IDX_OPTIMAL = 3;
    private static final int IDX_METER_MIN = 4, IDX_METER_MAX = 5;
    private static final int IDX_TARGET_MIN = 6, IDX_TARGET_MAX = 7;
    private static final int IDX_REQUIRED = 8;
    private static final int IDX_TARGET_START = 9;
    private static final int IDX_HISTORY_START = IDX_TARGET_START + SUFFIX_STEPS;
    private static final int SYNCED = IDX_HISTORY_START + SUFFIX_STEPS;
    // ---- frame geometry, shared with ForgingScreen
    // Every number here is a pixel in assets/mxt/textures/gui/forging_table.png: a slot's hitbox has to land
    // on the cell already drawn for it, so the screen reads these instead of repeating them.
    public static final int CELLS = 3;
    public static final int CELL = 18, CELL_PITCH = 19;
    public static final int BLUEPRINT_GRID_X = 7, METHOD_GRID_X = 250;
    public static final int GRID_Y = 17;
    public static final int RECESS_Y = 17, RECESS_W = 49, RECESS_H = 76;
    // Where the scrollbar sprite's top-left corner goes: the texture draws the bar in the gutter right of
    // the recess, so these are not derived from it.
    public static final int SCROLLBAR_X = 58, SCROLLBAR_X_RIGHT = 301;
    public static final int MACHINE_PITCH = 18;
    public static final int SLOT_TOP = 18;
    public static final int INPUT_X = 81;
    public static final int INVENTORY_X = 81, INVENTORY_Y = 152;
    public static final int HOTBAR_Y = 210;

    private final Container machine;
    private final ContainerLevelAccess access;
    // The opener; the client half needs it only for the registries behind the two selector lists and the
    // step icons, never for the level's blocks.
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

    // One slot on the machine surface: a plain Slot answers yes to everything, so the rule is asked here
    // too, through ForgingSurface.canPlace, rather than kept in a second copy.
    private final class MachineSlot extends Slot {
        MachineSlot(int index, int x, int y) {
            super(ForgingMenu.this.machine, index, x, y);
        }

        @Override
        public boolean mayPlace(@NonNull ItemStack stack) {
            return ForgingMenu.this.accepts(this.getContainerSlot(), stack);
        }

        // The surface is not a chest: what it holds is locked while a session runs, and its snapshot is
        // what a cancel or a failure returns.
        @Override
        public boolean mayPickup(@NonNull Player player) {
            return ForgingSurface.canTake(this.getContainerSlot(), ForgingMenu.this.sessionLocked())
                    && super.mayPickup(player);
        }

    }

    // Always goes through the ContainerLevelAccess, so a table broken under an open menu yields fallback
    // instead of a stale object.
    private <T> T fromTable(Function<ForgingTableBlockEntity, T> reader, T fallback) {
        return this.access
                .evaluate((level, pos) -> level.getBlockEntity(pos) instanceof ForgingTableBlockEntity table
                        ? Optional.ofNullable(reader.apply(table))
                        : Optional.<T>empty())
                .flatMap(Function.identity())
                .orElse(fallback);
    }

    // Resolved on demand from the menu, so a request keys on "the table I am standing at" rather than a
    // client-named position.
    public ForgingTableBlockEntity table() {
        return this.fromTable(table -> table, null);
    }

    // ForgingSurface.canPlace is the one filter, so the menu and the hopper path cannot enforce two
    // different rules.
    public boolean accepts(int index, ItemStack stack) {
        return ForgingSurface.canPlace(index, stack, this.active(),
                this.fromTable(ForgingTableBlockEntity::selectedBlueprint, null), this.player.level().registryAccess());
    }

    // Reads the synced flag so both sides answer alike: a slot that allowed a pickup the server would refuse
    // is a desync the player sees as an item bouncing back.
    public boolean sessionLocked() {
        return this.synced[IDX_ACTIVE].get() != 0;
    }

    // ------------------------------------------------------------------ the two lists

    // Derived through the same rule the server validates against.
    public List<Identifier> blueprints() {
        return ForgingWorkstationService.selectableBlueprintIds(this.machine, this.player.level().registryAccess());
    }

    // The blueprint is passed in so the list exists before a session does.
    public List<Identifier> methods(Identifier blueprintId) {
        return ForgingWorkstationService.availableMethodIds(this.machine, this.player.level().registryAccess(), blueprintId);
    }

    public int inputCount(ForgingMaterial entry) {
        return ForgingWorkstationService.availableCount(this.machine, entry);
    }

    // The server applies the same rule in its start action, so a button disabled here means a request that
    // would have been refused.
    public boolean materialsCovered(Identifier blueprintId) {
        ForgingBlueprint blueprint = this.blueprint(blueprintId);
        return blueprint != null && ForgingWorkstationService.materialsCovered(this.machine, blueprint.input());
    }

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

    public int targetStep(int position) {
        return position < 0 || position >= SUFFIX_STEPS ? NONE : this.synced[IDX_TARGET_START + position].get();
    }

    public int historyStep(int position) {
        return position < 0 || position >= SUFFIX_STEPS ? NONE : this.synced[IDX_HISTORY_START + position].get();
    }

    public static boolean isNone(int registryId) {
        return registryId == NONE;
    }

    public static Identifier methodId(Player player, int registryId) {
        if (registryId == NONE) return null;
        Registry<ForgingMethod> registry = player.level().registryAccess().lookupOrThrow(MxtResourceKeys.FORGING_METHOD);
        return registry.get(registryId).flatMap(holder -> holder.unwrapKey().map(ResourceKey::identifier)).orElse(null);
    }

    // ------------------------------------------------------------------ syncing

    @Override
    public void broadcastChanges() {
        this.syncFromTable();
        super.broadcastChanges();
    }

    private void syncFromTable() {
        // Server only: a client that recomputed this would overwrite what the packet just put in these slots.
        if (this.player.level().isClientSide()) return;
        ForgingTableBlockEntity surface = this.table();
        if (surface == null) return;

        boolean active = surface.forgingState().active();
        Snapshot snapshot = surface.forgingState().session().orElse(null);
        ForgingPlan plan = surface.forgingState().plan().orElse(null);
        this.synced[IDX_ACTIVE].set(active ? 1 : 0);
        this.synced[IDX_VALUE].set(snapshot == null ? 0 : snapshot.value());
        this.synced[IDX_STEPS].set(snapshot == null ? 0 : snapshot.steps());
        // From the plan, not the session: the shortest run is computed and stored there, and both are
        // dropped together, so this is zero exactly when `snapshot` is.
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
            // Both rows are right-aligned over the same six positions, so position five is the newest step
            // in both.
            this.synced[IDX_TARGET_START + index].set(requiredStep(pattern, index, required));
            this.synced[IDX_HISTORY_START + index].set(historyStep(history, index));
        }
    }

    // The last `required` entries of the six-step pattern, right-aligned; not the window historyStep uses.
    static int requiredStep(List<Identifier> pattern, int position, int required) {
        if (required <= 0 || pattern.size() != SUFFIX_STEPS) return NONE;
        int first = SUFFIX_STEPS - required;
        return position < first ? NONE : registryId(pattern.get(position));
    }

    // Right-aligned window over the last history.size() entries, so a short session shows its steps against
    // the right-hand end.
    static int historyStep(List<Identifier> history, int position) {
        int source = position - (SUFFIX_STEPS - history.size());
        if (source < 0 || source >= history.size()) return NONE;
        return registryId(history.get(source));
    }

    // Sent as an int because a data slot carries only a signed short on the wire, which is why a registry id
    // is safe here.
    private static int registryId(Identifier id) {
        if (id == null) return NONE;
        Registry<ForgingMethod> registry = MxtDatapackRegistries.registry(MxtResourceKeys.FORGING_METHOD);
        return registry.get(id).map(holder -> registry.getId(holder.value())).orElse(NONE);
    }

    // ------------------------------------------------------------------ interaction

    // No menu buttons: the two presses name their entry's own id through ForgingActionC2SPayload, which the
    // server re-resolves.
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
