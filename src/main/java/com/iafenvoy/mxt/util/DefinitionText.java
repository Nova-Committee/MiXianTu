package com.iafenvoy.mxt.util;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.NamedDefinition;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Localized names for datapack definitions, keyed {@code <category>.<registry namespace>.<id namespace>.<id path>}
 * off the registry the definition lives in, so {@code mxt:fire} in {@code mxt:aura} reads as
 * {@code aura.mxt.mxt.fire}; the category is always the registry's own path, which is why no caller passes one.
 * An untranslated key renders as the key itself. A definition implementing {@link NamedDefinition} carries its
 * texts itself, so the pack-written name and description are what get shown; only a holder that is bound can be
 * asked for them, since a held reference to an entry the current pack no longer provides has no value to read.
 * <p>
 * Which entry point a caller uses follows what it holds: {@link #name(Holder)} / {@link #description(Holder)} for
 * anything holding the reference, {@link #name(ResourceKey)} for a key whose registry is still known, and
 * {@link #name(Identifier, String)} only where nothing but the id survived - a ledger key, a command argument, a
 * network payload, or a definition reached through one of those.
 */
public final class DefinitionText {
    /**
     * What the description field appends to a definition's own key, so a reader that falls back to a key derives
     * the same one the field was defaulted from.
     */
    public static final String DESCRIPTION_SUFFIX = ".description";

    // The category a registry's keys are built with, for code that has to build a key by hand.
    public static String category(Identifier registry) {
        return registry.getPath();
    }

    public static String key(ResourceKey<?> key) {
        return key(key.registry(), key.identifier());
    }

    public static String key(Identifier registry, Identifier id) {
        return key(category(registry), registry.getNamespace(), id);
    }

    // The one place a definition key is built; ContextNameCodec generates its defaults through it.
    public static String key(String category, String registryNamespace, Identifier id) {
        return category + "." + registryNamespace + "." + id.toLanguageKey();
    }

    /**
     * The text a definition's own field falls back to when the pack omits it: the entry's key plus that field's
     * own suffix ({@code ""} for the name, {@code .description} for a description). The single place the fallback
     * is built, so a definition carrying the text and one deriving it can never disagree.
     */
    public static MutableComponent defaultText(String category, ResourceKey<?> entry, String suffix) {
        Identifier registry = entry.registry();
        return Component.translatable(key(category, registry.getNamespace(), entry.identifier()) + suffix);
    }

    public static MutableComponent name(ResourceKey<?> key) {
        return Component.translatable(key(key));
    }

    public static MutableComponent name(Holder<?> holder) {
        if (holder.isBound() && holder.value() instanceof NamedDefinition named) return named.name().copy();
        return holder.unwrapKey().map(DefinitionText::name).orElseGet(() -> Component.literal("?"));
    }

    // A bare id names no registry, so this overload assumes the mod's own namespace, which every MiXianTu
    // registry uses. Only for the paths that hold an id and nothing else: a ledger key, a command argument, a
    // network payload, or a definition reached through one of those.
    public static MutableComponent name(Identifier id, String category) {
        return Component.translatable(key(category, MiXianTu.MOD_ID, id));
    }

    /**
     * The text the definition wrote for its {@code description} field, falling back to that field's own key. Unlike
     * a name this is optional prose, so callers printing it to a tooltip should skip it unless {@link #resolved}
     * accepts it.
     */
    public static MutableComponent description(Holder<?> holder) {
        if (holder.isBound() && holder.value() instanceof NamedDefinition named) return named.description().copy();
        return holder.unwrapKey()
                .map(key -> defaultText(category(key.registry()), key, DESCRIPTION_SUFFIX))
                .orElseGet(() -> Component.literal("?"));
    }

    /**
     * Whether a definition's text reads as something rather than a bare key: a literal, or a defined key.
     */
    public static boolean resolved(Component text) {
        return !(text.getContents() instanceof TranslatableContents contents)
                || Language.getInstance().has(contents.getKey());
    }
}
