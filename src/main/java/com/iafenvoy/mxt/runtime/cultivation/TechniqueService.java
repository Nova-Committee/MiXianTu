package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.event.TechniqueLearnEvent.Post;
import com.iafenvoy.mxt.event.TechniqueLearnEvent.Pre;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Learning transaction with exclusive-tag conflict checks and a single post grant rebuild.
 */
public final class TechniqueService {
    private TechniqueService() {
    }

    public static Result learn(SpiritData spirit, Identifier id, CultivationTechnique definition, Lookup lookup) {
        if (spirit.learnedTechniques().contains(id)) return Result.rejected(Failure.ALREADY_LEARNED);
        Set<Identifier> existing = new HashSet<>();
        for (Identifier known : spirit.learnedTechniques())
            lookup.get(known).ifPresent(value -> existing.addAll(value.exclusiveTags()));
        if (definition.exclusiveTags().stream().anyMatch(existing::contains)) return Result.rejected(Failure.CONFLICT);
        if (NeoForge.EVENT_BUS.post(new Pre(spirit, id, definition)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        ArrayList<Identifier> values = new ArrayList<>(spirit.learnedTechniques());
        values.add(id);
        spirit.setLearnedTechniques(values);
        NeoForge.EVENT_BUS.post(new Post(spirit, id, definition));
        return Result.learnedResult();
    }

    /**
     * Entity-aware learning entry point that evaluates every declared fixed cultivation condition.
     */
    public static Result learn(LivingEntity entity, SpiritData spirit, Identifier id, CultivationTechnique definition, Lookup lookup, FormulaContext context) {
        boolean allowed = definition.learnCondition().test(entity, context);
        return allowed ? learn(spirit, id, definition, lookup) : Result.rejected(Failure.CONDITIONS);
    }

    @FunctionalInterface
    public interface Lookup {
        Optional<CultivationTechnique> get(Identifier id);
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
