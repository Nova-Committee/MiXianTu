package com.iafenvoy.mxt.screen.picker;

import com.iafenvoy.mxt.data.CurrencyValue;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.data.aura.ItemAura;
import com.iafenvoy.mxt.data.item.ContractScrollComponent;
import com.iafenvoy.mxt.data.item.FormationPlateComponent;
import com.iafenvoy.mxt.data.item.ItemBinding;
import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.data.item.RealmTokenComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent.TriggerMode;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.iafenvoy.mxt.util.matcher.builtin.ItemEntry;
import com.iafenvoy.mxt.util.matcher.builtin.TagEntry;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Registry-to-item catalogue behind the picker screen.
 *
 * <p>Every category names one registry and one function turning an entry into the rows that stand for it, so
 * the picker itself knows nothing about aura, currencies or bindings. This is the picker's <em>contents</em>
 * and nothing else: the screen builds its grid from here on the client, and taking an item out of that grid is
 * the vanilla creative inventory's own gesture, so no part of this catalogue has to be reproduced on the
 * server.</p>
 *
 * <p>A row is a {@link PickerItem}: the stack to draw, plus the names the row answers to. The names are the
 * provider's business because only the provider knows what a row <em>is</em> - for a plain item its display
 * name and its id say it, and for a definition carried by a stand-in item nothing on the stack does, so the
 * definition's name and id are handed over instead. The screen never has to reconstruct any of that from a
 * registry key and an entry id.</p>
 */
public final class ItemPickerManager {
    private static final List<ItemProvider<?>> PROVIDERS = new LinkedList<>();

    static {
        // Vanilla registries: every registered item, and every block that has an item form. A language key
        // built from these two registries is the game's own item and block name, so the stack already says
        // what the row is and nothing has to be written onto it.
        registerSingle(Registries.ITEM, holder -> plain(holder.value().getDefaultInstance(), holder));
        registerSingle(Registries.BLOCK, holder -> plain(holder.value().asItem().getDefaultInstance(), holder));

        // Item-shaped data pack registries: the definition already names the items it applies to, so the row
        // is that item, and the definition it came from is named by its own language key.
        registerMatcher(MxtResourceKeys.ITEM_AURA, ItemAura::entries);
        registerMatcher(MxtResourceKeys.CURRENCY, CurrencyValue::items);
        registerMatcher(MxtResourceKeys.SPIRIT_HERB, SpiritHerb::entries);
        registerMatcher(MxtResourceKeys.ITEM_BINDING, ItemBinding::entries);
        registerMatcher(MxtResourceKeys.WEAPON_BINDING, WeaponBinding::entries);
        registerMatcher(MxtResourceKeys.PILL_BINDING, PillBinding::entries);
        registerMatcher(MxtResourceKeys.TECHNIQUE_BINDING, TechniqueBinding::entries);

        // Definitions carried by a dedicated item: the definition is written onto the stack, and the name the
        // row shows is the definition's own rather than the stand-in item's.
        registerSingle(MxtResourceKeys.CONTRACT_TYPE, holder -> described(
                componentStack(new ItemStack(MxtItems.CONTRACT_SCROLL.get()), MxtDataComponents.CONTRACT_SCROLL,
                        new ContractScrollComponent(Optional.of(holder))),
                holder));
        registerSingle(MxtResourceKeys.REALM_INSTANCE, holder -> described(
                componentStack(new ItemStack(MxtItems.REALM_TOKEN.get()), MxtDataComponents.REALM_TOKEN,
                        new RealmTokenComponent(Optional.of(holder))),
                holder));
        registerSingle(MxtResourceKeys.FORMATION, holder -> described(
                componentStack(new ItemStack(MxtItems.FORMATION_PLATE.get()), MxtDataComponents.FORMATION_PLATE,
                        new FormationPlateComponent(List.of(), Optional.of(holder))),
                holder));
        registerSingle(MxtResourceKeys.TALISMAN, holder -> described(
                componentStack(new ItemStack(MxtItems.TALISMAN.get()), MxtDataComponents.TALISMAN,
                        new TalismanComponent(List.of(holder), TriggerMode.FIRE)),
                holder));

        // Auras have no item of their own, so one stand-in item carries whatever the definition is called.
        registerSingle(MxtResourceKeys.AURA, holder -> described(new ItemStack(MxtItems.SPIRIT_STONE.get()), holder));
        registerSingle(MxtResourceKeys.BLOCK_AURA, holder -> described(new ItemStack(MxtItems.SPIRIT_STONE.get()), holder));

        // A quality carries its name in the data pack rather than in a language file, so that name wins.
        registerSingle(MxtResourceKeys.ITEM_QUALITY, holder -> described(
                new ItemStack(MxtItems.IDENTIFICATION_MIRROR.get()), holder, holder.value().displayName()));
    }

    /**
     * The registered categories, in registration order. The wildcard key is unwidened here because that is
     * the shape the picker passes around; nothing ever reads the entry type back out of it.
     */
    @SuppressWarnings("unchecked")
    public static List<ResourceKey<Registry<?>>> categories() {
        List<ResourceKey<Registry<?>>> keys = new ArrayList<>(PROVIDERS.size());
        for (ItemProvider<?> provider : PROVIDERS) keys.add((ResourceKey<Registry<?>>) (ResourceKey<?>) provider.key());
        return List.copyOf(keys);
    }

    /**
     * Resolves the category id the open packet carries back to the registry it names.
     */
    public static Optional<ResourceKey<Registry<?>>> category(Identifier id) {
        return categories().stream().filter(key -> key.identifier().equals(id)).findFirst();
    }

