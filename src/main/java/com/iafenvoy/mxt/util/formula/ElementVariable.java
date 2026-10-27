package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.FormulaVariable;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;

import java.util.Set;

/**
 * The element names a formula can ask about a body: {@code element_count} for how many its roots carry, and one name
 * per element that answers 1 or 0. Saturn's own entity variable owns the shared prefix, so every name it already
 * answers keeps that answer and only the element ones are decided here; the candidates are asked in order and the
 * first one that can answer wins.
 */
public final class ElementVariable implements FormulaVariable {
    private static final Set<String> PREFIXES = Set.of("caster_", "target_");

    @Override
    public Set<String> names() {
        return Set.of();
    }

    @Override
    public Set<String> prefixes() {
        return PREFIXES;
    }

    @Override
    public double value(String key, String suffix, FormulaContext context) {
        Entity entity = "caster_".equals(key) ? context.caster() : context.target();
        if (entity == null) return Double.NaN;
        if ("element_count".equals(suffix)) return Elements.of(entity).size();
        Holder<Element> element = RegistryCaches.ELEMENT_NAMES.get(suffix);
        if (element == null) return Double.NaN;
        return Elements.of(entity).contains(element) ? 1.0D : 0.0D;
    }
}
