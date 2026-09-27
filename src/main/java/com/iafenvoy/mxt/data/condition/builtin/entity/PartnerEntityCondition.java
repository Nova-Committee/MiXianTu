package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Asks whether the entity has a partner nearby: living entities inside a radius, the asker never among them, each
 * tested by a bi-entity condition with the entity as actor. The radius is capped because the scan runs per
 * evaluation, and a condition here is evaluated both when a method is picked and on every settlement.
 */
public record PartnerEntityCondition(double range, Count count,
                                     BiEntityCondition biEntityCondition) implements EntityCondition {
    public static final MapCodec<PartnerEntityCondition> CODEC = RecordCodecBuilder.<PartnerEntityCondition>mapCodec(i -> i.group(
            Codec.doubleRange(0.5D, 32.0D).optionalFieldOf("range", 5.0D).forGetter(PartnerEntityCondition::range),
            Count.CODEC.optionalFieldOf("count", Count.ANY).forGetter(PartnerEntityCondition::count),
            BiEntityCondition.optionalCodec("bientity_condition").forGetter(PartnerEntityCondition::biEntityCondition)
    ).apply(i, PartnerEntityCondition::new)).validate(PartnerEntityCondition::validate);

    private static DataResult<PartnerEntityCondition> validate(PartnerEntityCondition condition) {
        return condition.count().valid()
                ? DataResult.success(condition)
                : DataResult.error(() -> "mxt:partner count needs 0 <= min <= max");
    }

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity self = ctx.entity();
        AABB area = self.getBoundingBox().inflate(this.range);
        double rangeSqr = this.range * this.range;
        int found = 0;
        for (Entity candidate : self.level().getEntities(self, area)) {
            // The box is square and the radius is not; measure before asking anything else.
            if (!(candidate instanceof LivingEntity living) || !living.isAlive()) continue;
            if (self.distanceToSqr(candidate) > rangeSqr) continue;
            if (!this.biEntityCondition.test(self, candidate, ctx)) continue;
            found++;
            if (this.count.exceeded(found)) return false;
        }
        return this.count.reached(found);
    }

    @Override
    public @NonNull MapCodec<PartnerEntityCondition> codec() {
        return CODEC;
    }

    // Both ends are inclusive; an absent max means "at least min, however many".
    public record Count(int min, Optional<Integer> max) {
        public static final Codec<Count> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("min", 1).forGetter(Count::min),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("max").forGetter(Count::max)
        ).apply(i, Count::new));
        public static final Count ANY = new Count(1, Optional.empty());

        private boolean valid() {
            return this.min >= 0 && this.max.map(max -> max >= this.min).orElse(true);
        }

        private boolean exceeded(int found) {
            return this.max.map(max -> found > max).orElse(false);
        }

        private boolean reached(int found) {
            return found >= this.min && this.max.map(max -> found <= max).orElse(true);
        }
    }
}
