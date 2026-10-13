package com.iafenvoy.mxt.util.formula.variable;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.runtime.aura.AuraLookup;
import com.iafenvoy.mxt.runtime.cultivation.CultivationRanks;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContext.ResourceSubject;
import com.iafenvoy.mxt.util.formula.FormulaVariable;
import net.minecraft.core.Holder;

import java.util.Set;

// The cultivation state of the resource the formula is evaluated for. These names exist only in a resource
// context, which is why {@code level} means a realm rank here while {@code caster_level} stays the vanilla
// experience level. {@code minor_stage} is the 0-based position inside the current stage's own minor stages,
// and unlike {@code realm} it answers NaN for a mortal, who stands on no stage there is to cut.
public final class RealmVariable implements FormulaVariable {
    private static final Set<String> NAMES = Set.of("realm", "realm_rank", "level", "absorbed_aura", "cultivation_progress", "minor_stage");

    @Override
    public Set<String> names() {
        return NAMES;
    }

    @Override
    public double value(String key, String suffix, FormulaContext context) {
        if (!suffix.isEmpty()) return Double.NaN;
        ResourceSubject subject = context.resource();
        if (subject == null) return Double.NaN;
        Holder<Aura> aura = AuraLookup
                .holder(AuraLookup.access(context), subject.resource()).orElse(null);
        if (key.equals("minor_stage"))
            return aura == null ? Double.NaN : CultivationService.minorStage(aura, subject.cultivation(), context);
        int rank = aura == null ? -1 : CultivationRanks.realmRank(subject.cultivation(), aura);
        if (key.equals("absorbed_aura") || key.equals("cultivation_progress"))
            return rank < 0 ? 0.0D : subject.cultivation().cultivationProgress(aura);
        return Math.max(0, rank);
    }
}
