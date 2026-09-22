package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.aura.Aura;
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
 * One inscribed talisman a carrier can hold: {@code abilities} is the only effect field, and {@code aura_cost}
 * is both the bill invoking it pays and what a carrier must be filled with before it fires - one aura at a time,
 * in this order, ids only (auras are pooled one value per aura, so tags make no sense here). Whether a carrier
 * fires on its own lives on the {@code mxt:talisman} stack, not here; the display name resolves from the entry id,
 * like every other definition (field semantics: the datapack format reference under {@code docs/}).
 */
public record Talisman(List<Either<Holder<Ability>, TagKey<Ability>>> abilities,
                       Map<Holder<Aura>, NumberProvider> auraCost) {
    public static final Codec<Holder<Talisman>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.TALISMAN);
    public static final Codec<Talisman> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("abilities", List.of()).forGetter(Talisman::abilities),
            CollectionCodecs.map(Aura.CODEC, NumberProvider.CODEC).optionalFieldOf("aura_cost", Map.of()).forGetter(Talisman::auraCost)
    ).apply(i, Talisman::new));
}
