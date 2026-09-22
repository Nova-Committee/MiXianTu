package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.runtime.damage.DamageCalculationService;
import com.iafenvoy.mxt.runtime.damage.DamageElements;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
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
import java.util.Set;

/**
 * Blows a hole where the action happens. The blast is credited to the caster, unlike {@code mxt:damage}:
 * something caused this explosion, and the entity that ran the action is that something, so a kill from it
 * counts as theirs and the element edges of their roots are read. The damage each entity takes is then shaped
 * by the same first layer every other strike goes through, which is why the calculator is wrapped rather than
 * left to vanilla.
 */
public record ExplodeAction(float power, ExplosionInteraction interaction, Optional<BlockCondition> indestructible,
                            boolean createFire) implements EntityAction {
    public static final MapCodec<ExplodeAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.fieldOf("power").forGetter(ExplodeAction::power),
            ExplosionInteraction.CODEC.optionalFieldOf("interaction", ExplosionInteraction.MOB).forGetter(ExplodeAction::interaction),
            BlockCondition.CODEC.optionalFieldOf("indestructible").forGetter(ExplodeAction::indestructible),
            Codec.BOOL.optionalFieldOf("create_fire", false).forGetter(ExplodeAction::createFire)
    ).apply(i, ExplodeAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        Level level = entity.level();
        if (level.isClientSide() || !Float.isFinite(this.power) || this.power < 0.0F) return;
        ExplosionDamageCalculator calculator = this.indestructible.<ExplosionDamageCalculator>map(condition -> new ExplosionDamageCalculator() {
            @Override
            public @NonNull Optional<Float> getBlockExplosionResistance(@NonNull Explosion explosion, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull BlockState state, @NonNull FluidState fluid) {
                Optional<Float> original = super.getBlockExplosionResistance(explosion, level, pos, state, fluid);
                return condition.test(entity.level(), pos, ctx) ? Optional.of(Math.max(original.orElse(0.0F), 3_600_000.0F)) : original;
            }
        }).orElseGet(ExplosionDamageCalculator::new);
        Entity caster = ctx.formula().caster();
        DamageSource source = level.damageSources().explosion(entity, caster);
        // The blast's own damage type is what its element is read from, exactly like every other strike: a pack
        // that claims minecraft:explosion for an element gives every explosion of this kind that meaning.
        Set<Holder<Element>> elements = DamageElements.strike(level, Optional.of(source.typeHolder()), caster);
        level.explode(entity, source, new ShapedCalculator(calculator, caster, ctx.formula(), elements,
                DamageCalculationService.bypasses(source)), ctx.position(), this.power, this.createFire, this.interaction);
    }

    @Override
    public @NonNull MapCodec<ExplodeAction> codec() {
        return CODEC;
    }

    /**
     * The blast's own rules with the damage pipeline layered on top: every other answer is the wrapped
     * calculator's, and only what one entity loses passes through layer one. Without this, a caster's
     * explosion would be the one hit in the mod that ignores their mastery and their roots.
     *
     * <p>A damage type the pack exempted ({@code mxt:no_bonus}) is the exception, and it is decided once for
     * the whole blast: the base amount is handed on untouched, exactly as {@code deal} does for a strike, and
     * the reduction layer reaches the same verdict from the same source.</p>
     */
    private static final class ShapedCalculator extends ExplosionDamageCalculator {
        private final ExplosionDamageCalculator base;
        private final Entity attacker;
        private final FormulaContext context;
        private final Set<Holder<Element>> elements;
        private final boolean bypassed;

        private ShapedCalculator(ExplosionDamageCalculator base, Entity attacker, FormulaContext context,
                                 Set<Holder<Element>> elements, boolean bypassed) {
            this.base = base;
            this.attacker = attacker;
            this.context = context;
            this.elements = elements;
            this.bypassed = bypassed;
        }

        @Override
        public @NonNull Optional<Float> getBlockExplosionResistance(@NonNull Explosion explosion, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull BlockState state, @NonNull FluidState fluid) {
            return this.base.getBlockExplosionResistance(explosion, level, pos, state, fluid);
        }

        @Override
        public boolean shouldBlockExplode(@NonNull Explosion explosion, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull BlockState state, float power) {
            return this.base.shouldBlockExplode(explosion, level, pos, state, power);
        }

        @Override
        public boolean shouldDamageEntity(@NonNull Explosion explosion, @NonNull Entity entity) {
            return this.base.shouldDamageEntity(explosion, entity);
        }

        @Override
        public float getKnockbackMultiplier(@NonNull Entity entity) {
            return this.base.getKnockbackMultiplier(entity);
        }

        @Override
        public float getEntityDamageAmount(@NonNull Explosion explosion, @NonNull Entity entity, float exposure) {
            double amount = this.base.getEntityDamageAmount(explosion, entity, exposure);
            if (this.bypassed) return (float) amount;
            return (float) DamageCalculationService.outgoing(this.attacker, entity, amount, this.context, this.elements);
        }
    }
}
