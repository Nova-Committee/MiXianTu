package com.iafenvoy.mxt.data.badge;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.function.Function;

/**
 * Metadata badge reserved for future selection and description screens.
 * It is intentionally data-only until a UI chooses to render it.
 */
public interface Badge {
    Codec<Holder<Badge>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.BADGE);
    Codec<Badge> DIRECT_CODEC = MxtRegistries.BADGE_TYPE.byNameCodec().dispatch("type", Badge::codec, Function.identity());

    Identifier sprite();

    MapCodec<? extends Badge> codec();
}
