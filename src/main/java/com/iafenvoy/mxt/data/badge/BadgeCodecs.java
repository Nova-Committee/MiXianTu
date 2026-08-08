package com.iafenvoy.mxt.data.badge;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * Shared JSON codecs for data-only badge text.
 */
public final class BadgeCodecs {
    public static final Codec<Component> TRANSLATABLE_COMPONENT = Codec.either(Codec.STRING, ComponentSerialization.CODEC)
            .xmap(value -> value.map(Component::translatable, component -> component), Either::right);

    private BadgeCodecs() {
    }
}
