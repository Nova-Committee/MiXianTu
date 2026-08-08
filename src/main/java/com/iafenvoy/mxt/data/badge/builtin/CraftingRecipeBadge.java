package com.iafenvoy.mxt.data.badge.builtin;

import com.iafenvoy.mxt.data.badge.Badge;
import com.iafenvoy.mxt.data.badge.BadgeCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * A recipe reference reserved for future badge tooltips.
 */
public record CraftingRecipeBadge(Identifier sprite, Identifier recipe, boolean fromPower,
                                  Optional<Component> prefix, Optional<Component> suffix) implements Badge {
    public static final MapCodec<CraftingRecipeBadge> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("sprite").forGetter(CraftingRecipeBadge::sprite),
            Identifier.CODEC.fieldOf("recipe").forGetter(CraftingRecipeBadge::recipe),
            Codec.BOOL.optionalFieldOf("from_power", false).forGetter(CraftingRecipeBadge::fromPower),
            BadgeCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("prefix").forGetter(CraftingRecipeBadge::prefix),
            BadgeCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("suffix").forGetter(CraftingRecipeBadge::suffix)
    ).apply(i, CraftingRecipeBadge::new));

    @Override
    public MapCodec<CraftingRecipeBadge> codec() {
        return CODEC;
    }
}
