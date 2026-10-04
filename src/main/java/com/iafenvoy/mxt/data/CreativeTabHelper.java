package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.screen.picker.ItemPickerManager;
import com.iafenvoy.mxt.screen.picker.ItemPickerManager.ItemProvider;
import com.iafenvoy.mxt.screen.picker.ItemPickerManager.PickerItem;
import com.iafenvoy.mxt.screen.picker.PickerCategory;
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
 * Reading the picker catalogue: a category's entries turned into rows, or into the item list a creative tab takes.
 * Every query is handed the registry access to read from, so the client reads the tables it has synced and the
 * server its own; nothing here writes, resolves a side by itself or touches an attachment.
 *
 * <p>The catalogue itself - which table maps to which rows - is {@link ItemPickerManager}. A key that was never
 * registered as a category gives an empty list rather than an exception, and a category keeps only the first
 * provider registered for it, so registering a second one for an id already taken is silently inert.</p>
 */
public final class CreativeTabHelper {
    /** Every row of one category. */
    public static List<PickerItem> itemsOf(Provider provider, PickerCategory category) {
        return itemsOf(provider, category, _ -> true);
    }

    /**
     * The rows of one category whose own entry passes {@code filter}. The filter sees the row's id - the
     * definition's for a registry, the item's for a data map - so a namespace check is one call.
     */
    public static List<PickerItem> itemsOf(Provider provider, PickerCategory category, Predicate<Identifier> filter) {
        ItemProvider item = ItemPickerManager.provider(category);
        return item == null ? List.of() : item.items().apply(provider, filter);
    }

    /** The same for a registry-backed category, which is what a caller holding a registry key has. */
    public static <T> List<PickerItem> itemsOf(Provider provider, ResourceKey<Registry<T>> key) {
        return itemsOf(provider, new PickerCategory.OfRegistry<>(key));
    }

    public static <T> List<PickerItem> itemsOf(Provider provider, ResourceKey<Registry<T>> key, Predicate<Identifier> filter) {
        return itemsOf(provider, new PickerCategory.OfRegistry<>(key), filter);
    }

    /**
     * The rows of one category whose entry id lives in {@code namespace} - the mod id of whatever shipped it.
     */
    public static List<PickerItem> itemsOfMod(Provider provider, PickerCategory category, String namespace) {
        return itemsOf(provider, category, inNamespace(namespace));
    }

    public static <T> List<PickerItem> itemsOfMod(Provider provider, ResourceKey<Registry<T>> key, String namespace) {
        return itemsOfMod(provider, new PickerCategory.OfRegistry<>(key), namespace);
    }

    /** Every category's rows whose entry id lives in {@code namespace}, in category registration order. */
    public static List<PickerItem> itemsOfMod(Provider provider, String namespace) {
        List<PickerItem> collected = new ArrayList<>();
        for (PickerCategory category : ItemPickerManager.categories()) collected.addAll(itemsOfMod(provider, category, namespace));
        return List.copyOf(collected);
    }

    /** The stacks of one category, which is what a creative tab takes. */
    public static List<ItemStack> stacksOf(Provider provider, PickerCategory category) {
        return stacks(itemsOf(provider, category));
    }

    public static List<ItemStack> stacksOf(Provider provider, PickerCategory category, Predicate<Identifier> filter) {
        return stacks(itemsOf(provider, category, filter));
    }

    public static <T> List<ItemStack> stacksOf(Provider provider, ResourceKey<Registry<T>> key) {
        return stacks(itemsOf(provider, key));
    }

    public static <T> List<ItemStack> stacksOf(Provider provider, ResourceKey<Registry<T>> key, Predicate<Identifier> filter) {
        return stacks(itemsOf(provider, key, filter));
    }

    public static <T> List<ItemStack> stacksOfMod(Provider provider, ResourceKey<Registry<T>> key, String namespace) {
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

    private static Predicate<Identifier> inNamespace(String namespace) {
        return id -> id.getNamespace().equals(namespace);
    }

    private CreativeTabHelper() {
    }
}
