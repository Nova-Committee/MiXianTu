package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.data.alchemy.MedicinalProperty;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.recipe.AlchemyRecipe;
import com.iafenvoy.mxt.recipe.AlchemyRecipe.Role;
import com.iafenvoy.mxt.recipe.AlchemyRecipeInput;
import com.iafenvoy.mxt.recipe.AlchemyRecipeInput.Slot;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService.HerbPotency;
import com.iafenvoy.mxt.runtime.alchemy.SpiritHerbService.HerbRole;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The only mixture check. Preview, recipe matching and start all call this.
 * Among full matches, only a unique strictly dominating demand vector resolves.
 */
public final class AlchemyResolver {
    private static final double EPSILON = 1.0E-6D;

    private AlchemyResolver() {
    }

    public static HerbRole herbRole(Role role) {
        return switch (role) {
            case MAIN -> HerbRole.MAIN;
            case AUXILIARY -> HerbRole.AUXILIARY;
            case CATALYST -> HerbRole.CATALYST;
        };
    }

    public static AlchemyMixture mixture(Provider access, List<Slot> slots, FormulaContext context) {
        Map<Identifier, Double> main = new LinkedHashMap<>();
        Map<Identifier, Double> auxiliary = new LinkedHashMap<>();
        double catalyst = 0.0D;
        double weight = 0.0D;
        double signed = 0.0D;
        AlchemyFailure failure = null;
        for (Slot slot : slots) {
            ItemStack stack = slot.stack();
            if (stack.isEmpty()) continue;
            Optional<HerbPotency> found = SpiritHerbService.potency(access, stack, herbRole(slot.role()), context);
            if (found.isEmpty()) {
                failure = AlchemyFailure.NOT_HERB;
                continue;
            }
            if (!found.get().placeable()) {
                if (failure == null) failure = AlchemyFailure.ZERO_POWER;
                continue;
            }
            HerbPotency potency = found.get();
            double contribution = stack.getCount() * potency.totalPower();
            weight += contribution;
            signed += contribution * potency.thermalBias();
            if (slot.role() == Role.CATALYST) catalyst += stack.getCount() * potency.catalystPower();
            else add(slot.role() == Role.MAIN ? main : auxiliary, potency.properties(), stack.getCount());
        }
        double balance = weight > 0.0D ? signed / weight : 0.0D;
        if (!Double.isFinite(balance)) balance = 0.0D;
        return new AlchemyMixture(main, auxiliary, catalyst, balance, weight, Optional.ofNullable(failure));
    }

    public static Evaluated evaluate(AlchemyRecipe recipe, FormulaContext context) {
        Map<Identifier, Double> main = numbers(recipe.mainRequirements(), context);
        Map<Identifier, Double> auxiliary = numbers(recipe.auxiliaryRequirements(), context);
        Double catalyst = positive(recipe.catalystRequirement(), context);
        Double balance = unit(recipe.balanceTolerance(), context);
        Double target = nonNegative(recipe.targetTemperature(), context);
        Double tolerance = nonNegative(recipe.temperatureTolerance(), context);
        Double duration = positive(recipe.duration(), context);
        Map<Identifier, Double> aura = nonNegativeMap(recipe.minimumAura(), context);
        boolean valid = main != null && auxiliary != null && catalyst != null && balance != null
                && target != null && tolerance != null && duration != null && aura != null;
        return new Evaluated(valid ? main : Map.of(), valid ? auxiliary : Map.of(), valid ? catalyst : 0.0D,
                valid ? balance : 0.0D, valid ? target : 0.0D, valid ? tolerance : 0.0D, valid ? duration : 0.0D,
                valid ? aura : Map.of(), valid);
    }

    public static boolean matches(AlchemyRecipe recipe, AlchemyRecipeInput input, Level level) {
        if (input == null || level == null) return false;
        FormulaContext context = FormulaContext.of(level);
        return matches(recipe, mixture(level.registryAccess(), input.slots(), context), evaluate(recipe, context));
    }

    public static boolean matches(AlchemyRecipe recipe, AlchemyMixture mixture, Evaluated evaluated) {
        return evaluated.valid() && gaps(recipe, mixture, evaluated).isEmpty() && mixture.defined();
    }

    public static List<Gap> gaps(AlchemyRecipe recipe, AlchemyMixture mixture, Evaluated evaluated) {
        if (!evaluated.valid()) return List.of(new Gap(Gap.Kind.MISSING, null, null, 0.0D, 0.0D));
        List<Gap> gaps = new ArrayList<>();
        evaluated.main().forEach((id, required) -> {
            double actual = mixture.main().getOrDefault(id, 0.0D);
            if (actual + EPSILON < required) gaps.add(new Gap(Gap.Kind.MISSING, Role.MAIN, id, actual, required));
        });
        evaluated.auxiliary().forEach((id, required) -> {
            double actual = mixture.auxiliary().getOrDefault(id, 0.0D);
            if (actual + EPSILON < required) gaps.add(new Gap(Gap.Kind.MISSING, Role.AUXILIARY, id, actual, required));
        });
        extra(mixture.main(), evaluated.main(), Role.MAIN, gaps);
        extra(mixture.auxiliary(), evaluated.auxiliary(), Role.AUXILIARY, gaps);
        if (mixture.catalyst() + EPSILON < evaluated.catalyst())
            gaps.add(new Gap(Gap.Kind.CATALYST, Role.CATALYST, null, mixture.catalyst(), evaluated.catalyst()));
        if (!mixture.defined() || Math.abs(mixture.balance()) > evaluated.balanceTolerance() + EPSILON)
            gaps.add(new Gap(Gap.Kind.BALANCE, null, null, mixture.balance(), evaluated.balanceTolerance()));
        return List.copyOf(gaps);
    }

