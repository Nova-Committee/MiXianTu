package com.iafenvoy.mxt.runtime.progression;

import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.runtime.EntitySources;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
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

    // A body with no record still stands on its entry level, so an absent record is not an absent answer.
    public static double of(LivingEntity holder, Identifier ability) {
        Provider access = holder.level().registryAccess();
        double best = 1.0D;
        for (EntitySources.Owner<?> owner : EntitySources.heldBy(holder)) {
            Holder<Progression> current = ProgressionService.current(owner).orElse(null);
            if (current == null) continue;
            boolean grants = ProgressionService.grantedAbilities(owner.definition(), current, access).stream()
                    .anyMatch(unlocked -> HolderHelper.id(unlocked).equals(ability));
            if (!grants) continue;
            double multiplier = current.value().damageMultiplier();
            if (Double.isFinite(multiplier) && multiplier > best) best = multiplier;
        }
        return best;
    }
}
