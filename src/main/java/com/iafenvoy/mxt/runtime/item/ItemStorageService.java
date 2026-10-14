package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.data.storage.DataStorageHolder;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * The state a carrier keeps for one of its abilities, addressed inside the stack's {@code mxt:storage} component by
 * the ability's holder plus the kind's class. A component is a value, so a write hands the stack a new holder instead
 * of mutating the one it already holds.
 */
public final class ItemStorageService {
    private ItemStorageService() {
    }

    public static <T extends DataStorage> Optional<T> get(ItemStack stack, Holder<Ability> ability, Class<T> kind) {
        return holder(stack).flatMap(value -> value.get(ability, kind));
    }

    public static void set(ItemStack stack, Holder<Ability> ability, DataStorage value) {
        if (stack.isEmpty()) return;
        stack.set(MxtDataComponents.STORAGE, holder(stack).orElseGet(DataStorageHolder::new).with(ability, value));
    }

    private static Optional<DataStorageHolder> holder(ItemStack stack) {
        return Optional.ofNullable(stack.get(MxtDataComponents.STORAGE));
    }
}
