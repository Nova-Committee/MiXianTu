package com.iafenvoy.mxt.util.formula.variable;

import com.iafenvoy.mxt.runtime.cultivation.LifeSpanService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaVariable;
import net.minecraft.world.entity.Entity;

import java.util.Set;

// How much life the caster has left and what its ceiling is, in ticks. Both answer NaN for a body with no
// ledger, so a condition can tell "never accounted for" from "accounted for and spent".
public final class LifespanVariable implements FormulaVariable {
    private static final Set<String> NAMES = Set.of("lifespan_remaining", "lifespan_total");

    @Override
    public Set<String> names() {
        return NAMES;
    }

    @Override
    public double value(String key, String suffix, FormulaContext context) {
        if (!suffix.isEmpty()) return Double.NaN;
        Entity entity = context.caster() != null ? context.caster() : context.player();
        if (entity == null) return Double.NaN;
        long value = key.equals("lifespan_remaining") ? LifeSpanService.remaining(entity) : LifeSpanService.total(entity);
        return value < 0L ? Double.NaN : value;
    }
}
