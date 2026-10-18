package com.iafenvoy.mxt.runtime.progression;

import com.iafenvoy.mxt.MiXianTu;
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
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.runtime.trigger.TriggerDispatcher;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.CompareOperation;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Optional;

/**
 * The owner-agnostic half of every progression: where a holder stands, what the next level is, what each level grants
 * and whether it may be entered. Which owners a body holds, when it advances and what happens then belong to the
 * owning module. Every question is asked of an {@link ProgressionOwners.Owner}, so it reaches the holder of that owner's
 * own sort and never another sort's records. Ordering comes from {@link RegistryCaches}.
 */
public final class ProgressionService {
    private ProgressionService() {
    }

    // Where the owner stands: its record, or its own entry level while it never advanced.
    public static <T extends ProgressionOwner> Optional<Holder<Progression>> current(ProgressionOwners.Owner<T> owner) {
        return owner.levels().current(owner.holder());
    }

    // The level the owner actually recorded, so an owner still standing on its entry level answers empty.
    public static <T extends ProgressionOwner> Optional<Holder<Progression>> recorded(ProgressionOwners.Owner<T> owner) {
        return owner.levels().recorded(owner.holder());
    }

    public static <T extends ProgressionOwner> Optional<Holder<Progression>> next(ProgressionOwners.Owner<T> owner) {
        return owner.levels().next(owner.holder());
    }

    public static <T extends ProgressionOwner> boolean set(ProgressionOwners.Owner<T> owner, Holder<Progression> level) {
        return owner.levels().set(owner.holder(), level);
    }

    // One step along the owner's own chain; both bounds and the record itself are the holder's business, so a
    // caller only says which direction.
    public static <T extends ProgressionOwner> boolean promote(ProgressionOwners.Owner<T> owner) {
        return owner.levels().promote(owner.holder());
    }

    public static <T extends ProgressionOwner> boolean demote(ProgressionOwners.Owner<T> owner) {
        return owner.levels().demote(owner.holder());
    }

    public static <T extends ProgressionOwner> boolean follows(ProgressionOwners.Owner<T> owner, Holder<Progression> level) {
        return owner.levels().follows(owner.holder(), level);
    }

    // What the next level asks for and what the body already has of the resource that measures it. A mastery
    // formula that cannot be evaluated answers empty rather than zero, so a caller never reads a false "done".
    public static <T extends ProgressionOwner> Optional<ProgressionMastery> masteryOf(Entity entity,
                                                                                      ProgressionOwners.Owner<T> owner) {
        Holder<Resource> mastery = owner.definition().masteryResource().orElse(null);
        if (mastery == null) return Optional.empty();
        Holder<Progression> target = next(owner).orElse(null);
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

    // Cumulative: what a level grants stays active on every later level, so everything at or below the current
    // one is the answer.
    public static List<Holder<Ability>> grantedAbilities(ProgressionOwner owner, Holder<Progression> current, Provider access) {
        if (current == null) return List.of();
        return owner.levels().entrySet().stream()
                .filter(entry -> RegistryCaches.PROGRESSION_CHAIN.compare(CompareOperation.GREATER_THAN_OR_EQUAL, current, entry.getKey()))
                .flatMap(entry -> RegistryCodecs.resolve(entry.getValue().abilities(), access, MxtResourceKeys.ABILITY))
                .distinct()
                .toList();
    }

    // What marks a level as entered: the level's own action runs first, so whoever reacts sees a body that has
    // already changed, and the signal is published last. Every path that writes a level ends here, so an
    // administrative write and a natural promotion look the same to a data pack.
    public static void enterLevel(LivingEntity entity, Identifier owner, ProgressionOwner definition,
                                  Holder<Progression> reached) {
        FormulaContext formula = FormulaContext.of(entity);
        definition.config(reached).ifPresent(config -> config.action().execute(entity, formula));
        int rank = RegistryCaches.PROGRESSION_CHAIN.rankOf(reached).orElse(0);
        TriggerContext triggerContext = new TriggerContext().actor(entity).level(entity.level()).formula(formula.with("level", rank));
        triggerContext.set("owner", owner.toString());
        triggerContext.set("level", (double) rank);
        TriggerDispatcher.publish(TriggerSignals.PROGRESSION_LEVEL, triggerContext, entity.level().getGameTime());
    }

    /**
     * Drops every recorded level its holder no longer reaches from that owner's own entry level, and answers how
     * many records went. Meant for login and datapack load, never for a read - the walk is only affordable there.
     */
    public static int pruneForeignLevels(Entity entity) {
        int cleared = 0;
        for (ProgressionOwners.Owner<?> owner : ProgressionOwners.heldBy(entity)) {
            Holder<Progression> stored = recorded(owner).orElse(null);
            if (stored == null || !prune(owner)) continue;
            MiXianTu.LOGGER.warn("Dropped progression level {} for owner {} on {}: not reachable from that owner's entry level",
                    HolderHelper.id(stored), owner.id(), entity.getName().getString());
            cleared++;
        }
        return cleared;
    }

    // Reachability is the owner's own chain's question, so the answer comes from the holder that keeps its records.
    public static <T extends ProgressionOwner> boolean prune(ProgressionOwners.Owner<T> owner) {
        return owner.levels().prune(owner.holder());
    }
}