    private static List<RecipeHolder<AlchemyRecipe>> all(RecipeManager manager) {
        return manager.getRecipes().stream()
                .filter(holder -> holder.value() instanceof AlchemyRecipe)
                .map(AlchemyResolver::<AlchemyRecipe>cast)
                .sorted(Comparator.comparing(holder -> holder.id().identifier().toString()))
                .toList();
    }

    // A provisional winner may be incomparable with an earlier candidate; verify it against every match.
    public static @Nullable Candidate dominating(List<Candidate> candidates) {
        Candidate champion = null;
        for (Candidate candidate : candidates) {
            if (!candidate.matched()) continue;
            if (champion == null || dominates(candidate.evaluated(), champion.evaluated())) champion = candidate;
        }
        if (champion == null) return null;
        for (Candidate candidate : candidates) {
            if (!candidate.matched() || candidate == champion) continue;
            if (!dominates(champion.evaluated(), candidate.evaluated())) return null;
        }
        return champion;
    }

    private static boolean dominates(Evaluated candidate, Evaluated other) {
        int main = cover(candidate.main(), other.main());
        if (main < 0) return false;
        int auxiliary = cover(candidate.auxiliary(), other.auxiliary());
        if (auxiliary < 0) return false;
        if (candidate.catalyst() < other.catalyst()) return false;
        return main > 0 || auxiliary > 0 || candidate.catalyst() > other.catalyst();
    }

    /**
     * 1 if the candidate strictly covers the other, 0 if equal, -1 if keys differ or a value is lower.
     */
    private static int cover(Map<Identifier, Double> candidate, Map<Identifier, Double> other) {
        if (candidate.size() != other.size()) return -1;
        boolean strict = false;
        for (Map.Entry<Identifier, Double> entry : candidate.entrySet()) {
            Double required = other.get(entry.getKey());
            if (required == null) return -1;
            double value = entry.getValue();
            if (value < required) return -1;
            if (value > required) strict = true;
        }
        return strict ? 1 : 0;
    }

    public static List<Candidate> candidates(RecipeManager manager, AlchemyMixture mixture, FormulaContext context) {
        List<Candidate> result = new ArrayList<>();
        for (RecipeHolder<AlchemyRecipe> holder : all(manager)) {
            Evaluated evaluated = evaluate(holder.value(), context);
            List<Gap> found = gaps(holder.value(), mixture, evaluated);
            result.add(new Candidate(holder.id(), evaluated.valid() && found.isEmpty() && mixture.defined(), found, evaluated));
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Recipe<?>> RecipeHolder<T> cast(RecipeHolder<?> holder) {
        return (RecipeHolder<T>) holder;
    }

    private static void add(Map<Identifier, Double> into, Map<Holder<MedicinalProperty>, Double> properties, int count) {
        properties.forEach((property, amount) -> into.merge(HolderHelper.id(property), amount * count, Double::sum));
    }

    private static void extra(Map<Identifier, Double> actual, Map<Identifier, Double> required, Role role, List<Gap> gaps) {
        actual.forEach((id, amount) -> {
            if (Math.abs(amount) > EPSILON && !required.containsKey(id))
                gaps.add(new Gap(Gap.Kind.EXTRA, role, id, amount, 0.0D));
        });
    }

    private static @Nullable Map<Identifier, Double> numbers(Map<Holder<MedicinalProperty>, NumberProvider> providers, FormulaContext context) {
        Map<Identifier, Double> result = new LinkedHashMap<>();
        for (Map.Entry<Holder<MedicinalProperty>, NumberProvider> entry : providers.entrySet()) {
            Double value = positive(entry.getValue(), context);
            if (value == null) return null;
            result.put(HolderHelper.id(entry.getKey()), value);
        }
        return result;
    }

    private static @Nullable Map<Identifier, Double> nonNegativeMap(Map<Holder<Aura>, NumberProvider> providers, FormulaContext context) {
        Map<Identifier, Double> result = new LinkedHashMap<>();
        for (Map.Entry<Holder<Aura>, NumberProvider> entry : providers.entrySet()) {
            Double value = nonNegative(entry.getValue(), context);
            if (value == null) return null;
            result.put(HolderHelper.id(entry.getKey()), value);
        }
        return result;
    }

    private static @Nullable Double positive(NumberProvider provider, FormulaContext context) {
        double value = provider.evaluate(context);
        return Double.isFinite(value) && value > 0.0D ? value : null;
    }

    private static @Nullable Double nonNegative(NumberProvider provider, FormulaContext context) {
        double value = provider.evaluate(context);
        return Double.isFinite(value) && value >= 0.0D ? value : null;
    }

    private static @Nullable Double unit(NumberProvider provider, FormulaContext context) {
        double value = provider.evaluate(context);
        return Double.isFinite(value) && value >= 0.0D && value <= 1.0D ? value : null;
    }

    public record Evaluated(Map<Identifier, Double> main, Map<Identifier, Double> auxiliary, double catalyst,
                            double balanceTolerance, double targetTemperature, double temperatureTolerance,
                            double duration, Map<Identifier, Double> minimumAura, boolean valid) {
    }

    /**
     * {@code role} is null only for a balance gap.
     */
    public record Gap(Kind kind, @Nullable Role role, @Nullable Identifier property, double actual, double required) {
        public enum Kind {MISSING, EXTRA, CATALYST, BALANCE}
    }

    public record Candidate(ResourceKey<Recipe<?>> id, boolean matched, List<Gap> gaps, Evaluated evaluated) {
    }
}
