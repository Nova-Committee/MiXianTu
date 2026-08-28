package com.iafenvoy.mxt.data.condition;

import com.iafenvoy.mxt.data.condition.builtin.block.meta.AndBlockCondition;
import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;
import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import com.iafenvoy.mxt.util.formula.FormulaContext;

import java.util.List;
import java.util.function.Function;

/**
 * Code-owned predicate evaluated at a world position.
 */
public interface BlockCondition {
    Codec<BlockCondition> SINGLE_CODEC = MxtRegistries.BLOCK_CONDITION_TYPE.byNameCodec().dispatch("type", BlockCondition::codec, Function.identity());
    Codec<BlockCondition> CODEC = Codec.either(SINGLE_CODEC, SINGLE_CODEC.listOf()).xmap(
            value -> value.map(condition -> condition, AndBlockCondition::new),
            condition -> condition instanceof AndBlockCondition(
                    List<BlockCondition> conditions
            ) ? Either.right(conditions) : Either.left(condition)
    );

    boolean test(BlockConditionContext context);

    default boolean test(Level level, BlockPos pos, Context parent) {
        return this.test(parent.copyTo(new BlockConditionContext(level, pos, parent.formula())));
    }

    default boolean test(Level level, BlockPos pos, FormulaContext formula) {
        return this.test(new BlockConditionContext(level, pos, formula));
    }

    MapCodec<? extends BlockCondition> codec();
}
