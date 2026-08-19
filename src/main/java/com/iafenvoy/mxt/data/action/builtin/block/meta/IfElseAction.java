package com.iafenvoy.mxt.data.action.builtin.block.meta;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Optional;

public record IfElseAction(BlockCondition condition, BlockAction ifAction,
                           Optional<BlockAction> elseAction) implements BlockAction {
    public static final MapCodec<IfElseAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockCondition.CODEC.fieldOf("condition").forGetter(IfElseAction::condition),
            BlockAction.CODEC.fieldOf("if_action").forGetter(IfElseAction::ifAction),
            BlockAction.CODEC.optionalFieldOf("else_action").forGetter(IfElseAction::elseAction)
    ).apply(i, IfElseAction::new));

    @Override
    public void execute(Level level, BlockPos pos, FormulaContext context) {
        if (this.condition.test(level, pos, context)) this.ifAction.execute(level, pos, context);
        else this.elseAction.ifPresent(action -> action.execute(level, pos, context));
    }

    @Override
    public MapCodec<IfElseAction> codec() {
        return CODEC;
    }
}
