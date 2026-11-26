package com.iafenvoy.mxt.runtime.spirit;

import com.iafenvoy.mxt.data.aura.ItemAuraComponent;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.item.SpiritStoneItem;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

public final class StackAuraMigration {
    private StackAuraMigration() {
    }

    public static void normalize(ItemStack stack) {
        DataComponentPatch original = stack.getComponentsPatch();
        DataComponentPatch normalized = normalizedPatch(stack, original);
        if (normalized != original) stack.applyComponents(normalized);
    }

    public static DataComponentPatch normalizedPatch(ItemInstance item, DataComponentPatch original) {
        if (original.isEmpty() || !MxtDataComponents.ITEM_AURA.isBound() || !MxtDataComponents.SPIRIT_STORAGE.isBound())
            return original;
        DataComponentType<ItemAuraComponent> fuelType = MxtDataComponents.ITEM_AURA.get();
        DataComponentType<SpiritStorageComponent> storageType = MxtDataComponents.SPIRIT_STORAGE.get();
        ItemAuraComponent fuel = original.get(DataComponentMap.EMPTY, fuelType);
        SpiritStorageComponent storage = original.get(DataComponentMap.EMPTY, storageType);
        boolean oldFuel = fuel != null && !fuel.perItem();
        boolean oldStorage = storage != null && !storage.perItem() && item.typeHolder().isBound()
                && item.typeHolder().value() instanceof SpiritStoneItem;
        if (!oldFuel && !oldStorage) return original;
        // Template patches can be decoded before the item's default components are bound.
        DataComponentPatch.Builder components = DataComponentPatch.builder();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : original.entrySet())
            copy(components, entry.getKey(), entry.getValue());
        if (oldFuel) components.set(fuelType, fuel.normalize(item.count()));
        if (oldStorage) components.set(storageType, storage.normalize(item.count()));
        return components.build();
    }

    @SuppressWarnings("unchecked")
    private static <T> void copy(DataComponentPatch.Builder builder, DataComponentType<T> type, Optional<?> value) {
        if (value.isPresent()) builder.set(type, (T) value.get());
        else builder.remove(type);
    }
}
