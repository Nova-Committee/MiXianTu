package com.iafenvoy.mxt.runtime.progression;

import com.iafenvoy.mxt.attachment.ProgressionAttachment;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.progression.ProgressionOwner;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.EntitySources;
import com.iafenvoy.mxt.runtime.ability.AbilityGrantService;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * The administrative write to a progression. It stores a level the way a promotion does, so what a level grants
 * and what a data pack hears about it are the same whether the body climbed there or an operator put it there.
 */
public final class ProgressionAdminService {
    private ProgressionAdminService() {
    }

    public static Result setLevel(LivingEntity entity, Identifier owner, Holder<Progression> level, boolean force) {
        ProgressionOwner definition = EntitySources.heldBy(entity).stream()
                .filter(held -> held.id().equals(owner))
                .map(EntitySources.Owner::definition)
                .findFirst().orElse(null);
        if (definition == null || definition.entryLevel().isEmpty()) return Result.rejected(Failure.UNKNOWN_OWNER);
        // A level from another chain compares against nothing, so the stored order would refuse both bounds.
        if (!force && !ProgressionService.follows(definition, level)) return Result.rejected(Failure.FOREIGN_LEVEL);
        ProgressionAttachment progress = entity.getData(MxtAttachments.PROGRESSION);
        Holder<Progression> current = progress.level(owner);
        if (current != null && HolderHelper.id(current).equals(HolderHelper.id(level)))
            return Result.rejected(Failure.SAME_LEVEL);
        progress.setLevel(owner, level);
        // The same order a promotion takes: record, then what entering the level means, then the grants it owes.
        ProgressionService.enterLevel(entity, owner, definition, level);
        AbilityGrantService.recalculate(entity);
        return Result.changedResult();
    }

    // SERVER_ONLY and UNKNOWN_LEVEL belong to the script entry, which takes ids rather than parsed arguments.
    public enum Failure {UNKNOWN_OWNER, FOREIGN_LEVEL, SAME_LEVEL, UNKNOWN_LEVEL, SERVER_ONLY}

    public record Result(boolean changed, Failure failure) {
        public static Result changedResult() {
            return new Result(true, null);
        }

        public static Result rejected(Failure failure) {
            return new Result(false, failure);
        }
    }
}
