package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Server-authoritative mutations for independent spirit-root and physique ownership.
 */
public final class CultivationIdentityService {
    private CultivationIdentityService() {
    }

    public static Result grantSpiritRoot(LivingEntity entity, Identifier id, SpiritRoot definition) {
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        Holder<SpiritRoot> root = MxtDatapackRegistries.holder(MxtResourceKeys.SPIRIT_ROOT, id).orElse(null);
        if (root == null) return Result.rejected(Failure.DISABLED);
        if (spirit.spiritRoots().contains(root)) return Result.rejected(Failure.ALREADY_HELD);
        List<Holder<SpiritRoot>> roots = new LinkedList<>(spirit.spiritRoots());
        roots.add(root);
        spirit.setSpiritRoots(roots);
        CultivationGrantService.recalculate(entity, spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
        return Result.changedResult();
    }

    public static Result grantPhysique(LivingEntity entity, Identifier id, Physique definition, FormulaContext context) {
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        Holder<Physique> physique = MxtDatapackRegistries.holder(MxtResourceKeys.PHYSIQUE, id).orElse(null);
        if (physique == null) return Result.rejected(Failure.DISABLED);
        if (!definition.allowStacking() && spirit.physiques().contains(physique))
            return Result.rejected(Failure.ALREADY_HELD);
        boolean conditions = definition.holderCondition().test(entity, context);
        if (!conditions) return Result.rejected(Failure.CONDITIONS);
        Set<Identifier> exclusive = new HashSet<>(definition.exclusiveTags());
        boolean conflict = spirit.physiques().stream().map(Holder::value)
                .anyMatch(existing -> existing.exclusiveTags().stream().anyMatch(exclusive::contains));
        if (conflict) return Result.rejected(Failure.EXCLUSIVE_CONFLICT);
        List<Holder<Physique>> physiques = new LinkedList<>(spirit.physiques());
        physiques.add(physique);
        spirit.setPhysiques(physiques);
        CultivationGrantService.recalculate(entity, spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
        return Result.changedResult();
    }

    public static boolean removeSpiritRoot(LivingEntity entity, Identifier id) {
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        Holder<SpiritRoot> root = MxtDatapackRegistries.holder(MxtResourceKeys.SPIRIT_ROOT, id).orElse(null);
        if (root == null) return false;
        List<Holder<SpiritRoot>> roots = new LinkedList<>(spirit.spiritRoots());
        if (!roots.remove(root)) return false;
        spirit.setSpiritRoots(roots);
        CultivationGrantService.recalculate(entity, spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
        return true;
    }

    public static boolean removePhysique(LivingEntity entity, Identifier id) {
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        Holder<Physique> physique = MxtDatapackRegistries.holder(MxtResourceKeys.PHYSIQUE, id).orElse(null);
        if (physique == null) return false;
        List<Holder<Physique>> physiques = new LinkedList<>(spirit.physiques());
        if (!physiques.remove(physique)) return false;
        spirit.setPhysiques(physiques);
        CultivationGrantService.recalculate(entity, spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
        return true;
    }

    public enum Failure {DISABLED, ALREADY_HELD, CONDITIONS, EXCLUSIVE_CONFLICT}

    public record Result(boolean changed, Failure failure) {
        private static Result changedResult() {
            return new Result(true, null);
        }

        private static Result rejected(Failure failure) {
            return new Result(false, failure);
        }
    }
}
