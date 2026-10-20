package com.iafenvoy.mxt.runtime.progression;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.progression.ProgressionOwner;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.profiling.ProfilerScope;
import com.iafenvoy.mxt.util.profiling.ServerProfiling;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Advances every progression a body holds: the level's {@code mastery} says how much of the owner's mastery resource
 * is needed, its {@code condition} says what else it takes, and the signal is published after the record is written.
 * Which owners a body holds comes from {@link com.iafenvoy.mxt.event.dispatcher.CollectProgressionOwnersEvent}, so
 * this knows nothing about techniques or creature profiles; what a level means stays in the owner's own definition,
 * and where the record lives stays in the holder the owner came with.
 */
public final class ProgressionDriver {
    // Bound on how many levels one pass may climb, so a misconfigured chain cannot stall a tick.
    private static final int MAX_PROMOTIONS_PER_PASS = 64;

    private ProgressionDriver() {
    }

    // True once anything advanced, which is the caller's cue to rebuild what the levels grant.
    public static boolean tick(LivingEntity entity) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.PROGRESSION)) {
            if (entity.level().isClientSide()) return false;
            List<ProgressionOwners.Owner<?>> owners = ProgressionOwners.heldBy(entity);
            if (owners.isEmpty()) return false;
            ResourceHolderAttachment resources = entity.getExistingData(MxtAttachments.RESOURCE_HOLDER).orElse(null);
            if (resources == null) return false;
            FormulaContext context = FormulaContext.of(entity);
            boolean advanced = false;
            for (ProgressionOwners.Owner<?> owner : owners)
                for (int step = 0; step < MAX_PROMOTIONS_PER_PASS; step++) {
                    if (!promote(entity, resources, owner, context)) break;
                    advanced = true;
                }
            return advanced;
        }
    }

    private static <T extends ProgressionOwner> boolean promote(LivingEntity entity, ResourceHolderAttachment resources,
                                                                ProgressionOwners.Owner<T> owner, FormulaContext context) {
        try (ProfilerScope _ = ServerProfiling.scope(ServerProfiling.Source.PROGRESSION)) {
            Holder<Resource> mastery = owner.definition().masteryResource().orElse(null);
            if (mastery == null) return false;
            Holder<Progression> target = ProgressionService.next(owner).orElse(null);
            if (target == null) return false;
            double required = target.value().mastery().evaluate(context);
            if (!Double.isFinite(required) || resources.get(mastery) < required) return false;
            if (!ProgressionService.advanceCondition(owner.definition(), target).test(entity, context)) return false;
            owner.levels().set(owner.holder(), target);
            ProgressionService.enterLevel(entity, owner.id(), owner.definition(), target);
            return true;
        }
    }
}
