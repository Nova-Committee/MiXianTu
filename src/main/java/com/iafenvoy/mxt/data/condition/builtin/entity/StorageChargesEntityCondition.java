package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.storage.ChargesDataStorage;
import com.iafenvoy.mxt.util.formula.NumberRange;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Reads how many uses the {@code mxt:charges} of a named host have left. A host that was never spent keeps no
 * count and reads as full (the declaration's {@code maximum}); one that declares no charge pool is false.
 */
public record StorageChargesEntityCondition(Identifier family, Identifier id,
                                            Optional<NumberRange> remaining) implements EntityCondition {
    public static final MapCodec<StorageChargesEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("family").forGetter(StorageChargesEntityCondition::family),
            Identifier.CODEC.fieldOf("id").forGetter(StorageChargesEntityCondition::id),
            NumberRange.CODEC.optionalFieldOf("remaining").forGetter(StorageChargesEntityCondition::remaining)
    ).apply(i, StorageChargesEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        StorageConditionReading reading = StorageConditionReading.of(ctx.entity(), this.family, this.id).orElse(null);
        if (reading == null) return false;
        ChargesDataStorage declaration = reading.declared(ChargesDataStorage.class).orElse(null);
        if (declaration == null) return false;
        double maximum = declaration.maximum().evaluate(ctx.formula());
        double left = reading.stored(ChargesDataStorage.class).flatMap(ChargesDataStorage::remaining).orElse(maximum);
        if (!Double.isFinite(maximum) || !Double.isFinite(left)) return false;
        return this.remaining.isEmpty() || this.remaining.get().test(left, ctx.formula());
    }

    @Override
    public @NonNull MapCodec<StorageChargesEntityCondition> codec() {
        return CODEC;
    }
}
