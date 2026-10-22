package com.iafenvoy.mxt.util.formula.variable;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.runtime.cultivation.CultivationRanks;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaVariable;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

// The cultivation state of the resource the formula is evaluated for. These names exist only in a resource
// context, which is why {@code level} means a realm rank here while {@code caster_level} stays the vanilla
// experience level. {@code minor_stage} is the 0-based position inside the standing stage's own minor stages,
// cut by the requirement that reaches the next one; unlike {@code realm} it answers NaN for a mortal, who stands
// on no stage there is to cut, and for a chain with nothing left to climb.
public final class RealmVariable implements FormulaVariable {
    private static final Set<String> NAMES = Set.of("realm", "realm_rank", "level", "absorbed_aura", "cultivation_progress", "minor_stage");

    @Override
    public Set<String> names() {
        return NAMES;
    }

    @Override
    public double value(String key, String suffix, FormulaContext context) {
        if (!suffix.isEmpty()) return Double.NaN;
        Holder<Resource> resource = context.resource();
        if (resource == null || !(context.caster() instanceof LivingEntity caster)) return Double.NaN;
        CultivationAttachment spirit = caster.getData(MxtAttachments.CULTIVATION);
        Holder<Aura> aura = RegistryCaches.RESOURCE_AURA.get(resource).orElse(null);
        if (key.equals("minor_stage"))
            return aura == null ? Double.NaN : CultivationService.minorStage(aura, spirit, context);
        int rank = aura == null ? -1 : CultivationRanks.realmRank(spirit, aura);
        if (key.equals("absorbed_aura") || key.equals("cultivation_progress"))
            return rank < 0 ? 0.0D : spirit.cultivationProgress(aura);
        return Math.max(0, rank);
    }
}
