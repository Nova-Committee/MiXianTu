package com.iafenvoy.mxt.util;

import com.iafenvoy.mxt.util.codec.CombinedCodecs;
import com.iafenvoy.mxt.data.item.ItemDefinitionReference;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface ItemMatcher {
    Codec<List<Entry>> ENTRIES_CODEC = CombinedCodecs.combineCodec(Entry.CODEC);

    List<Entry> entries();

    default int priority() {
        return 0;
    }

    static <T extends ItemMatcher> Optional<T> find(Registry<T> registry, @NotNull ItemStack stack) {
        return find(registry.stream(), stack);
    }

    static <T extends ItemMatcher> Optional<T> find(Stream<T> matchers, @NotNull ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return matchers
                .filter(matcher -> matcher.entries().stream().anyMatch(entry -> entry.matches(stack)))
                .max(Comparator.comparingInt(ItemMatcher::priority));
    }

    /** One matcher entry: a physical item, an item tag, or a data-driven item reference. */
    sealed interface Entry permits ItemEntry, TagEntry, DefinitionEntry {
        Codec<Entry> CODEC = Trio.codec(
                BuiltInRegistries.ITEM.byNameCodec(),
                TagKey.hashedCodec(Registries.ITEM),
                ItemDefinitionReference.OBJECT_CODEC
        ).xmap(
                value -> value.map(ItemEntry::new, TagEntry::new, DefinitionEntry::new),
                entry -> switch (entry) {
                    case ItemEntry(Item item) -> Trio.first(item);
                    case TagEntry(TagKey<Item> tag) -> Trio.second(tag);
                    case DefinitionEntry(ItemDefinitionReference reference) -> Trio.third(reference);
                }
        );

        boolean matches(ItemStack stack);

        static Entry item(Item item) {
            return new ItemEntry(item);
        }

        static Entry tag(TagKey<Item> tag) {
            return new TagEntry(tag);
        }

        static Entry definition(ItemDefinitionReference reference) {
            return new DefinitionEntry(reference);
        }
    }

    record ItemEntry(Item item) implements Entry {
        @Override
        public boolean matches(ItemStack stack) {
            return stack.is(this.item);
        }
    }

    record TagEntry(TagKey<Item> tag) implements Entry {
        @Override
        public boolean matches(ItemStack stack) {
            return stack.is(this.tag);
        }
    }

    record DefinitionEntry(ItemDefinitionReference reference) implements Entry {
        @Override
        public boolean matches(ItemStack stack) {
            return ItemBindingService.matches(stack, this.reference);
        }
    }
}
