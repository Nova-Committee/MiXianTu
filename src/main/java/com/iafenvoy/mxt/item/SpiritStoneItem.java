package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.runtime.spirit.AuraTransfers;
import com.iafenvoy.mxt.api.UseItemAuraAccess;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.ItemAuraComponent;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.cultivation.ItemAuraService;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A chargeable spirit stone whose capacity comes from its {@code item_aura} definition and whose content is what
 * its store holds (see {@link SpiritStorageComponent}). A stone holds one aura at a time, so it reads the store's
 * sole aura rather than answering for every aura the store could name. It is a {@link UseItemAuraAccess} because it
 * is filled by hand, not because it describes itself: its pour is left to that definition, which is what the
 * interface's empty {@code pour} means.
 */
public class SpiritStoneItem extends Item implements UseItemAuraAccess {
    public SpiritStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public Object2DoubleMap<Holder<Aura>> getCapacity(@Nullable LivingEntity entity, @NotNull HolderLookup.Provider registries, ItemStack stack) {
        Object2DoubleMap<Holder<Aura>> result = new Object2DoubleOpenHashMap<>();
        Holder<Aura> aura = this.contentType(stack);
        if (aura != null) result.put(aura, ItemAuraService.capacity(registries, stack));
        return result;
    }

    @Override
    public double insert(@Nullable LivingEntity entity, @NotNull HolderLookup.Provider registries, ItemStack stack, Holder<Aura> aura, double amount, boolean exact, boolean simulate) {
        double capacity = this.getCapacity(entity, registries, stack, aura);
        AuraAccess.requireNonNegative(capacity);
        AuraAccess.requireNonNegative(amount);
        if (stack.getItem() != this || !aura.equals(this.contentType(stack))) return amount;

        double stored = this.normalizeStored(stack, capacity, aura);
        AuraTransfers.Transfer transfer = AuraTransfers.insert(stored, capacity, amount, exact);
        double accepted = amount - transfer.remainder();
        if (!simulate && accepted > 0) {
            stack.set(MxtDataComponents.SPIRIT_STORAGE, this.store(stack).with(aura, transfer.stored()));
            ItemAuraComponent fuel = stack.get(MxtDataComponents.ITEM_AURA);
            if (fuel != null && fuel.remain() <= 0.0D) stack.remove(MxtDataComponents.ITEM_AURA);
        }
        return transfer.remainder();
    }

    @Override
    public double extract(@Nullable LivingEntity entity, @NotNull HolderLookup.Provider registries, ItemStack stack, Holder<Aura> aura, double amount, boolean exact, boolean simulate) {
        double capacity = this.getCapacity(entity, registries, stack, aura);
        AuraAccess.requireNonNegative(capacity);
        AuraAccess.requireNonNegative(amount);
        if (stack.getItem() != this || !aura.equals(this.contentType(stack))) return amount;

        double stored = this.normalizeStored(stack, capacity, aura);
        AuraTransfers.Transfer transfer = AuraTransfers.extract(stored, amount, exact);
        double extracted = amount - transfer.remainder();
        // Emptying the store takes its key with it, so a drained stone is an empty store rather than one holding
        // zero of the aura it used to carry.
        if (!simulate && extracted > 0)
            stack.set(MxtDataComponents.SPIRIT_STORAGE, this.store(stack).with(aura, transfer.stored()));
        return transfer.remainder();
    }

    // The store is asked before the definition because a store cannot be reinterpreted: its amounts are filed under
    // the aura they count, so a definition re-typed by a data pack can only stop matching what is already in the world.
    private Holder<Aura> contentType(ItemStack stack) {
        SpiritStorageComponent component = stack.get(MxtDataComponents.SPIRIT_STORAGE);
        return (component == null ? Optional.<Holder<Aura>>empty() : component.soleAura())
                .orElseGet(() -> ItemAuraService.type(stack).orElse(null));
    }

    private SpiritStorageComponent store(ItemStack stack) {
        return stack.getOrDefault(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY);
    }

    // A missing component means a pristine, fully charged spirit stone.
    private double normalizeStored(ItemStack stack, double capacity, Holder<Aura> aura) {
        SpiritStorageComponent component = stack.get(MxtDataComponents.SPIRIT_STORAGE);
        if (component == null) return capacity;

        return component.get(aura);
    }
}
