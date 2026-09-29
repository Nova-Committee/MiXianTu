package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.attachment.CreatureSpiritAttachment;
import com.iafenvoy.mxt.attachment.ProgressionAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ability.AbilityGrantService;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/**
 * The creature half of a progression record's life: the growth a bond paid for goes with the bond. The record
 * lives on the creature itself (a level is only ever stored on the body that climbed it), so what a released
 * contract takes away is that record, and whatever it granted has to be rebuilt away with it.
 */
public final class CreatureProgressionService {
    private CreatureProgressionService() {
    }

    // A release and a death are the same thing here. Innate abilities come from the profile's entry level, which
    // the creature keeps, so only what the recorded levels added is dropped.
    public static boolean clear(Mob creature) {
        ProgressionAttachment progress = creature.getExistingData(MxtAttachments.PROGRESSION).orElse(null);
        if (progress == null) return false;
        Identifier owner = creature.getExistingData(MxtAttachments.CREATURE_SPIRIT)
                .flatMap(CreatureSpiritAttachment::profile)
                .map(HolderHelper::id)
                .orElse(null);
        if (owner == null || !progress.clearLevel(owner)) return false;
        AbilityGrantService.recalculate(creature);
        return true;
    }
}
