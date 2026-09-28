package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.ProgressionAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.event.TechniqueLearnEvent.Post;
import com.iafenvoy.mxt.event.TechniqueLearnEvent.Pre;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Learning and forgetting transactions with exclusive-tag conflict checks and a single post grant rebuild.
 */
public final class TechniqueService {
    private TechniqueService() {
    }

    public static Result learn(SpiritIdentityAttachment spirit, Holder<Technique> technique) {
        Technique definition = technique.value();
        if (spirit.learnedTechniques().contains(technique)) return Result.rejected(Failure.ALREADY_LEARNED);
        Set<Identifier> existing = new HashSet<>();
        for (Holder<Technique> known : spirit.learnedTechniques())
            existing.addAll(known.value().exclusiveTags());
        if (definition.exclusiveTags().stream().anyMatch(existing::contains)) return Result.rejected(Failure.CONFLICT);
        if (NeoForge.EVENT_BUS.post(new Pre(spirit, technique)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        List<Holder<Technique>> values = new LinkedList<>(spirit.learnedTechniques());
        values.add(technique);
        spirit.setLearnedTechniques(values);
        NeoForge.EVENT_BUS.post(new Post(spirit, technique));
        return Result.changedResult();
    }

    public static Result learn(LivingEntity entity, SpiritIdentityAttachment spirit, Holder<Technique> technique, FormulaContext context) {
        boolean allowed = technique.value().learnCondition().test(entity, context);
        if (!allowed) return Result.rejected(Failure.CONDITIONS);
        Result result = learn(spirit, technique);
        if (result.changed()) {
            CultivationGrantService.recalculate(entity, spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
        }
        return result;
    }

    // Removal is asked by id, so it also reaches a reference the current pack no longer provides. The technique's
    // own progression record goes with it, because a re-learn starts at the entry level; realm progress, resources
    // and the running method live in other attachments and are none of this method's business.
    public static Result forget(LivingEntity entity, SpiritIdentityAttachment spirit, Identifier id) {
        List<Holder<Technique>> techniques = new ArrayList<>(spirit.learnedTechniques());
        ProgressionAttachment progress = entity.getData(MxtAttachments.PROGRESSION);
        boolean forgotten = techniques.removeIf(technique -> HolderHelper.id(technique).equals(id));
        boolean cleared = progress.clearLevel(id);
        if (!forgotten && !cleared) return Result.rejected(Failure.ABSENT);
        spirit.setLearnedTechniques(techniques);
        CultivationGrantService.recalculate(entity, spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
        return Result.changedResult();
    }

    // DISABLED and SERVER_ONLY belong to the script boundary, which resolves the technique by id first.
    public enum Failure {ALREADY_LEARNED, CONFLICT, CONDITIONS, CANCELLED, ABSENT, DISABLED, SERVER_ONLY}

    public record Result(boolean changed, Failure failure) {
        static Result changedResult() {
            return new Result(true, null);
        }

        static Result rejected(Failure failure) {
            return new Result(false, failure);
        }
    }
}
