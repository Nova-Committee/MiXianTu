package com.iafenvoy.mxt.data.forging;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.forging.ForgingPlan;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.HolderSetCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Defines the bounded meter, unordered material requirement, allowed methods and finish rule for one
 * forgeable result.
 *
 * <p>{@code input} is an order-independent material list: the workstation container only has to
 * hold at least the declared amount of every entry. {@code maxSteps} is optional and, when it is
 * omitted, the session can never fail for running too long.</p>
 *
 * <h2>Two axes, and neither replaces the other</h2>
 * A blueprint says <em>where the value has to end up</em> and <em>which techniques this piece may be
 * made with</em>; a {@link ForgingMethod} says <em>how the value moves</em>; a {@link ToolBinding} says
 * <em>which techniques this player can perform</em>. What may actually be struck is the intersection of
 * the last two - see {@code ForgingWorkstationService#availableMethodIds}.
 *
 * <h2>{@code allowed_methods}</h2>
 * Optional, and a {@link HolderSet}: a list of method ids, a single {@code "#namespace:tag"} string, or
 * omitted entirely. <b>Declaring nothing - or declaring an empty list - restricts nothing</b>, so the
 * method list is then decided by the tools alone. That is deliberate: a blueprint that wants a specific
 * set says so, and one that does not care does not have to enumerate the whole registry.
 *
 * <p>A tag that resolves to no methods is <em>not</em> the same as declaring nothing: the blueprint
 * asked for a set and got an empty one, so nothing is allowed. That keeps a mistyped tag from silently
 * opening everything up.</p>
 */
public record ForgingBlueprint(List<ForgingMaterial> input, HolderSet<ForgingMethod> allowedMethods, MeterBounds meter,
                               FinishPattern finishPattern, int maxSteps, List<QualityThreshold> qualityByExtraSteps,
                               Identifier result, EntityAction completeAction, EntityAction failAction,
                               FailureSettlement failureSettlement) {
    /**
     * Marker for "the blueprint defines no failure condition". The forge surface always has 15
     * input slots, so a session can never exceed this sentinel step count.
     */
    public static final int UNLIMITED_STEPS = 0;
    /**
     * Number of input slots on the forge surface. A blueprint can never require more entries.
     */
    public static final int MAX_INPUT_ENTRIES = 15;

    /**
     * Ids or one {@code #tag}, which is what {@link HolderSetCodec} reads. {@code alwaysUseList} is
     * false, so a one-entry list may also be written as a bare string.
     */
    public static final Codec<HolderSet<ForgingMethod>> METHODS_CODEC =
            HolderSetCodec.create(MxtResourceKeys.FORGING_METHOD, ForgingMethod.CODEC, false);

    public static final Codec<Holder<ForgingBlueprint>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.FORGING_BLUEPRINT);
    public static final Codec<ForgingBlueprint> DIRECT_CODEC = RecordCodecBuilder.<ForgingBlueprint>create(i -> i.group(
            ForgingMaterial.CODEC.listOf().fieldOf("input").forGetter(ForgingBlueprint::input),
            METHODS_CODEC.optionalFieldOf("allowed_methods", HolderSet.empty()).forGetter(ForgingBlueprint::allowedMethods),
            MeterBounds.MAP_CODEC.forGetter(ForgingBlueprint::meter),
            FinishPattern.MAP_CODEC.codec().optionalFieldOf("finish_pattern", FinishPattern.none()).forGetter(ForgingBlueprint::finishPattern),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("max_steps", UNLIMITED_STEPS).forGetter(ForgingBlueprint::maxSteps),
            QualityThreshold.CODEC.listOf().fieldOf("quality_by_extra_steps").forGetter(ForgingBlueprint::qualityByExtraSteps),
            Identifier.CODEC.fieldOf("result").forGetter(ForgingBlueprint::result),
            EntityAction.optionalCodec("complete_action").forGetter(ForgingBlueprint::completeAction),
            EntityAction.optionalCodec("fail_action").forGetter(ForgingBlueprint::failAction),
            FailureSettlement.CODEC.codec().optionalFieldOf("failure_settlement", FailureSettlement.destroyInput()).forGetter(ForgingBlueprint::failureSettlement)
    ).apply(i, ForgingBlueprint::new)).validate(ForgingBlueprint::validate);

    /**
     * Whether this blueprint restricts the method list at all.
     *
     * <p>Not the same question as "is the set empty". A tag is a declaration whose members are decided
     * elsewhere, so it always counts as one, even when it resolves to nothing; an absent field or an
     * empty list is no declaration at all.</p>
     */
    public boolean restrictsMethods() {
        return this.allowedMethods.unwrap().left().isPresent()
                || !this.allowedMethods.unwrap().right().orElse(List.of()).isEmpty();
    }

    /**
     * The methods this blueprint accepts, in the order the datapack declared them, or every enabled
     * registered method when it declares none.
     */
    public List<Holder<ForgingMethod>> resolvedMethods(RegistryAccess registries) {
        if (this.restrictsMethods()) return this.allowedMethods.stream().toList();
        return MxtDatapackRegistries.holders(registries, MxtResourceKeys.FORGING_METHOD)
                .map(method -> (Holder<ForgingMethod>) method).toList();
    }

    private static DataResult<ForgingBlueprint> validate(ForgingBlueprint definition) {
        String inputError = inputError(definition.input);
        if (inputError != null) return DataResult.error(() -> inputError);
        if (!definition.meter.valid())
            return DataResult.error(() -> "Invalid forging meter or target range");
        // The pattern's shape is checked here and its membership in the allowed set is checked when the
        // plan is built, because a tag's members are not known at decode time.
        if (!definition.finishPattern.valid())
            return DataResult.error(() -> "Invalid finish_pattern");
        if (!QualityThreshold.valid(definition.qualityByExtraSteps))
            return DataResult.error(() -> "quality_by_extra_steps must be ascending and end at Integer.MAX_VALUE");
        return DataResult.success(definition);
    }

    /**
     * Rejects empty, oversized or duplicated material requirements. Duplicates are rejected
     * instead of merged so a datapack typo is reported at load time rather than silently fixed.
     */
    private static String inputError(List<ForgingMaterial> input) {
        if (input.isEmpty()) return "input must not be empty";
        if (input.size() > MAX_INPUT_ENTRIES)
            return "input must not contain more than " + MAX_INPUT_ENTRIES + " entries";
        Set<Identifier> seen = new HashSet<>();
        for (ForgingMaterial material : input) {
            if (!seen.add(material.id())) return "input must not contain the same item twice";
            if (material.resolve().isEmpty()) return "unknown input item " + material.id();
        }
        return null;
    }

    /**
     * Resolves a stable plan for one started session; later datapack reloads do not mutate that session.
     *
     * <p>The deltas are this blueprint's allowed methods - every enabled registered method when it
     * declares none. Both the plan's keys and its finish-pattern check come from that one set, so a
     * pattern naming a method the blueprint does not allow is rejected here rather than at settlement.</p>
     *
     * <p>{@link #UNLIMITED_STEPS} is normalised to {@link Integer#MAX_VALUE} here so the plan keeps
     * its "positive, bounded" contract even though the datapack field is optional.</p>
     */
    public ForgingPlan plan(RegistryAccess registries) {
        Map<Identifier, Integer> deltas = new LinkedHashMap<>();
        for (Holder<ForgingMethod> method : this.resolvedMethods(registries))
            deltas.put(HolderHelper.id(method), method.value().valueDelta());
        int planMaxSteps = this.maxSteps == UNLIMITED_STEPS ? Integer.MAX_VALUE : this.maxSteps;
        return new ForgingPlan(this.meter.min(), this.meter.max(), this.meter.targetMin(), this.meter.targetMax(), this.finishPattern.steps().stream().map(HolderHelper::id).toList(), this.finishPattern.requiredSuffixSteps(), deltas, planMaxSteps);
    }

    /**
     * Whether the session may fail by exceeding {@link #maxSteps()}.
     */
    public boolean hasStepLimit() {
        return this.maxSteps != UNLIMITED_STEPS;
    }

    public Holder<ItemQuality> qualityFor(int extraSteps) {
        if (extraSteps < 0) throw new IllegalArgumentException("extraSteps must be non-negative");
        return this.qualityByExtraSteps.stream().filter(entry -> extraSteps <= entry.maxExtraSteps()).findFirst().orElseThrow().quality();
    }

    /**
     * Total number of material slots this blueprint occupies, used for surface layout hints.
     */
    public int inputEntryCount() {
        return this.input.size();
    }

    public record MeterBounds(int min, int max, int targetMin, int targetMax) {
        public static final MapCodec<MeterBounds> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("meter_min").forGetter(MeterBounds::min),
                Codec.INT.fieldOf("meter_max").forGetter(MeterBounds::max),
                Codec.INT.fieldOf("target_min").forGetter(MeterBounds::targetMin),
                Codec.INT.fieldOf("target_max").forGetter(MeterBounds::targetMax)
        ).apply(i, MeterBounds::new));

        public boolean valid() {
            return this.min < 0 && this.max > 0 && this.min <= this.targetMin && this.targetMin <= this.targetMax && this.targetMax <= this.max;
        }
    }

    /**
     * Optional six-step suffix requirement. When {@code requiredSuffixSteps} is zero the pattern
     * is unused, but the surface still renders the six result slots.
     */
    public record FinishPattern(List<Holder<ForgingMethod>> steps, int requiredSuffixSteps) {
        public static final MapCodec<FinishPattern> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                AutoIgnoreListCodec.create(ForgingMethod.CODEC).optionalFieldOf("steps", List.of()).forGetter(FinishPattern::steps),
                Codec.intRange(0, 6).optionalFieldOf("required_suffix_steps", 0).forGetter(FinishPattern::requiredSuffixSteps)
        ).apply(i, FinishPattern::new));

        public static FinishPattern none() {
            return new FinishPattern(List.of(), 0);
        }

        public boolean valid() {
            if (this.requiredSuffixSteps == 0) return this.steps.isEmpty() || this.steps.size() == 6;
            return this.steps.size() == 6;
        }
    }

    public record QualityThreshold(int maxExtraSteps, Holder<ItemQuality> quality) {
        public static final Codec<QualityThreshold> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("max_extra_steps").forGetter(QualityThreshold::maxExtraSteps), ItemQuality.CODEC.fieldOf("quality").forGetter(QualityThreshold::quality)
        ).apply(i, QualityThreshold::new));

        static boolean valid(List<QualityThreshold> entries) {
            if (entries.isEmpty() || entries.getLast().maxExtraSteps() != Integer.MAX_VALUE) return false;
            int previous = -1;
            for (QualityThreshold entry : entries) {
                if (entry.maxExtraSteps() < previous) return false;
                previous = entry.maxExtraSteps();
            }
            return true;
        }
    }

    /**
     * Failure handling is deliberately part of the blueprint snapshot. The ratios
     * are independent rolls for the locked input and the optional failure product;
     * this keeps multi-material forging inputs meaningful without inventing fractional stacks.
     */
    public record FailureSettlement(Optional<Identifier> result, double inputReturnRatio, double materialLossRatio) {
        public static final MapCodec<FailureSettlement> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.optionalFieldOf("result").forGetter(FailureSettlement::result),
                Codec.doubleRange(0, 1).optionalFieldOf("input_return_ratio", 0.0D).forGetter(FailureSettlement::inputReturnRatio),
                Codec.doubleRange(0, 1).optionalFieldOf("material_loss_ratio", 1.0D).forGetter(FailureSettlement::materialLossRatio)
        ).apply(i, FailureSettlement::new));

        public static FailureSettlement destroyInput() {
            return new FailureSettlement(Optional.empty(), 0.0D, 1.0D);
        }

        public FailureSettlement {
            if (!Double.isFinite(inputReturnRatio) || !Double.isFinite(materialLossRatio)) {
                throw new IllegalArgumentException("Forging failure ratios must be finite");
            }
        }

        public double failureProductRatio() {
            return 1 - this.materialLossRatio;
        }
    }

}
