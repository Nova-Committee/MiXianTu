package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
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
 * Reads a technique's mastery: the abilities each configured level grants and the conditions to climb.
 *
 * <p>The chain belongs to {@link SkillStage}, the technique only annotates it. Every
 * {@code configuration} entry's {@code ability} is a <em>minimum</em> requirement: its abilities are
 * active while the holder stands on that level or a later one, so raising a level adds to what the
 * earlier levels granted instead of replacing it. Ordering comes from {@link ServerCache}, which
 * validates the chain and ranks its levels before anything compares them, so two levels of different
 * chains are never ordered against each other.</p>
 *
 * <p>The entry's {@code condition} is read the other way round: it is the requirement to reach that
 * level, so it is asked for the level a holder advances <em>to</em>.</p>
 */
public final class SkillStageService {
    private SkillStageService() {
    }

    /**
     * The level a holder currently stands on in this technique: the level it has advanced to, or the
     * technique's entry level while it never advanced. An empty result means the technique has no
     * chain at all, so nothing can be granted or climbed.
     */
    public static Optional<Holder<SkillStage>> currentStage(SpiritIdentityAttachment spirit, Holder<CultivationTechnique> technique) {
        Holder<SkillStage> stored = spirit.techniqueStage(technique);
        return Optional.ofNullable(stored != null ? stored : technique.value().defaultStage().orElse(null));
    }

    /**
     * Every ability the technique grants at the given level, with item tags already expanded and
     * duplicates removed. An empty list means nothing is granted - either the technique configures no
     * abilities, the holder has no level yet, or no chain is available to order the levels.
     */
    public static List<Holder<Ability>> unlockedAbilities(CultivationTechnique technique, Holder<SkillStage> current) {
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
     * The condition required to reach the given level. A level the technique does not configure
     * requires nothing; the cache rejects a chain whose steps are not all configured, so this only
     * happens for the entry level, which no holder advances into.
     */
    public static EntityCondition advanceCondition(CultivationTechnique technique, Holder<SkillStage> target) {
        return Optional.ofNullable(technique.configuration().get(target))
                .map(CultivationTechnique.StageConfiguration::condition).orElse(AlwaysTrueCondition.INSTANCE);
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
