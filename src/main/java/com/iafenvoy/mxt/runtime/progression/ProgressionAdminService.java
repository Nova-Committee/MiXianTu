package com.iafenvoy.mxt.runtime.progression;

import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.progression.ProgressionOwner;
import com.iafenvoy.mxt.runtime.ability.AbilityGrantService;
import com.iafenvoy.mxt.runtime.failure.ProgressionFailure;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import com.iafenvoy.mxt.runtime.failure.Failure;

/**
 * The administrative write to a progression. It stores a level the way a promotion does, so what a level grants and
 * what a data pack hears about it are the same whether the body climbed there or an operator put it there. A level
 * below the current one is a demotion, which is how a pack takes growth back without redefining the chain.
 */
public final class ProgressionAdminService {
    private ProgressionAdminService() {
    }

    public static Result setLevel(LivingEntity entity, Identifier owner, Holder<Progression> level, boolean force) {
        return ProgressionOwners.held(entity, owner)
                .map(held -> setLevel(entity, held, level, force))
                .orElseGet(() -> Result.rejected(ProgressionFailure.UNKNOWN_OWNER));
    }

    public static <T extends ProgressionOwner> Result setLevel(LivingEntity entity, ProgressionOwners.Owner<T> owner,
                                                               Holder<Progression> level, boolean force) {
        if (owner.definition().entryLevel().isEmpty()) return Result.rejected(ProgressionFailure.UNKNOWN_OWNER);
        // A level from another chain compares against nothing, so the stored order would refuse both bounds.
        if (!force && !ProgressionService.follows(owner, level)) return Result.rejected(ProgressionFailure.FOREIGN_LEVEL);
        Holder<Progression> current = ProgressionService.recorded(owner).orElse(null);
        if (current != null && current.is(level))
            return Result.rejected(ProgressionFailure.SAME_LEVEL);
        ProgressionService.set(owner, level);
        // The same order a promotion takes: record, then what entering the level means, then the grants it owes.
        ProgressionService.enterLevel(entity, owner.id(), owner.definition(), level);
        AbilityGrantService.recalculate(entity);
        return Result.changedResult();
    }

    public record Result(boolean changed, Failure failure) {
        public static Result changedResult() {
            return new Result(true, null);
        }

        public static Result rejected(Failure failure) {
            return new Result(false, failure);
        }
    }
}
