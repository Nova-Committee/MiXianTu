package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.api.AuraHandler;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.Costs;
import com.iafenvoy.mxt.data.cost.context.AuraCostContexts;
import com.iafenvoy.mxt.data.cost.context.CostSources;
import com.iafenvoy.mxt.recipe.SpiritCraftingInput;
import com.iafenvoy.mxt.recipe.SpiritRecipe;
import com.iafenvoy.mxt.registry.MxtBlockEntities;
import com.iafenvoy.mxt.registry.MxtRecipeTypes;
import com.iafenvoy.mxt.runtime.MxtProfilerSources;
import com.iafenvoy.mxt.runtime.spirit.AuraHandlerUtil;
import com.iafenvoy.mxt.runtime.spirit.AuraTransfers;
import com.iafenvoy.mxt.screen.menu.SpiritCraftingMenu;
import com.iafenvoy.saturn.data.cost.Cost;
import com.iafenvoy.saturn.data.cost.CostPayment;
import com.iafenvoy.saturn.data.cost.context.CostContext;
import com.iafenvoy.saturn.profiling.ProfilerScope;
import com.iafenvoy.saturn.profiling.ServerProfiling;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Stores the crafting grid and independently buffered aura inside the placed block.
 */
public final class SpiritCraftingTableBlockEntity extends BlockEntity implements MenuProvider, AuraHandler, WorldlyContainer {
    private final SimpleContainer grid = new SimpleContainer(9) {
        @Override
        public void setChanged() {
            super.setChanged();
            SpiritCraftingTableBlockEntity.this.markChangedAndSync();
        }
    };
    private final Map<Holder<Aura>, Double> aura = new LinkedHashMap<>();
    private final SnapshotJournal<Map<Holder<Aura>, Double>> auraJournal = new SnapshotJournal<>() {
        @Override
        protected Map<Holder<Aura>, Double> createSnapshot() {
            return new LinkedHashMap<>(SpiritCraftingTableBlockEntity.this.aura);
        }

        @Override
        protected void revertToSnapshot(Map<Holder<Aura>, Double> snapshot) {
            SpiritCraftingTableBlockEntity.this.aura.clear();
            SpiritCraftingTableBlockEntity.this.aura.putAll(snapshot);
        }

        @Override
        protected void onRootCommit(Map<Holder<Aura>, Double> original) {
            if (!SpiritCraftingTableBlockEntity.this.isRemoved() && !original.equals(SpiritCraftingTableBlockEntity.this.aura))
                SpiritCraftingTableBlockEntity.this.markChangedAndSync();
        }
    };
    private final SimpleContainer result = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            SpiritCraftingTableBlockEntity.this.markChangedAndSync();
        }
    };
    private Map<Holder<Aura>, Double> requiredAura = Map.of();

    public SpiritCraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(MxtBlockEntities.SPIRIT_CRAFTING_TABLE.get(), pos, state);
    }

    public SimpleContainer grid() {
        return this.grid;
    }

    public SimpleContainer result() {
        return this.result;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpiritCraftingTableBlockEntity table) {
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.CRAFTING)) {
            if (level.getServer() != null) table.craftAvailable(level, level.getServer().getRecipeManager());
        }
    }

    private void craftAvailable(Level level, RecipeManager recipeManager) {
        SpiritCraftingInput input = new SpiritCraftingInput(IntStream.range(0, 9).mapToObj(this.grid::getItem).toList());
        RecipeHolder<? extends SpiritRecipe> shaped = recipeManager.getRecipeFor(MxtRecipeTypes.SPIRIT_SHAPED.get(), input, level).orElse(null);
        SpiritRecipe recipe = shaped == null ? recipeManager.getRecipeFor(MxtRecipeTypes.SPIRIT_SHAPELESS.get(), input, level).map(RecipeHolder::value).orElse(null) : shaped.value();
        if (recipe == null) {
            this.requiredAura = Map.of();
            this.clearAura();
            return;
        }
        Map<Holder<Aura>, Double> costs = this.costs(recipe.aura(), level);
        this.configureAuraCosts(costs);
        ItemStack produced = recipe.result().create();
        ItemStack stored = this.result.getItem(0);
        int max = Math.min(stored.isEmpty() ? produced.getMaxStackSize() : stored.getMaxStackSize(), this.result.getMaxStackSize(produced));
        if (!this.hasAura(costs) || (!stored.isEmpty() && (!ItemStack.isSameItemSameComponents(stored, produced)
                || stored.getCount() + produced.getCount() > max))) return;
        // Paid through the shared transaction, so a recipe's aura price behaves like every other cost.
        CostContext bankContext = AuraCostContexts.bank(this, null, FormulaContext.of(level), CostSources.RECIPE);
        if (CostPayment.pay(recipe.aura(), bankContext) instanceof Result.FailureResult<?>) return;
        for (int index = 0; index < this.grid.getContainerSize(); index++) this.grid.getItem(index).shrink(1);
        if (stored.isEmpty()) this.result.setItem(0, produced);
        else stored.grow(produced.getCount());
        this.grid.setChanged();
        this.result.setChanged();
    }

    private Map<Holder<Aura>, Double> costs(List<Cost> aura, Level level) {
        Map<Holder<Aura>, Double> amounts = Costs.auras(aura,
                CostContext.account(null, null, FormulaContext.of(level), CostSources.RECIPE));
        if (amounts == null) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(amounts));
    }

    public double aura(Holder<Aura> aura) {
        return this.aura.getOrDefault(aura, 0.0D);
    }

    public Map<Holder<Aura>, Double> auras() {
        return this.aura;
    }

    public Map<Holder<Aura>, Double> requiredAura() {
        return this.requiredAura;
    }

    @Override
    public Set<Holder<Aura>> getAuras() {
        Set<Holder<Aura>> types = new LinkedHashSet<>(this.requiredAura.keySet());
        types.addAll(this.aura.keySet());
        return Set.copyOf(types);
    }

    @Override
    public double getAmount(Holder<Aura> aura) {
        return this.aura(aura);
    }

    @Override
    public double getCapacity(Holder<Aura> aura) {
        return AuraTransfers.multiply(this.requiredAura.getOrDefault(aura, 0.0D), 99.0D);
    }

    @Override
    public Object storageIdentity(Holder<Aura> aura) {
        return this;
    }

    @Override
    public double insert(Holder<Aura> aura, double amount, TransactionContext transaction) {
        AuraHandlerUtil.requireNonNegative(amount);
        if (this.isRemoved() || this.level != null && this.level.isClientSide()) return 0.0D;
        double before = this.aura(aura);
        double after = AuraTransfers.insert(before, this.getCapacity(aura), amount, false).stored();
        this.updateAura(aura, before, after, transaction);
        return after - before;
    }

    @Override
    public double extract(Holder<Aura> aura, double amount, TransactionContext transaction) {
        AuraHandlerUtil.requireNonNegative(amount);
        if (this.isRemoved() || this.level != null && this.level.isClientSide()) return 0.0D;
        double before = this.aura(aura);
        double after = AuraTransfers.extract(before, amount, false).stored();
        this.updateAura(aura, before, after, transaction);
        return before - after;
    }

    private void updateAura(Holder<Aura> type, double before, double after, TransactionContext transaction) {
        if (before == after) return;
        this.auraJournal.updateSnapshots(transaction);
        if (after == 0.0D) this.aura.remove(type);
        else this.aura.put(type, after);
    }

    // Changing the grid or recipe invalidates any partially supplied aura rather than keeping it as general storage.
    public void configureAuraCosts(Map<Holder<Aura>, Double> costs) {
        if (this.requiredAura.equals(costs)) return;
        this.requiredAura = new LinkedHashMap<>(costs);
        this.clearAura();
    }

    public void clearAura() {
        if (this.aura.isEmpty()) return;
        this.aura.clear();
        this.markChangedAndSync();
    }

    public boolean hasAura(Map<Holder<Aura>, Double> costs) {
        return costs.entrySet().stream().allMatch(entry -> entry.getValue() >= 0 && AuraTransfers.covers(this.aura(entry.getKey()), entry.getValue()));
    }

    @Override
    public int getContainerSize() {
        return this.grid.getContainerSize() + 1;
    }

    @Override
    public boolean isEmpty() {
        return this.grid.isEmpty() && this.result.isEmpty();
    }

    @Override
    public @NonNull ItemStack getItem(int index) {
        return index < 9 ? this.grid.getItem(index) : this.result.getItem(index - 9);
    }

    @Override
    public @NonNull ItemStack removeItem(int index, int count) {
        return index < 9 ? this.grid.removeItem(index, count) : this.result.removeItem(index - 9, count);
    }

    @Override
    public @NonNull ItemStack removeItemNoUpdate(int index) {
        return index < 9 ? this.grid.removeItemNoUpdate(index) : this.result.removeItemNoUpdate(index - 9);
    }

    @Override
    public void setItem(int index, @NonNull ItemStack stack) {
        if (index < 9) this.grid.setItem(index, stack);
        else this.result.setItem(index - 9, stack);
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return this.level != null && !this.isRemoved() && player.distanceToSqr(this.worldPosition.getCenter()) <= 64.0D;
    }

    @Override
    public void clearContent() {
        this.grid.clearContent();
        this.result.clearContent();
    }

    @Override
    public int @NonNull [] getSlotsForFace(@NonNull Direction side) {
        return side == Direction.DOWN ? new int[]{9} : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, @NonNull ItemStack stack, @Nullable Direction side) {
        return slot >= 0 && slot < 9 && this.canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, @NonNull ItemStack stack, @NonNull Direction side) {
        return slot == 9 && side == Direction.DOWN;
    }

    @Override
    public @NonNull Component getDisplayName() {
        return Component.translatable("screen.mxt.spirit_crafting_table");
    }

    // A ContainerLevelAccess, not the entity, so nothing has to be null-checked in the menu's client half.
    @Override
    public AbstractContainerMenu createMenu(int id, @NonNull Inventory inventory, @NonNull Player player) {
        return new SpiritCraftingMenu(id, inventory, ContainerLevelAccess.create(player.level(), this.getBlockPos()));
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        List<ItemStack> values = input.read("items", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        this.grid.clearContent();
        for (int index = 0; index < Math.min(this.grid.getContainerSize(), values.size()); index++)
            this.grid.setItem(index, values.get(index));
        // Intake is deliberately transient. A table never restores a generic aura buffer.
        this.aura.clear();
        this.result.setItem(0, input.read("result", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("items", ItemStack.OPTIONAL_CODEC.listOf(), this.grid.getItems());
        output.store("result", ItemStack.OPTIONAL_CODEC, this.result.getItem(0));
    }

    private void markChangedAndSync() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide())
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }
}
