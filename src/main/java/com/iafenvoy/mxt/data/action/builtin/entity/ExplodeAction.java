package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

public record ExplodeAction(float power, ExplosionInteraction interaction, Optional<BlockCondition> indestructible,
                            boolean createFire) implements EntityAction {
    public static final MapCodec<ExplodeAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.fieldOf("power").forGetter(ExplodeAction::power),
            ExplosionInteraction.CODEC.optionalFieldOf("interaction", ExplosionInteraction.MOB).forGetter(ExplodeAction::interaction),
            BlockCondition.CODEC.optionalFieldOf("indestructible").forGetter(ExplodeAction::indestructible),
            Codec.BOOL.optionalFieldOf("create_fire", false).forGetter(ExplodeAction::createFire)
    ).apply(instance, ExplodeAction::new));

    @Override
    public void execute(Entity entity, FormulaContext context) {
        Level level = entity.level();
        if (level.isClientSide() || !Float.isFinite(this.power) || this.power < 0.0F) return;
        ExplosionDamageCalculator calculator = this.indestructible.<ExplosionDamageCalculator>map(condition -> new ExplosionDamageCalculator() {
            @Override
            public @NonNull Optional<Float> getBlockExplosionResistance(@NonNull Explosion explosion, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull BlockState state, @NonNull FluidState fluid) {
                Optional<Float> original = super.getBlockExplosionResistance(explosion, level, pos, state, fluid);
                return condition.test(entity.level(), pos, context) ? Optional.of(Math.max(original.orElse(0.0F), 3_600_000.0F)) : original;
            }
        }).orElseGet(ExplosionDamageCalculator::new);
        level.explode(entity, level.damageSources().explosion(entity, null), calculator, entity.position(), this.power, this.createFire, this.interaction);
    }

    @Override
    public MapCodec<ExplodeAction> codec() {
        return CODEC;
    }
}
