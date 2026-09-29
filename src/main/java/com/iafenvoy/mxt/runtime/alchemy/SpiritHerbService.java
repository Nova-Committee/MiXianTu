package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.alchemy.MedicinalProperty;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.stream.Stream;

/**
 * Resolves the highest-priority herb binding, its age and the potency of one furnace role. Age is a formula
 * input, not a second multiplier: a pack that wants older herbs to be stronger writes {@code herb_age} into the potency.
 */
public final class SpiritHerbService {
    public static final String HERB_AGE = "herb_age";

    private SpiritHerbService() {
    }

    public static Optional<SpiritHerb> find(ItemStack stack) {
        return findHolder(MxtDatapackRegistries.holders(MxtResourceKeys.SPIRIT_HERB), stack).map(Holder::value);
    }

    public static Optional<SpiritHerb> find(Provider access, ItemStack stack) {
        return findHolder(access, stack).map(Holder::value);
    }

    public static Optional<Holder<SpiritHerb>> findHolder(Provider access, ItemStack stack) {
        return findHolder(MxtDatapackRegistries.holders(access, MxtResourceKeys.SPIRIT_HERB), stack);
    }

    /**
     * The highest-priority herb whose growth seeds accept this stack. Item bindings and seeds are separate lists,
     * so a seed does not have to be the harvested item. A tie keeps registry order.
     */
    public static Optional<Holder<SpiritHerb>> findSeed(Provider access, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return MxtDatapackRegistries.holders(access, MxtResourceKeys.SPIRIT_HERB)
                .filter(holder -> holder.value().growth().filter(growth -> matches(growth.seeds(), stack)).isPresent())
                .min(Comparator.comparing(Holder::value, ItemMatcher.ORDER))
                .map(holder -> holder);
    }

    public static int age(ItemStack stack, SpiritHerb herb) {
        Integer stored = stack.get(MxtDataComponents.HERB_AGE);
        return stored == null ? herb.defaultAge() : stored;
    }

    public static int age(Provider access, ItemStack stack) {
        return find(access, stack).map(herb -> age(stack, herb)).orElse(0);
    }

    /**
     * Empty only when no loaded binding claims the stack. A matching herb with no power in this role is still
     * returned, so the furnace can refuse it as zero power rather than as an unknown item.
     */
    public static Optional<HerbPotency> potency(Provider access, ItemStack stack, HerbRole role, FormulaContext context) {
        return findHolder(access, stack).map(herb -> potency(herb, stack, role, context));
    }

    public static HerbPotency potency(Holder<SpiritHerb> herb, ItemStack stack, HerbRole role, FormulaContext context) {
        SpiritHerb value = herb.value();
        int years = age(stack, value);
        FormulaContext aged = context.with(HERB_AGE, years);
        double bias = value.thermalBias();
        if (role == HerbRole.CATALYST) {
            double catalyst = positive(value.catalystPower().evaluate(aged));
            return new HerbPotency(herb, years, Map.of(), catalyst, bias, catalyst);
        }
        Map<Holder<MedicinalProperty>, NumberProvider> source = role == HerbRole.MAIN
                ? value.mainEffects() : value.auxiliaryEffects();
        Map<Holder<MedicinalProperty>, Double> properties = new LinkedHashMap<>();
        double total = 0.0D;
        for (Map.Entry<Holder<MedicinalProperty>, NumberProvider> entry : source.entrySet()) {
            if (!entry.getKey().isBound()) continue;
            double amount = entry.getValue().evaluate(aged);
            if (!Double.isFinite(amount) || amount == 0.0D) continue;
            properties.put(entry.getKey(), amount);
            total += amount;
        }
        if (!Double.isFinite(total)) total = 0.0D;
        return new HerbPotency(herb, years, Map.copyOf(properties), 0.0D, bias, total);
    }

    /**
     * {@code growth_rate} evaluated in the given context, then multiplied once by {@code max(0, 1 + bonus)}.
     * An invalid bonus is treated as zero so a corrupt zone cannot invent a second factor.
     */
    public static double appliedGrowth(NumberProvider rate, FormulaContext context, double bonus) {
        double safeBonus = Double.isFinite(bonus) && bonus >= -1.0D ? bonus : 0.0D;
        double raw = rate.evaluate(context);
        if (!Double.isFinite(raw) || raw < 0.0D) return 0.0D;
        double amount = raw * Math.max(0.0D, 1.0D + safeBonus);
        return Double.isFinite(amount) && amount > 0.0D ? amount : 0.0D;
    }

    private static Optional<Holder<SpiritHerb>> findHolder(Stream<Reference<SpiritHerb>> holders, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return ItemMatcher.find(holders, Holder::value, stack).map(holder -> holder);
    }

    private static boolean matches(List<ItemMatcher.Entry> entries, ItemStack stack) {
        for (ItemMatcher.Entry entry : entries) if (entry.matches(stack)) return true;
        return false;
    }


    public static void validateBoundHarvests(Provider access) {
        List<String> errors = new ArrayList<>();
        access.lookupOrThrow(MxtResourceKeys.SPIRIT_HERB).listElements().forEach(holder -> holder.value().growth().ifPresent(growth -> {
            ItemStack harvested = growth.harvest().create();
            Identifier self = holder.key().identifier();
            Identifier found = findHolder(access, harvested).flatMap(Holder::unwrapKey).map(ResourceKey::identifier).orElse(null);
            if (!self.equals(found)) {
                errors.add(self + " harvest " + BuiltInRegistries.ITEM.getKey(harvested.getItem())
                        + " resolves to " + (found == null ? "no spirit herb" : found));
            }
        }));
        if (errors.isEmpty()) return;
        String message = "Spirit herb harvest must be claimed by that herb: " + String.join("; ", errors);
        MiXianTu.LOGGER.error(message);
        throw new IllegalStateException(message);
    }

    private static double positive(double value) {
        return Double.isFinite(value) && value > 0.0D ? value : 0.0D;
    }

    public enum HerbRole {
        MAIN, AUXILIARY, CATALYST
    }

    /**
     * One item in one role. {@code totalPower} is the role sum and may be zero; {@code catalystPower} is zero
     * unless the role is the catalyst.
     */
    public record HerbPotency(Holder<SpiritHerb> herb, int age, Map<Holder<MedicinalProperty>, Double> properties,
                              double catalystPower, double thermalBias, double totalPower) {
        public boolean placeable() {
            return this.totalPower > 0.0D && Double.isFinite(this.totalPower) && Double.isFinite(this.thermalBias);
        }
    }
}
