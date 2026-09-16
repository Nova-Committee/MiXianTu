package com.iafenvoy.mxt.data.formation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * The display module: where the array's boundary is, drawn as particles. It never affects an entity.
 * {@code interval_periods} is counted in periods rather than ticks, because the dispatch only happens on a
 * period boundary and a tick count would silently be rounded to that grid.
 */
public record RangeDisplayFormationAction(ParticleOptions particle, int intervalPeriods, int points,
                                          Shape shape) implements FormationActionType {
    public static final MapCodec<RangeDisplayFormationAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ParticleTypes.CODEC.fieldOf("particle").forGetter(RangeDisplayFormationAction::particle),
            // The upper bound is a courtesy, not a rule: one packet per point per nearby player per
            // period is the real cost, so a definition can make itself expensive but not unreadable.
            Codec.intRange(1, 1200).optionalFieldOf("interval_periods", 1).forGetter(RangeDisplayFormationAction::intervalPeriods),
            Codec.intRange(1, 512).optionalFieldOf("points", 32).forGetter(RangeDisplayFormationAction::points),
            Shape.CODEC.optionalFieldOf("shape", Shape.RING).forGetter(RangeDisplayFormationAction::shape)
    ).apply(i, RangeDisplayFormationAction::new));

    @Override
    public MapCodec<RangeDisplayFormationAction> codec() {
        return CODEC;
    }

    /**
     * Which outline of the range to draw.
     */
    public enum Shape implements StringRepresentable {
        /// A horizontal circle at the controller's own height: the array's ground plan, and the cheapest
        /// thing to read from inside it.
        RING,
        /// Points spread over the whole sphere, so a radius that reaches underground or overhead is
        /// visible as well; they are spread over a far larger surface, so it reads sparser.
        SPHERE;

        public static final Codec<Shape> CODEC = StringRepresentable.fromEnum(Shape::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
