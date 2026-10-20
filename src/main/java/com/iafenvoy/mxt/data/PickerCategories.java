package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.data.alchemy.HeatSource;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb.Growth;
import com.iafenvoy.mxt.data.aura.BlockAura;
import com.iafenvoy.mxt.data.item.*;
import com.iafenvoy.mxt.data.item.TalismanComponent.TriggerMode;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.iafenvoy.mxt.util.matcher.builtin.ItemEntry;
import com.iafenvoy.mxt.util.matcher.builtin.TagEntry;
import com.iafenvoy.picker.PickerCatalogue;
import com.iafenvoy.picker.api.PickerRow;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
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
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * What this mod's registries look like in the item picker. Every category is one datapack registry plus one function
 * turning an entry of it into the rows that stand for it, registered through {@link PickerCatalogue}; the picker owns
 * the screen, the payload, the command and the reading, so nothing here draws or searches anything.
 *
 * <p>Registration runs once from the mod constructor. {@code minecraft:item} and {@code minecraft:block} are the
 * picker's own built-ins, so they are not registered here.
 */
public final class PickerCategories {
    private PickerCategories() {
    }

    public static void register() {
        // Item-shaped definitions: the row is the matched item, named by the definition's own language key.
        registerMatcher(MxtResourceKeys.ITEM_AURA);
        registerMatcher(MxtResourceKeys.CURRENCY);
        registerMatcher(MxtResourceKeys.PILL_BINDING);
        registerMatcher(MxtResourceKeys.ARTIFACT);
        registerMatcher(MxtResourceKeys.ITEM_BINDING);
        registerMatcher(MxtResourceKeys.WEAPON_BINDING);
        registerMatcher(MxtResourceKeys.TOOL_BINDING);
        registerMatcher(MxtResourceKeys.BLUEPRINT_BINDING);
        registerMatcher(MxtResourceKeys.DEFAULT_QUALITY);

        // Block-shaped definitions: the definition names no item, so the row is every block its `blocks` claims.
        registerBlocks(MxtResourceKeys.BLOCK_AURA, BlockAura::blocks);
        registerBlocks(MxtResourceKeys.HEAT_SOURCE, HeatSource::blocks);
        // A herb is item-shaped in effect: what a player reads it on is its fruit, not the block it grows as.
        registerHerbs(MxtResourceKeys.SPIRIT_HERB);

        // One generic carrier per pill definition: the component names the pill, which is how a built-in dose is
        // handed out. Bindings stay item-shaped rows, because a binding is only about which items those are.
        PickerCatalogue.registerSingle(MxtResourceKeys.PILL, holder -> described(componentStack(MxtItems.PILL.toStack(), MxtDataComponents.PILL, PillComponent.ofPill(holder)), holder));

        // Definitions carried by a dedicated item: written onto the stack, and the definition's own name wins.
        PickerCatalogue.registerSingle(MxtResourceKeys.CONTRACT_TYPE, holder -> described(componentStack(MxtItems.CONTRACT_SCROLL.toStack(), MxtDataComponents.CONTRACT_SCROLL, new ContractScrollComponent(Optional.of(holder))), holder));
        PickerCatalogue.registerSingle(MxtResourceKeys.SECRET_REALM, holder -> described(componentStack(MxtItems.SECRET_REALM_TOKEN.toStack(), MxtDataComponents.SECRET_REALM_TOKEN, new SecretRealmTokenComponent(Optional.of(holder))), holder));
        PickerCatalogue.registerSingle(MxtResourceKeys.FORMATION, holder -> described(componentStack(MxtItems.FORMATION_PLATE.toStack(), MxtDataComponents.FORMATION_PLATE, new FormationPlateComponent(List.of(), Optional.of(holder))), holder));
        PickerCatalogue.registerSingle(MxtResourceKeys.TALISMAN, holder -> described(componentStack(MxtItems.TALISMAN.toStack(), MxtDataComponents.TALISMAN, TalismanComponent.of(holder, TriggerMode.FIRE)), holder));
        // A technique has no item of its own: the row is the carrier the mod generates for it, which is the item
        // the declaration names or the jade slip.
        PickerCatalogue.registerSingle(MxtResourceKeys.TECHNIQUE, (holder, access) -> described(ItemBindingService.techniqueCarrier(access, holder), holder));
        PickerCatalogue.registerSingle(MxtResourceKeys.ALCHEMY_FURNACE, (holder, access) -> {
            ItemStack stack = componentStack(MxtBlocks.ALCHEMY_FURNACE.toStack(), MxtDataComponents.ALCHEMY_FURNACE, holder);
            Component name = searchName(holder);
            Component quality = QualityService.find(access, stack)
                    .map(QualityService::displayName)
                    .orElse(Component.translatable("screen.mxt.alchemy.no_quality"));
            return new PickerRow(stack, names(name, quality, idName(HolderHelper.idOrNull(holder))));
        });
        PickerCatalogue.registerSingle(MxtResourceKeys.ALCHEMY_WALL_MATERIAL, holder -> described(componentStack(MxtBlocks.ALCHEMY_FURNACE_CASING.toStack(), MxtDataComponents.ALCHEMY_WALL_MATERIAL, holder), holder));

        // Auras have no item of their own, so one stand-in item carries whatever the definition is called.
        PickerCatalogue.registerSingle(MxtResourceKeys.AURA, holder -> described(MxtItems.SPIRIT_STONE.toStack(), holder));
        // A spirit root and a physique have items of their own: each row carries the component, so the stack taken
        // out grants that root or that physique.
        PickerCatalogue.registerSingle(MxtResourceKeys.SPIRIT_ROOT, holder -> described(componentStack(MxtItems.SPIRIT_ROOT.toStack(), MxtDataComponents.SPIRIT_ROOT, holder), holder));
        PickerCatalogue.registerSingle(MxtResourceKeys.PHYSIQUE, holder -> described(componentStack(MxtItems.PHYSIQUE.toStack(), MxtDataComponents.PHYSIQUE, holder), holder));

        // A quality carries its name and its colour in the data pack rather than in a language file, so that name wins -
        // and the row is drawn in the tier's own colour, which is the one place the ladder is visible side by side.
        PickerCatalogue.registerSingle(MxtResourceKeys.ITEM_QUALITY, holder -> described(MxtItems.IDENTIFICATION_MIRROR.toStack(), holder, DefinitionText.resolved(QualityService.displayName(holder)).orElse(null)));
    }

