package com.iafenvoy.mxt.data.action.builtin.block.meta;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Optional;

public record ChanceAction(BlockAction action, float chance, Optional<BlockAction> failAction) implements BlockAction {
    public static final MapCodec<ChanceAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockAction.CODEC.fieldOf("action").forGetter(ChanceAction::action),
            Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(ChanceAction::chance),
            BlockAction.CODEC.optionalFieldOf("fail_action").forGetter(ChanceAction::failAction)
    ).apply(i, ChanceAction::new));

    @Override
    public void execute(Level level, BlockPos pos, FormulaContext context) {
        if (level.getRandom().nextFloat() < this.chance) this.action.execute(level, pos, context);
        else this.failAction.ifPresent(action -> action.execute(level, pos, context));
    }

    @Override
    public MapCodec<ChanceAction> codec() {
        return CODEC;
    }
}
