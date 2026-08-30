package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * A binding condition with an optional translation key shown in the item tooltip.
 *
 * <p>Entries without a description are encoded as the condition itself, while
 * described entries use an object containing {@code condition} and
 * {@code description}.</p>
 */
public record ConditionEntry(EntityCondition condition, Optional<String> description) {
    private static final Codec<ConditionEntry> DESCRIBED_CODEC = RecordCodecBuilder.create(i -> i.group(
            EntityCondition.CODEC.fieldOf("condition").forGetter(ConditionEntry::condition),
            Codec.STRING.optionalFieldOf("description").forGetter(ConditionEntry::description)
    ).apply(i, ConditionEntry::new));

    public static final Codec<ConditionEntry> CODEC = Codec.either(EntityCondition.CODEC, DESCRIBED_CODEC)
            .xmap(value -> value.map(condition -> new ConditionEntry(condition, Optional.empty()), entry -> entry),
                    entry -> entry.description().isPresent()
                            ? Either.right(entry)
                            : Either.left(entry.condition()));

    public ConditionEntry(EntityCondition condition) {
        this(condition, Optional.empty());
    }
}
