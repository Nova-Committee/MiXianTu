package com.iafenvoy.mxt.data.cultivation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * Element relations are data driven; tags classify elements but do not encode precedence.
 */
public record ElementDefinition(String translationKey,
                                List<Either<Holder<ElementDefinition>, TagKey<ElementDefinition>>> overcomes,
                                List<Either<Holder<ElementDefinition>, TagKey<ElementDefinition>>> adaptedTo,
                                List<Identifier> auraTags) {
    public static final Codec<Holder<ElementDefinition>> HOLDER_CODEC = RegistryFixedCodec.create(MxtRegistryKeys.ELEMENT);
    public static final Codec<ElementDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("translation_key", "").forGetter(ElementDefinition::translationKey),
            RegistryCodecs.holderOrTagList(MxtRegistryKeys.ELEMENT).optionalFieldOf("overcomes", List.of()).forGetter(ElementDefinition::overcomes),
            RegistryCodecs.holderOrTagList(MxtRegistryKeys.ELEMENT).optionalFieldOf("adapted_to", List.of()).forGetter(ElementDefinition::adaptedTo),
            Identifier.CODEC.listOf().optionalFieldOf("aura_tags", List.of()).forGetter(ElementDefinition::auraTags)
    ).apply(instance, ElementDefinition::new));
}
