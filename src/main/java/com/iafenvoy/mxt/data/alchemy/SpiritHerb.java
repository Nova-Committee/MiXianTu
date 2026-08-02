package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.data.MaterialGrade;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;

import java.util.List;

/**
 * Cultivation herb growth and material classification metadata.
 */
public record SpiritHerb(Holder<MaterialGrade> materialGrade, NumberProvider age, List<Identifier> elementTags,
                         List<Identifier> materialTags, NumberProvider growthRate, NumberProvider dropChance) {
    public static final Codec<SpiritHerb> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MaterialGrade.CODEC.fieldOf("material_grade").forGetter(SpiritHerb::materialGrade), NumberProvider.CODEC.optionalFieldOf("age", new Constant(0.0D)).forGetter(SpiritHerb::age),
            Identifier.CODEC.listOf().optionalFieldOf("element_tags", List.of()).forGetter(SpiritHerb::elementTags), Identifier.CODEC.listOf().optionalFieldOf("material_tags", List.of()).forGetter(SpiritHerb::materialTags),
            NumberProvider.CODEC.optionalFieldOf("growth_rate", new Constant(0.0D)).forGetter(SpiritHerb::growthRate), NumberProvider.CODEC.optionalFieldOf("drop_chance", new Constant(1.0D)).forGetter(SpiritHerb::dropChance)
    ).apply(instance, SpiritHerb::new));
}
