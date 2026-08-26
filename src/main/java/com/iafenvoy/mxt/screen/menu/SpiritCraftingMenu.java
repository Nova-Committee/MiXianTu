package com.iafenvoy.mxt.screen.menu;

import com.iafenvoy.mxt.item.block.entity.SpiritCraftingTableBlockEntity;
import com.iafenvoy.mxt.recipe.SpiritCraftingInput;
import com.iafenvoy.mxt.recipe.SpiritRecipe;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.registry.MxtRecipeTypes;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
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
import java.util.stream.IntStream;

/**
 * Vanilla-sized crafting menu restricted to the two spirit recipe types.
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
    private final SpiritCraftingTableBlockEntity table;
    private final DataSlot[] progressTypes = new DataSlot[MAX_PROGRESS_ENTRIES];
    private final DataSlot[] progressAmounts = new DataSlot[MAX_PROGRESS_ENTRIES];
    private final DataSlot[] progressRequirements = new DataSlot[MAX_PROGRESS_ENTRIES];
    private RecipeMatch current;

    public SpiritCraftingMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(9), ContainerLevelAccess.NULL, null);
    }

    public SpiritCraftingMenu(int id, Inventory inventory, Container grid, ContainerLevelAccess access, SpiritCraftingTableBlockEntity table) {
        super(MxtMenus.SPIRIT_CRAFTING_TABLE.get(), id);
        this.player = inventory.player;
        this.grid = grid;
        this.access = access;
        this.table = table;
        this.result = table == null ? new SimpleContainer(1) : table.result();
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
                this.addSlot(new Slot(grid, column + row * 3, 30 + column * 18, 17 + row * 18));
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
        for (int column = 0; column < 9; column++) this.addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        this.updateResult();
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

    private void updateResult() {
        this.current = this.findRecipe();
        if (this.table != null) this.table.configureAuraCosts(this.current == null ? Map.of() : this.current.costs());
        this.syncProgress();
    }

    private void syncProgress() {
        if (this.table == null) return;
        Registry<Resource> registry = this.player.level().registryAccess().lookupOrThrow(MxtResourceKeys.RESOURCE);
        int index = 0;
        if (this.current != null) {
            for (Entry<Holder<Resource>, Integer> entry : this.current.costs().entrySet()) {
                if (index >= MAX_PROGRESS_ENTRIES) break;
                this.progressTypes[index].set(registry.getId(entry.getKey().value()));
                this.progressAmounts[index].set(Math.clamp(this.table.aura(entry.getKey()), 0, Short.MAX_VALUE));
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
