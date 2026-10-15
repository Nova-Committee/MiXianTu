package com.iafenvoy.mxt.util;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.Optional;

/**
 * Generate language key: {@code <registry namespace>.<registry path>.<id namespace>.<id path>}
 */
public final class DefinitionText {
    public static final String DESCRIPTION_SUFFIX = "description", MINOR_STAGE_SUFFIX = "minor_stage";

    private static ResourceKey<?> keyOf(Holder<?> holder) {
        return holder.unwrapKey().orElseThrow(() -> new IllegalArgumentException("A holder that names no registry entry has no definition text"));
    }

    public static String key(Holder<?> holder) {
        return key(keyOf(holder));
    }

    public static String key(ResourceKey<?> key) {
        return key(key.registry(), key.identifier());
    }

    public static <T> String key(ResourceKey<Registry<T>> registry, Identifier id) {
        return key(registry.identifier(), id);
    }

    public static String key(Identifier registry, Identifier id) {
        return id.toLanguageKey(registry.toLanguageKey());
    }

    public static String key(Holder<?> holder, String suffix) {
        return key(keyOf(holder), suffix);
    }

    public static String key(ResourceKey<?> key, String suffix) {
        return key(key.registry(), key.identifier(), suffix);
    }

    public static <T> String key(ResourceKey<Registry<T>> registry, Identifier id, String suffix) {
        return key(registry.identifier(), id, suffix);
    }

    public static String key(Identifier registry, Identifier id, String suffix) {
        return id.toLanguageKey(registry.toLanguageKey(), suffix);
    }

    private static MutableComponent translatable(String key) {
        return Component.translatable(key);
    }

    public static MutableComponent text(Holder<?> holder) {
        return translatable(key(holder));
    }

    public static MutableComponent text(ResourceKey<?> key) {
        return translatable(key(key));
    }

    public static <T> MutableComponent text(ResourceKey<Registry<T>> registry, Identifier id) {
        return translatable(key(registry, id));
    }

    public static MutableComponent text(Identifier registry, Identifier id) {
        return translatable(key(registry, id));
    }

    public static MutableComponent text(Holder<?> holder, String suffix) {
        return translatable(key(holder, suffix));
    }

    public static MutableComponent text(ResourceKey<?> key, String suffix) {
        return translatable(key(key, suffix));
    }

    public static <T> MutableComponent text(ResourceKey<Registry<T>> registry, Identifier id, String suffix) {
        return translatable(key(registry, id, suffix));
    }

    public static MutableComponent text(Identifier registry, Identifier id, String suffix) {
        return translatable(key(registry, id, suffix));
    }

    /**
     * The description of a definition, which is its name key plus {@link #DESCRIPTION_SUFFIX}.
     */
    public static MutableComponent description(Holder<?> holder) {
        return text(holder, DESCRIPTION_SUFFIX);
    }

    public static MutableComponent minorStage(Holder<?> holder, int index) {
        return text(holder, MINOR_STAGE_SUFFIX + "." + index);
    }

    /**
     * The text itself, or nothing when there is none to draw: an entry a pack wrote no words for must be skipped
     * rather than printed as its own key. A description is the case this exists for - it is a generated key, so it
     * only reads as text once a resource pack translated it - but a literal, and a translatable carrying a
     * fallback, always count as text.
     */
    public static Optional<MutableComponent> resolved(Component text) {
        if (text.getContents() instanceof TranslatableContents contents && contents.getFallback() == null && !Language.getInstance().has(contents.getKey()))
            return Optional.empty();
        return text.getString().isBlank() ? Optional.empty() : Optional.of(text.copy());
    }

    /**
     * A name for somewhere that has to show something - a row, a title, an argument inside another sentence. An id
     * still says which entry this is, where the generated key says nothing; a place free to draw nothing at all
     * uses {@link #resolved(Component)} instead.
     */
    public static MutableComponent nameOrId(Holder<?> holder) {
        return resolved(text(holder)).orElseGet(() -> Component.literal(keyOf(holder).identifier().toString()));
    }

    public static MutableComponent nameOrId(ResourceKey<?> key) {
        return resolved(text(key)).orElseGet(() -> Component.literal(key.identifier().toString()));
    }

    public static <T> MutableComponent nameOrId(ResourceKey<Registry<T>> registry, Identifier id) {
        return resolved(text(registry, id)).orElseGet(() -> Component.literal(id.toString()));
    }
}
