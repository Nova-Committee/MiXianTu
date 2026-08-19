package com.iafenvoy.mxt.data.cultivation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Element relations are data driven; tags classify elements but do not encode precedence.
 */
public record Element(String translationKey, List<Either<Holder<Element>, TagKey<Element>>> overcomes,
                      List<Either<Holder<Element>, TagKey<Element>>> adaptedTo, List<Identifier> auraKinds) {
    public static final Codec<Holder<Element>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ELEMENT);
    public static final Codec<Element> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("translation_key", "").forGetter(Element::translationKey),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("overcomes", List.of()).forGetter(Element::overcomes),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("adapted_to", List.of()).forGetter(Element::adaptedTo),
            Identifier.CODEC.listOf().optionalFieldOf("aura_kinds", List.of()).forGetter(Element::auraKinds)
    ).apply(i, Element::new));

    /**
     * Do not expand element holders here. Element relations can form cycles, and a holder's
     * diagnostic string delegates back to its value's {@code toString()}.
     */
    @Override
    public @NonNull String toString() {
        return "Element[translationKey=" + this.translationKey + ", overcomes=" + this.overcomes.size()
                + ", adaptedTo=" + this.adaptedTo.size() + ", auraKinds=" + this.auraKinds + "]";
    }
}
