package com.iafenvoy.mxt.util;

import com.iafenvoy.mxt.util.codec.CombinedCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Codec;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import java.util.Locale;
import java.util.regex.Pattern;
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
    sealed interface Entry permits ItemEntry, TagEntry, PatternEntry {
        Codec<JsonElement> JSON = Codec.PASSTHROUGH.xmap(dynamic -> (JsonElement) dynamic.getValue(),
                value -> new Dynamic<>(JsonOps.INSTANCE, value));
        Codec<Entry> CODEC = JSON.comapFlatMap(Entry::decode, Entry::encode);

        boolean matches(ItemStack stack);

        static Entry item(Item item) {
            return new ItemEntry(item);
        }

        static Entry tag(TagKey<Item> tag) {
            return new TagEntry(tag);
        }

        private static DataResult<Entry> decode(JsonElement element) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString())
                return decodeString(element.getAsString());
            if (!element.isJsonObject()) return DataResult.error(() -> "Item matcher must be a string or object");
            JsonObject object = element.getAsJsonObject();
            if (object.has("id")) return decode(object.get("id"));
            if (object.has("tag")) return decodeString("#" + object.get("tag").getAsString());
            if (object.has("wildcard")) return DataResult.success(new PatternEntry(object.get("wildcard").getAsString(), false));
            if (object.has("regex")) return DataResult.success(new PatternEntry(object.get("regex").getAsString(), true));
            return DataResult.error(() -> "Item matcher object must contain id, tag, regex or wildcard");
        }

        private static DataResult<Entry> decodeString(String value) {
            if (value.startsWith("#")) {
                var id = net.minecraft.resources.Identifier.tryParse(value.substring(1));
                return id == null ? DataResult.error(() -> "Invalid item tag: " + value)
                        : DataResult.success(tag(TagKey.create(Registries.ITEM, id)));
            }
            if (value.startsWith("regex:")) return DataResult.success(new PatternEntry(value.substring(6), true));
            if (value.indexOf('*') >= 0 || value.indexOf('?') >= 0) return DataResult.success(new PatternEntry(value, false));
            var id = net.minecraft.resources.Identifier.tryParse(value);
            if (id == null) return DataResult.error(() -> "Invalid item id: " + value);
            return BuiltInRegistries.ITEM.getOptional(id).<DataResult<Entry>>map(item -> DataResult.success(item(item)))
                    .orElseGet(() -> DataResult.error(() -> "Unknown item: " + value));
        }

        private static JsonElement encode(Entry entry) {
            return switch (entry) {
                case ItemEntry(Item item) -> new com.google.gson.JsonPrimitive(BuiltInRegistries.ITEM.getKey(item).toString());
                case TagEntry(TagKey<Item> tag) -> new com.google.gson.JsonPrimitive("#" + tag.location());
                case PatternEntry(String pattern, boolean regex) -> {
                    JsonObject object = new JsonObject();
                    object.addProperty(regex ? "regex" : "wildcard", pattern);
                    yield object;
                }
            };
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

    record PatternEntry(String pattern, boolean regex) implements Entry {
        public PatternEntry {
            if (pattern.isEmpty()) throw new IllegalArgumentException("Item matcher pattern must not be empty");
            if (regex) Pattern.compile(pattern);
        }

        @Override
        public boolean matches(ItemStack stack) {
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (regex) return Pattern.matches(pattern, id);
            StringBuilder expression = new StringBuilder("^");
            for (int i = 0; i < pattern.length(); i++) {
                char c = pattern.charAt(i);
                if (c == '*') expression.append(".*");
                else if (c == '?') expression.append('.');
                else expression.append(Pattern.quote(String.valueOf(c)));
            }
            return Pattern.matches(expression.append('$').toString(), id);
        }
    }
}
