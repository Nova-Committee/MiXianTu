package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.item.block.entity.SpiritCraftingTableBlockEntity;
import com.iafenvoy.mxt.recipe.SpiritCraftingInput;
import com.iafenvoy.mxt.recipe.SpiritRecipe;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.registry.MxtRecipeTypes;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Vanilla-sized crafting menu restricted to the two spirit recipe types.
 *
 * <h2>Reaching the table</h2>
 * The table is reached through {@link ContainerLevelAccess}, the vanilla handle for "the block this
 * menu belongs to", created by {@code SpiritCraftingTableBlockEntity#createMenu}. The server gets a
 * real access and resolves the block on demand; the client gets {@link ContainerLevelAccess#NULL},
 * whose every lookup is empty. Nothing about the block is therefore held across the menu's life, and a
 * table that is broken while its menu is open stops being found instead of lingering here.
 *
 * <p>Both containers the block owns - the crafting grid and the result slot - are resolved the same
 * way, server side through the access and client side as stand-ins that the container content packet
 * fills through the slots.
 *
 * <h2>The aura readout</h2>
 * The aura buffer lives on the block and the recipe is matched by this menu, so the progress rows need
 * both. They are published to data slots, which is also why the client half must not compute them: it
 * can see neither the recipe (matching needs a server level) nor the buffer, and writing the slots
 * there would overwrite what the server had just sent.
 */
public final class SpiritCraftingMenu extends AbstractContainerMenu {
    private static final int MAX_PROGRESS_ENTRIES = 8;
    private static final int RESULT_SLOT = 0;
    private static final int GRID_START = 1;
    private static final int PLAYER_START = 10;
    private final Player player;
    private final Container grid;
    private final Container result;
    private final ContainerLevelAccess access;
    private final DataSlot[] progressTypes = new DataSlot[MAX_PROGRESS_ENTRIES];
    private final DataSlot[] progressAmounts = new DataSlot[MAX_PROGRESS_ENTRIES];
    private final DataSlot[] progressRequirements = new DataSlot[MAX_PROGRESS_ENTRIES];
    private RecipeMatch current;

    public SpiritCraftingMenu(int id, Inventory inventory) {
        this(id, inventory, ContainerLevelAccess.NULL);
    }

    public SpiritCraftingMenu(int id, Inventory inventory, ContainerLevelAccess access) {
        super(MxtMenus.SPIRIT_CRAFTING_TABLE.get(), id);
        this.player = inventory.player;
        this.access = access;
        this.grid = this.fromTable(SpiritCraftingTableBlockEntity::grid, new SimpleContainer(9));
        this.result = this.fromTable(SpiritCraftingTableBlockEntity::result, new SimpleContainer(1));
        for (int index = 0; index < MAX_PROGRESS_ENTRIES; index++) {
            this.progressTypes[index] = DataSlot.standalone();
            this.progressAmounts[index] = DataSlot.standalone();
            this.progressRequirements[index] = DataSlot.standalone();
            this.progressTypes[index].set(-1);
            this.addDataSlot(this.progressTypes[index]);
            this.addDataSlot(this.progressAmounts[index]);
            this.addDataSlot(this.progressRequirements[index]);
        }
        this.addSlot(new Slot(this.result, RESULT_SLOT, 124, 35) {
            @Override
            public boolean mayPlace(@NonNull ItemStack stack) {
                return false;
            }

            @Override
            public void onTake(@NonNull Player player, @NonNull ItemStack stack) {
                super.onTake(player, stack);
            }
        });
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 3; column++)
                this.addSlot(new Slot(this.grid, column + row * 3, 30 + column * 18, 17 + row * 18));
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
        for (int column = 0; column < 9; column++) this.addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        this.updateResult();
    }

    /**
     * Runs one action against the spirit crafting table, or does nothing when there is none.
     *
     * <p>No action means no table: the block was broken or replaced under an open menu, the chunk is
     * not loaded, or this is the client half, which has no access at all. Every caller here treats that
     * as "nothing to do" rather than as "nothing to show", which is what keeps a client from writing
     * over the data slots the server publishes.
     */
    private void withTable(Consumer<SpiritCraftingTableBlockEntity> action) {
        this.access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof SpiritCraftingTableBlockEntity table) action.accept(table);
        });
    }

    /**
     * Runs one read against the spirit crafting table, or returns {@code fallback} when there is none.
     */
    private <T> T fromTable(Function<SpiritCraftingTableBlockEntity, T> reader, T fallback) {
        return this.access
                .evaluate((level, pos) -> level.getBlockEntity(pos) instanceof SpiritCraftingTableBlockEntity table
                        ? Optional.ofNullable(reader.apply(table))
                        : Optional.<T>empty())
                .flatMap(Function.identity())
                .orElse(fallback);
    }

    public Holder<Resource> progressResource(int index) {
        if (index < 0 || index >= MAX_PROGRESS_ENTRIES) return null;
        int rawId = this.progressTypes[index].get();
        if (rawId < 0) return null;
        Registry<Resource> registry = this.player.level().registryAccess().lookupOrThrow(MxtResourceKeys.RESOURCE);
        return registry.get(rawId).orElse(null);
    }

    public int progressAmount(int index) {
        return index < 0 || index >= MAX_PROGRESS_ENTRIES ? 0 : this.progressAmounts[index].get();
    }

    public int progressRequirement(int index) {
        return index < 0 || index >= MAX_PROGRESS_ENTRIES ? 0 : this.progressRequirements[index].get();
    }

    @Override
    public void slotsChanged(@NonNull Container changed) {
        this.updateResult();
    }

    @Override
    public void broadcastChanges() {
        this.updateResult();
        super.broadcastChanges();
    }

    /**
     * Re-matches the grid, hands the matched costs to the block's intake window, and republishes the
     * rows.
     *
     * <p>This runs on both halves - {@code slotsChanged} and {@code broadcastChanges} both call it - and
     * does almost nothing on the client, which is the point. {@link #findRecipe} needs a server level, so
     * the client matches nothing; {@link #withTable} finds no block, so it configures nothing; and
     * {@link #syncProgress} therefore writes nothing. The client's three progress arrays keep whatever
     * the server published, and the readout on the right of the screen is that.
     */
    private void updateResult() {
        this.current = this.findRecipe();
        Map<Holder<Resource>, Integer> costs = this.current == null ? Map.of() : this.current.costs();
        this.withTable(table -> table.configureAuraCosts(costs));
        this.syncProgress();
    }

    private void syncProgress() {
        // The whole row is read in one visit to the block, and no block means no rows written at all -
        // which is what leaves the client's data slots holding what the server published rather than
        // being blanked by a half that cannot see either the recipe or the buffer.
        this.withTable(table -> {
            Registry<Resource> registry = this.player.level().registryAccess().lookupOrThrow(MxtResourceKeys.RESOURCE);
            int index = 0;
            if (this.current != null) {
                for (Entry<Holder<Resource>, Integer> entry : this.current.costs().entrySet()) {
                    if (index >= MAX_PROGRESS_ENTRIES) break;
                    this.progressTypes[index].set(registry.getId(entry.getKey().value()));
                    this.progressAmounts[index].set(Math.clamp(table.aura(entry.getKey()), 0, Short.MAX_VALUE));
                    this.progressRequirements[index].set(Math.clamp(entry.getValue(), 0, Short.MAX_VALUE));
                    index++;
                }
            }
            while (index < MAX_PROGRESS_ENTRIES) {
                this.progressTypes[index].set(-1);
                this.progressAmounts[index].set(0);
                this.progressRequirements[index].set(0);
                index++;
            }
        });
    }

    private RecipeMatch findRecipe() {
        if (!(this.player.level() instanceof ServerLevel level)) return null;
        SpiritCraftingInput input = this.input();
        RecipeManager manager = level.getServer().getRecipeManager();
        RecipeHolder<? extends SpiritRecipe> shaped = manager.getRecipeFor(MxtRecipeTypes.SPIRIT_SHAPED.get(), input, level).orElse(null);
        if (shaped != null) return this.match(shaped.value());
        RecipeHolder<? extends SpiritRecipe> shapeless = manager.getRecipeFor(MxtRecipeTypes.SPIRIT_SHAPELESS.get(), input, level).orElse(null);
        return shapeless == null ? null : this.match(shapeless.value());
    }

    private RecipeMatch match(SpiritRecipe recipe) {
        return new RecipeMatch(recipe, this.costs(recipe.aura()));
    }

    private Map<Holder<Resource>, Integer> costs(Map<Holder<Resource>, NumberProvider> aura) {
        Map<Holder<Resource>, Integer> costs = new LinkedHashMap<>();
        for (Entry<Holder<Resource>, NumberProvider> entry : aura.entrySet()) {
            double value = entry.getValue().evaluate(FormulaContext.of(this.player));
            if (!Double.isFinite(value) || value < 0.0D || value > Integer.MAX_VALUE) return Map.of();
            costs.put(entry.getKey(), (int) Math.ceil(value));
        }
        return costs;
    }

    private SpiritCraftingInput input() {
        return new SpiritCraftingInput(IntStream.range(0, 9).mapToObj(this.grid::getItem).toList());
    }

    @Override
    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        if (index == RESULT_SLOT) {
            if (!this.moveItemStackTo(slot.getItem(), PLAYER_START, this.slots.size(), true)) return ItemStack.EMPTY;
            slot.onQuickCraft(slot.getItem(), original);
            slot.onTake(player, slot.getItem());
        } else if (index >= GRID_START && index < PLAYER_START) {
            if (!this.moveItemStackTo(slot.getItem(), PLAYER_START, this.slots.size(), false)) return ItemStack.EMPTY;
        } else if (!this.moveItemStackTo(slot.getItem(), GRID_START, PLAYER_START, false)) {
            return ItemStack.EMPTY;
        }
        if (slot.getItem().isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return stillValid(this.access, player, MxtBlocks.SPIRIT_CRAFTING_TABLE.get());
    }

    private record RecipeMatch(SpiritRecipe recipe, Map<Holder<Resource>, Integer> costs) {
    }
}
