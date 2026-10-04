package com.iafenvoy.mxt.runtime.progression;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.ProgressionAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.AlwaysCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.progression.ProgressionConfig;
import com.iafenvoy.mxt.data.progression.ProgressionOwner;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.trigger.TriggerContext;
import com.iafenvoy.mxt.data.trigger.TriggerSignals;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.EntitySources;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.trigger.TriggerDispatcher;
import com.iafenvoy.mxt.util.ChainCache;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;

/**
 * The owner-agnostic half of every progression: where a holder stands, what the next level is, what each level
 * grants and whether it may be entered. Which owners a body holds, when it advances and what happens then belong
 * to the owning module. Ordering comes from {@link ServerCache}.
 */
public final class ProgressionService {
    // Bound on the ownership walk, so a chain that escaped validation cannot stall a login.
    private static final int MAX_CHAIN_LENGTH = 512;

    private ProgressionService() {
    }

    // The recorded level, or the owner's entry level while the holder never advanced; empty means the owner has
    // no chain, so nothing can be granted or climbed for it.
    public static Optional<Holder<Progression>> currentLevel(ProgressionAttachment progress, Identifier owner,
                                                             ProgressionOwner definition) {
        Holder<Progression> stored = progress == null ? null : progress.level(owner);
        return Optional.ofNullable(stored != null ? stored : definition.entryLevel().orElse(null));
    }

    public static Optional<Holder<Progression>> nextLevel(Holder<Progression> current) {
        return current == null ? Optional.empty() : current.value().nextLevel();
    }

    // Where a body stands on one owner it holds: the record, or that owner's entry level. Empty when the body
    // does not hold the owner at all, which is also what an administrative write answers with.
    public static Optional<Holder<Progression>> currentLevelOf(Entity entity, Identifier owner) {
        return EntitySources.held(entity, owner).flatMap(held ->
                currentLevel(entity.getExistingData(MxtAttachments.PROGRESSION).orElse(null), owner, held.definition()));
    }

    public static Optional<Holder<Progression>> nextLevelOf(Entity entity, Identifier owner) {
        return currentLevelOf(entity, owner).flatMap(ProgressionService::nextLevel);
    }

