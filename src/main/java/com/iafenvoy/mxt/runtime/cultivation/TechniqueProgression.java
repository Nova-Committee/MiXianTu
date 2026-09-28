package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.ProgressionAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.progression.ProgressionService;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * The technique side of the shared progression: which level a learned technique stands on and the multiplier that
 * level gives to the abilities it grants. Everything else about a progression is owner-agnostic.
 */
public final class TechniqueProgression {
    private TechniqueProgression() {
    }

    // Asked per ability rather than per holder, because a chain speaks for the abilities it unlocks: the
    // multiplier of a body-refining manual belongs to what that manual grants, not to every hit the holder
    // lands. Several techniques can grant the same ability and the best is taken - the hit is one hit.
    public static double damageMultiplier(LivingEntity holder, Identifier ability) {
        SpiritIdentityAttachment spirit = holder.getExistingData(MxtAttachments.SPIRIT_IDENTITY).orElse(null);
        if (spirit == null) return 1.0D;
        ProgressionAttachment progress = holder.getExistingData(MxtAttachments.PROGRESSION).orElse(null);
        double best = 1.0D;
        for (Holder<Technique> technique : spirit.learnedTechniques()) {
            Holder<Progression> current = ProgressionService.currentLevel(progress, HolderHelper.id(technique), technique.value()).orElse(null);
            if (current == null) continue;
            boolean grants = ProgressionService.grantedAbilities(technique.value(), current).stream()
                    .anyMatch(unlocked -> HolderHelper.id(unlocked).equals(ability));
            if (!grants) continue;
            double multiplier = current.value().damageMultiplier();
            if (Double.isFinite(multiplier) && multiplier > best) best = multiplier;
        }
        return best;
    }
}
