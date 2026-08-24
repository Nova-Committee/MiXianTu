package com.iafenvoy.mxt.util;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;

/** Builds localized names for datapack definitions from their registry identifiers. */
public final class DefinitionText {
    private DefinitionText() {
    }

    public static MutableComponent name(Identifier id, String category) {
        return Component.translatable(id.toLanguageKey(category));
    }

    public static <T> MutableComponent name(Holder<T> holder, String category) {
        Identifier id = HolderHelper.idOrNull(holder);
        return id == null ? Component.literal("?") : name(id, category);
    }
}
