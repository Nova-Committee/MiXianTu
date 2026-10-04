package com.iafenvoy.mxt.screen.picker;

import com.iafenvoy.mxt.data.item.*;
import com.iafenvoy.mxt.data.item.TalismanComponent.TriggerMode;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDataMaps;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
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
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The picker's catalogue: each category names one table and one function turning an entry of it into the rows that
 * stand for it, and nothing else decides what a table offers. Client-side contents only - the grid is built from
 * the client's own synced registries and data maps, and taking an item out of it is the vanilla creative
 * inventory's gesture.
 *
 * <p>Reading the catalogue - rows of a category, rows of one mod, the item list a creative tab takes - is
 * {@code com.iafenvoy.mxt.data.CreativeTabHelper}, which is also what other mods call.</p>
 */
public final class ItemPickerManager {
    private static final List<ItemProvider> PROVIDERS = new LinkedList<>();

    static {
        // Vanilla registries: the game's own item and block name is already on the stack, so nothing is written onto it.
        registerSingle(Registries.ITEM, holder -> plain(holder.value().getDefaultInstance(), holder));
        registerSingle(Registries.BLOCK, holder -> plain(holder.value().asItem().getDefaultInstance(), holder));

        // Item data maps: the row is the item the value is attached to, and the item's own name names it.
        registerDataMap(MxtDataMaps.ITEM_AURA, Registries.ITEM, Item::getDefaultInstance);
        registerDataMap(MxtDataMaps.CURRENCY, Registries.ITEM, Item::getDefaultInstance);

        // Item-shaped definitions: the row is the matched item, named by the definition's own language key.
        registerMatcher(MxtResourceKeys.SPIRIT_HERB);
        registerMatcher(MxtResourceKeys.PILL_BINDING);
        registerMatcher(MxtResourceKeys.ARTIFACT);
        registerDataMap(MxtDataMaps.ITEM_BINDING, Registries.ITEM, Item::getDefaultInstance);
        registerDataMap(MxtDataMaps.WEAPON_BINDING, Registries.ITEM, Item::getDefaultInstance);
        registerDataMap(MxtDataMaps.TOOL_BINDING, Registries.ITEM, Item::getDefaultInstance);
        registerDataMap(MxtDataMaps.BLUEPRINT_BINDING, Registries.ITEM, Item::getDefaultInstance);
        registerDataMap(MxtDataMaps.BLOCK_AURA, Registries.BLOCK, block -> block.asItem().getDefaultInstance());

        // One generic carrier per pill definition: the component names the pill, which is how a built-in dose is
        // handed out. Bindings stay item-shaped rows, because a binding is only about which items those are.
        registerSingle(MxtResourceKeys.PILL, holder -> described(componentStack(MxtItems.PILL.toStack(), MxtDataComponents.PILL, PillComponent.ofPill(holder)), holder));

        // Definitions carried by a dedicated item: written onto the stack, and the definition's own name wins.
        registerSingle(MxtResourceKeys.CONTRACT_TYPE, holder -> described(componentStack(MxtItems.CONTRACT_SCROLL.toStack(), MxtDataComponents.CONTRACT_SCROLL, new ContractScrollComponent(Optional.of(holder))), holder));
        registerSingle(MxtResourceKeys.SECRET_REALM, holder -> described(componentStack(MxtItems.SECRET_REALM_TOKEN.toStack(), MxtDataComponents.SECRET_REALM_TOKEN, new SecretRealmTokenComponent(Optional.of(holder))), holder));
        registerSingle(MxtResourceKeys.FORMATION, holder -> described(componentStack(MxtItems.FORMATION_PLATE.toStack(), MxtDataComponents.FORMATION_PLATE, new FormationPlateComponent(List.of(), Optional.of(holder))), holder));
        registerSingle(MxtResourceKeys.TALISMAN, holder -> described(componentStack(MxtItems.TALISMAN.toStack(), MxtDataComponents.TALISMAN, new TalismanComponent(List.of(holder), TriggerMode.FIRE)), holder));
        // A technique has no item of its own: the row is the carrier the mod generates for it, which is the item
        // the declaration names or the jade slip.
        registerSingle(MxtResourceKeys.TECHNIQUE, (holder, access) -> described(ItemBindingService.techniqueCarrier(access, holder), holder));
        register(MxtResourceKeys.ALCHEMY_FURNACE, (holder, access) -> {
            if (!holder.isBound()) return List.of();
            ItemStack stack = componentStack(MxtBlocks.ALCHEMY_FURNACE.toStack(), MxtDataComponents.ALCHEMY_FURNACE, holder);
            Component name = DefinitionText.name(holder);
            Component quality = QualityService.find(access, stack)
                    .map(value -> QualityService.coloredName(value, value.value().name()))
                    .orElse(Component.translatable("screen.mxt.alchemy.no_quality"));
            return List.of(new PickerItem(stack, names(name, quality, idName(HolderHelper.idOrNull(holder)))));
        });
        registerSingle(MxtResourceKeys.ALCHEMY_WALL_MATERIAL, holder -> described(componentStack(MxtBlocks.ALCHEMY_FURNACE_CASING.toStack(), MxtDataComponents.ALCHEMY_WALL_MATERIAL, holder), holder));

        // Auras have no item of their own, so one stand-in item carries whatever the definition is called.
        registerSingle(MxtResourceKeys.AURA, holder -> described(MxtItems.SPIRIT_STONE.toStack(), holder));
        // A spirit root and a physique have items of their own: each row carries the component, so the stack taken
        // out grants that root or that physique.
        registerSingle(MxtResourceKeys.SPIRIT_ROOT, holder -> described(componentStack(MxtItems.SPIRIT_ROOT.toStack(), MxtDataComponents.SPIRIT_ROOT, holder), holder));
        registerSingle(MxtResourceKeys.PHYSIQUE, holder -> described(componentStack(MxtItems.PHYSIQUE.toStack(), MxtDataComponents.PHYSIQUE, holder), holder));

        // A quality carries its name in the data pack rather than in a language file, so that name wins - and the
        // row is drawn in the tier's own colour, which is the one place the ladder is visible side by side.
        registerSingle(MxtResourceKeys.ITEM_QUALITY, holder -> described(MxtItems.IDENTIFICATION_MIRROR.toStack(), holder, QualityService.coloredName(holder, holder.value().name())));
    }

