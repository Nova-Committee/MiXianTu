package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.condition.AlwaysTrueCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.cultivation.Technique.StageConfiguration;
import com.iafenvoy.mxt.data.cultivation.SkillStage;
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
 * Reads a technique's mastery: the abilities each configured level grants and the conditions to climb. The
 * chain belongs to {@link SkillStage}, and an entry's {@code ability} is a minimum requirement — its
 * abilities are active on that level or any later one — while its {@code condition} is asked for the level a
 * holder advances to. Ordering comes from {@link ServerCache}.
 */
public final class SkillStageService {
    private SkillStageService() {
    }

    /**
     * The level the holder stands on: the one it advanced to, or the technique's entry level while it
     * never advanced. Empty means the technique has no chain, so nothing can be granted or climbed.
     */
    public static Optional<Holder<SkillStage>> currentStage(SpiritIdentityAttachment spirit, Holder<Technique> technique) {
        Holder<SkillStage> stored = spirit.techniqueStage(technique);
        return Optional.ofNullable(stored != null ? stored : technique.value().defaultStage().orElse(null));
    }

    /**
     * Every ability granted at the given level, with tags expanded and duplicates removed.
     */
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

    /**
     * The level this technique advances to, or empty at the top of the chain. The chain is the one its
     * {@code default_stage} names, so a level of another chain is never the next step.
     */
    public static Optional<Holder<SkillStage>> nextStage(Technique technique, Holder<SkillStage> current) {
        if (current == null) return Optional.empty();
        Identifier skill = technique.defaultStage().map(stage -> stage.value().skill()).orElse(current.value().skill());
        if (!current.value().skill().equals(skill)) return Optional.empty();
        return current.value().nextStage().filter(next -> next.value().skill().equals(skill));
    }

    /**
     * The condition required to reach the given level; an unconfigured level requires nothing. The cache
     * rejects a chain with unconfigured steps, so that is only the entry level, which none advances into.
     */
    public static EntityCondition advanceCondition(Technique technique, Holder<SkillStage> target) {
        return Optional.ofNullable(technique.configuration().get(target))
                .map(StageConfiguration::condition).orElse(AlwaysTrueCondition.INSTANCE);
    }

    /**
     * Whether the holder may advance from its current level to the next. Read-only.
     */
    public static boolean canAdvance(LivingEntity entity, Technique technique, Holder<SkillStage> current,
                                     FormulaContext context) {
        Holder<SkillStage> target = nextStage(technique, current).orElse(null);
        return target != null && advanceCondition(technique, target).test(entity, context);
    }
}
