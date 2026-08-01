package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Cultivation herb growth and material classification metadata.
 */
public record SpiritHerbDefinition(Identifier materialGrade, NumberProvider age,
                                   List<Identifier> elementTags, List<Identifier> materialTags,
                                   NumberProvider growthRate,
                                   NumberProvider dropChance) {
    public static final Codec<SpiritHerbDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("material_grade").forGetter(SpiritHerbDefinition::materialGrade), NumberProvider.CODEC.optionalFieldOf("age", new Constant(0.0D)).forGetter(SpiritHerbDefinition::age),
            Identifier.CODEC.listOf().optionalFieldOf("element_tags", List.of()).forGetter(SpiritHerbDefinition::elementTags), Identifier.CODEC.listOf().optionalFieldOf("material_tags", List.of()).forGetter(SpiritHerbDefinition::materialTags),
            NumberProvider.CODEC.optionalFieldOf("growth_rate", new Constant(0.0D)).forGetter(SpiritHerbDefinition::growthRate), NumberProvider.CODEC.optionalFieldOf("drop_chance", new Constant(1.0D)).forGetter(SpiritHerbDefinition::dropChance)
    ).apply(instance, SpiritHerbDefinition::new));
}
