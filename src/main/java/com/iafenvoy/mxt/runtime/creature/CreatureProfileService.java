package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.attachment.CreatureSpiritAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.runtime.MxtProfilerSources;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.saturn.profiling.ProfilerScope;
import com.iafenvoy.saturn.profiling.ServerProfiling;
import com.iafenvoy.saturn.runtime.ability.AbilityGrantService;
import com.iafenvoy.saturn.util.codec.RegistryCodecs;
import com.iafenvoy.saturn.util.formula.FormulaContext;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.Map;

/**
 * Applies the first eligible creature profile in registry ID order.
 */
public final class CreatureProfileService {
    private CreatureProfileService() {
    }

    public static boolean applySelected(Mob creature) {
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.CREATURE)) {
            if (creature.getExistingData(MxtAttachments.CREATURE_SPIRIT).flatMap(CreatureSpiritAttachment::profile).isPresent())
                return false;

            FormulaContext context = null;
            AuraResult aura = null;
            for (Holder<CreatureProfile> candidate : RegistryCaches.CREATURE_PROFILE_ORDER.get()) {
                CreatureProfile profile = candidate.value();
                if (!RegistryCodecs.matches(profile.entities(), BuiltInRegistries.ENTITY_TYPE, creature.getType()))
                    continue;
                if (context == null) context = FormulaContext.of(creature);
                if (!profile.condition().test(creature, context)) continue;
                if (!profile.minimumAura().isEmpty() || !profile.preferredAuraElements().isEmpty()) {
                    if (aura == null) aura = AuraService.getPositionAura(creature.level(), creature.blockPosition());
                    if (!matchesAura(profile, aura, context)) continue;
                }

                double intelligence = evaluate(profile.intelligence(), context);
                if (!Double.isFinite(intelligence) || intelligence < 0.0D) continue;
                creature.getData(MxtAttachments.CREATURE_SPIRIT)
                        .apply(candidate, intelligence, profile.innerCore().map(ItemStackTemplate::create).orElse(ItemStack.EMPTY));
                AbilityGrantService.recalculate(creature);
                profile.spawnAction().execute(creature, context);
                return true;
            }
            return false;
        }
    }

    private static boolean matchesAura(CreatureProfile profile, AuraResult aura, FormulaContext context) {
        for (Map.Entry<Holder<Aura>, NumberProvider> entry : profile.minimumAura().entrySet()) {
            double minimum = evaluate(entry.getValue(), context);
            if (!Double.isFinite(minimum) || minimum < 0.0D || aura.pool(entry.getKey()).amount() < minimum)
                return false;
        }
        return profile.preferredAuraElements().isEmpty() || aura.aura().entrySet().stream()
                .anyMatch(entry -> entry.getValue().amount() > 0.0D && entry.getKey().value().auraType()
                        .filter(type -> RegistryCodecs.matches(profile.preferredAuraElements(), type)).isPresent());
    }

    private static double evaluate(NumberProvider provider, FormulaContext context) {
        try {
            return provider.evaluate(context);
        } catch (RuntimeException exception) {
            return Double.NaN;
        }
    }
}
