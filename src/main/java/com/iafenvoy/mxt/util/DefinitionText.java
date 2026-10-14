package com.iafenvoy.mxt.util;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Generate language key: {@code <registry namespace>.<registry path>.<id namespace>.<id path>}
 */
public final class DefinitionText {
    public static final String DESCRIPTION_SUFFIX = "description", MINOR_STAGE_SUFFIX = "minor_stage";

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

    // Every definition text is generated from the entry's own key, so a holder that names no entry has no text and
    // nothing to fall back to: the only such holder is an inline definition, and it is never a registered one.
    private static ResourceKey<?> keyOf(Holder<?> holder) {
        return holder.unwrapKey().orElseThrow(() -> new IllegalArgumentException("A holder that names no registry entry has no definition text"));
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
     * Whether a definition's text reads as something rather than a bare key: a literal, or a defined key.
     */
    //FIXME::No longer use this method
    public static boolean resolved(Component text) {
        return !(text.getContents() instanceof TranslatableContents contents) || Language.getInstance().has(contents.getKey());
    }
}
