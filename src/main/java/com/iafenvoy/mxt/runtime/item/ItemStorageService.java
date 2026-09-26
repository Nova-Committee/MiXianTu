package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.data.storage.DataStorageHolder;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * The state a carrier keeps for one of its abilities, addressed inside the stack's {@code mxt:storage} component by
 * the ability's id plus the kind's class. A component is a value, so a write hands the stack a new holder instead of
 * mutating the one it already holds.
 */
public final class ItemStorageService {
    private ItemStorageService() {
    }

    public static <T extends DataStorage> Optional<T> get(ItemStack stack, Identifier id, Class<T> kind) {
        return holder(stack).flatMap(value -> value.get(id, kind));
    }

    public static void set(ItemStack stack, Identifier id, DataStorage value, long gameTime) {
        if (stack.isEmpty()) return;
        stack.set(MxtDataComponents.STORAGE, holder(stack).orElseGet(DataStorageHolder::new).with(id, value, gameTime));
    }

    private static Optional<DataStorageHolder> holder(ItemStack stack) {
        return Optional.ofNullable(stack.get(MxtDataComponents.STORAGE));
    }
}