    // Item and tag entries name their items outright. Every other entry is asked of each registered item instead,
    // because a wildcard, a regex, an ingredient or a capability answers from the item alone. The entries that read
    // the stack - a tier, a technique component - answer no for a bare stack and so contribute no row, which is the
    // reading this side has always had.
    private static List<ItemStack> stackItems(List<Entry> entries) {
        Set<Item> items = new LinkedHashSet<>();
        List<Entry> byItem = new ArrayList<>();
        for (Entry entry : entries) {
            switch (entry) {
                case ItemEntry(Item item) -> items.add(item);
                case TagEntry(TagKey<Item> tag) -> BuiltInRegistries.ITEM.get(tag)
                        .ifPresent(set -> set.forEach(holder -> items.add(holder.value())));
                default -> byItem.add(entry);
            }
        }
        if (!byItem.isEmpty()) {
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack candidate = item.getDefaultInstance();
                for (Entry entry : byItem) {
                    if (!entry.matches(candidate)) continue;
                    items.add(item);
                    break;
                }
            }
        }
        List<ItemStack> stacks = new ArrayList<>(items.size());
        for (Item item : items) stacks.add(new ItemStack(item));
        return stacks;
    }

    /**
     * A herb is shown by the item it is read from, which is the fruit its {@code growth.drops} claims - that is the
     * stack a tooltip and a furnace actually ask about. A herb with no growth bears no fruit, so it falls back to
     * the item form of every block it claims, which is the same fallback {@code SpiritHerbService.find} uses.
     */
    private static void registerHerbs(ResourceKey<Registry<SpiritHerb>> key) {
        PickerCatalogue.register(key, (holder, access) -> {
            Identifier id = HolderHelper.idOrNull(holder);
            Component definition = searchName(holder);
            SpiritHerb herb = holder.value();
            List<PickerRow> rows = new ArrayList<>();
            Optional<Growth> growth = herb.growth();
            if (growth.isPresent()) {
                // A tag entry shows the whole fruit family it covers.
                for (Holder<Item> item : RegistryCodecs.resolve(growth.get().drops(), access, Registries.ITEM).toList()) {
                    ItemStack stack = item.value().getDefaultInstance();
                    if (stack.isEmpty()) continue;
                    rows.add(new PickerRow(stack, names(stack.getHoverName(), definition, idName(id))));
                }
                return List.copyOf(rows);
            }
            for (Holder<Block> block : RegistryCodecs.resolve(herb.blocks(), access, Registries.BLOCK).toList()) {
                ItemStack stack = block.value().asItem().getDefaultInstance();
                if (stack.isEmpty()) continue;
                rows.add(new PickerRow(stack, names(stack.getHoverName(), definition, idName(id))));
            }
            return List.copyOf(rows);
        });
    }

    // A definition that names blocks rather than items is expanded against the block registry: every block it
    // claims is one row, named by the definition, so a tag entry shows the whole family it covers.
    private static <T> void registerBlocks(ResourceKey<Registry<T>> key, Function<T, List<Either<Holder<Block>, TagKey<Block>>>> blocks) {
        PickerCatalogue.register(key, (holder, access) -> {
            Identifier id = HolderHelper.idOrNull(holder);
            Component definition = searchName(holder);
            List<PickerRow> rows = new ArrayList<>();
            for (Holder<Block> block : RegistryCodecs.resolve(blocks.apply(holder.value()), access, Registries.BLOCK).toList()) {
                ItemStack stack = block.value().asItem().getDefaultInstance();
                if (stack.isEmpty()) continue;
                rows.add(new PickerRow(stack, names(stack.getHoverName(), definition, idName(id))));
            }
            return List.copyOf(rows);
        });
    }

    private static <T extends ItemMatcher> void registerMatcher(ResourceKey<Registry<T>> key) {
        registerMatcher(key, ItemMatcher::entries);
    }

    private static <T> void registerMatcher(ResourceKey<Registry<T>> key, Function<T, List<Entry>> entries) {
        PickerCatalogue.register(key, holder -> {
            Identifier id = HolderHelper.idOrNull(holder);
            Component definition = searchName(holder);
            List<PickerRow> rows = new ArrayList<>();
            for (ItemStack stack : stackItems(entries.apply(holder.value())))
                rows.add(new PickerRow(stack, names(stack.getHoverName(), definition, idName(id))));
            return rows;
        });
    }

    private static PickerRow described(ItemStack stack, Holder<?> holder) {
        return described(stack, holder, searchName(holder));
    }

    // The picker never draws these: they only widen what the search box matches, and a key no pack translated would
    // widen it with noise. The id is added separately, so dropping one loses no way to find the row.
    private static @Nullable Component searchName(Holder<?> holder) {
        return DefinitionText.resolved(DefinitionText.text(holder)).orElse(null);
    }

    private static PickerRow described(ItemStack stack, Holder<?> holder, Component name) {
        return new PickerRow(stack, names(name, idName(HolderHelper.idOrNull(holder))));
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
}
