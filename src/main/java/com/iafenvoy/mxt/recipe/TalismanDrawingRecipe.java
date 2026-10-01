package com.iafenvoy.mxt.recipe;

import com.iafenvoy.mxt.data.Talisman;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtRecipeSerializers;
import com.iafenvoy.mxt.registry.MxtRecipeTypes;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Point;
import com.iafenvoy.mxt.runtime.talisman.TalismanDrawingScorer.Stroke;
import com.iafenvoy.mxt.util.formula.FormulaVariables;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.*;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;

import java.util.*;

/**
 * A talisman formula: what is drawn ({@code pattern}), how strictly it is scored ({@code judgement}) and what the
 * result is ({@code result}). The formula's name and description are the referenced {@code mxt:talisman}
 * definition's, never a field here.
 *
 * <p>The default cost is this type's own: one {@code mxt:blank_talisman} out of the workstation slot, which
 * {@code costs} can only add to. It never matches a crafting grid, so {@link #matches} answers false.
 */
public record TalismanDrawingRecipe(EntityCondition unlockCondition, Holder<Talisman> talisman, List<Cost> costs,
                                    Pattern pattern, TalismanDrawingScorer.Judgement judgement, Settlement result)
        implements Recipe<RecipeInput> {
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * The one variable the settlement formulas get from the drawing.
     */
    public static final String PERCENTAGE = "percentage";
    /**
     * The paper the station slot holds and the default cost takes; the slot's mayPlace reads the same item.
     */
    public static final int DEFAULT_PAPER_COUNT = 1;

    public static final Codec<Point> POINT_CODEC = Codec.DOUBLE.listOf(2, 2)
            .xmap(values -> new Point(values.getFirst(), values.getLast()), point -> List.of(point.x(), point.y()));
    public static final Codec<Stroke> STROKE_CODEC = POINT_CODEC.listOf(2, 4096)
            .xmap(Stroke::new, Stroke::points);
    public static final Codec<List<Stroke>> STROKES_CODEC = STROKE_CODEC.listOf(1, 256)
            .validate(TalismanDrawingRecipe::validateStrokes);

    /**
     * Scoring parameters. Every field has a default, so the whole block may be left out; the preprocessing
     * constants (resample step, RDP threshold) are code constants of {@link TalismanDrawingScorer}, not fields.
     *
     * <p>The record itself is the scorer's, so the recipe and the algorithm can never disagree about the fields.
     */
    public static final Codec<TalismanDrawingScorer.Judgement> JUDGEMENT_CODEC =
            RecordCodecBuilder.<TalismanDrawingScorer.Judgement>create(i -> i.group(
                    positive("sigma", 1.0D).forGetter(TalismanDrawingScorer.Judgement::sigma),
                    weight("direction_weight").forGetter(TalismanDrawingScorer.Judgement::directionWeight),
                    weight("topology_weight").forGetter(TalismanDrawingScorer.Judgement::topologyWeight),
                    weight("order_weight").forGetter(TalismanDrawingScorer.Judgement::orderWeight),
                    Codec.BOOL.optionalFieldOf("stroke_count_strict", false)
                            .forGetter(TalismanDrawingScorer.Judgement::strokeCountStrict),
                    nonNegative("min_stroke_length", 0.02D).forGetter(TalismanDrawingScorer.Judgement::minStrokeLength),
                    Codec.BOOL.optionalFieldOf("preview", true).forGetter(TalismanDrawingScorer.Judgement::preview)
            ).apply(i, TalismanDrawingScorer.Judgement::new)).validate(TalismanDrawingRecipe::validateJudgement);

    public static final MapCodec<TalismanDrawingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityCondition.optionalCodec("unlock_condition").forGetter(TalismanDrawingRecipe::unlockCondition),
            Talisman.CODEC.fieldOf("talisman").forGetter(TalismanDrawingRecipe::talisman),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(TalismanDrawingRecipe::costs),
            Pattern.CODEC.fieldOf("pattern").forGetter(TalismanDrawingRecipe::pattern),
            JUDGEMENT_CODEC.optionalFieldOf("judgement", TalismanDrawingScorer.Judgement.DEFAULT)
                    .forGetter(TalismanDrawingRecipe::judgement),
            Settlement.CODEC.fieldOf("result").forGetter(TalismanDrawingRecipe::result)
    ).apply(i, TalismanDrawingRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, TalismanDrawingRecipe> PACKET_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    public static Item paper() {
        return MxtItems.BLANK_TALISMAN.get();
    }

    @Override
    public boolean matches(@NonNull RecipeInput input, @NonNull Level level) {
        return false;
    }

    @Override
    public @NonNull ItemStack assemble(@NonNull RecipeInput input) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    // Never laid out in a grid; without this the recipe manager warns on every load about a non-special recipe
    // with impossible placement info.
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public @NonNull String group() {
        return "mxt.talisman_drawing";
    }

    @Override
    public @NonNull RecipeSerializer<? extends Recipe<RecipeInput>> getSerializer() {
        return MxtRecipeSerializers.TALISMAN_DRAWING.get();
    }

    @Override
    public @NonNull RecipeType<? extends Recipe<RecipeInput>> getType() {
        return MxtRecipeTypes.TALISMAN_DRAWING.get();
    }

    @Override
    public @NonNull PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public @NonNull RecipeBookCategory recipeBookCategory() {
        return new RecipeBookCategory();
    }

    /**
     * How the reference layer is shown while tracing.
     */
    public enum Guide implements StringRepresentable {
        ALWAYS, FADE, NONE;

        public static final Codec<Guide> CODEC = StringRepresentable.fromEnum(Guide::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * The talisman shape, in normalized canvas coordinates: [0,1]^2, the canvas itself is a code constant.
     */
    public record Pattern(Guide guide, boolean showOrder, double tolerance, List<Stroke> strokes) {
        public static final double DEFAULT_TOLERANCE = 0.06D;
        public static final Codec<Pattern> CODEC = RecordCodecBuilder.create(i -> i.group(
                Guide.CODEC.optionalFieldOf("guide", Guide.FADE).forGetter(Pattern::guide),
                Codec.BOOL.optionalFieldOf("show_order", false).forGetter(Pattern::showOrder),
                NumberProvider.FINITE_DOUBLE_CODEC.validate(value -> value > 0.0D
                                ? DataResult.success(value)
                                : DataResult.error(() -> "pattern.tolerance must be positive"))
                        .optionalFieldOf("tolerance", DEFAULT_TOLERANCE).forGetter(Pattern::tolerance),
                STROKES_CODEC.fieldOf("strokes").forGetter(Pattern::strokes)
        ).apply(i, Pattern::new));
    }

    private static MapCodec<Double> positive(String name, double fallback) {
        return NumberProvider.FINITE_DOUBLE_CODEC.validate(value -> value > 0.0D
                ? DataResult.success(value)
                : DataResult.error(() -> name + " must be positive")).optionalFieldOf(name, fallback);
    }

    private static MapCodec<Double> weight(String name) {
        return NumberProvider.FINITE_DOUBLE_CODEC.validate(value -> value >= 0.0D
                ? DataResult.success(value)
                : DataResult.error(() -> name + " must not be negative")).optionalFieldOf(name, 0.25D);
    }

    private static MapCodec<Double> nonNegative(String name, double fallback) {
        return NumberProvider.FINITE_DOUBLE_CODEC.validate(value -> value >= 0.0D
                ? DataResult.success(value)
                : DataResult.error(() -> name + " must not be negative")).optionalFieldOf(name, fallback);
    }

    private static DataResult<TalismanDrawingScorer.Judgement> validateJudgement(TalismanDrawingScorer.Judgement judgement) {
        if (judgement.directionWeight() == 0.0D && judgement.topologyWeight() == 0.0D && judgement.orderWeight() == 0.0D)
            LOGGER.warn("A talisman drawing recipe scores on shape alone: every auxiliary weight is zero");
        return DataResult.success(judgement);
    }

    /**
     * What a finished drawing settles into; only the server reads it.
     */
    public record Settlement(List<Grade> grades, List<ItemStackTemplate> failureOutputs,
                             EntityAction successAction, EntityAction failureAction) {
        public static final Codec<Settlement> CODEC = RecordCodecBuilder.create(i -> i.group(
                Grade.CODEC.listOf(1, 64).validate(TalismanDrawingRecipe::validateGrades)
                        .fieldOf("grades").forGetter(Settlement::grades),
                ItemStackTemplate.CODEC.listOf().optionalFieldOf("failure_outputs", List.of())
                        .forGetter(Settlement::failureOutputs),
                EntityAction.optionalCodec("success_action").forGetter(Settlement::successAction),
                EntityAction.optionalCodec("failure_action").forGetter(Settlement::failureAction)
        ).apply(i, Settlement::new));

        /**
         * The grade a completion falls into: the highest threshold that is not above it, if any.
         */
        public Optional<Grade> gradeFor(double completion) {
            Grade hit = null;
            for (Grade grade : this.grades) {
                if (grade.minCompletion() > completion) break;
                hit = grade;
            }
            return Optional.ofNullable(hit);
        }
    }

    /**
     * One pass line and what it produces.
     */
    public record Grade(double minCompletion, Optional<Holder<ItemQuality>> quality,
                        Optional<NumberProvider> maxDamage, NumberProvider chargeRatio,
                        List<ItemStackTemplate> outputs) {
        public static final Codec<Grade> CODEC = RecordCodecBuilder.create(i -> i.group(
                NumberProvider.FINITE_DOUBLE_CODEC.validate(value -> value >= 0.0D && value <= 1.0D
                                ? DataResult.success(value)
                                : DataResult.error(() -> "min_completion must be in [0, 1]"))
                        .fieldOf("min_completion").forGetter(Grade::minCompletion),
                ItemQuality.CODEC.optionalFieldOf("quality").forGetter(Grade::quality),
                NumberProvider.CODEC.validate(provider -> validateVariables(provider, "max_damage"))
                        .optionalFieldOf("max_damage").forGetter(Grade::maxDamage),
                NumberProvider.CODEC.validate(provider -> validateVariables(provider, "charge_ratio"))
                        .optionalFieldOf("charge_ratio", new Constant(0.0D)).forGetter(Grade::chargeRatio),
                ItemStackTemplate.CODEC.listOf().optionalFieldOf("outputs", List.of()).forGetter(Grade::outputs)
        ).apply(i, Grade::new));
    }

    private static DataResult<List<Stroke>> validateStrokes(List<Stroke> strokes) {
        double sumX = 0.0D, sumY = 0.0D;
        int count = 0;
        for (Stroke stroke : strokes)
            for (Point point : stroke.points()) {
                if (!Double.isFinite(point.x()) || !Double.isFinite(point.y()))
                    return DataResult.error(() -> "A pattern point must be finite");
                if (point.x() < 0.0D || point.x() > 1.0D || point.y() < 0.0D || point.y() > 1.0D)
                    return DataResult.error(() -> "A pattern point must be inside [0, 1]^2, got " + point.x() + ", " + point.y());
                sumX += point.x();
                sumY += point.y();
                count++;
            }
        double centerX = sumX / count, centerY = sumY / count;
        double squared = 0.0D;
        for (Stroke stroke : strokes)
            for (Point point : stroke.points())
                squared += (point.x() - centerX) * (point.x() - centerX) + (point.y() - centerY) * (point.y() - centerY);
        // The RMS radius is the divisor of the scale normalization, so a pattern whose points all coincide has no
        // meaning at all.
        if (Math.sqrt(squared / count) <= 1.0E-6D)
            return DataResult.error(() -> "A pattern must not have every point in the same place");
        return DataResult.success(strokes);
    }

    private static DataResult<List<Grade>> validateGrades(List<Grade> grades) {
        double previous = Double.NEGATIVE_INFINITY;
        for (Grade grade : grades) {
            double before = previous;
            if (grade.minCompletion() <= before)
                return DataResult.error(() -> "result.grades must be unique and ascending, but " + grade.minCompletion()
                        + " does not follow " + before);
            previous = grade.minCompletion();
        }
        return DataResult.success(grades);
    }

    /**
     * The settlement formulas may read the drawing's completion plus whatever the formula variable registry
     * provides, so a name that is neither is a content bug rather than a silent zero.
     */
    private static DataResult<NumberProvider> validateVariables(NumberProvider provider, String field) {
        for (String name : variables(provider))
            if (!name.equals(PERCENTAGE) && !FormulaVariables.contains(name))
                return DataResult.error(() -> field + " reads the unknown variable '" + name + "'");
        return DataResult.success(provider);
    }

    private static Set<String> variables(NumberProvider provider) {
        Set<String> names = new LinkedHashSet<>();
        collect(provider, names);
        return names;
    }

    private static void collect(NumberProvider provider, Set<String> names) {
        switch (provider) {
            case Expression expression -> names.addAll(FormulaVariables.find(expression.source()));
            case ContextVariable variable -> names.add(variable.variable());
            case Sum sum -> sum.summands().forEach(summand -> collect(summand, names));
            case Uniform uniform -> {
                collect(uniform.min(), names);
                collect(uniform.max(), names);
            }
            case Binomial binomial -> {
                collect(binomial.trials(), names);
                collect(binomial.probability(), names);
            }
            case WeightedList list -> list.distribution().forEach(entry -> collect(entry.value(), names));
            case Conditional conditional -> {
                conditional.branches().forEach(branch -> collect(branch.value(), names));
                conditional.fallback().ifPresent(fallback -> collect(fallback, names));
            }
            // Constants carry no name, and a script owns its own reading.
            case null, default -> {
            }
        }
    }
}
