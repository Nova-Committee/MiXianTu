package com.iafenvoy.mxt.runtime.progression;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.ProgressionAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.AlwaysCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.progression.ProgressionConfig;
import com.iafenvoy.mxt.data.progression.ProgressionOwner;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ServerCache;
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

    /**
     * Drops every recorded level its holder no longer reaches from that owner's own entry level, and answers how
     * many records went. Meant for login and datapack load, never for a read - the walk is only affordable there.
     */
    public static int pruneForeignLevels(Entity entity) {
        ProgressionAttachment progress = entity.getExistingData(MxtAttachments.PROGRESSION).orElse(null);
        if (progress == null) return 0;
        int cleared = 0;
        for (ProgressionSources.Owner owner : ProgressionSources.heldBy(entity)) {
            Holder<Progression> stored = progress.level(owner.id());
            if (stored == null || follows(owner.definition(), stored)) continue;
            if (!progress.clearLevel(owner.id())) continue;
            MiXianTu.LOGGER.warn("Dropped progression level {} for owner {} on {}: not reachable from that owner's entry level",
                    HolderHelper.id(stored), owner.id(), entity.getName().getString());
            cleared++;
        }
        return cleared;
    }

    // The entry level itself, or anything the links reach from it.
    private static boolean follows(ProgressionOwner owner, Holder<Progression> level) {
        Identifier wanted = HolderHelper.id(level);
        Holder<Progression> current = owner.entryLevel().orElse(null);
        for (int step = 0; current != null && step < MAX_CHAIN_LENGTH; step++) {
            if (HolderHelper.id(current).equals(wanted)) return true;
            current = nextLevel(current).orElse(null);
        }
        return false;
    }
}
