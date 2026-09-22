package com.iafenvoy.mxt.util;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Localized names for datapack definitions, keyed {@code <category>.<namespace>.<path>} off the registry the
 * definition lives in, so {@code mxt:fire} in {@code mxt:aura} reads as {@code aura.mxt.fire}. An untranslated key
 * renders as the key itself; {@link #CATEGORIES} lists registries translated under a different path.
 */
public final class DefinitionText {
    private static final Map<Identifier, String> CATEGORIES = new LinkedHashMap<>();

    static {
        category(MxtResourceKeys.ITEM_QUALITY, "quality");
    }

    public static void category(ResourceKey<? extends Registry<?>> registry, String category) {
        CATEGORIES.put(registry.identifier(), category);
    }

    public static String key(ResourceKey<?> key) {
        return key(key.registry(), key.identifier());
    }

    public static String key(Identifier registry, Identifier id) {
        return id.toLanguageKey(CATEGORIES.getOrDefault(registry, registry.getPath()));
    }

    public static MutableComponent name(ResourceKey<?> key) {
        return Component.translatable(key(key));
    }

    public static MutableComponent name(Holder<?> holder) {
        return holder.unwrapKey().map(DefinitionText::name).orElseGet(() -> Component.literal("?"));
    }

    public static MutableComponent name(Holder<?> holder, String category) {
        Identifier id = HolderHelper.idOrNull(holder);
        return id == null ? Component.literal("?") : name(id, category);
    }

    public static MutableComponent name(Identifier id, String category) {
        return Component.translatable(id.toLanguageKey(category));
    }

    /**
     * A free-form pack value, shown verbatim unless the language file translates {@code mxt.rarity.<value>};
     * the same reading a technique's {@code grade} gets.
     */
    public static MutableComponent rarity(String rarity) {
        String key = "mxt.rarity." + rarity;
        return Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(rarity);
    }
}
