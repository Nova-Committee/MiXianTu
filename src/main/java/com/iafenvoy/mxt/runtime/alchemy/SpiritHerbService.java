package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.alchemy.MedicinalProperty;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb.Growth;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Resolves the highest-priority herb binding, its age and the potency of one furnace role. Age is a formula
 * input, not a second multiplier: a pack that wants older herbs to be stronger writes {@code herb_age} into the potency.
 */
public final class SpiritHerbService {
    public static final String HERB_AGE = "herb_age";

    private SpiritHerbService() {
    }

    /**
     * The herb that owns this stack. A harvested fruit is traceable back to its plant only through the drop
     * matcher, which is why this deliberately does not key off the item the way the old binding did.
     * <p>
     * A herb with no {@code growth} bears no fruit, so the drop route can never match it; its block's own item is
     * the fallback, because that is the only item-shaped handle such a herb has. Without this a furnace-only herb
     * would be unreadable - its power fields would exist but nothing could ever trigger them.
     */
    public static Optional<Holder<SpiritHerb>> find(Provider access, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return MxtDatapackRegistries.holders(access, MxtResourceKeys.SPIRIT_HERB)
                .filter(holder -> claims(access, holder.value(), stack))
                .min(Comparator.comparing(Holder::value, ORDER))
                .map(holder -> holder);
    }

    /**
     * Whether this herb owns the stack. A fruit matches through {@code growth.drops}; a herb with no growth bears
     * no fruit, so the item form of the blocks it claims is the fallback - without it such a herb could never be
     * read from any item, and its power fields would be dead weight. A herb that does have a growth never matches
     * by its block, so no stack can be claimed twice.
     */
    public static boolean claims(Provider access, SpiritHerb herb, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (matchesDrops(herb, stack)) return true;
        if (herb.growth().isPresent()) return false;
        return RegistryCodecs.resolve(herb.blocks(), access, Registries.BLOCK)
                .anyMatch(block -> block.value().asItem() == stack.getItem());
    }

    /**
     * The same question without a registry provider, for the item matcher: it is also evaluated on a client, where
     * the honest answer to "is this the item that herb is read from" is the one the block registry alone can give.
     */
    public static boolean claims(SpiritHerb herb, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (matchesDrops(herb, stack)) return true;
        if (herb.growth().isPresent()) return false;
        return RegistryCodecs.resolve(herb.blocks(), BuiltInRegistries.BLOCK)
                .anyMatch(block -> block.value().asItem() == stack.getItem());
    }

    private static boolean matchesDrops(SpiritHerb herb, ItemStack stack) {
        return herb.growth()
                .filter(growth -> RegistryCodecs.matchesValue(growth.drops(), BuiltInRegistries.ITEM, stack.getItem()))
                .isPresent();
    }

    public static Optional<Holder<SpiritHerb>> findHolder(Provider access, ItemStack stack) {
        return find(access, stack);
    }

    /**
     * The highest-priority herb that claims this block. Unlike an item binding there is nothing else to ask: a
     * herb's identity <i>is</i> the block it grows as, so this one lookup answers "is this a spirit herb" for the
     * growth service, the drop stamp and Jade alike. A tie keeps registry order.
     */
    public static Optional<Holder<SpiritHerb>> findBlock(Provider access, BlockState state) {
        if (state == null || state.isAir()) return Optional.empty();
        Block block = state.getBlock();
        return MxtDatapackRegistries.holders(access, MxtResourceKeys.SPIRIT_HERB)
                .filter(holder -> RegistryCodecs.matchesValue(holder.value().blocks(), BuiltInRegistries.BLOCK, block))
                .min(Comparator.comparing(Holder::value, ORDER))
                .map(holder -> holder);
    }

    private static final Comparator<SpiritHerb> ORDER =
            Comparator.comparingInt(SpiritHerb::priority).reversed();

    public static int age(ItemStack stack, SpiritHerb herb) {
        Integer stored = stack.get(MxtDataComponents.HERB_AGE);
        return stored == null ? herb.defaultAge() : stored;
    }

    public static int age(Provider access, ItemStack stack) {
        return find(access, stack).map(holder -> age(stack, holder.value())).orElse(0);
    }

    /**
     * Empty only when no loaded binding claims the stack. A matching herb with no power in this role is still
     * returned, so the furnace can refuse it as zero power rather than as an unknown item.
     */
    public static Optional<HerbPotency> potency(Provider access, ItemStack stack, HerbRole role, FormulaContext context) {
        return find(access, stack).map(herb -> potency(herb, stack, role, context));
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

    /**
     * Two herbs claiming the same block, or the same drop, is ambiguous: the block decides which herb grows and
     * the drop decides which herb an item came from, and neither can answer when a tie is broken only by registry
     * order. A tag overlapping another entry is not reported - that is what {@code priority} is for.
     */
    public static void validateExclusiveClaims(Provider access) {
        List<String> errors = new ArrayList<>();
        List<Holder.Reference<SpiritHerb>> all = access.lookupOrThrow(MxtResourceKeys.SPIRIT_HERB).listElements().toList();
        for (int a = 0; a < all.size(); a++) {
            for (int b = a + 1; b < all.size(); b++) {
                Holder.Reference<SpiritHerb> first = all.get(a);
                Holder.Reference<SpiritHerb> second = all.get(b);
                if (first.value().priority() != second.value().priority()) continue;
                if (overlapsBlocks(first.value(), second.value()))
                    errors.add(first.key().identifier() + " and " + second.key().identifier() + " claim the same block");
                if (overlapsDrops(first.value(), second.value()))
                    errors.add(first.key().identifier() + " and " + second.key().identifier() + " claim the same drop");
            }
        }
        if (errors.isEmpty()) return;
        String message = "Spirit herb claims must be unique: " + String.join("; ", errors);
        MiXianTu.LOGGER.error(message);
        throw new IllegalStateException(message);
    }

    private static boolean overlapsBlocks(SpiritHerb first, SpiritHerb second) {
        return first.blocks().stream().anyMatch(entry -> second.blocks().contains(entry));
    }

    private static boolean overlapsDrops(SpiritHerb first, SpiritHerb second) {
        List<Either<Holder<Item>, TagKey<Item>>> a = first.growth().map(Growth::drops).orElse(List.of());
        List<Either<Holder<Item>, TagKey<Item>>> b = second.growth().map(Growth::drops).orElse(List.of());
        return a.stream().anyMatch(b::contains);
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
