package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.api.UseItemAuraAccess;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.cultivation.ItemAuraService;
import com.iafenvoy.mxt.runtime.spirit.AuraTransfers;
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

import static com.iafenvoy.mxt.runtime.spirit.AuraHandlerUtil.requireNonNegative;

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
        Holder<Aura> aura = this.contentType(registries, stack);
        if (aura != null) result.put(aura, ItemAuraService.capacity(registries, stack));
        return result;
    }

    @Override
    public double insert(@Nullable LivingEntity entity, @NotNull HolderLookup.Provider registries, ItemStack stack, Holder<Aura> aura, double amount, boolean exact, boolean simulate) {
        requireNonNegative(amount);
        if (stack.isEmpty() || stack.getItem() != this || !aura.equals(this.contentType(registries, stack)))
            return amount;
        int count = stack.getCount();
        double capacity = ItemAuraService.capacity(registries, stack) / count;
        double stored = storedPerItem(stack, aura, capacity);
        AuraTransfers.Transfer transfer = AuraTransfers.insert(stored, capacity, amount / count, exact);
        if (!simulate && transfer.stored() > stored) setStoredPerItem(stack, aura, transfer.stored());
        return transfer.stored() == stored ? amount : Math.min(amount, transfer.remainder() * count);
    }

    @Override
    public double extract(@Nullable LivingEntity entity, @NotNull HolderLookup.Provider registries, ItemStack stack, Holder<Aura> aura, double amount, boolean exact, boolean simulate) {
        requireNonNegative(amount);
        if (stack.isEmpty() || stack.getItem() != this || !aura.equals(this.contentType(registries, stack)))
            return amount;
        int count = stack.getCount();
        double stored = storedPerItem(stack, aura, ItemAuraService.capacity(registries, stack) / count);
        AuraTransfers.Transfer transfer = AuraTransfers.extract(stored, amount / count, exact);
        if (!simulate && transfer.stored() < stored) setStoredPerItem(stack, aura, transfer.stored());
        return transfer.stored() == stored ? amount : Math.min(amount, transfer.remainder() * count);
    }

    private Holder<Aura> contentType(HolderLookup.Provider registries, ItemStack stack) {
        return storedAuraType(stack).orElseGet(() -> ItemAuraService.type(registries, stack).orElse(null));
    }

    public double storedAura(HolderLookup.Provider registries, ItemStack stack, Holder<Aura> aura) {
        if (stack.isEmpty() || !aura.equals(this.contentType(registries, stack))) return 0.0D;
        int count = stack.getCount();
        return AuraTransfers.multiply(storedPerItem(stack, aura, ItemAuraService.capacity(registries, stack) / count), count);
    }

    public static Optional<Holder<Aura>> storedAuraType(ItemStack stack) {
        SpiritStorageComponent storage = stack.get(MxtDataComponents.SPIRIT_STORAGE);
        return storage != null && storage.perItem() ? storage.soleAura() : Optional.empty();
    }

    public static double storedPerItem(ItemStack stack, Holder<Aura> aura, double freshCapacity) {
        SpiritStorageComponent storage = stack.get(MxtDataComponents.SPIRIT_STORAGE);
        if (storage == null) return stack.has(MxtDataComponents.ITEM_AURA) ? 0.0D : freshCapacity;
        // Only per-item values can safely be scaled by the stack count.
        return storage.perItem() ? storage.get(aura) : 0.0D;
    }

    public static void setStoredPerItem(ItemStack stack, Holder<Aura> aura, double stored) {
        SpiritStorageComponent storage = stored == 0.0D ? SpiritStorageComponent.EMPTY_PER_ITEM
                : SpiritStorageComponent.EMPTY_PER_ITEM.with(aura, stored);
        stack.set(MxtDataComponents.SPIRIT_STORAGE, storage);
    }
}
