package com.iafenvoy.mxt.api;

import net.minecraft.network.chat.Component;

/**
 * A datapack definition that carries its own display texts. Both fields are optional in JSON: an omitted one is
 * filled in by {@code ContextNameCodec} with the entry's own translation key, so a pack can either translate the
 * generated key or write the text out. Display code asks the definition instead of deriving the key itself, which
 * is what lets a written name win over the generated one.
 */
public interface NamedDefinition {
    /**
     * The definition's own name: written out in the pack, or the generated key when the field is absent.
     */
    Component name();

    /**
     * The definition's own description, generated as {@code <name key>.description} when the field is absent.
     */
    Component description();
}
