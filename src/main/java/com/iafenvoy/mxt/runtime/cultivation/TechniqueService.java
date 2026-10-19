package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.TechniqueAttachment;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.event.TechniqueLearnEvent.Post;
import com.iafenvoy.mxt.event.TechniqueLearnEvent.Pre;
import com.iafenvoy.mxt.runtime.ability.AbilityGrantService;
import com.iafenvoy.mxt.runtime.failure.TechniqueFailure;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import com.iafenvoy.mxt.runtime.failure.Failure;
import com.iafenvoy.mxt.runtime.failure.Failures;

/**
 * Learning and forgetting transactions with exclusive-tag conflict checks and a single post grant rebuild.
 */
public final class TechniqueService {
    private TechniqueService() {
    }

    public static Result learn(TechniqueAttachment spirit, Holder<Technique> technique) {
        Technique definition = technique.value();
        if (spirit.learnedTechniques().contains(technique)) return Result.rejected(TechniqueFailure.ALREADY_LEARNED);
        Set<Identifier> existing = new HashSet<>();
        for (Holder<Technique> known : spirit.learnedTechniques())
            existing.addAll(known.value().exclusiveTags());
        if (definition.exclusiveTags().stream().anyMatch(existing::contains)) return Result.rejected(TechniqueFailure.CONFLICT);
        if (NeoForge.EVENT_BUS.post(new Pre(spirit, technique)).isCanceled())
            return Result.rejected(Failures.CANCELLED);
        List<Holder<Technique>> values = new LinkedList<>(spirit.learnedTechniques());
        values.add(technique);
        spirit.setLearnedTechniques(values);
        NeoForge.EVENT_BUS.post(new Post(spirit, technique));
        return Result.changedResult();
    }

    public static Result learn(LivingEntity entity, TechniqueAttachment spirit, Holder<Technique> technique, FormulaContext context) {
        boolean allowed = technique.value().learnCondition().test(entity, context);
        if (!allowed) return Result.rejected(Failures.CONDITIONS);
        Result result = learn(spirit, technique);
        if (result.changed()) {
            AbilityGrantService.recalculate(entity);
        }
        return result;
    }

    // Removal is asked by id, so it also reaches a reference the current pack no longer provides. The technique's
    // own progression record goes with it, because a re-learn starts at the entry level; realm progress, resources
    // and the running method live in other attachments and are none of this method's business. A reference the pack
    // no longer provides has no holder here, so the record is dropped through the identity half instead.
    public static Result forget(LivingEntity entity, TechniqueAttachment spirit, @Nullable Holder<Technique> technique) {
        if (technique == null) return Result.rejected(TechniqueFailure.ABSENT);
        List<Holder<Technique>> techniques = new ArrayList<>(spirit.learnedTechniques());
        boolean forgotten = techniques.removeIf(technique::equals);
        boolean cleared = spirit.techniqueLevels().clear(technique);
        if (!forgotten && !cleared) return Result.rejected(TechniqueFailure.ABSENT);
        spirit.setLearnedTechniques(techniques);
        AbilityGrantService.recalculate(entity);
        return Result.changedResult();
    }

    public record Result(boolean changed, Failure failure) {
        static Result changedResult() {
            return new Result(true, null);
        }

        static Result rejected(Failure failure) {
            return new Result(false, failure);
        }
    }
}
