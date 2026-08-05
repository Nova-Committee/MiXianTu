package com.iafenvoy.mxt.data.condition;

import com.iafenvoy.mxt.data.condition.builtin.block.meta.AndBlockCondition;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Function;

/**
 * Code-owned predicate evaluated at a world position.
 */
public interface BlockCondition {
    Codec<BlockCondition> SINGLE_CODEC = MxtTypeRegistries.BLOCK_CONDITION_TYPE.byNameCodec().dispatch("type", BlockCondition::codec, Function.identity());
    Codec<BlockCondition> CODEC = Codec.either(SINGLE_CODEC, SINGLE_CODEC.listOf()).xmap(
            value -> value.map(condition -> condition, AndBlockCondition::new),
            condition -> condition instanceof AndBlockCondition(
                    List<BlockCondition> conditions
            ) ? Either.right(conditions) : Either.left(condition)
    );

    boolean test(Level level, BlockPos pos, FormulaContext context);

    MapCodec<? extends BlockCondition> codec();
}
