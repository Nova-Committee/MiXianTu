package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.api.UseItemAuraAccess;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.ItemAuraComponent;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.cultivation.ItemAuraService;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    public Object2IntMap<Holder<Aura>> getCapacity(@Nullable LivingEntity entity, ItemStack stack) {
        Object2IntMap<Holder<Aura>> result = new Object2IntOpenHashMap<>();
        Holder<Aura> aura = this.contentType(stack);
        if (aura != null) result.put(aura, ItemAuraService.capacity(stack));
        return result;
    }

    @Override
    public int insert(@Nullable LivingEntity entity, ItemStack stack, Holder<Aura> aura, int amount, boolean simulate) {
        int capacity = this.getCapacity(entity, stack, aura);
        AuraAccess.requireNonNegative(capacity);
        AuraAccess.requireNonNegative(amount);
        if (stack.getItem() != this || !aura.equals(this.contentType(stack))) return amount;

        int stored = this.normalizeStored(stack, capacity, aura, simulate);
        int accepted = Math.min(amount, capacity - stored);
        if (!simulate && accepted > 0) {
            stack.set(MxtDataComponents.SPIRIT_STORAGE, this.store(stack).with(aura, stored + accepted));
            ItemAuraComponent fuel = stack.get(MxtDataComponents.ITEM_AURA);
            if (fuel != null && fuel.remain() <= 0.0D) stack.remove(MxtDataComponents.ITEM_AURA);
        }
        return amount - accepted;
    }

    @Override
    public int extract(@Nullable LivingEntity entity, ItemStack stack, Holder<Aura> aura, int amount, boolean simulate) {
        int capacity = this.getCapacity(entity, stack, aura);
        AuraAccess.requireNonNegative(capacity);
        AuraAccess.requireNonNegative(amount);
        if (stack.getItem() != this || !aura.equals(this.contentType(stack))) return amount;

        int stored = this.normalizeStored(stack, capacity, aura, simulate);
        int extracted = Math.min(amount, stored);
        // Emptying the store takes its key with it, so a drained stone is an empty store rather than one holding
        // zero of the aura it used to carry.
        if (!simulate && extracted > 0)
            stack.set(MxtDataComponents.SPIRIT_STORAGE, this.store(stack).with(aura, stored - extracted));
        return amount - extracted;
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
    private int normalizeStored(ItemStack stack, int capacity, Holder<Aura> aura, boolean simulate) {
        SpiritStorageComponent component = stack.get(MxtDataComponents.SPIRIT_STORAGE);
        if (component == null) return capacity;

        int stored = Math.min(capacity, component.get(aura));
        if (!simulate && stored != component.get(aura))
            stack.set(MxtDataComponents.SPIRIT_STORAGE, component.with(aura, stored));
        return stored;
    }
}
