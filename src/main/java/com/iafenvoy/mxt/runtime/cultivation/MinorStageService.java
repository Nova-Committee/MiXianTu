package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.MinorStageAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityGrantService;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * The body's minor stages: the highest one it has reached inside each realm stage, and the abilities those
 * stages unlock. The record only grows, so an unlock survives a breakthrough even though progress restarts.
 */
public final class MinorStageService {
    private MinorStageService() {
    }

    // Called where progress lands and where a breakthrough commits, so the record is never behind what the body
    // has actually reached. The grant rebuild only happens when the record grew, which is once per stage crossed.
    public static boolean refresh(LivingEntity entity, Holder<Aura> aura, FormulaContext context) {
        CultivationAttachment cultivation = entity.getData(MxtAttachments.CULTIVATION);
        MinorStageAttachment stages = entity.getData(MxtAttachments.MINOR_STAGES);
        if (!record(cultivation, stages, aura, context)) return false;
        AbilityGrantService.recalculate(entity);
        return true;
    }

    // True when the record grew: no realm, no minor stages and a formula that cannot be resolved all read as
    // "nothing to record" rather than as a failure.
    public static boolean record(CultivationAttachment cultivation, MinorStageAttachment stages,
                                 Holder<Aura> aura, FormulaContext context) {
        Holder<RealmStage> stage = cultivation.realmStage(aura);
        if (stage == null) return false;
        double index = CultivationService.minorStage(aura, cultivation, context);
        return Double.isFinite(index) && stages.raiseMinorStageRecord(stage, (int) index);
    }

    // An entry is a minimum: its abilities are active on that minor stage or any later one, the same reading a
    // technique's configured levels get.
    public static List<Holder<Ability>> unlockedAbilities(Holder<RealmStage> stage, int reached, Provider access) {
        if (reached < 0) return List.of();
        return stage.value().minorStageAbilities().stream()
                .filter(entry -> entry.stage() <= reached)
                .flatMap(entry -> RegistryCodecs.resolve(entry.abilities(), access, MxtResourceKeys.ABILITY))
                .distinct()
                .toList();
    }
}
