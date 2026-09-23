package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.storage.builtin.ResourceDataStorage;
import com.iafenvoy.mxt.util.formula.NumberRange;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Reads the {@code amount} of the {@code mxt:resource} of a named host, counting an unwritten one as 0; a host
 * that declares no such kind is simply false.
 */
public record StorageResourceEntityCondition(Identifier family, Identifier id,
                                             Optional<NumberRange> amount) implements EntityCondition {
    public static final MapCodec<StorageResourceEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("family").forGetter(StorageResourceEntityCondition::family),
            Identifier.CODEC.fieldOf("id").forGetter(StorageResourceEntityCondition::id),
            NumberRange.CODEC.optionalFieldOf("amount").forGetter(StorageResourceEntityCondition::amount)
    ).apply(i, StorageResourceEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        StorageConditionReading reading = StorageConditionReading.of(ctx.entity(), this.family, this.id).orElse(null);
        if (reading == null) return false;
        if (reading.declared(ResourceDataStorage.class).isEmpty()) return false;
        Optional<ResourceDataStorage> stored = reading.stored(ResourceDataStorage.class);
        return this.amount.map(numberRange -> numberRange.test(stored.flatMap(ResourceDataStorage::amount).orElse(0.0D), ctx.formula())).orElseGet(stored::isPresent);
    }

    @Override
    public @NonNull MapCodec<StorageResourceEntityCondition> codec() {
        return CODEC;
    }
}
