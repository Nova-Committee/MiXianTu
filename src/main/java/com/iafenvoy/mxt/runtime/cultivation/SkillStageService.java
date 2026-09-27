package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.AlwaysCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.SkillStage;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.cultivation.Technique.StageConfiguration;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;

/**
 * Reads a technique's mastery: the abilities each configured level grants and the conditions to climb. An
 * entry's {@code ability} is a minimum requirement - its abilities are active on that level or any later one -
 * while its {@code condition} is asked for the level a holder advances to. Ordering comes from
 * {@link ServerCache}.
 */
public final class SkillStageService {
    private SkillStageService() {
    }

    // The level it advanced to, or the technique's entry level while it never advanced; empty means the
    // technique has no chain, so nothing can be granted or climbed.
    public static Optional<Holder<SkillStage>> currentStage(SpiritIdentityAttachment spirit, Holder<Technique> technique) {
        Holder<SkillStage> stored = spirit.techniqueStage(technique);
        return Optional.ofNullable(stored != null ? stored : technique.value().defaultStage().orElse(null));
    }

    public static List<Holder<Ability>> unlockedAbilities(Technique technique, Holder<SkillStage> current) {
        if (technique.configuration().isEmpty() || current == null) return List.of();
        ServerCache cache = ServerCache.get().orElse(null);
        if (cache == null) return List.of();
        Identifier currentId = HolderHelper.id(current);
        return technique.configuration().entrySet().stream()
                .filter(entry -> cache.isStageAtLeast(currentId, HolderHelper.id(entry.getKey())))
                .flatMap(entry -> RegistryCodecs.resolve(entry.getValue().abilities(), MxtDatapackRegistries.registry(MxtResourceKeys.ABILITY)))
                .distinct()
                .toList();
    }

    // The chain is the one its default_stage names, so a level of another chain is never the next step.
    public static Optional<Holder<SkillStage>> nextStage(Technique technique, Holder<SkillStage> current) {
        if (current == null) return Optional.empty();
        Identifier skill = technique.defaultStage().map(stage -> stage.value().skill()).orElse(current.value().skill());
        if (!current.value().skill().equals(skill)) return Optional.empty();
        return current.value().nextStage().filter(next -> next.value().skill().equals(skill));
    }

    // An unconfigured level requires nothing; the cache rejects a chain with unconfigured steps, so that is
    // only the entry level, which none advances into.
    public static EntityCondition advanceCondition(Technique technique, Holder<SkillStage> target) {
        return Optional.ofNullable(technique.configuration().get(target))
                .map(StageConfiguration::condition).orElse(AlwaysCondition.INSTANCE);
    }

    public static boolean canAdvance(LivingEntity entity, Technique technique, Holder<SkillStage> current,
                                     FormulaContext context) {
        Holder<SkillStage> target = nextStage(technique, current).orElse(null);
        return target != null && advanceCondition(technique, target).test(entity, context);
    }

    // Asked per ability rather than per holder, because a chain speaks for the abilities it unlocks: the
    // multiplier of a body-refining manual belongs to what that manual grants, not to every hit the holder
    // lands. Several techniques can grant the same ability and the best is taken - the hit is one hit.
    public static double damageMultiplier(LivingEntity holder, Identifier ability) {
        SpiritIdentityAttachment spirit = holder.getData(MxtAttachments.SPIRIT_IDENTITY);
        double best = 1.0D;
        for (Holder<Technique> technique : spirit.learnedTechniques()) {
            Holder<SkillStage> current = currentStage(spirit, technique).orElse(null);
            if (current == null) continue;
            boolean grants = unlockedAbilities(technique.value(), current).stream()
                    .anyMatch(unlocked -> HolderHelper.id(unlocked).equals(ability));
            if (!grants) continue;
            double multiplier = current.value().damageMultiplier();
            if (Double.isFinite(multiplier) && multiplier > best) best = multiplier;
        }
        return best;
    }
}