    /**
     * One row the picker can offer: the stack to draw, and the names the row answers to.
     *
     * <p>The names are the provider's business, because only the provider knows what a row <em>is</em>. The
     * row's display name is always among them - a row has to be reachable by what it looks like it is called -
     * and the rest are the ids it goes by, so a definition carried by a stand-in item can be found both by the
     * name on the stack and by the id it has in its registry. There is a list rather than a single name
     * because a definition can be known by more than one.</p>
     */
    public record PickerItem(ItemStack stack, List<Component> names) {
    }

    /**
     * Everything one category offers, in registry order.
     */
    public static List<PickerItem> itemsOf(Provider provider, ResourceKey<Registry<?>> key) {
        ItemProvider<?> item = providerOf(key);
        if (item == null) return List.of();
        return item.collectItems(provider);
    }

    /**
     * Flattens matching entries into the items they name: an item entry becomes that item, a tag entry
     * every item in the tag. Entries matching by anything else have no concrete item and are skipped.
     */
    public static List<ItemStack> stackItems(List<Entry> entries) {
        Set<Item> items = new LinkedHashSet<>();
        for (Entry entry : entries) {
            switch (entry) {
                case ItemEntry(Item item) -> items.add(item);
                case TagEntry(TagKey<Item> tag) -> BuiltInRegistries.ITEM.get(tag)
                        .ifPresent(set -> set.forEach(holder -> items.add(holder.value())));
                default -> {
                }
            }
        }
        List<ItemStack> stacks = new ArrayList<>(items.size());
        for (Item item : items) stacks.add(new ItemStack(item));
        return stacks;
    }

    /**
     * Registers one row per entry of a registry, which is what a category with nothing to expand needs.
     */
    public static <T> void registerSingle(ResourceKey<Registry<T>> key, Function<Holder<T>, PickerItem> provider) {
        register(key, holder -> List.of(provider.apply(holder)));
    }

    /**
     * Registers a category whose entries expand to several rows.
     */
    public static <T> void register(ResourceKey<Registry<T>> key, Function<Holder<T>, List<PickerItem>> provider) {
        PROVIDERS.add(new ItemProvider<>(key, provider));
    }

    /**
     * Registers the item-shaped entries of a definition: each entry expands to the items it matches, drawn as
     * the plain items themselves, and a row answers to the name of the item it shows, the definition's name,
     * and the definition's id.
     */
    private static <T> void registerMatcher(ResourceKey<Registry<T>> key, Function<T, List<Entry>> entries) {
        register(key, holder -> {
            Identifier id = HolderHelper.idOrNull(holder);
            Component definition = DefinitionText.name(holder);
            List<ItemStack> stacks = stackItems(entries.apply(holder.value()));
            List<PickerItem> items = new ArrayList<>(stacks.size());
            for (ItemStack stack : stacks)
                items.add(new PickerItem(stack, names(stack.getHoverName(), definition, idName(id))));
            return items;
        });
    }

    /**
     * A row for a registry whose entries are their own item: the stack already says what it is, so the row is
     * found by what it shows and by the id it is registered under.
     */
    private static PickerItem plain(ItemStack stack, Holder<?> holder) {
        return new PickerItem(stack, names(stack.getHoverName(), idName(HolderHelper.idOrNull(holder))));
    }

    /**
     * A row for a definition nothing on the stack names - an item-shaped definition, or one carried by a
     * stand-in item - so the row is searched by the name the definition's own language key gives it.
     */
    private static PickerItem described(ItemStack stack, Holder<?> holder) {
        return described(stack, holder, DefinitionText.name(holder));
    }

    /**
     * A row for a definition that has a name of its own, such as a quality, where that name is what the row is
     * searched by.
     */
    private static PickerItem described(ItemStack stack, Holder<?> holder, Component name) {
        return new PickerItem(stack, names(name, idName(HolderHelper.idOrNull(holder))));
    }

    /**
     * The names a row answers to, in the order they are searched. A name that resolves to nothing is dropped
     * rather than left to match every search term, which is also what a missing id contributes.
     */
    private static List<Component> names(Component... names) {
        List<Component> kept = new ArrayList<>(names.length);
        for (Component name : names) if (name != null && !name.getString().isBlank()) kept.add(name.copy());
        return kept.isEmpty() ? List.of() : List.copyOf(kept);
    }

    /** An id as one more name a row can be found by. */
    private static @Nullable Component idName(@Nullable Identifier id) {
        return id == null ? null : Component.literal(id.toString());
    }

    private static <T> ItemStack componentStack(ItemStack stack, DeferredHolder<DataComponentType<?>, DataComponentType<T>> type, T value) {
        stack.set(type.get(), value);
        return stack;
    }

    private static ItemProvider<?> providerOf(ResourceKey<Registry<?>> key) {
        for (ItemProvider<?> provider : PROVIDERS)
            if (provider.key().equals(key)) return provider;
        return null;
    }

    /**
     * One category: the registry it walks, and how one entry of that registry becomes the rows standing for
     * it.
     */
    public record ItemProvider<T>(ResourceKey<Registry<T>> key, Function<Holder<T>, List<PickerItem>> items) {
        private List<PickerItem> collectItems(Provider provider) {
            List<PickerItem> collected = new ArrayList<>();
            provider.lookup(this.key).stream().flatMap(HolderLookup::listElements).forEach(holder -> {
                for (PickerItem item : this.items.apply(holder)) {
                    if (item == null || item.stack().isEmpty()) continue;
                    collected.add(item);
                }
            });
            return List.copyOf(collected);
        }
    }

    private ItemPickerManager() {
    }
}
