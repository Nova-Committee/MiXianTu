package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.AuraChunkData;
import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
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
                                    Function<Identifier, Optional<SpiritRoot>> roots,
                                    Function<Identifier, Optional<CultivationTechnique>> techniques) {
        double total = 0.0D;
        int count = 0;
        for (Holder<SpiritRoot> rootHolder : spirit.spiritRoots()) {
            SpiritRoot root = rootHolder.value();
            double base = root.cultivationMultiplier().evaluate(context);
            double bias = aura.elementBias().getOrDefault(root.element(), 0.0D);
            if (!Double.isFinite(base) || !Double.isFinite(bias) || base < 0.0D) return Double.NaN;
            total += base * Math.max(0.0D, 1.0D + bias);
            count++;
        }
        double result = count == 0 ? 1.0D : total / count;
        if (spirit.activeTechnique().isPresent()) {
            CultivationTechnique technique = spirit.activeTechnique().orElseThrow().value();
            {
                double modifier = technique.cultivationModifier().evaluate(context);
                if (!Double.isFinite(modifier) || modifier < 0.0D) return Double.NaN;
                result *= modifier;
            }
        }
        return Double.isFinite(result) && result >= 0.0D ? result : Double.NaN;
    }

    public static double multiplier(SpiritData spirit, AuraResult aura, FormulaContext context,
                                    Function<Identifier, Optional<SpiritRoot>> roots,
                                    Function<Identifier, Optional<CultivationTechnique>> techniques) {
        double total = 0.0D;
        int count = 0;
        for (Holder<SpiritRoot> rootHolder : spirit.spiritRoots()) {
            SpiritRoot root = rootHolder.value();
            double base = root.cultivationMultiplier().evaluate(context);
            double element = aura.elementAura().getOrDefault(root.element(), 0.0D);
            if (!Double.isFinite(base) || !Double.isFinite(element) || base < 0.0D) return Double.NaN;
            double modifier = Math.max(0.0D, 1.0D + element + (element > 0.0D ? aura.elementFitBonus() : -aura.elementConflictPenalty()));
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

    public static double abilityMultiplier(SpiritData spirit, Collection<Either<Holder<Element>, TagKey<Element>>> elements, FormulaContext context,
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
