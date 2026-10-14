package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.attachment.CreatureSpiritAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ability.AbilityGrantService;
import net.minecraft.world.entity.Mob;

/**
 * The creature half of a progression record's life: the growth a bond paid for goes with the bond. The record lives
 * in the creature's own spirit attachment (a level is only ever stored on the body that climbed it), so what a
 * released contract takes away is that record, and whatever it granted has to be rebuilt away with it.
 */
public final class CreatureProgressionService {
    private CreatureProgressionService() {
    }

    // A release and a death are the same thing here. Innate abilities come from the profile's entry level, which
    // the creature keeps, so only what the recorded levels added is dropped.
    public static boolean clear(Mob creature) {
        CreatureSpiritAttachment spirit = creature.getExistingData(MxtAttachments.CREATURE_SPIRIT).orElse(null);
        if (spirit == null || spirit.profileLevels().isEmpty()) return false;
        spirit.profileLevels().clear();
        AbilityGrantService.recalculate(creature);
        return true;
    }
}
