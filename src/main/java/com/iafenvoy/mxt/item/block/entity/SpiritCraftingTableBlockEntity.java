package com.iafenvoy.mxt.item.block.entity;

import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtBlockEntities;
import com.iafenvoy.mxt.runtime.spirit.SpiritAccess;
import com.iafenvoy.mxt.screen.menu.SpiritCraftingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores the crafting grid and independently buffered elemental aura inside the placed block.
 */
public final class SpiritCraftingTableBlockEntity extends BlockEntity implements MenuProvider, SpiritAccess, Container {
    private final SimpleContainer grid = new SimpleContainer(9) {
        @Override
        public void setChanged() {
            super.setChanged();
            SpiritCraftingTableBlockEntity.this.markChangedAndSync();
        }
    };
    private final Map<Holder<Element>, Integer> aura = new LinkedHashMap<>();
    private Map<Holder<Element>, Integer> requiredAura = Map.of();

    public SpiritCraftingTableBlockEntity(BlockPos pos, BlockState state) {
        super(MxtBlockEntities.SPIRIT_CRAFTING_TABLE.get(), pos, state);
    }

    public SimpleContainer grid() {
        return this.grid;
    }

    public int aura(Holder<Element> type) {
        return this.aura.getOrDefault(type, 0);
    }

    public Map<Holder<Element>, Integer> auras() {
        return this.aura;
    }

    public Map<Holder<Element>, Integer> requiredAura() {
        return this.requiredAura;
    }

    /**
     * Opens a temporary intake window for one already-matched recipe. Changing the
     * grid or recipe invalidates any partially supplied aura instead of retaining it
     * as general-purpose block storage.
     */
    public void configureAuraCosts(Map<Holder<Element>, Integer> costs) {
        if (this.requiredAura.equals(costs)) return;
        this.requiredAura = new LinkedHashMap<>(costs);
        this.clearAura();
    }

    public void clearAura() {
        if (this.aura.isEmpty()) return;
        this.aura.clear();
        this.markChangedAndSync();
    }

    public boolean hasAura(Map<Holder<Element>, Integer> costs) {
        return costs.entrySet().stream().allMatch(entry -> entry.getValue() >= 0 && this.aura(entry.getKey()) >= entry.getValue());
    }

    /**
     * Deducts all costs together, preventing partial payment when one type is insufficient.
     */
    public boolean consumeAura(Map<Holder<Element>, Integer> costs) {
        if (!this.hasAura(costs)) return false;
        this.aura.clear();
        this.markChangedAndSync();
        return true;
    }

    @Override
    public int getContainerSize() {
        return this.grid.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return this.grid.isEmpty();
    }

    @Override
    public @NonNull ItemStack getItem(int index) {
        return this.grid.getItem(index);
    }

    @Override
    public @NonNull ItemStack removeItem(int index, int count) {
        return this.grid.removeItem(index, count);
    }

    @Override
    public @NonNull ItemStack removeItemNoUpdate(int index) {
        return this.grid.removeItemNoUpdate(index);
    }

    @Override
    public void setItem(int index, @NonNull ItemStack stack) {
        this.grid.setItem(index, stack);
    }

    @Override
    public boolean stillValid(@NonNull Player player) {
        return this.level != null && !this.isRemoved() && player.distanceToSqr(this.worldPosition.getCenter()) <= 64.0D;
    }

    @Override
    public void clearContent() {
        this.grid.clearContent();
    }

    @Override
    public int add(Holder<Element> type, int amount, boolean simulate) {
        SpiritAccess.requireNonNegative(amount);
        int required = this.requiredAura.getOrDefault(type, 0);
        int accepted = Math.min(amount, Math.max(0, required - this.aura(type)));
        if (!simulate && accepted > 0) {
            this.aura.put(type, this.aura(type) + accepted);
            this.markChangedAndSync();
        }
        return amount - accepted;
    }

    @Override
    public int extract(Holder<Element> type, int amount, boolean simulate) {
        SpiritAccess.requireNonNegative(amount);
        int extracted = Math.min(amount, this.aura(type));
        if (!simulate && extracted > 0) {
            int remaining = this.aura(type) - extracted;
            if (remaining == 0) this.aura.remove(type);
            else this.aura.put(type, remaining);
            this.markChangedAndSync();
        }
        return amount - extracted;
    }

    @Override
    public @NonNull Component getDisplayName() {
        return Component.translatable("container.mxt.spirit_crafting_table");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, @NonNull Inventory inventory, @NonNull Player player) {
        return new SpiritCraftingMenu(id, inventory, this.grid(), ContainerLevelAccess.create(player.level(), this.getBlockPos()), this);
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
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("items", ItemStack.OPTIONAL_CODEC.listOf(), this.grid.getItems());
    }

    private void markChangedAndSync() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide())
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
    }
}
