package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Map;

/**
 * One inscribed talisman a carrier can hold. {@code abilities} is what invoking it grants, and it is the only
 * effect field: every skill-like effect in this mod already lands on an {@link Ability}, so a talisman needs no
 * effect vocabulary of its own.
 * <p>
 * {@code aura_cost} is the aura bill invoking it pays, one entry per resource with a {@link NumberProvider}
 * amount, so a cost may be a constant or a formula over the invoking context. Auras are pooled per concrete
 * resource, which is why this map takes ids only and not the tags {@code abilities} accepts. An absent or empty
 * map is a talisman that costs nothing. Nothing charges this bill yet: the service that invokes a talisman is
 * not written.
 * <p>
 * Further fields (charges, a use condition) are added as optional fields, which keeps the files written today
 * parsing. The display name is deliberately not a field: like every other definition it resolves from the entry
 * id, as {@code mxt.talisman.<path>}.
 */
public record Talisman(List<Either<Holder<Ability>, TagKey<Ability>>> abilities,
                       Map<Holder<Resource>, NumberProvider> auraCost) {
    public static final Codec<Holder<Talisman>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.TALISMAN);
    public static final Codec<Talisman> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("abilities", List.of()).forGetter(Talisman::abilities),
            CollectionCodecs.map(Resource.CODEC, NumberProvider.CODEC).optionalFieldOf("aura_cost", Map.of()).forGetter(Talisman::auraCost)
    ).apply(i, Talisman::new));
}
