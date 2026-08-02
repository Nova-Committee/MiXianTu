package com.iafenvoy.mxt.util;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

/**
 * Type-safe codecs for references into native and datapack registries.
 */
public final class HolderHelper {
    public static final Identifier EMPTY = Identifier.fromNamespaceAndPath("", "");

    /**
     * Obtains the stable id of a registry-backed holder.
     */
    public static Identifier id(Holder<?> holder) {
        return holder.unwrapKey().map(ResourceKey::identifier).orElse(EMPTY);
    }
}
