package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.data.MaterialGrade;
import com.iafenvoy.mxt.util.ItemMatcher;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;

import java.util.List;

/**
 * Metadata attached to existing items selected by {@code items}; this framework
 * never registers a dedicated herb Item for a datapack entry.
 */
public record SpiritHerb(List<ItemMatcher.Entry> entries, Holder<MaterialGrade> materialGrade, NumberProvider age,
                         List<Identifier> elementTags, List<Identifier> materialTags, NumberProvider growthRate,
                         NumberProvider dropChance) implements ItemMatcher {
    public static final Codec<SpiritHerb> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemMatcher.ENTRIES_CODEC.fieldOf("items").forGetter(SpiritHerb::entries),
            MaterialGrade.CODEC.fieldOf("material_grade").forGetter(SpiritHerb::materialGrade), NumberProvider.CODEC.optionalFieldOf("age", new Constant(0.0D)).forGetter(SpiritHerb::age),
            Identifier.CODEC.listOf().optionalFieldOf("element_tags", List.of()).forGetter(SpiritHerb::elementTags), Identifier.CODEC.listOf().optionalFieldOf("material_tags", List.of()).forGetter(SpiritHerb::materialTags),
            NumberProvider.CODEC.optionalFieldOf("growth_rate", new Constant(0.0D)).forGetter(SpiritHerb::growthRate), NumberProvider.CODEC.optionalFieldOf("drop_chance", new Constant(1.0D)).forGetter(SpiritHerb::dropChance)
    ).apply(instance, SpiritHerb::new));

    public SpiritHerb {
        entries = List.copyOf(entries);
    }
}
