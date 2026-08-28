package com.iafenvoy.mxt.data.action.builtin.block;

import com.iafenvoy.mxt.data.context.action.BlockActionContext;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
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
                            boolean createFire) implements BlockAction {
    public static final MapCodec<ExplodeAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.fieldOf("power").forGetter(ExplodeAction::power),
            ExplosionInteraction.CODEC.optionalFieldOf("interaction", ExplosionInteraction.MOB).forGetter(ExplodeAction::interaction),
            BlockCondition.CODEC.optionalFieldOf("indestructible").forGetter(ExplodeAction::indestructible),
            Codec.BOOL.optionalFieldOf("create_fire", false).forGetter(ExplodeAction::createFire)
    ).apply(i, ExplodeAction::new));

    @Override
    public void execute(@NonNull BlockActionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        if (level.isClientSide() || !Float.isFinite(this.power) || this.power < 0.0F) return;
        ExplosionDamageCalculator calculator = this.indestructible.<ExplosionDamageCalculator>map(condition -> new ExplosionDamageCalculator() {
            @Override
            public @NonNull Optional<Float> getBlockExplosionResistance(@NonNull Explosion explosion, @NonNull BlockGetter level, @NonNull BlockPos target, @NonNull BlockState state, @NonNull FluidState fluid) {
                Optional<Float> original = super.getBlockExplosionResistance(explosion, level, target, state, fluid);
                return condition.test(this.thisLevel(), target, ctx) ? Optional.of(Math.max(original.orElse(0.0F), 3_600_000.0F)) : original;
            }

            private Level thisLevel() {
                return level;
            }
        }).orElseGet(ExplosionDamageCalculator::new);
        level.explode(null, level.damageSources().explosion(null, null), calculator, pos.getCenter(), this.power, this.createFire, this.interaction);
    }

    @Override
    public @NonNull MapCodec<ExplodeAction> codec() {
        return CODEC;
    }
}
