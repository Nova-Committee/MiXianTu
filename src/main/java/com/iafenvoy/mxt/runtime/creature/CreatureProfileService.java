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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Applies the first eligible creature profile in descending priority and then registry ID order.
 */
public final class CreatureProfileService {
    private CreatureProfileService() {
    }

    public static boolean applySelected(Mob creature) {
        if (!(creature.level() instanceof ServerLevel level)) return false;
        try (ProfilerScope _ = ServerProfiling.scope(MxtProfilerSources.CREATURE)) {
            if (creature.getExistingData(MxtAttachments.CREATURE_SPIRIT).flatMap(CreatureSpiritAttachment::profile).isPresent())
                return false;

            Selection selection = select(level, creature, null);
            if (selection == null) return false;
            CreatureProfile profile = selection.profile().value();
            creature.getData(MxtAttachments.CREATURE_SPIRIT).apply(selection.profile(), selection.intelligence(),
                    profile.innerCore().map(ItemStackTemplate::create).orElse(ItemStack.EMPTY));
            AbilityGrantService.recalculate(creature);
            profile.spawnAction().execute(creature, selection.context());
            return true;
        }
    }

    public static Inspection inspect(ServerLevel level, Mob creature) {
        List<Rejection> rejected = new ArrayList<>();
        Selection selection = select(level, creature, rejected::add);
        return new Inspection(selection == null ? null : selection.profile(), List.copyOf(rejected));
    }

    private static @Nullable Selection select(ServerLevel level, Mob creature, @Nullable Consumer<Rejection> rejected) {
        FormulaContext context = null;
        AuraResult aura = null;
        for (Holder<CreatureProfile> candidate : RegistryCaches.CREATURE_PROFILE_ORDER.get()) {
            CreatureProfile profile = candidate.value();
            if (!RegistryCodecs.matches(profile.entities(), BuiltInRegistries.ENTITY_TYPE, creature.getType()))
                continue;
            if (context == null) context = FormulaContext.of(creature);
            if (!profile.condition().test(creature, context)) {
                if (rejected != null) rejected.accept(new Rejection(candidate, Reason.CONDITION));
                continue;
            }
            if (!profile.minimumAura().isEmpty() || !profile.preferredAuraElements().isEmpty()) {
                if (aura == null) aura = AuraService.getPositionAura(level, creature.blockPosition());
                if (!matchesAura(profile, aura, context)) {
                    if (rejected != null) rejected.accept(new Rejection(candidate, Reason.AURA));
                    continue;
                }
            }
            double intelligence = evaluate(profile.intelligence(), context);
            if (Double.isFinite(intelligence) && intelligence >= 0.0D)
                return new Selection(candidate, intelligence, context);
            if (rejected != null) rejected.accept(new Rejection(candidate, Reason.INTELLIGENCE));
        }
        return null;
    }

    public record Inspection(@Nullable Holder<CreatureProfile> winner, List<Rejection> rejected) {
    }

    public record Rejection(Holder<CreatureProfile> profile, Reason reason) {
    }

    public enum Reason {CONDITION, AURA, INTELLIGENCE}

    private record Selection(Holder<CreatureProfile> profile, double intelligence, FormulaContext context) {
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
