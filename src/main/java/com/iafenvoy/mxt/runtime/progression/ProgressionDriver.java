package com.iafenvoy.mxt.runtime.progression;

import com.iafenvoy.mxt.attachment.ProgressionAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.EntitySources;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Advances every progression a body holds: the level's {@code mastery} says how much of the owner's mastery
 * resource is needed, its {@code condition} says what else it takes, and the signal is published after the
 * record is written. Which owners a body holds comes from {@link EntitySources.Owns}, so this knows nothing
 * about techniques or creature profiles; what a level means stays in the owner's own definition.
 */
public final class ProgressionDriver {
    // Bound on how many levels one pass may climb, so a misconfigured chain cannot stall a tick.
    private static final int MAX_PROMOTIONS_PER_PASS = 64;

    private ProgressionDriver() {
    }

    // True once anything advanced, which is the caller's cue to rebuild what the levels grant.
    public static boolean tick(LivingEntity entity) {
        if (entity.level().isClientSide()) return false;
        List<EntitySources.Owner> owners = EntitySources.heldBy(entity);
        if (owners.isEmpty()) return false;
        // Read-only until something actually advances: an owner-less body never gets an attachment from here.
        ProgressionAttachment progress = entity.getData(MxtAttachments.PROGRESSION);
        ResourceHolderAttachment resources = entity.getData(MxtAttachments.RESOURCE_HOLDER);
        FormulaContext context = FormulaContext.of(entity);
        boolean advanced = false;
        for (EntitySources.Owner owner : owners)
            for (int step = 0; step < MAX_PROMOTIONS_PER_PASS; step++) {
                if (!promote(entity, progress, resources, owner, context)) break;
                advanced = true;
            }
        return advanced;
    }

    private static boolean promote(LivingEntity entity, ProgressionAttachment progress,
                                   ResourceHolderAttachment resources, EntitySources.Owner owner,
                                   FormulaContext context) {
        Holder<Resource> mastery = owner.definition().masteryResource().orElse(null);
        if (mastery == null) return false;
        Holder<Progression> target = ProgressionService.currentLevel(progress, owner.id(), owner.definition())
                .flatMap(ProgressionService::nextLevel).orElse(null);
        if (target == null) return false;
        double required = target.value().mastery().evaluate(context);
        if (!Double.isFinite(required) || resources.get(mastery) < required) return false;
        if (!ProgressionService.advanceCondition(owner.definition(), target).test(entity, context)) return false;
        progress.setLevel(owner.id(), target);
        ProgressionService.enterLevel(entity, owner.id(), owner.definition(), target);
        return true;
    }
}
