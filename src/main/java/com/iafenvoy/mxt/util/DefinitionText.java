package com.iafenvoy.mxt.util;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds localized names for datapack definitions from their registry identifiers.
 *
 * <p>A definition is not an item and carries no name of its own - nothing about it can be read off a stack - so
 * the only name it can have is one a language file gives it. That name is keyed by position: the registry the
 * definition is registered in, its namespace, and its path, which is the {@code <category>.<namespace>.<path>}
 * shape {@link Identifier#toLanguageKey(String)} builds. A definition {@code mxt:fire} in {@code mxt:aura} is
 * therefore looked up as {@code aura.mxt.fire}, and a data pack names it by writing that key.</p>
 *
 * <p>Two ways to ask for it, depending on what the caller has:</p>
 * <ul>
 *   <li>a {@link ResourceKey} or a {@link Holder} already says which registry it lives in, so the category is
 *       read off it - {@link #name(Holder)} and {@link #name(ResourceKey)} need nothing else; and</li>
 *   <li>a bare {@link Identifier} needs the category naming explicitly, which is what
 *       {@link #name(Identifier, String)} is for.</li>
 * </ul>
 *
 * <p>A key that nothing translates renders as the key itself, which is still the definition's id in readable
 * form rather than a blank, so a data pack with no lang file yet stays usable and searchable.</p>
 *
 * <p>Most registries are translated under their own path, which is why the category can be derived at all. The
 * few that are not are declared once in {@link #CATEGORIES} rather than repeated at every call site, so the
 * same definition can never be called one thing by a tooltip and another by the item picker.</p>
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
}
