package com.iafenvoy.mxt.runtime.behavior;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Immutable server-side context supplied when a direct domain behaviour is invoked.
 */
public record BehaviorContext(@NotNull Kind kind, @NotNull Identifier definition, @NotNull Optional<ServerLevel> level,
                              @NotNull Optional<Entity> actor, @NotNull Optional<Entity> target,
                              @NotNull Optional<BlockPos> position,
                              @Nullable FormulaContext formula, boolean success) {
    public BehaviorContext {
        formula = formula == null ? FormulaContext.EMPTY : formula;
    }

    public static BehaviorContext of(@NotNull Kind kind, @NotNull Identifier definition, @Nullable Entity actor, @Nullable FormulaContext formula, boolean success) {
        ServerLevel level = actor != null && actor.level() instanceof ServerLevel serverLevel ? serverLevel : null;
        return new BehaviorContext(kind, definition, Optional.ofNullable(level), Optional.ofNullable(actor), Optional.empty(), Optional.empty(), formula, success);
    }

    public static BehaviorContext at(@NotNull Kind kind, @NotNull Identifier definition, @Nullable ServerLevel level, @Nullable BlockPos position, @Nullable FormulaContext formula, boolean success) {
        return new BehaviorContext(kind, definition, Optional.ofNullable(level), Optional.empty(), Optional.empty(), Optional.ofNullable(position), formula, success);
    }

    public enum Kind {
        FORGING_COMPLETE, FORGING_FAIL,
        ALCHEMY_SUCCESS, ALCHEMY_FAILURE,
        FORMATION_ACTIVATE, FORMATION_MAINTAIN, FORMATION_TRIGGER,
        TRIBULATION_PHASE_START, TRIBULATION_PHASE_END, TRIBULATION_SUCCESS, TRIBULATION_FAILURE,
        CULTIVATION_GAIN, BREAKTHROUGH_SUCCESS, BREAKTHROUGH_FAILURE,
        CONTRACT_FOLLOW, CONTRACT_COMBAT, CONTRACT_BREAK, CONTRACT_PENALTY,
        ARTIFACT_REFINE,
        REALM_ENTER, REALM_EXIT
    }
}
