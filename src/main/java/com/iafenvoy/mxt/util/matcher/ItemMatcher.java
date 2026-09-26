package com.iafenvoy.mxt.util.matcher;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.codec.CombinedCodecs;
import com.iafenvoy.mxt.util.matcher.builtin.ItemEntry;
import com.iafenvoy.mxt.util.matcher.builtin.TagEntry;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
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
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * "Which definition applies to this stack": a declaration lists entries and any one of them matching is a match.
 *
 * <p>Overlapping declarations resolve by {@link #priority()}, which each definition declares in its own file: the
 * highest priority wins, and only a tie falls back to registry order.
 */
public interface ItemMatcher {
    int DEFAULT_PRIORITY = 0;
    Codec<List<Entry>> ENTRIES_CODEC = CombinedCodecs.combineCodec(Entry.CODEC);
    Comparator<ItemMatcher> ORDER = Comparator.comparingInt(ItemMatcher::priority).reversed();

    List<Entry> entries();

    int priority();

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
        return matchers.filter(matcher -> matcher.entries().stream().anyMatch(entry -> entry.matches(stack))).sorted(ORDER);
    }

    interface Entry {
        MapCodec<Entry> TYPED_CODEC = MxtRegistries.ITEM_MATCHER_ENTRY_TYPE.byNameCodec().dispatchMap("type", Entry::codec, Function.identity());
        Codec<Entry> SHORTCUT_CODEC = Codec.either(BuiltInRegistries.ITEM.byNameCodec(), TagKey.hashedCodec(Registries.ITEM)).xmap(
                e -> e.map(ItemEntry::new, TagEntry::new),
                entry -> switch (entry) {
                    case ItemEntry(Item item) -> Either.left(item);
                    case TagEntry(TagKey<Item> tag) -> Either.right(tag);
                    default ->
                            throw new IllegalArgumentException("Only item and tag matchers support shorthand encoding");
                });
        Codec<Entry> CODEC = Codec.either(SHORTCUT_CODEC, TYPED_CODEC.codec()).xmap(e -> e.map(Function.identity(), Function.identity()), Either::right);

        boolean matches(ItemStack stack);

        // Whether matching is decided by the item alone. A caller that caches per item may only cache these; the
        // ones reading stack data have to be asked about every stack.
        default boolean itemLevel() {
            return true;
        }

        MapCodec<? extends Entry> codec();
    }
}
