package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.List;

public record CompositeAbilityType(List<Holder<Ability>> abilities, boolean allRequired) implements AbilityType {
    public static final MapCodec<CompositeAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            AutoIgnoreListCodec.create(Ability.CODEC).fieldOf("abilities").forGetter(CompositeAbilityType::abilities),
            Codec.BOOL.optionalFieldOf("all_required", true).forGetter(CompositeAbilityType::allRequired)
    ).apply(i, CompositeAbilityType::new));

    @Override
    public MapCodec<CompositeAbilityType> codec() {
        return CODEC;
    }
}