    /**
     * One row the picker can offer. The row's display name is always among {@code names}, a list rather than a
     * single name because a definition can be known by more than one.
     */
    public record PickerItem(ItemStack stack, List<Component> names) {
    }

    /**
     * One category and the rows it can offer, keyed by the id the row's own entry is known by.
     */
    public record ItemProvider(PickerCategory category,
                               BiFunction<Provider, Predicate<Identifier>, List<PickerItem>> items) {
    }

    /**
     * The id of every category, in the order they were registered.
     */
    public static List<PickerCategory> categories() {
        return PROVIDERS.stream().map(ItemProvider::category).toList();
    }

    /**
     * The category one id names, empty for an id this mod does not offer.
     */
    public static Optional<PickerCategory> category(Identifier id) {
        return categories().stream().filter(category -> category.id().equals(id)).findFirst();
    }

    public static @Nullable ItemProvider provider(PickerCategory category) {
        for (ItemProvider provider : PROVIDERS)
            if (provider.category().equals(category)) return provider;
        return null;
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
        register(key, (holder, _) -> List.of(provider.apply(holder)));
    }

    // The provider is what a row needs when it has to resolve a second registry - a technique's carrier item is
    // named by its declaration, and only the registries know which item that is.
    public static <T> void registerSingle(ResourceKey<Registry<T>> key, BiFunction<Holder<T>, Provider, PickerItem> provider) {
        register(key, (holder, access) -> List.of(provider.apply(holder, access)));
    }

    // A key keeps the first provider registered for it; a later one is inert rather than merged, so a mod that adds
    // rows under an id the mod already uses gets nothing back instead of a doubled category.
    public static <T> void register(ResourceKey<Registry<T>> key, Function<Holder<T>, List<PickerItem>> provider) {
        register(key, (holder, _) -> provider.apply(holder));
    }

    public static <T> void register(ResourceKey<Registry<T>> key, BiFunction<Holder<T>, Provider, List<PickerItem>> rows) {
        PickerCategory category = new PickerCategory.OfRegistry<>(key);
        PROVIDERS.add(new ItemProvider(category, (provider, filter) -> {
            List<PickerItem> collected = new ArrayList<>();
            provider.lookup(key).stream().flatMap(HolderLookup::listElements).forEach(holder -> {
                if (!filter.test(holder.key().identifier())) return;
                for (PickerItem item : rows.apply(holder, provider)) {
                    if (item == null || item.stack().isEmpty()) continue;
                    collected.add(item);
                }
            });
            return List.copyOf(collected);
        }));
    }

    // A data map has no definitions to walk: every entry carrying a value is one row, and the entry names itself.
    private static <R> void registerDataMap(DataMapType<R, ?> table, ResourceKey<Registry<R>> registry, Function<R, ItemStack> row) {
        PROVIDERS.add(new ItemProvider(new PickerCategory.OfDataMap(table), (provider, filter) ->
                provider.lookupOrThrow(registry).listElements()
                        .filter(holder -> holder.getData(table) != null)
                        .filter(holder -> filter.test(holder.key().identifier()))
                        .map(holder -> {
                            ItemStack stack = row.apply(holder.value());
                            return new PickerItem(stack, names(stack.getHoverName(), idName(holder.key().identifier())));
                        })
                        .filter(item -> !item.stack().isEmpty())
                        .toList()));
    }

    private static <T extends ItemMatcher> void registerMatcher(ResourceKey<Registry<T>> key) {
        registerMatcher(key, ItemMatcher::entries);
    }

    private static <T extends ItemMatcher> void registerMatcher(ResourceKey<Registry<T>> key, Function<T, List<Entry>> entries) {
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

    private ItemPickerManager() {
    }
}
