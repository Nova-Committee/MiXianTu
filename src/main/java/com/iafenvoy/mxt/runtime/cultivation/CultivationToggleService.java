package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The enable/disable module for spirit roots and physiques: the server-authoritative way to switch a held root
 * or physique off without giving it up. Off is not removed - the body still holds it and removal still works -
 * but nothing it provides applies: no element, no cultivation multiplier, no granted ability, no passive
 * attribute and no rule it states about other roots, which is why every reader asks for the active
 * collections. There is deliberately no player entry point (no command, key or screen); the storage lives in
 * {@link SpiritIdentityAttachment} so that a toggle travels with the things it toggles.
 */
public final class CultivationToggleService {
    private CultivationToggleService() {
    }

    // Re-derives what the body is granted in the same step, so the abilities the root handed out are released
    // or handed back with the switch.
    public static Result setSpiritRootEnabled(LivingEntity entity, Holder<SpiritRoot> root, boolean enabled) {
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        if (!spirit.spiritRoots().contains(root)) return Result.rejected(Failure.NOT_HELD);
        if (!spirit.setSpiritRootEnabled(root, enabled)) return Result.unchangedToggle();
        CultivationGrantService.recalculate(entity, spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
        return Result.changedToggle();
    }

    public static Result setPhysiqueEnabled(LivingEntity entity, Holder<Physique> physique, boolean enabled) {
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        if (!spirit.physiques().contains(physique)) return Result.rejected(Failure.NOT_HELD);
        if (!spirit.setPhysiqueEnabled(physique, enabled)) return Result.unchangedToggle();
        CultivationGrantService.recalculate(entity, spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
        return Result.changedToggle();
    }

    public static boolean isSpiritRootEnabled(LivingEntity entity, Holder<SpiritRoot> root) {
        return entity.getData(MxtAttachments.SPIRIT_IDENTITY).isSpiritRootEnabled(root);
    }

    public static boolean isPhysiqueEnabled(LivingEntity entity, Holder<Physique> physique) {
        return entity.getData(MxtAttachments.SPIRIT_IDENTITY).isPhysiqueEnabled(physique);
    }

    // The shape a "seal your cultivation" effect wants; answers how many toggles that changed.
    public static Result setAllEnabled(LivingEntity entity, boolean enabled) {
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        int changed = 0;
        for (Holder<SpiritRoot> root : List.copyOf(spirit.spiritRoots()))
            if (spirit.setSpiritRootEnabled(root, enabled)) changed++;
        for (Holder<Physique> physique : List.copyOf(spirit.physiques()))
            if (spirit.setPhysiqueEnabled(physique, enabled)) changed++;
        if (changed == 0) return Result.unchangedToggle();
        CultivationGrantService.recalculate(entity, spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
        return Result.changedToggle();
    }

    public enum Failure {NOT_HELD, SERVER_ONLY}

    public record Result(boolean changed, @Nullable Failure failure) {
        private static Result changedToggle() {
            return new Result(true, null);
        }

        private static Result unchangedToggle() {
            return new Result(false, null);
        }

        private static Result rejected(Failure failure) {
            return new Result(false, failure);
        }
    }
}
