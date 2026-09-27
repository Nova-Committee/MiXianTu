package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.runtime.item.PillService;
import net.minecraft.world.entity.Entity;

import java.util.Set;

/**
 * {@code pill_toxicity} reads the same ledger as {@code mxt:pill_toxicity}. Absent storage is 0, not a new attachment.
 */
public final class PillToxicityVariable implements FormulaVariable {
    private static final Set<String> NAMES = Set.of("pill_toxicity");

    @Override
    public Set<String> names() {
        return NAMES;
    }

    @Override
    public double value(String key, String suffix, FormulaContext context) {
        if (!suffix.isEmpty() || !"pill_toxicity".equals(key)) return Double.NaN;
        Entity entity = context.caster() != null ? context.caster() : context.player();
        return entity == null ? Double.NaN : PillService.toxicity(entity);
    }
}
