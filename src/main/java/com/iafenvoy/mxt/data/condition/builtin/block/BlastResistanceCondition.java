package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

/**
 * Compares the block's blast resistance.
 *
 * <p>The NeoForge replacement for {@code getExplosionResistance()} takes the level, position, entity and
 * explosion because a block may vary its resistance per explosion. This condition is evaluated without
 * an explosion — it is a static property check usable from any block context — so it reads the base
 * value, which is what the deprecated method returns. The suppression is the decision, not an
 * oversight: the alternative is dropping the condition or inventing an explosion that never happened.</p>
 */
@SuppressWarnings("deprecation")
public record BlastResistanceCondition(Comparison comparison) implements BlockCondition {
    public static final MapCodec<BlastResistanceCondition> CODEC = Comparison.CODEC.xmap(BlastResistanceCondition::new, BlastResistanceCondition::comparison);

    @Override
    public boolean test(@NonNull BlockConditionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        FormulaContext context = ctx.formula();
        return this.comparison.compare(level.getBlockState(pos).getBlock().getExplosionResistance());
    }

    @Override
    public @NonNull MapCodec<BlastResistanceCondition> codec() {
        return CODEC;
    }
}
