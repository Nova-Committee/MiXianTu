package com.iafenvoy.mxt.util;

import com.iafenvoy.mxt.util.codec.CombinedCodecs;
import com.mojang.datafixers.util.Either;
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
        return findAll(matchers, stack).findFirst();
    }

    static <T extends ItemMatcher> Stream<T> findAll(Registry<T> registry, @NotNull ItemStack stack) {
        return findAll(registry.stream(), stack);
    }

    static <T extends ItemMatcher> Stream<T> findAll(Stream<T> matchers, @NotNull ItemStack stack) {
        return matchers.filter(matcher -> matcher.entries().stream().anyMatch(entry -> entry.matches(stack))).sorted(Comparator.comparingInt(ItemMatcher::priority));
    }

    /**
     * One matcher entry: a physical item or an item tag.
     */
    sealed interface Entry permits ItemEntry, TagEntry {
        Codec<Entry> CODEC = Codec.either(BuiltInRegistries.ITEM.byNameCodec(), TagKey.hashedCodec(Registries.ITEM)).xmap(
                value -> value.map(ItemEntry::new, TagEntry::new),
                entry -> switch (entry) {
                    case ItemEntry(Item item) -> Either.left(item);
                    case TagEntry(TagKey<Item> tag) -> Either.right(tag);
                });

        boolean matches(ItemStack stack);

        static Entry item(Item item) {
            return new ItemEntry(item);
        }

        static Entry tag(TagKey<Item> tag) {
            return new TagEntry(tag);
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
}
