package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.AlwaysTrueCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
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
 * Reads a technique's mastery: the abilities each level unlocks and the conditions to climb.
 *
 * <p>Every {@code stage_abilities} key is a <em>minimum</em> requirement: its abilities are active
 * while the holder stands on that level or a later one of the same chain, so raising a level adds
 * to what the earlier levels granted instead of replacing it. Ordering comes from
 * {@link ServerCache}, which validates the chain and ranks its levels before anything compares
 * them, so two levels of different chains are never ordered against each other.</p>
 *
 * <p>{@code advance_conditions} is read the other way round: the key is the level a holder advances
 * <em>to</em>, and the chain is shared while the conditions are the technique's own.</p>
 */
public final class SkillStageService {
    private SkillStageService() {
    }

    /**
     * Every ability the technique unlocks at the given level, with item tags already expanded and
     * duplicates removed. An empty list means nothing is unlocked - either the technique gates no
     * abilities, the holder has no level yet, or no chain is available to order the levels.
     */
    public static List<Holder<Ability>> unlockedAbilities(CultivationTechnique technique, Holder<SkillStage> current) {
        if (technique.stageAbilities().isEmpty() || current == null) return List.of();
        ServerCache cache = ServerCache.get().orElse(null);
        if (cache == null) return List.of();
        Identifier currentId = HolderHelper.id(current);
        return technique.stageAbilities().entrySet().stream()
                .filter(entry -> cache.isStageAtLeast(currentId, HolderHelper.id(entry.getKey())))
                .flatMap(entry -> RegistryCodecs.resolve(entry.getValue(), MxtDatapackRegistries.registry(MxtResourceKeys.ABILITY)))
                .distinct()
                .toList();
    }

    /**
     * The level this technique advances to from the given one, or empty at the top of the chain. The
     * technique's chain is the one its {@code default_stage} names, so a level of another chain is
     * never treated as the next step.
     */
    public static Optional<Holder<SkillStage>> nextStage(CultivationTechnique technique, Holder<SkillStage> current) {
        if (current == null) return Optional.empty();
        Identifier skill = technique.defaultStage().map(stage -> stage.value().skill()).orElse(current.value().skill());
        if (!current.value().skill().equals(skill)) return Optional.empty();
        return current.value().nextStage().filter(next -> next.value().skill().equals(skill));
    }

    /**
     * The condition required to advance to the given level. A level the technique does not list
     * requires nothing, so a chain can be climbed freely where only some steps are gated.
     */
    public static EntityCondition advanceCondition(CultivationTechnique technique, Holder<SkillStage> target) {
        return technique.advanceConditions().getOrDefault(target, AlwaysTrueCondition.INSTANCE);
    }

    /**
     * Whether the holder may advance from its current level to the next one. This is the query the
     * advancement path uses; it does not change any state by itself.
     */
    public static boolean canAdvance(LivingEntity entity, CultivationTechnique technique, Holder<SkillStage> current,
                                     FormulaContext context) {
        Holder<SkillStage> target = nextStage(technique, current).orElse(null);
        return target != null && advanceCondition(technique, target).test(entity, context);
    }
}
