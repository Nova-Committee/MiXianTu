package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.recipe.SpiritCraftingInput;
import com.iafenvoy.mxt.recipe.SpiritRecipe;
import com.iafenvoy.mxt.registry.MxtBlockEntities;
import com.iafenvoy.mxt.registry.MxtRecipeTypes;
import com.iafenvoy.mxt.screen.menu.SpiritCraftingMenu;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.LivingEntity;
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
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.IntStream;

/**
 * Stores the crafting grid and independently buffered aura inside the placed block.
 */
public final class SpiritCraftingTableBlockEntity extends BlockEntity implements MenuProvider, AuraAccess, WorldlyContainer {
    private final SimpleContainer grid = new SimpleContainer(9) {
        @Override
        public void setChanged() {
            super.setChanged();
            SpiritCraftingTableBlockEntity.this.markChangedAndSync();
        }
    };
    private final Map<Holder<Aura>, Integer> aura = new LinkedHashMap<>();
    private final SimpleContainer result = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            SpiritCraftingTableBlockEntity.this.markChangedAndSync();
        }
    };
    private Map<Holder<Aura>, Integer> requiredAura = Map.of();

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
        if (level.getServer() != null) table.craftAvailable(level, level.getServer().getRecipeManager());
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
        Map<Holder<Aura>, Integer> costs = this.costs(recipe.aura());
        this.configureAuraCosts(costs);
        ItemStack produced = recipe.result().create();
        ItemStack stored = this.result.getItem(0);
        int max = Math.min(stored.isEmpty() ? produced.getMaxStackSize() : stored.getMaxStackSize(), this.result.getMaxStackSize(produced));
        if (!this.hasAura(costs) || (!stored.isEmpty() && (!ItemStack.isSameItemSameComponents(stored, produced)
                || stored.getCount() + produced.getCount() > max))) return;
        if (!this.consumeAura(costs)) return;
        for (int index = 0; index < this.grid.getContainerSize(); index++) this.grid.getItem(index).shrink(1);
        if (stored.isEmpty()) this.result.setItem(0, produced);
        else stored.grow(produced.getCount());
        this.grid.setChanged();
        this.result.setChanged();
    }

    private Map<Holder<Aura>, Integer> costs(Map<Holder<Aura>, NumberProvider> aura) {
        Map<Holder<Aura>, Integer> costs = new LinkedHashMap<>();
        for (Entry<Holder<Aura>, NumberProvider> entry : aura.entrySet()) {
            double value = entry.getValue().evaluate(FormulaContext.of(this.level));
            if (!Double.isFinite(value) || value < 0.0D || value > Integer.MAX_VALUE) return Map.of();
            costs.put(entry.getKey(), (int) Math.ceil(value));
        }
        return costs;
    }

    public int aura(Holder<Aura> aura) {
        return this.aura.getOrDefault(aura, 0);
    }

    public Map<Holder<Aura>, Integer> auras() {
        return this.aura;
    }

    public Map<Holder<Aura>, Integer> requiredAura() {
        return this.requiredAura;
    }

    @Override
    public Object2IntMap<Holder<Aura>> getCapacity(@Nullable LivingEntity entity) {
        Object2IntMap<Holder<Aura>> result = new Object2IntOpenHashMap<>();
        this.requiredAura.forEach((aura, amount) -> result.put(aura,
                (int) Math.clamp((long) amount * 99L, 0L, Integer.MAX_VALUE)));
        return result;
    }

    // Changing the grid or recipe invalidates any partially supplied aura rather than keeping it as general storage.
    public void configureAuraCosts(Map<Holder<Aura>, Integer> costs) {
        if (this.requiredAura.equals(costs)) return;
        this.requiredAura = new LinkedHashMap<>(costs);
        this.clearAura();
    }

    public void clearAura() {
        if (this.aura.isEmpty()) return;
        this.aura.clear();
        this.markChangedAndSync();
    }

    public boolean hasAura(Map<Holder<Aura>, Integer> costs) {
        return costs.entrySet().stream().allMatch(entry -> entry.getValue() >= 0 && this.aura(entry.getKey()) >= entry.getValue());
    }

    // One craft's costs are deducted together; any remaining active-recipe buffer stays for the next craft.
    public boolean consumeAura(Map<Holder<Aura>, Integer> costs) {
        if (!this.hasAura(costs)) return false;
        costs.forEach((aura, amount) -> {
            int remaining = this.aura(aura) - amount;
            if (remaining == 0) this.aura.remove(aura);
            else this.aura.put(aura, remaining);
        });
        this.markChangedAndSync();
        return true;
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
    public int insert(@Nullable LivingEntity entity, Holder<Aura> aura, int amount, boolean simulate) {
        AuraAccess.requireNonNegative(amount);
        int required = (int) Math.clamp((long) this.requiredAura.getOrDefault(aura, 0) * 99L,
                0L, Integer.MAX_VALUE);
        int accepted = Math.min(amount, Math.max(0, required - this.aura(aura)));
        if (!simulate && accepted > 0) {
            this.aura.put(aura, this.aura(aura) + accepted);
            this.markChangedAndSync();
        }
        return amount - accepted;
    }

    @Override
    public int extract(@Nullable LivingEntity entity, Holder<Aura> aura, int amount, boolean simulate) {
        AuraAccess.requireNonNegative(amount);
        int extracted = Math.min(amount, this.aura(aura));
        if (!simulate && extracted > 0) {
            int remaining = this.aura(aura) - extracted;
            if (remaining == 0) this.aura.remove(aura);
            else this.aura.put(aura, remaining);
            this.markChangedAndSync();
        }
        return amount - extracted;
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
