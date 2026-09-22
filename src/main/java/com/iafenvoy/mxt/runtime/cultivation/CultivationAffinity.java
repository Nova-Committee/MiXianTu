package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.ability.Ability.AffinityMode;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * Resolves the data-driven cultivation affinity without conflating independent physiques with elements. Every
 * reading starts from the body's own attachment and is taken at the moment it is asked for: a root is a
 * definition, not a value, and looking one up a second time by id would only let the two readings disagree.
 */
public final class CultivationAffinity {
    private CultivationAffinity() {
    }

    // Attachment-only path: element separation is retained, but there are no zone-specific modifiers.
    public static double multiplier(SpiritIdentityAttachment spirit, AuraChunkAttachment aura, FormulaContext context) {
        double total = 0.0D;
        int count = 0;
        for (Holder<SpiritRoot> rootHolder : spirit.activeSpiritRoots()) {
            SpiritRoot root = rootHolder.value();
            double base = root.cultivationMultiplier().evaluate(context);
            AuraPool pool = aura.auras().entrySet().stream()
                    .filter(entry -> Elements.enabled(entry.getKey().value().auraType())
                            && entry.getKey().value().auraType().filter(root.element()::equals).isPresent())
                    .map(Entry::getValue).findFirst().orElse(AuraPool.empty());
            double concentration = pool.amount() / Math.max(1.0D, pool.maximum());
            if (!Double.isFinite(base) || !Double.isFinite(concentration) || base < 0.0D) return Double.NaN;
            total += base * Math.max(0.0D, 1.0D + concentration);
            count++;
        }
        return combine(spirit, context, total, count);
    }

    public static double multiplier(SpiritIdentityAttachment spirit, AuraResult aura, FormulaContext context) {
        double total = 0.0D;
        int count = 0;
        for (Holder<SpiritRoot> rootHolder : spirit.activeSpiritRoots()) {
            SpiritRoot root = rootHolder.value();
            double base = root.cultivationMultiplier().evaluate(context);
            AuraPool pool = aura.aura().entrySet().stream()
                    .filter(entry -> Elements.enabled(entry.getKey().value().auraType())
                            && entry.getKey().value().auraType().filter(root.element()::equals).isPresent())
                    .map(Entry::getValue).findFirst().orElse(AuraPool.empty());
            double concentration = pool.amount() / Math.max(1.0D, pool.maximum());
            if (!Double.isFinite(base) || !Double.isFinite(concentration) || base < 0.0D) return Double.NaN;
            // A place opposes a root to the degree that it is made of elements that root has a relation to, so
            // an empty place is not a hostile one. The fit bonus still needs the pool itself, because it is
            // about this root's aura being here at all.
            double modifier = Math.max(0.0D, 1.0D + concentration
                    + (pool.amount() > 0.0D ? aura.elementFitBonus() : 0.0D)
                    - aura.elementConflictPenalty() * opposition(root.element(), aura));
            total += base * modifier;
            count++;
        }
        return combine(spirit, context, total, count);
    }

    // No root means no root-side multiplier at all, which is 1 rather than zero: a body without a spirit root
    // still cultivates, it simply gets nothing extra for an element it does not have.
    private static double combine(SpiritIdentityAttachment spirit, FormulaContext context, double total, int count) {
        double result = count == 0 ? 1.0D : total / count;
        for (Holder<Technique> techniqueHolder : spirit.learnedTechniques()) {
            double modifier = techniqueHolder.value().cultivationModifier().evaluate(context);
            if (!Double.isFinite(modifier) || modifier < 0.0D) return Double.NaN;
            result *= modifier;
        }
        return Double.isFinite(result) && result >= 0.0D ? result : Double.NaN;
    }

    // How much of a place is made of elements this one has a relation to, each weighted by its concentration.
    // Only live auras count, and the element's own pools are never its opposition; this is what a zone's
    // element_conflict_penalty is multiplied by.
    private static double opposition(Holder<Element> element, AuraResult aura) {
        double total = 0.0D;
        for (Entry<Holder<Aura>, AuraPool> entry : aura.aura().entrySet()) {
            AuraPool pool = entry.getValue();
            if (pool.amount() <= 0.0D) continue;
            Optional<Holder<Element>> other = entry.getKey().value().auraType().filter(Elements::enabled);
            if (other.isEmpty() || other.get().equals(element)) continue;
            if (!element.value().overcomes(other.get()) && !element.value().adapts(other.get())) continue;
            double concentration = pool.amount() / Math.max(1.0D, pool.maximum());
            if (Double.isFinite(concentration)) total += concentration;
        }
        return total;
    }

    // A casting with no matching live root is worth nothing, which is the same answer the cast gate reads: an
    // ability with an affinity nobody in this body has is not cast at all.
    public static double abilityMultiplier(SpiritIdentityAttachment spirit, Collection<Either<Holder<Element>, TagKey<Element>>> elements,
                                           FormulaContext context, AffinityMode mode) {
        if (elements.isEmpty()) return 1.0D;
        double total = 0.0D;
        double best = Double.NaN;
        int count = 0;
        for (Holder<SpiritRoot> rootHolder : spirit.activeSpiritRoots()) {
            SpiritRoot root = rootHolder.value();
            if (!Elements.matches(elements, root.element())) continue;
            double modifier = root.elementAbilityModifier().evaluate(context);
            if (!Double.isFinite(modifier) || modifier < 0.0D) return Double.NaN;
            total += modifier;
            best = Double.isNaN(best) ? modifier : Math.max(best, modifier);
            count++;
        }
        if (count == 0) return 0.0D;
        return mode == AffinityMode.MAX ? best : total / count;
    }
}
