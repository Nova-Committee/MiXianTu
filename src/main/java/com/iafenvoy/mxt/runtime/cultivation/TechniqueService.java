package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.event.TechniqueLearnEvent.Post;
import com.iafenvoy.mxt.event.TechniqueLearnEvent.Pre;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Learning transaction with exclusive-tag conflict checks and a single post grant rebuild.
 */
public final class TechniqueService {
    private TechniqueService() {
    }

    public static Result learn(SpiritIdentityAttachment spirit, Holder<CultivationTechnique> technique) {
        if (MxtDatapackRegistries.isDisabled(MxtResourceKeys.CULTIVATION_TECHNIQUE, technique))
            return Result.rejected(Failure.DISABLED);
        CultivationTechnique definition = technique.value();
        if (spirit.learnedTechniques().contains(technique)) return Result.rejected(Failure.ALREADY_LEARNED);
        Set<Identifier> existing = new HashSet<>();
        for (Holder<CultivationTechnique> known : spirit.learnedTechniques())
            existing.addAll(known.value().exclusiveTags());
        if (definition.exclusiveTags().stream().anyMatch(existing::contains)) return Result.rejected(Failure.CONFLICT);
        if (NeoForge.EVENT_BUS.post(new Pre(spirit, technique)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        List<Holder<CultivationTechnique>> values = new LinkedList<>(spirit.learnedTechniques());
        values.add(technique);
        spirit.setLearnedTechniques(values);
        NeoForge.EVENT_BUS.post(new Post(spirit, technique));
        return Result.learnedResult();
    }

    /**
     * Entity-aware learning entry point that evaluates every declared fixed cultivation condition.
     */
    public static Result learn(LivingEntity entity, SpiritIdentityAttachment spirit, Holder<CultivationTechnique> technique, FormulaContext context) {
        boolean allowed = technique.value().learnCondition().test(entity, context);
        if (!allowed) return Result.rejected(Failure.CONDITIONS);
        Result result = learn(spirit, technique);
        if (result.learned()) {
            CultivationGrantService.recalculate(entity, spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
        }
        return result;
    }

    public enum Failure {DISABLED, ALREADY_LEARNED, CONFLICT, CONDITIONS, CANCELLED}

    public record Result(boolean learned, Failure failure) {
        static Result learnedResult() {
            return new Result(true, null);
        }

        static Result rejected(Failure failure) {
            return new Result(false, failure);
        }
    }
}
