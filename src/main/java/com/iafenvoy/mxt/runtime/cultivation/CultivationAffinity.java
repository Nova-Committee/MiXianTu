package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritAttachment;
import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves the data-driven cultivation affinity without conflating independent physiques with elements.
 */
public final class CultivationAffinity {
    private CultivationAffinity() {
    }

    /**
     * Legacy attachment-only path; it retains element separation but has no zone-specific modifiers.
     */
    public static double multiplier(SpiritAttachment spirit, AuraChunkAttachment aura, FormulaContext context,
                                    Function<Identifier, Optional<SpiritRoot>> roots,
                                    Function<Identifier, Optional<CultivationTechnique>> techniques) {
        double total = 0.0D;
        int count = 0;
        for (Holder<SpiritRoot> rootHolder : spirit.spiritRoots()) {
            SpiritRoot root = rootHolder.value();
            double base = root.cultivationMultiplier().evaluate(context);
            AuraPool pool = aura.auras().entrySet().stream()
                    .filter(entry -> entry.getKey().value().auraType().filter(root.element()::equals).isPresent())
                    .map(Entry::getValue).findFirst().orElse(new AuraPool(0.0D, 0.0D, 0.0D));
            double concentration = pool == null ? 0.0D : pool.amount() / Math.max(1.0D, pool.maximum());
            if (!Double.isFinite(base) || !Double.isFinite(concentration) || base < 0.0D) return Double.NaN;
            total += base * Math.max(0.0D, 1.0D + concentration);
            count++;
        }
        double result = count == 0 ? 1.0D : total / count;
        if (spirit.activeTechnique().isPresent()) {
            double modifier = spirit.activeTechnique().orElseThrow().value().cultivationModifier().evaluate(context);
            if (!Double.isFinite(modifier) || modifier < 0.0D) return Double.NaN;
            result *= modifier;
        }
        return Double.isFinite(result) && result >= 0.0D ? result : Double.NaN;
    }

    public static double multiplier(SpiritAttachment spirit, AuraResult aura, FormulaContext context,
                                    Function<Identifier, Optional<SpiritRoot>> roots,
                                    Function<Identifier, Optional<CultivationTechnique>> techniques) {
        double total = 0.0D;
        int count = 0;
        for (Holder<SpiritRoot> rootHolder : spirit.spiritRoots()) {
            SpiritRoot root = rootHolder.value();
            double base = root.cultivationMultiplier().evaluate(context);
            AuraPool pool = aura.aura().entrySet().stream()
                    .filter(entry -> entry.getKey().value().auraType().filter(root.element()::equals).isPresent())
                    .map(Entry::getValue).findFirst().orElse(new AuraPool(0.0D, 0.0D, 0.0D));
            double concentration = pool.amount() / Math.max(1.0D, pool.maximum());
            if (!Double.isFinite(base) || !Double.isFinite(concentration) || base < 0.0D) return Double.NaN;
            double modifier = Math.max(0.0D, 1.0D + concentration
                    + (pool.amount() > 0.0D ? aura.elementFitBonus() : -aura.elementConflictPenalty()));
            total += base * modifier;
            count++;
        }
        double result = count == 0 ? 1.0D : total / count;
        if (spirit.activeTechnique().isPresent()) {
            CultivationTechnique technique = spirit.activeTechnique().orElseThrow().value();
            result *= technique.cultivationModifier().evaluate(context);
        }
        return Double.isFinite(result) && result >= 0.0D ? result : Double.NaN;
    }

    public static double abilityMultiplier(SpiritAttachment spirit, Collection<Either<Holder<Element>, TagKey<Element>>> elements, FormulaContext context,
                                           Function<Identifier, Optional<SpiritRoot>> roots) {
        if (elements.isEmpty()) return 1.0D;
        double total = 0.0D;
        int count = 0;
        for (Holder<SpiritRoot> rootHolder : spirit.spiritRoots()) {
            SpiritRoot root = rootHolder.value();
            if (!RegistryCodecs.matches(elements, root.element())) continue;
            double modifier = root.elementAbilityModifier().evaluate(context);
            if (!Double.isFinite(modifier) || modifier < 0.0D) return Double.NaN;
            total += modifier;
            count++;
        }
        return count == 0 ? 0.0D : total / count;
    }
}
