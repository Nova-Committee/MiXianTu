package com.iafenvoy.mxt.data.material;

import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * A quality tier used by forging, artifacts, alchemy, and energy conversion.
 */
public record MaterialGradeDefinition(int tier, NumberProvider valueMultiplier,
                                      NumberProvider forgingModifier, NumberProvider alchemyModifier,
                                      List<Identifier> applicableTags) {
    public static final Codec<MaterialGradeDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("tier").forGetter(MaterialGradeDefinition::tier), NumberProvider.CODEC.optionalFieldOf("value_multiplier", new Constant(1.0D)).forGetter(MaterialGradeDefinition::valueMultiplier),
            NumberProvider.CODEC.optionalFieldOf("forging_modifier", new Constant(1.0D)).forGetter(MaterialGradeDefinition::forgingModifier), NumberProvider.CODEC.optionalFieldOf("alchemy_modifier", new Constant(1.0D)).forGetter(MaterialGradeDefinition::alchemyModifier),
            Identifier.CODEC.listOf().optionalFieldOf("applicable_tags", List.of()).forGetter(MaterialGradeDefinition::applicableTags)
    ).apply(instance, MaterialGradeDefinition::new));
}
