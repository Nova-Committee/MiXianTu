package com.iafenvoy.mxt.screen.picker;

import com.iafenvoy.mxt.data.CurrencyValue;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.aura.ItemAura;
import com.iafenvoy.mxt.data.item.ContractScrollComponent;
import com.iafenvoy.mxt.data.item.FormationPlateComponent;
import com.iafenvoy.mxt.data.item.ItemBinding;
import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.data.item.RealmTokenComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent.TriggerMode;
import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
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
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Registry-to-item catalogue behind the picker screen: each category names one registry and one function
 * turning an entry into the rows that stand for it. Client-side contents only - the grid is built from the
 * client's own synced registries, and taking an item out of it is the vanilla creative inventory's gesture.
 */
public final class ItemPickerManager {
    private static final List<ItemProvider<?>> PROVIDERS = new LinkedList<>();

    static {
        // Vanilla registries: the game's own item and block name is already on the stack, so nothing is written onto it.
        registerSingle(Registries.ITEM, holder -> plain(holder.value().getDefaultInstance(), holder));
        registerSingle(Registries.BLOCK, holder -> plain(holder.value().asItem().getDefaultInstance(), holder));

        // Item-shaped definitions: the row is the matched item, named by the definition's own language key.
        registerMatcher(MxtResourceKeys.ITEM_AURA, ItemAura::entries);
        registerMatcher(MxtResourceKeys.CURRENCY, CurrencyValue::items);
        registerMatcher(MxtResourceKeys.SPIRIT_HERB, SpiritHerb::entries);
        registerMatcher(MxtResourceKeys.ITEM_BINDING, ItemBinding::entries);
        registerMatcher(MxtResourceKeys.WEAPON_BINDING, WeaponBinding::entries);
        registerMatcher(MxtResourceKeys.PILL_BINDING, PillBinding::entries);
        registerMatcher(MxtResourceKeys.ARTIFACT, Artifact::entries);

        // Definitions carried by a dedicated item: written onto the stack, and the definition's own name wins.
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
        // A technique has no item of its own: the row is the carrier the mod generates for it, which is the item
        // the declaration names or the jade slip.
        registerSingle(MxtResourceKeys.TECHNIQUE, (holder, access) -> described(ItemBindingService.techniqueCarrier(access, holder), holder));

        // Auras have no item of their own, so one stand-in item carries whatever the definition is called.
        registerSingle(MxtResourceKeys.AURA, holder -> described(new ItemStack(MxtItems.SPIRIT_STONE.get()), holder));
        registerSingle(MxtResourceKeys.BLOCK_AURA, holder -> described(new ItemStack(MxtItems.SPIRIT_STONE.get()), holder));

        // A quality carries its name in the data pack rather than in a language file, so that name wins.
        registerSingle(MxtResourceKeys.ITEM_QUALITY, holder -> described(
                new ItemStack(MxtItems.IDENTIFICATION_MIRROR.get()), holder, holder.value().displayName()));
    }

    // Wildcard key is unwidened here because that is the shape the picker passes around; nothing reads the entry type back out.
    @SuppressWarnings("unchecked")
    public static List<ResourceKey<Registry<?>>> categories() {
        List<ResourceKey<Registry<?>>> keys = new ArrayList<>(PROVIDERS.size());
        for (ItemProvider<?> provider : PROVIDERS) keys.add((ResourceKey<Registry<?>>) (ResourceKey<?>) provider.key());
        return List.copyOf(keys);
    }

    public static Optional<ResourceKey<Registry<?>>> category(Identifier id) {
        return categories().stream().filter(key -> key.identifier().equals(id)).findFirst();
    }

    /**
     * One row the picker can offer. The row's display name is always among {@code names}, a list rather than a
     * single name because a definition can be known by more than one.
     */
    public record PickerItem(ItemStack stack, List<Component> names) {
    }

    public static List<PickerItem> itemsOf(Provider provider, ResourceKey<Registry<?>> key) {
        ItemProvider<?> item = providerOf(key);
        if (item == null) return List.of();
        return item.collectItems(provider);
    }

    // Entries matching by anything but item or tag have no concrete item and are dropped silently.
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

    public static <T> void registerSingle(ResourceKey<Registry<T>> key, Function<Holder<T>, PickerItem> provider) {
        register(key, (holder, access) -> List.of(provider.apply(holder)));
    }

    // The provider is what a row needs when it has to resolve a second registry - a technique's carrier item is
    // named by its declaration, and only the registries know which item that is.
    public static <T> void registerSingle(ResourceKey<Registry<T>> key, BiFunction<Holder<T>, Provider, PickerItem> provider) {
        register(key, (holder, access) -> List.of(provider.apply(holder, access)));
    }

    public static <T> void register(ResourceKey<Registry<T>> key, Function<Holder<T>, List<PickerItem>> provider) {
        PROVIDERS.add(new ItemProvider<>(key, (holder, access) -> provider.apply(holder)));
    }

    public static <T> void register(ResourceKey<Registry<T>> key, BiFunction<Holder<T>, Provider, List<PickerItem>> provider) {
        PROVIDERS.add(new ItemProvider<>(key, provider));
    }

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

    private static PickerItem plain(ItemStack stack, Holder<?> holder) {
        return new PickerItem(stack, names(stack.getHoverName(), idName(HolderHelper.idOrNull(holder))));
    }

    private static PickerItem described(ItemStack stack, Holder<?> holder) {
        return described(stack, holder, DefinitionText.name(holder));
    }

    private static PickerItem described(ItemStack stack, Holder<?> holder, Component name) {
        return new PickerItem(stack, names(name, idName(HolderHelper.idOrNull(holder))));
    }

    // A null or blank name is dropped rather than left to match every search term, which is also what a missing id contributes.
    private static List<Component> names(Component... names) {
        List<Component> kept = new ArrayList<>(names.length);
        for (Component name : names) if (name != null && !name.getString().isBlank()) kept.add(name.copy());
        return kept.isEmpty() ? List.of() : List.copyOf(kept);
    }

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

    public record ItemProvider<T>(ResourceKey<Registry<T>> key, BiFunction<Holder<T>, Provider, List<PickerItem>> items) {
        private List<PickerItem> collectItems(Provider provider) {
            List<PickerItem> collected = new ArrayList<>();
            provider.lookup(this.key).stream().flatMap(HolderLookup::listElements).forEach(holder -> {
                for (PickerItem item : this.items.apply(holder, provider)) {
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
