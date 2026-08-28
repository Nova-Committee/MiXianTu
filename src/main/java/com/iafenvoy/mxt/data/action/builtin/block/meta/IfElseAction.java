package com.iafenvoy.mxt.data.action.builtin.block.meta;

import com.iafenvoy.mxt.data.context.action.BlockActionContext;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

public record IfElseAction(BlockCondition condition, BlockAction ifAction,
                           BlockAction elseAction) implements BlockAction {
    public static final MapCodec<IfElseAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockCondition.CODEC.fieldOf("condition").forGetter(IfElseAction::condition),
            BlockAction.CODEC.fieldOf("if_action").forGetter(IfElseAction::ifAction),
            BlockAction.optionalCodec("else_action").forGetter(IfElseAction::elseAction)
    ).apply(i, IfElseAction::new));

    @Override
    public void execute(@NonNull BlockActionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        FormulaContext context = ctx.formula();
        if (this.condition.test(level, pos, ctx)) this.ifAction.execute(level, pos, ctx);
        else this.elseAction.execute(level, pos, ctx);
    }

    @Override
    public @NonNull MapCodec<IfElseAction> codec() {
        return CODEC;
    }
}
