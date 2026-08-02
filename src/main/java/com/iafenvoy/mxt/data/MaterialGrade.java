package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;

/**
 * A quality tier used by forging, artifacts, alchemy, and energy conversion.
 */
public record MaterialGrade(int tier, NumberProvider valueMultiplier, NumberProvider forgingModifier,
                            NumberProvider alchemyModifier, List<Identifier> applicableTags) {
    public static final Codec<MaterialGrade> DIRECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("tier").forGetter(MaterialGrade::tier),
            NumberProvider.CODEC.optionalFieldOf("value_multiplier", new Constant(1)).forGetter(MaterialGrade::valueMultiplier),
            NumberProvider.CODEC.optionalFieldOf("forging_modifier", new Constant(1)).forGetter(MaterialGrade::forgingModifier),
            NumberProvider.CODEC.optionalFieldOf("alchemy_modifier", new Constant(1)).forGetter(MaterialGrade::alchemyModifier),
            Identifier.CODEC.listOf().optionalFieldOf("applicable_tags", List.of()).forGetter(MaterialGrade::applicableTags)
    ).apply(instance, MaterialGrade::new));
    public static final Codec<Holder<MaterialGrade>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.MATERIAL_GRADE);
}
