package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ability.AbilityGrantService;
import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.Result;
import com.iafenvoy.mxt.runtime.resulting.failures.CultivationFailure;
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
    public static ToggleResult setSpiritRootEnabled(LivingEntity entity, Holder<SpiritRoot> root, boolean enabled) {
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        if (!spirit.spiritRoots().contains(root)) return ToggleResult.rejected(CultivationFailure.NOT_HELD);
        if (!spirit.setSpiritRootEnabled(root, enabled)) return ToggleResult.unchangedToggle();
        AbilityGrantService.recalculate(entity);
        return ToggleResult.changedToggle();
    }

    public static ToggleResult setPhysiqueEnabled(LivingEntity entity, Holder<Physique> physique, boolean enabled) {
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        if (!spirit.physiques().contains(physique)) return ToggleResult.rejected(CultivationFailure.NOT_HELD);
        if (!spirit.setPhysiqueEnabled(physique, enabled)) return ToggleResult.unchangedToggle();
        AbilityGrantService.recalculate(entity);
        return ToggleResult.changedToggle();
    }

    public static boolean isSpiritRootEnabled(LivingEntity entity, Holder<SpiritRoot> root) {
        return entity.getData(MxtAttachments.SPIRIT_IDENTITY).isSpiritRootEnabled(root);
    }

    public static boolean isPhysiqueEnabled(LivingEntity entity, Holder<Physique> physique) {
        return entity.getData(MxtAttachments.SPIRIT_IDENTITY).isPhysiqueEnabled(physique);
    }

    // The shape a "seal your cultivation" effect wants; answers how many toggles that changed.
    public static ToggleResult setAllEnabled(LivingEntity entity, boolean enabled) {
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        int changed = 0;
        for (Holder<SpiritRoot> root : List.copyOf(spirit.spiritRoots()))
            if (spirit.setSpiritRootEnabled(root, enabled)) changed++;
        for (Holder<Physique> physique : List.copyOf(spirit.physiques()))
            if (spirit.setPhysiqueEnabled(physique, enabled)) changed++;
        if (changed == 0) return ToggleResult.unchangedToggle();
        AbilityGrantService.recalculate(entity);
        return ToggleResult.changedToggle();
    }

    public static final class ToggleResult extends Result {
        private final boolean changed;

        private ToggleResult(boolean changed, @Nullable Failure failure) {
            super(failure, null);
            this.changed = changed;
        }

        private static ToggleResult changedToggle() {
            return new ToggleResult(true, null);
        }

        private static ToggleResult unchangedToggle() {
            return new ToggleResult(false, null);
        }

        public static ToggleResult rejected(Failure failure) {
            return new ToggleResult(false, failure);
        }

        // Not the same question as failure(): a switch that was already in the state asked for changed nothing
        // and refused nothing.
        public boolean changed() {
            return this.changed;
        }
    }
}
