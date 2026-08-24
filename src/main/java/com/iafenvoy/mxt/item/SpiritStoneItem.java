package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.spirit.SpiritAccess;
import com.iafenvoy.mxt.runtime.spirit.SpiritItemAccess;
import com.iafenvoy.mxt.runtime.cultivation.ItemAuraService;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * A chargeable spirit stone whose capacity is supplied by its {@code item_aura} definition.
 */
public class SpiritStoneItem extends Item implements SpiritItemAccess {
    public SpiritStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public Object2IntMap<Holder<Resource>> getCapacity(@Nullable LivingEntity entity, ItemStack stack) {
        Object2IntMap<Holder<Resource>> result = new Object2IntOpenHashMap<>();
        ItemAuraService.type(stack).ifPresent(resource -> result.put(resource, ItemAuraService.capacity(stack)));
        return result;
    }

    @Override
    public int add(@Nullable LivingEntity entity, ItemStack stack, Holder<Resource> resource, int amount, boolean simulate) {
        int capacity = this.getCapacity(entity, stack).getInt(resource);
        SpiritAccess.requireNonNegative(capacity);
        SpiritAccess.requireNonNegative(amount);
        if (stack.getItem() != this || ItemAuraService.type(stack).filter(resource::equals).isEmpty()) return amount;

        int stored = this.normalizeStored(stack, capacity, simulate);
        int accepted = Math.min(amount, capacity - stored);
        if (!simulate && accepted > 0)
            stack.set(MxtDataComponents.SPIRIT_STORAGE, new SpiritStorageComponent(stored + accepted));
        return amount - accepted;
    }

    @Override
    public int extract(@Nullable LivingEntity entity, ItemStack stack, Holder<Resource> resource, int amount, boolean simulate) {
        int capacity = this.getCapacity(entity, stack).getInt(resource);
        SpiritAccess.requireNonNegative(capacity);
        SpiritAccess.requireNonNegative(amount);
        if (stack.getItem() != this || ItemAuraService.type(stack).filter(resource::equals).isEmpty()) return amount;

        int stored = this.normalizeStored(stack, capacity, simulate);
        int extracted = Math.min(amount, stored);
        if (!simulate && extracted > 0)
            stack.set(MxtDataComponents.SPIRIT_STORAGE, new SpiritStorageComponent(stored - extracted));
        return amount - extracted;
    }

    /**
     * A missing component represents a pristine, fully charged spirit stone.
     */
    private int normalizeStored(ItemStack stack, int capacity, boolean simulate) {
        SpiritStorageComponent component = stack.get(MxtDataComponents.SPIRIT_STORAGE);
        if (component == null) return capacity;

        int stored = Math.min(capacity, component.amount());
        if (!simulate && stored != component.amount())
            stack.set(MxtDataComponents.SPIRIT_STORAGE, new SpiritStorageComponent(stored));
        return stored;
    }
}
