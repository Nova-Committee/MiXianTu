package com.iafenvoy.mxt.util;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Localized names for datapack definitions, keyed {@code <category>.<registry namespace>.<id namespace>.<id path>}
 * off the registry the definition lives in, so {@code mxt:fire} in {@code mxt:aura} reads as
 * {@code aura.mxt.mxt.fire}. An untranslated key renders as the key itself; {@link #CATEGORIES} lists registries
 * translated under a different path. A definition implementing {@link NamedDefinition} carries its texts itself,
 * so the pack-written name is what gets shown.
 */
public final class DefinitionText {
    private static final Map<Identifier, String> CATEGORIES = new LinkedHashMap<>();

    static {
        category(MxtResourceKeys.ITEM_QUALITY, "quality");
    }

    public static void category(ResourceKey<? extends Registry<?>> registry, String category) {
        CATEGORIES.put(registry.identifier(), category);
    }

    // The category a registry's keys are built with, for code that has to build a key by hand.
    public static String category(Identifier registry) {
        return CATEGORIES.getOrDefault(registry, registry.getPath());
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
    public static Component defaultText(String category, ResourceKey<?> entry, String suffix) {
        Identifier registry = entry.registry();
        return Component.translatable(key(category, registry.getNamespace(), entry.identifier()) + suffix);
    }

    public static MutableComponent name(ResourceKey<?> key) {
        return Component.translatable(key(key));
    }

    public static MutableComponent name(Holder<?> holder) {
        if (holder.value() instanceof NamedDefinition named) return named.name().copy();
        return holder.unwrapKey().map(DefinitionText::name).orElseGet(() -> Component.literal("?"));
    }

    public static MutableComponent name(Holder<?> holder, String category) {
        if (holder.value() instanceof NamedDefinition named) return named.name().copy();
        Identifier id = HolderHelper.idOrNull(holder);
        return id == null ? Component.literal("?") : Component.translatable(key(category, registryNamespace(holder), id));
    }

    // A bare id names no registry, so this overload assumes the mod's own namespace, which every MiXianTu
    // registry uses.
    public static MutableComponent name(Identifier id, String category) {
        return Component.translatable(key(category, MiXianTu.MOD_ID, id));
    }

    /** Whether a definition's text reads as something rather than a bare key: a literal, or a defined key. */
    public static boolean resolved(Component text) {
        return !(text.getContents() instanceof TranslatableContents contents)
                || Language.getInstance().has(contents.getKey());
    }

    /**
     * A free-form pack value, shown verbatim unless the language file translates {@code mxt.rarity.<value>};
     * the same reading a technique's {@code grade} gets.
     */
    public static MutableComponent rarity(String rarity) {
        String key = "mxt.rarity." + rarity;
        return Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(rarity);
    }

    private static String registryNamespace(Holder<?> holder) {
        return holder.unwrapKey().map(key -> key.registry().getNamespace()).orElse(MiXianTu.MOD_ID);
    }
}
