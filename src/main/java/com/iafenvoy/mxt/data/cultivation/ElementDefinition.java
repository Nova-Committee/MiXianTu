package com.iafenvoy.mxt.data.cultivation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Element relations are data driven; tags classify elements but do not encode precedence.
 */
public record ElementDefinition(String translationKey, List<Identifier> overcomes,
                                List<Identifier> adaptedTo, List<Identifier> auraTags) {
    public static final Codec<ElementDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("translation_key", "").forGetter(ElementDefinition::translationKey),
            Identifier.CODEC.listOf().optionalFieldOf("overcomes", List.of()).forGetter(ElementDefinition::overcomes),
            Identifier.CODEC.listOf().optionalFieldOf("adapted_to", List.of()).forGetter(ElementDefinition::adaptedTo),
            Identifier.CODEC.listOf().optionalFieldOf("aura_tags", List.of()).forGetter(ElementDefinition::auraTags)
    ).apply(instance, ElementDefinition::new));
}
