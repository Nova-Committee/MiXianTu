package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.Map;
import java.util.Optional;

/**
 * One thing that happens when enough of an element has built up on a body. A reaction is a demand, not an amount:
 * every listed element has to reach its own number. Leaving {@code consume} out takes the demand itself, while an
 * empty map takes nothing at all. Reactions are tried in {@code priority} order (highest first, then registry
 * id), and the first whose demand and condition are met is the one that fires.
 */
public record ElementReaction(Map<Holder<Element>, NumberProvider> amounts,
                              Optional<Map<Holder<Element>, NumberProvider>> consume,
                              EntityCondition condition, Optional<EntityAction> action, int priority) {
    public static final Codec<Holder<ElementReaction>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ELEMENT_REACTION);
    public static final Codec<ElementReaction> DIRECT_CODEC = RecordCodecBuilder.<ElementReaction>create(i -> i.group(
            CollectionCodecs.map(Element.CODEC, NumberProvider.CODEC).fieldOf("amounts").forGetter(ElementReaction::amounts),
            CollectionCodecs.map(Element.CODEC, NumberProvider.CODEC).optionalFieldOf("consume").forGetter(ElementReaction::consume),
            EntityCondition.optionalCodec("condition").forGetter(ElementReaction::condition),
            EntityAction.CODEC.optionalFieldOf("action").forGetter(ElementReaction::action),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(ElementReaction::priority)
    ).apply(i, ElementReaction::new)).validate(ElementReaction::validate);

    public Map<Holder<Element>, NumberProvider> consumption() {
        return this.consume.orElse(this.amounts);
    }

    private static DataResult<ElementReaction> validate(ElementReaction reaction) {
        if (reaction.amounts().isEmpty())
            return DataResult.error(() -> "Element reaction needs at least one element to be about");
        for (Holder<Element> element : reaction.consume.orElse(Map.of()).keySet())
            if (!reaction.amounts().containsKey(element))
                return DataResult.error(() -> "Element reaction consumes an element it never asked for: " + element);
        return DataResult.success(reaction);
    }
}
