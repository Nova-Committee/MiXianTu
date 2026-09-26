package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One inscribed talisman a carrier can hold: {@code abilities} is the only effect field, and {@code aura_cost}
 * is both the bill invoking it pays and what a carrier must be filled with before it fires - one aura at a time,
 * in this order, ids only (auras are pooled one value per aura, so tags make no sense here). {@code durability}
 * is how much wear the carrier made of it has and {@code consume} how much one invocation takes off, both
 * summed over everything written on one carrier; zero durability means the carrier never wears out but is spent
 * whole, which is what a talisman was before either field existed. {@code costs} is what the holder pays on top
 * of the pour for one invocation - the talisman's own price, and with it its threshold: a price that cannot be
 * paid refuses the invocation, which is what a "needs enough spirit power" condition would have said.
 * {@code quality} is the tier a carrier made from this starts on. Whether a carrier fires on its own lives on the
 * {@code mxt:talisman} stack, not here; the display text comes from the entry's own {@code name} /
 * {@code description} fields, generated from the entry id when the pack omits them.
 */
public record Talisman(Component name, Component description,
                       List<Either<Holder<Ability>, TagKey<Ability>>> abilities,
                       Map<Holder<Aura>, NumberProvider> auraCost,
                       int durability, int consume,
                       Optional<Holder<ItemQuality>> quality, List<Cost> costs) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.TALISMAN.identifier());
    public static final Codec<Holder<Talisman>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.TALISMAN);
    public static final Codec<Talisman> DIRECT_CODEC = RecordCodecBuilder.<Talisman>create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(Talisman::name),
            ContextNameCodec.description(CATEGORY).forGetter(Talisman::description),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("abilities", List.of()).forGetter(Talisman::abilities),
            CollectionCodecs.map(Aura.CODEC, NumberProvider.CODEC).optionalFieldOf("aura_cost", Map.of()).forGetter(Talisman::auraCost),
            Codec.INT.optionalFieldOf("durability", 0).forGetter(Talisman::durability),
            Codec.INT.optionalFieldOf("consume", 1).forGetter(Talisman::consume),
            ItemQuality.CODEC.optionalFieldOf("quality").forGetter(Talisman::quality),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(Talisman::costs)
    ).apply(i, Talisman::new)).flatXmap(Talisman::validate, Talisman::validate);

    private static DataResult<Talisman> validate(Talisman talisman) {
        if (talisman.durability < 0)
            return DataResult.error(() -> "A talisman's durability cannot be negative");
        return DataResult.success(talisman);
    }
}
