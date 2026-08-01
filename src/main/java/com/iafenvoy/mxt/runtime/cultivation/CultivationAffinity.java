package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.AuraChunkData;
import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.cultivation.CultivationTechniqueDefinition;
import com.iafenvoy.mxt.data.cultivation.SpiritRootDefinition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves the data-driven cultivation affinity without conflating independent physiques with elements.
 */
public final class CultivationAffinity {
    private CultivationAffinity() {
    }

    public static double multiplier(SpiritData spirit, AuraChunkData aura, FormulaContext context,
                                    Function<Identifier, Optional<SpiritRootDefinition>> roots,
                                    Function<Identifier, Optional<CultivationTechniqueDefinition>> techniques) {
        double total = 0.0D;
        int count = 0;
        for (Identifier rootId : spirit.spiritRoots()) {
            SpiritRootDefinition root = roots.apply(rootId).orElse(null);
            if (root == null) continue;
            double base = root.cultivationMultiplier().evaluate(context);
            double bias = aura.elementBias().getOrDefault(root.element(), 0.0D);
            if (!Double.isFinite(base) || !Double.isFinite(bias) || base < 0.0D) return Double.NaN;
            total += base * Math.max(0.0D, 1.0D + bias);
            count++;
        }
        double result = count == 0 ? 1.0D : total / count;
        if (spirit.activeTechnique().isPresent()) {
            CultivationTechniqueDefinition technique = techniques.apply(spirit.activeTechnique().orElseThrow()).orElse(null);
            if (technique != null) {
                double modifier = technique.cultivationModifier().evaluate(context);
                if (!Double.isFinite(modifier) || modifier < 0.0D) return Double.NaN;
                result *= modifier;
            }
        }
        return Double.isFinite(result) && result >= 0.0D ? result : Double.NaN;
    }

    public static double abilityMultiplier(SpiritData spirit, Collection<Identifier> elements, FormulaContext context,
                                           Function<Identifier, Optional<SpiritRootDefinition>> roots) {
        if (elements.isEmpty()) return 1.0D;
        double total = 0.0D;
        int count = 0;
        for (Identifier rootId : spirit.spiritRoots()) {
            SpiritRootDefinition root = roots.apply(rootId).orElse(null);
            if (root == null || !elements.contains(root.element())) continue;
            double modifier = root.elementAbilityModifier().evaluate(context);
            if (!Double.isFinite(modifier) || modifier < 0.0D) return Double.NaN;
            total += modifier;
            count++;
        }
        return count == 0 ? 0.0D : total / count;
    }
}
