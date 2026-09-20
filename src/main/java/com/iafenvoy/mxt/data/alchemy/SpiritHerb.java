package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * Metadata attached to existing items selected by {@code items}; this framework
 * never registers a dedicated herb Item for a datapack entry.
 *
 * <p>{@code element_tags} is the herb's affinity, written with the element registry: an entry names one
 * element, a {@code #} tag names every element in it, and elements a pack disabled are not part of either.
 * That is what makes "a fire-aligned herb" a single declaration here rather than a second vocabulary that has
 * to be kept in step with the element definitions by hand - see {@code mxt:herb_tag}, the matcher that reads
 * this field.</p>
 */
public record SpiritHerb(List<Entry> entries, Holder<ItemQuality> quality, NumberProvider age,
                         List<Either<Holder<Element>, TagKey<Element>>> elementTags, List<Identifier> materialTags,
                         NumberProvider growthRate, NumberProvider dropChance) implements ItemMatcher {
    public static final Codec<SpiritHerb> CODEC = RecordCodecBuilder.create(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(SpiritHerb::entries),
            ItemQuality.CODEC.fieldOf("quality").forGetter(SpiritHerb::quality), NumberProvider.CODEC.optionalFieldOf("age", new Constant(0.0D)).forGetter(SpiritHerb::age),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("element_tags", List.of()).forGetter(SpiritHerb::elementTags),
            Identifier.CODEC.listOf().optionalFieldOf("material_tags", List.of()).forGetter(SpiritHerb::materialTags),
            NumberProvider.CODEC.optionalFieldOf("growth_rate", new Constant(0.0D)).forGetter(SpiritHerb::growthRate), NumberProvider.CODEC.optionalFieldOf("drop_chance", new Constant(1.0D)).forGetter(SpiritHerb::dropChance)
    ).apply(i, SpiritHerb::new));
}
