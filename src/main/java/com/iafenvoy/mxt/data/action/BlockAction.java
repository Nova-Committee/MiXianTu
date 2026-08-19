package com.iafenvoy.mxt.data.action;

import com.iafenvoy.mxt.data.action.builtin.block.meta.SequenceBlockAction;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Function;

/**
 * Code-owned action evaluated at a world position.
 */
public interface BlockAction {
    Codec<BlockAction> SINGLE_CODEC = MxtRegistries.BLOCK_ACTION_TYPE.byNameCodec().dispatch("type", BlockAction::codec, Function.identity());
    Codec<BlockAction> CODEC = Codec.either(SINGLE_CODEC, SINGLE_CODEC.listOf()).xmap(
            value -> value.map(action -> action, SequenceBlockAction::new),
            action -> action instanceof SequenceBlockAction(
                    List<BlockAction> actions
            ) ? Either.right(actions) : Either.left(action)
    );

    void execute(Level level, BlockPos pos, FormulaContext context);

    MapCodec<? extends BlockAction> codec();
}
