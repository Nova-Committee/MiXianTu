package com.iafenvoy.mxt.data.condition.builtin.bientity;

import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Compares the two entities' view or body rotation vectors.
 */
public record RelativeRotationCondition(EnumSet<Axis> axis, RotationType actorRotation, RotationType targetRotation,
                                        Comparison comparison) implements BiEntityCondition {
    public static final MapCodec<RelativeRotationCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Axis.CODEC.listOf().xmap(EnumSet::copyOf, List::copyOf).optionalFieldOf("axis", EnumSet.allOf(Axis.class)).forGetter(RelativeRotationCondition::axis),
            RotationType.CODEC.optionalFieldOf("actor_rotation", RotationType.HEAD).forGetter(RelativeRotationCondition::actorRotation),
            RotationType.CODEC.optionalFieldOf("target_rotation", RotationType.BODY).forGetter(RelativeRotationCondition::targetRotation),
            Comparison.CODEC.forGetter(RelativeRotationCondition::comparison)
    ).apply(i, RelativeRotationCondition::new));

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        Vec3 actorVector = reduceAxes(this.actorRotation.getRotation(actor), this.axis);
        Vec3 targetVector = reduceAxes(this.targetRotation.getRotation(target), this.axis);
        double product = actorVector.length() * targetVector.length();
        return product > 0.0D && this.comparison.compare(actorVector.dot(targetVector) / product);
    }

    private static Vec3 reduceAxes(Vec3 vector, EnumSet<Axis> axes) {
        return new Vec3(axes.contains(Axis.X) ? vector.x : 0.0D,
                axes.contains(Axis.Y) ? vector.y : 0.0D,
                axes.contains(Axis.Z) ? vector.z : 0.0D);
    }

    @Override
    public @NonNull MapCodec<RelativeRotationCondition> codec() {
        return CODEC;
    }

    public enum RotationType implements StringRepresentable {
        HEAD(entity -> entity.getViewVector(1.0F)),
        BODY(entity -> entity instanceof LivingEntity living ? getRotationVector(living.yBodyRot) : entity.getViewVector(1.0F));

        public static final Codec<RotationType> CODEC = StringRepresentable.fromValues(RotationType::values);
        private final Function<Entity, Vec3> function;

        RotationType(Function<Entity, Vec3> function) {
            this.function = function;
        }

        private static Vec3 getRotationVector(float yaw) {
            float radians = -yaw * ((float) Math.PI / 180.0F);
            return new Vec3(Mth.sin(radians), 0.0D, Mth.cos(radians));
        }

        public Vec3 getRotation(Entity entity) {
            return this.function.apply(entity);
        }

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
