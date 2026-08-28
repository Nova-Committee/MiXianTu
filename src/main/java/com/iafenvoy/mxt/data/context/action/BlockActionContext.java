package com.iafenvoy.mxt.data.context.action;

import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.Optional;

public class BlockActionContext extends Context {
    private final Level level;
    private final BlockPos pos;
    private final Optional<Direction> direction;
    private final FormulaContext formula;

    public BlockActionContext(Level level, BlockPos pos, FormulaContext formula) {
        this(level, pos, Optional.empty(), formula);
    }

    public BlockActionContext(Level level, BlockPos pos, Optional<Direction> direction, FormulaContext formula) {
        this.level = level;
        this.pos = pos;
        this.direction = direction;
        this.formula = formula;
    }

    public Level level() {
        return this.level;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public Optional<Direction> direction() {
        return this.direction;
    }

    @Override
    public FormulaContext formula() {
        return this.formula;
    }
}
