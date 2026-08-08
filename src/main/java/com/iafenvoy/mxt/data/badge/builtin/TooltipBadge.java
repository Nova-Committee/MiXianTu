package com.iafenvoy.mxt.data.badge.builtin;

import com.iafenvoy.mxt.data.badge.Badge;
import com.iafenvoy.mxt.data.badge.BadgeCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A sprite with a translatable tooltip.
 */
public record TooltipBadge(Identifier sprite, Component text) implements Badge {
    public static final MapCodec<TooltipBadge> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("sprite").forGetter(TooltipBadge::sprite),
            BadgeCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("text", Component.empty()).forGetter(TooltipBadge::text)
    ).apply(i, TooltipBadge::new));

    @Override
    public MapCodec<TooltipBadge> codec() {
        return CODEC;
    }
}