    // What the next level asks for and what the body already has of the resource that measures it. A mastery
    // formula that cannot be evaluated answers empty rather than zero, so a caller never reads a false "done".
    public static Optional<ProgressionMastery> masteryOf(Entity entity, Identifier owner) {
        EntitySources.Owner held = EntitySources.held(entity, owner).orElse(null);
        if (held == null) return Optional.empty();
        Holder<Resource> mastery = held.definition().masteryResource().orElse(null);
        if (mastery == null) return Optional.empty();
        Holder<Progression> target = nextLevelOf(entity, owner).orElse(null);
        if (target == null) return Optional.empty();
        final double required;
        try {
            required = target.value().mastery().evaluate(FormulaContext.of(entity));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        double have = entity.getExistingData(MxtAttachments.RESOURCE_HOLDER)
                .map(holder -> holder.get(mastery)).orElse(0.0D);
        return Optional.of(new ProgressionMastery(have, required, HolderHelper.id(mastery)));
    }

    // An unconfigured level requires nothing; the cache rejects a chain with unconfigured steps, so that is only
    // the entry level, which none advances into.
    public static EntityCondition advanceCondition(ProgressionOwner owner, Holder<Progression> target) {
        return owner.config(target).map(ProgressionConfig::condition).orElse(AlwaysCondition.INSTANCE);
    }

    public static boolean canAdvance(LivingEntity entity, ProgressionOwner owner, Holder<Progression> current,
                                     FormulaContext context) {
        Holder<Progression> target = nextLevel(current).orElse(null);
        return target != null && advanceCondition(owner, target).test(entity, context);
    }

    // Cumulative: what a level grants stays active on every later level, so everything at or below the current
    // one is the answer.
    public static List<Holder<Ability>> grantedAbilities(ProgressionOwner owner, Holder<Progression> current) {
        ServerCache cache = ServerCache.get().orElse(null);
        if (cache == null || current == null) return List.of();
        Identifier currentId = HolderHelper.id(current);
        return owner.levels().entrySet().stream()
                .filter(entry -> cache.isLevelAtLeast(currentId, HolderHelper.id(entry.getKey())))
                .flatMap(entry -> RegistryCodecs.resolve(entry.getValue().abilities(), MxtDatapackRegistries.registry(MxtResourceKeys.ABILITY)))
                .distinct()
                .toList();
    }

    public static void setLevel(LivingEntity entity, Identifier owner, Holder<Progression> level) {
        entity.getData(MxtAttachments.PROGRESSION).setLevel(owner, level);
    }

    // What marks a level as entered: the level's own action runs first, so whoever reacts sees a body that has
    // already changed, and the signal is published last. Every path that writes a level ends here, so an
    // administrative write and a natural promotion look the same to a data pack.
    public static void enterLevel(LivingEntity entity, Identifier owner, ProgressionOwner definition,
                                  Holder<Progression> reached) {
        FormulaContext formula = FormulaContext.of(entity);
        definition.config(reached).ifPresent(config -> config.action().execute(entity, formula));
        int rank = ServerCache.get().flatMap(cache -> cache.rankForLevel(HolderHelper.id(reached))).orElse(0);
        TriggerContext triggerContext = new TriggerContext().actor(entity).level(entity.level())
                .formula(formula.with("level", rank));
        triggerContext.set("owner", owner.toString());
        triggerContext.set("level", (double) rank);
        TriggerDispatcher.publish(TriggerSignals.PROGRESSION_LEVEL, triggerContext, entity.level().getGameTime());
    }

    /**
     * Drops every recorded level its holder no longer reaches from that owner's own entry level, and answers how
     * many records went. Meant for login and datapack load, never for a read - the walk is only affordable there.
     */
    public static int pruneForeignLevels(Entity entity) {
        ProgressionAttachment progress = entity.getExistingData(MxtAttachments.PROGRESSION).orElse(null);
        if (progress == null) return 0;
        int cleared = 0;
        for (EntitySources.Owner owner : EntitySources.heldBy(entity)) {
            Holder<Progression> stored = progress.level(owner.id());
            if (stored == null || follows(owner.definition(), stored)) continue;
            if (!progress.clearLevel(owner.id())) continue;
            MiXianTu.LOGGER.warn("Dropped progression level {} for owner {} on {}: not reachable from that owner's entry level",
                    HolderHelper.id(stored), owner.id(), entity.getName().getString());
            cleared++;
        }
        return cleared;
    }

    // The entry level itself, or anything the links reach from it. Also the answer an administrative write asks
    // before it stores a level, so the rule lives in one place.
    public static boolean follows(ProgressionOwner owner, Holder<Progression> level) {
        return rankFromEntry(owner, level) >= 0;
    }

    /**
     * Where a level sits above the owner's own entry level (0 is the entry itself), or -1 when the owner never
     * reaches it. The validated chains answer while a server is running; a client, or anything asking before a world
     * is loaded, gets the definitions' own links, bounded so a chain that escaped validation cannot stall it.
     */
    public static int rankFromEntry(ProgressionOwner owner, Holder<Progression> level) {
        Holder<Progression> entry = owner.entryLevel().orElse(null);
        if (entry == null) return -1;
        Identifier entryId = HolderHelper.id(entry);
        Identifier wanted = HolderHelper.id(level);
        ServerCache cache = ServerCache.get().orElse(null);
        if (cache != null) {
            ChainCache.Chain<Progression> chain = cache.progressionChains().chainOf(entryId).orElse(null);
            if (chain == null) return -1;
            int from = chain.indexOf(entryId);
            int at = chain.indexOf(wanted);
            return at < from ? -1 : at - from;
        }
        Holder<Progression> current = entry;
        for (int rank = 0; current != null && rank < MAX_CHAIN_LENGTH; rank++) {
            if (HolderHelper.id(current).equals(wanted)) return rank;
            current = nextLevel(current).orElse(null);
        }
        return -1;
    }

    /**
     * How many levels the owner's climb has, counted from its entry level up; zero when it declares no entry, and
     * zero for a chain the server refused.
     */
    public static int levelCount(ProgressionOwner owner) {
        Holder<Progression> entry = owner.entryLevel().orElse(null);
        if (entry == null) return 0;
        Identifier entryId = HolderHelper.id(entry);
        ServerCache cache = ServerCache.get().orElse(null);
        if (cache != null)
            return cache.progressionChains().chainOf(entryId)
                    .map(chain -> chain.size() - chain.indexOf(entryId)).orElse(0);
        int count = 0;
        Holder<Progression> current = entry;
        while (current != null && count < MAX_CHAIN_LENGTH) {
            count++;
            current = nextLevel(current).orElse(null);
        }
        return count;
    }
}
