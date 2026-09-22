package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.storage.CooldownDataStorage;
import com.iafenvoy.mxt.util.formula.NumberRange;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Reads how much of the {@code mxt:cooldown} of a named host is left, anchored at the tick the value was written -
 * the same anchor the runtime reads. Nothing written means nothing is running: ready, with no time left.
 */
public record StorageCooldownEntityCondition(Identifier family, Identifier id, Optional<NumberRange> remaining,
                                             Optional<Boolean> ready) implements EntityCondition {
    public static final MapCodec<StorageCooldownEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("family").forGetter(StorageCooldownEntityCondition::family),
            Identifier.CODEC.fieldOf("id").forGetter(StorageCooldownEntityCondition::id),
            NumberRange.CODEC.optionalFieldOf("remaining").forGetter(StorageCooldownEntityCondition::remaining),
            Codec.BOOL.optionalFieldOf("ready").forGetter(StorageCooldownEntityCondition::ready)
    ).apply(i, StorageCooldownEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        StorageConditionReading reading = StorageConditionReading.of(ctx.entity(), this.family, this.id).orElse(null);
        if (reading == null) return false;
        CooldownDataStorage declaration = reading.declared(CooldownDataStorage.class).orElse(null);
        if (declaration == null) return false;
        Optional<CooldownDataStorage> stored = reading.stored(CooldownDataStorage.class);
        double left = 0.0D;
        if (stored.isPresent()) {
            double length = stored.get().duration().orElseGet(() -> declaration.ticks().evaluate(ctx.formula()));
            if (!Double.isFinite(length)) return false;
            long elapsed = Math.max(0L, ctx.entity().level().getGameTime() - reading.changedAt(CooldownDataStorage.class));
            left = Math.max(0.0D, length - elapsed);
        }
        if (this.remaining.isPresent() && !this.remaining.get().test(left, ctx.formula())) return false;
        return this.ready.isEmpty() || this.ready.get() == (left <= 0.0D);
    }

    @Override
    public @NonNull MapCodec<StorageCooldownEntityCondition> codec() {
        return CODEC;
    }
}
