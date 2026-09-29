package com.iafenvoy.mxt.runtime.progression;

import com.iafenvoy.mxt.attachment.ProgressionAttachment;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

/**
 * The multiplier a level gives to the abilities the owner of its chain grants. Asked per ability rather than per
 * holder, because a chain speaks for the abilities it unlocks: the multiplier of a body-refining manual belongs
 * to what that manual grants, not to every hit the holder lands. Several owners can grant the same ability and
 * the best is taken - the hit is one hit.
 */
public final class ProgressionDamageMultiplier {
    private ProgressionDamageMultiplier() {
    }

    // A body with no record still stands on its entry level, so the attachment may be absent here.
    public static double of(LivingEntity holder, Identifier ability) {
        ProgressionAttachment progress = holder.getExistingData(MxtAttachments.PROGRESSION).orElse(null);
        double best = 1.0D;
        for (ProgressionSources.Owner owner : ProgressionSources.heldBy(holder)) {
            Holder<Progression> current = ProgressionService.currentLevel(progress, owner.id(), owner.definition()).orElse(null);
            if (current == null) continue;
            boolean grants = ProgressionService.grantedAbilities(owner.definition(), current).stream()
                    .anyMatch(unlocked -> HolderHelper.id(unlocked).equals(ability));
            if (!grants) continue;
            double multiplier = current.value().damageMultiplier();
            if (Double.isFinite(multiplier) && multiplier > best) best = multiplier;
        }
        return best;
    }
}
