package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.screen.picker.ItemPickerManager;
import com.iafenvoy.mxt.screen.picker.ItemPickerManager.ItemProvider;
import com.iafenvoy.mxt.screen.picker.ItemPickerManager.PickerItem;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reading the picker catalogue: a registry's definitions turned into rows, or into the item list a creative tab
 * takes. Every query is handed the registry access to read from, so the client reads the tables it has synced and
 * the server its own; nothing here writes, resolves a side by itself or touches an attachment.
 *
 * <p>The catalogue itself - which registry maps to which rows - is {@link ItemPickerManager}. A key that was never
 * registered as a category gives an empty list rather than an exception, and a registry keeps only the first
 * category registered for it, so registering a second one for a key already taken is silently inert.</p>
 */
public final class CreativeTabHelper {
    /** Every row of one category, in registry order. */
    public static List<PickerItem> itemsOf(Provider provider, ResourceKey<? extends Registry<?>> key) {
        return itemsOf(provider, key, _ -> true);
    }

    /**
     * The rows of one category whose definition passes {@code filter}. The filter sees the definition's holder,
     * which is where its id lives, so a namespace check and a check on the definition itself are one call.
     */
    public static List<PickerItem> itemsOf(Provider provider, ResourceKey<? extends Registry<?>> key, Predicate<Holder<?>> filter) {
        ItemProvider<?> item = ItemPickerManager.provider(key);
        return item == null ? List.of() : item.collectItems(provider, filter);
    }

    /**
     * The rows of one category whose definition id lives in {@code namespace} - the mod id of whatever shipped it.
     * For the item and block categories the definition is the item, so the namespace is the item's.
     */
    public static List<PickerItem> itemsOfMod(Provider provider, ResourceKey<? extends Registry<?>> key, String namespace) {
        return itemsOf(provider, key, inNamespace(namespace));
    }

    /** Every category's rows whose definition id lives in {@code namespace}, in category registration order. */
    public static List<PickerItem> itemsOfMod(Provider provider, String namespace) {
        List<PickerItem> collected = new ArrayList<>();
        for (ResourceKey<Registry<?>> key : ItemPickerManager.categories()) collected.addAll(itemsOfMod(provider, key, namespace));
        return List.copyOf(collected);
    }

    /** The stacks of one category, which is what a creative tab takes. */
    public static List<ItemStack> stacksOf(Provider provider, ResourceKey<? extends Registry<?>> key) {
        return stacks(itemsOf(provider, key));
    }

    public static List<ItemStack> stacksOf(Provider provider, ResourceKey<? extends Registry<?>> key, Predicate<Holder<?>> filter) {
        return stacks(itemsOf(provider, key, filter));
    }

    public static List<ItemStack> stacksOfMod(Provider provider, ResourceKey<? extends Registry<?>> key, String namespace) {
        return stacks(itemsOfMod(provider, key, namespace));
    }

    public static List<ItemStack> stacksOfMod(Provider provider, String namespace) {
        return stacks(itemsOfMod(provider, namespace));
    }

    // Vanilla's own creative tab rule, and its `accept` refuses these duplicates outright: several stand-in rows
    // (every `mxt:aura` entry is the same spirit stone) collapse into one stack. Copies, so a caller editing what
    // it was handed cannot reach the row the picker draws from.
    private static List<ItemStack> stacks(List<PickerItem> items) {
        Set<ItemStack> distinct = ItemStackLinkedSet.createTypeAndComponentsSet();
        for (PickerItem item : items) distinct.add(item.stack());
        List<ItemStack> stacks = new ArrayList<>(distinct.size());
        for (ItemStack stack : distinct) stacks.add(stack.copy());
        return List.copyOf(stacks);
    }

    private static Predicate<Holder<?>> inNamespace(String namespace) {
        return holder -> {
            Identifier id = HolderHelper.idOrNull(holder);
            return id != null && id.getNamespace().equals(namespace);
        };
    }

    private CreativeTabHelper() {
    }
}
