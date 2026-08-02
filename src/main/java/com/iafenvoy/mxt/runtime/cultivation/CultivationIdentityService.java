package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.cultivation.PhysiqueDefinition;
import com.iafenvoy.mxt.data.cultivation.SpiritRootDefinition;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Server-authoritative mutations for independent spirit-root and physique ownership.
 */
public final class CultivationIdentityService {
    private CultivationIdentityService() {
    }

    public static Result grantSpiritRoot(LivingEntity entity, Identifier id, SpiritRootDefinition definition) {
        SpiritData spirit = entity.getData(MxtAttachments.SPIRIT_DATA);
        if (spirit.spiritRoots().contains(id)) return Result.rejected(Failure.ALREADY_HELD);
        ArrayList<Identifier> roots = new ArrayList<>(spirit.spiritRoots());
        roots.add(id);
        spirit.setSpiritRoots(roots);
        CultivationGrantService.recalculate(spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
        return Result.changedResult();
    }

    public static Result grantPhysique(LivingEntity entity, Identifier id, PhysiqueDefinition definition, FormulaContext context) {
        SpiritData spirit = entity.getData(MxtAttachments.SPIRIT_DATA);
        if (!definition.allowStacking() && spirit.physiques().contains(id))
            return Result.rejected(Failure.ALREADY_HELD);
        boolean conditions = definition.holderConditions().stream().allMatch(condition -> condition.test(entity, context));
        if (!conditions) return Result.rejected(Failure.CONDITIONS);
        Set<Identifier> exclusive = new HashSet<>(definition.exclusiveTags());
        boolean conflict = spirit.physiques().stream().map(physiqueId -> MxtDatapackRegistries.get(MxtDatapackRegistries.PHYSIQUE, physiqueId)).flatMap(Optional::stream)
                .anyMatch(existing -> existing.exclusiveTags().stream().anyMatch(exclusive::contains));
        if (conflict) return Result.rejected(Failure.EXCLUSIVE_CONFLICT);
        ArrayList<Identifier> physiques = new ArrayList<>(spirit.physiques());
        physiques.add(id);
        spirit.setPhysiques(physiques);
        CultivationGrantService.recalculate(spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
        return Result.changedResult();
    }

    public static boolean removeSpiritRoot(LivingEntity entity, Identifier id) {
        SpiritData spirit = entity.getData(MxtAttachments.SPIRIT_DATA);
        ArrayList<Identifier> roots = new ArrayList<>(spirit.spiritRoots());
        if (!roots.remove(id)) return false;
        spirit.setSpiritRoots(roots);
        CultivationGrantService.recalculate(spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
        return true;
    }

    public static boolean removePhysique(LivingEntity entity, Identifier id) {
        SpiritData spirit = entity.getData(MxtAttachments.SPIRIT_DATA);
        ArrayList<Identifier> physiques = new ArrayList<>(spirit.physiques());
        if (!physiques.remove(id)) return false;
        spirit.setPhysiques(physiques);
        CultivationGrantService.recalculate(spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
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
