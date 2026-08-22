package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.attachment.PillToxicityComponent;
import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-authoritative pill consumption and cumulative toxicity transaction.
 */
public final class PillService {
    private PillService() {
    }

    public static Result consume(LivingEntity entity, PillBinding definition) {
        FormulaContext context = FormulaContext.of(entity);
        definition.onConsume().execute(entity, context);
        PillToxicityComponent toxicity = entity.getData(MxtAttachments.PILL_TOXICITY);
        double value = toxicity.add(definition.toxicityGain().evaluate(context));
        double threshold = definition.toxicityThreshold().evaluate(context);
        if (Double.isFinite(threshold) && value >= threshold) {
            definition.onOverdose().execute(entity, context);
            toxicity.set(definition.toxicityAfterOverdose().evaluate(context));
            return Result.overdosed(toxicity.toxicity());
        }
        return Result.consumed(value);
    }

    public record Result(boolean consumed, boolean overdosed, double toxicity) {
        static Result disabled() {
            return new Result(false, false, 0.0D);
        }

        static Result consumed(double toxicity) {
            return new Result(true, false, toxicity);
        }

        static Result overdosed(double toxicity) {
            return new Result(true, true, toxicity);
        }
    }
}
