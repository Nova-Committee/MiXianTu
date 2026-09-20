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
 * One thing that happens when enough of an element has built up on a body.
 *
 * <p>A reaction is a demand, not an amount: {@code amounts} says how much of each element has to be attached
 * before it is answered, and every listed element has to reach its own number, so a two-element reaction
 * simply lists two. {@code consume} says what it takes away when it fires; leaving the field out takes the
 * demand itself, which is the reading a pack means most of the time, and writing an empty map takes nothing
 * at all (a reaction that keeps the buildup it answered).</p>
 *
 * <p>{@code condition} and {@code action} are both optional, and the condition is what makes a reaction
 * situational - a fire reaction that only happens while it is raining, a reaction whose effect depends on what
 * the body is standing in. Reactions are tried in {@code priority} order (highest first, then registry id), and
 * the first one whose demand and condition are met is the one that fires.</p>
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

    /**
     * What this reaction takes away: the demand itself unless the pack said otherwise, and nothing at all when
     * it wrote an empty map.
     */
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
