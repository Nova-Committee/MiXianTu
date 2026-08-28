package com.iafenvoy.mxt.data.action;

import com.iafenvoy.mxt.data.action.builtin.block.meta.SequenceBlockAction;
import com.iafenvoy.mxt.data.context.action.BlockActionContext;
import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import com.iafenvoy.mxt.util.formula.FormulaContext;

import java.util.Optional;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;

public interface BlockAction {
    Codec<BlockAction> SINGLE_CODEC = MxtRegistries.BLOCK_ACTION_TYPE.byNameCodec().dispatch("type", BlockAction::codec, Function.identity());
    Codec<BlockAction> CODEC = Codec.either(SINGLE_CODEC.listOf(), SINGLE_CODEC).xmap(value -> value.map(SequenceBlockAction::new, Function.identity()), Either::right);

    static MapCodec<BlockAction> optionalCodec(String name) {
        return CODEC.optionalFieldOf(name, NoOpAction.INSTANCE);
    }

    @NotNull MapCodec<? extends BlockAction> codec();

    void execute(@NotNull BlockActionContext context);

    default void execute(Level level, BlockPos pos, Context parent) {
        this.execute(parent.copyTo(new BlockActionContext(level, pos, parent.formula())));
    }

    default void execute(Level level, BlockPos pos, Optional<Direction> direction, Context parent) {
        this.execute(parent.copyTo(new BlockActionContext(level, pos, direction, parent.formula())));
    }

    default void execute(Level level, BlockPos pos, FormulaContext formula) {
        this.execute(new BlockActionContext(level, pos, formula));
    }
}
