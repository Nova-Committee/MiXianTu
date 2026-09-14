package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.cultivation.SkillStage;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.trigger.TriggerContext;
import com.iafenvoy.mxt.data.trigger.TriggerSignals;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.trigger.TriggerDispatcher;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Advances a learned technique when its mastery reaches the next level.
 *
 * <p>Only data decides what happens: the level's own {@code mastery} says how much is needed, the
 * technique's {@code configuration} says what else it takes through that level's {@code condition},
 * and the value that measures mastery is the technique's {@code mastery_resource}, which a content
 * pack grows however it likes - a trigger rule, a cultivation profile or a script. Nothing here
 * knows what mastery means.</p>
 *
 * <p>A promotion is committed before the {@code mxt:technique_stage} signal is published, so a
 * reaction already reads the new level; the granted abilities are recalculated once at the end of the
 * pass rather than once per level.</p>
 */
public final class TechniqueMasteryService {
    /**
     * Bound on how many levels one pass may climb, so a misconfigured chain cannot stall a tick.
     */
    private static final int MAX_PROMOTIONS_PER_PASS = 64;

    private TechniqueMasteryService() {
    }

    /**
     * One promotion pass over every learned technique of an entity. A technique without a mastery
     * resource, without a next level, or below the next level's requirement is one comparison away
     * from being skipped, so this is cheap enough for the periodic tick.
     */
    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        if (spirit.learnedTechniques().isEmpty()) return;
        AbilityAttachment abilities = entity.getData(MxtAttachments.ABILITY_HOLDER);
        ResourceHolderAttachment resources = entity.getData(MxtAttachments.RESOURCE_HOLDER);
        FormulaContext context = FormulaContext.of(entity);
        boolean advanced = false;
        for (Holder<CultivationTechnique> technique : List.copyOf(spirit.learnedTechniques()))
            for (int step = 0; step < MAX_PROMOTIONS_PER_PASS; step++) {
                if (!promote(entity, spirit, resources, technique, context)) break;
                advanced = true;
            }
        if (advanced) CultivationGrantService.recalculate(entity, spirit, abilities);
    }

    private static boolean promote(LivingEntity entity, SpiritIdentityAttachment spirit, ResourceHolderAttachment resources,
                                   Holder<CultivationTechnique> technique, FormulaContext context) {
        CultivationTechnique definition = technique.value();
        Holder<Resource> mastery = definition.masteryResource().orElse(null);
        if (mastery == null) return false;
        Holder<SkillStage> target = SkillStageService.currentStage(spirit, technique)
                .flatMap(current -> SkillStageService.nextStage(definition, current)).orElse(null);
        if (target == null) return false;
        double required = target.value().mastery().evaluate(context);
        if (!Double.isFinite(required) || resources.get(mastery) < required) return false;
        if (!SkillStageService.advanceCondition(definition, target).test(entity, context)) return false;
        spirit.setTechniqueStage(technique, target);
        int rank = ServerCache.get().flatMap(cache -> cache.rankForStage(HolderHelper.id(target))).orElse(0);
        TriggerContext triggerContext = new TriggerContext().actor(entity).level(entity.level())
                .formula(context.with("stage", rank));
        triggerContext.set("technique", HolderHelper.id(technique).toString());
        triggerContext.set("stage", (double) rank);
        TriggerDispatcher.publish(TriggerSignals.TECHNIQUE_STAGE, triggerContext, entity.level().getGameTime());
        return true;
    }
}
