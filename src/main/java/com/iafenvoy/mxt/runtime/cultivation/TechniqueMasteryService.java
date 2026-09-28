package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ProgressionAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.trigger.TriggerContext;
import com.iafenvoy.mxt.data.trigger.TriggerSignals;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.progression.ProgressionService;
import com.iafenvoy.mxt.runtime.trigger.TriggerDispatcher;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Advances a learned technique when its mastery reaches the next level. Only data decides: the level's
 * {@code mastery} says how much is needed, its {@code condition} says what else it takes, and
 * {@code mastery_resource} names the value that measures mastery. A promotion is committed before the
 * {@code mxt:progression_level} signal is published.
 */
public final class TechniqueMasteryService {
    // Bound on how many levels one pass may climb, so a misconfigured chain cannot stall a tick.
    private static final int MAX_PROMOTIONS_PER_PASS = 64;

    private TechniqueMasteryService() {
    }

    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        if (spirit.learnedTechniques().isEmpty()) return;
        ProgressionAttachment progress = entity.getData(MxtAttachments.PROGRESSION);
        AbilityAttachment abilities = entity.getData(MxtAttachments.ABILITY_HOLDER);
        ResourceHolderAttachment resources = entity.getData(MxtAttachments.RESOURCE_HOLDER);
        FormulaContext context = FormulaContext.of(entity);
        boolean advanced = false;
        for (Holder<Technique> technique : List.copyOf(spirit.learnedTechniques()))
            for (int step = 0; step < MAX_PROMOTIONS_PER_PASS; step++) {
                if (!promote(entity, progress, resources, technique, context)) break;
                advanced = true;
            }
        if (advanced) CultivationGrantService.recalculate(entity, spirit, abilities);
    }

    private static boolean promote(LivingEntity entity, ProgressionAttachment progress,
                                   ResourceHolderAttachment resources, Holder<Technique> technique,
                                   FormulaContext context) {
        Technique definition = technique.value();
        Holder<Resource> mastery = definition.masteryResource().orElse(null);
        if (mastery == null) return false;
        Identifier owner = HolderHelper.id(technique);
        Holder<Progression> target = ProgressionService.currentLevel(progress, owner, definition)
                .flatMap(ProgressionService::nextLevel).orElse(null);
        if (target == null) return false;
        double required = target.value().mastery().evaluate(context);
        if (!Double.isFinite(required) || resources.get(mastery) < required) return false;
        if (!ProgressionService.advanceCondition(definition, target).test(entity, context)) return false;
        progress.setLevel(owner, target);
        int rank = ServerCache.get().flatMap(cache -> cache.rankForLevel(HolderHelper.id(target))).orElse(0);
        TriggerContext triggerContext = new TriggerContext().actor(entity).level(entity.level())
                .formula(context.with("level", rank));
        triggerContext.set("owner", owner.toString());
        triggerContext.set("level", (double) rank);
        TriggerDispatcher.publish(TriggerSignals.PROGRESSION_LEVEL, triggerContext, entity.level().getGameTime());
        return true;
    }
}
