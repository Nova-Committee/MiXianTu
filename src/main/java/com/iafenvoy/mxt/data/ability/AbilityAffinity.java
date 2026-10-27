package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.saturn.data.ability.Ability;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * The elements an ability prefers, as its own datapack entry: Saturn keeps affinity out of the ability definition, so a
 * pack that wants one answers it here instead. One entry can name a single ability or a tag, and the first entry that
 * matches an ability is the one the runtime reads.
 */
public record AbilityAffinity(List<Either<Holder<Ability>, TagKey<Ability>>> abilities,
                              List<Either<Holder<Element>, TagKey<Element>>> elements,
                              AffinityMode mode) {
    public static final Codec<AbilityAffinity> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.holderOrTagList(SaturnResourceKeys.ABILITY).fieldOf("abilities").forGetter(AbilityAffinity::abilities),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).fieldOf("elements").forGetter(AbilityAffinity::elements),
            AffinityMode.CODEC.optionalFieldOf("mode", AffinityMode.AVERAGE).forGetter(AbilityAffinity::mode)
    ).apply(i, AbilityAffinity::new));

    public boolean matches(Holder<Ability> ability) {
        return RegistryCodecs.matches(this.abilities, ability);
    }

    /**
     * The same question asked with the definition itself, which is all a hook that was handed the value has.
     */
    public boolean matches(Ability ability, Registry<Ability> registry) {
        return RegistryCodecs.matches(this.abilities, registry, ability);
    }

    /**
     * No element named is the same answer as no entry at all: the ability is not element-bound.
     */
    public boolean isEmpty() {
        return this.elements.isEmpty();
    }
}
