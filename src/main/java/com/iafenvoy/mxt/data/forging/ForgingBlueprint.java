package com.iafenvoy.mxt.data.forging;

import com.iafenvoy.mxt.registry.BehaviorReferences;
import com.iafenvoy.mxt.registry.BehaviorReferences.Reference;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.runtime.forging.ForgingPlan;
import com.iafenvoy.mxt.util.CollectionHelper;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Defines the bounded meter and allowed methods for one forgeable result.
 */
public record ForgingBlueprint(Identifier input, List<Holder<ForgingMethod>> allowedMethods, MeterBounds meter,
                               FinishPattern finishPattern, int maxSteps, List<QualityThreshold> qualityByExtraSteps,
                               Identifier result, List<Either<Holder<Block>, TagKey<Block>>> workstationBlocks,
                               Optional<Identifier> completeBehavior, Optional<Identifier> failBehavior,
                               FailureSettlement failureSettlement) {
    public static Codec<ForgingBlueprint> codec(Codec<Holder<ForgingMethod>> methodCodec) {
        return RecordCodecBuilder.<ForgingBlueprint>create(instance -> instance.group(
                Identifier.CODEC.fieldOf("input").forGetter(ForgingBlueprint::input),
                AutoIgnoreListCodec.create(methodCodec).fieldOf("allowed_methods").forGetter(ForgingBlueprint::allowedMethods),
                MeterBounds.MAP_CODEC.forGetter(ForgingBlueprint::meter),
                FinishPattern.MAP_CODEC.codec().optionalFieldOf("finish_pattern", FinishPattern.none()).forGetter(ForgingBlueprint::finishPattern),
                Codec.INT.optionalFieldOf("max_steps", 64).forGetter(ForgingBlueprint::maxSteps),
                QualityThreshold.CODEC.listOf().fieldOf("quality_by_extra_steps").forGetter(ForgingBlueprint::qualityByExtraSteps),
                Identifier.CODEC.fieldOf("result").forGetter(ForgingBlueprint::result),
                RegistryCodecs.holderOrTagList(Registries.BLOCK).optionalFieldOf("workstation_blocks", defaultWorkstationBlocks()).forGetter(ForgingBlueprint::workstationBlocks),
                Identifier.CODEC.optionalFieldOf("on_complete_behavior").forGetter(ForgingBlueprint::completeBehavior),
                Identifier.CODEC.optionalFieldOf("on_fail_behavior").forGetter(ForgingBlueprint::failBehavior),
                FailureSettlement.CODEC.codec().optionalFieldOf("failure_settlement", FailureSettlement.destroyInput()).forGetter(ForgingBlueprint::failureSettlement)
        ).apply(instance, ForgingBlueprint::new)).validate(ForgingBlueprint::validate);
    }

    private static DataResult<ForgingBlueprint> validate(ForgingBlueprint definition) {
        if (definition.allowedMethods.isEmpty() || definition.allowedMethods.stream().map(ForgingBlueprint::methodId).distinct().count() != definition.allowedMethods.size()) {
            return DataResult.error(() -> "allowed_methods must not be empty");
        }
        if (!definition.meter.valid()) {
            return DataResult.error(() -> "Invalid forging meter or target range");
        }
        if (definition.maxSteps <= 0 || definition.workstationBlocks.isEmpty())
            return DataResult.error(() -> "max_steps and workstation_blocks must not be empty");
        if (!definition.finishPattern.valid() || !CollectionHelper.containsAllFast(definition.allowedMethods.stream().map(ForgingBlueprint::methodId).toList(), definition.finishPattern.steps().stream().map(ForgingBlueprint::methodId).toList()))
            return DataResult.error(() -> "Invalid finish_pattern");
        if (!QualityThreshold.valid(definition.qualityByExtraSteps))
            return DataResult.error(() -> "quality_by_extra_steps must be ascending and end at Integer.MAX_VALUE");
        return BehaviorReferences.validate(definition, MxtTypeRegistries.FORGING_COMPLETION_BEHAVIOR,
                new Reference("on_complete_behavior", definition.completeBehavior),
                new Reference("on_fail_behavior", definition.failBehavior));
    }

    private static List<Either<Holder<Block>, TagKey<Block>>> defaultWorkstationBlocks() {
        Identifier anvil = Identifier.fromNamespaceAndPath("minecraft", "anvil");
        Block block = BuiltInRegistries.BLOCK.getOptional(anvil).orElseThrow();
        return List.of(Either.left(BuiltInRegistries.BLOCK.wrapAsHolder(block)));
    }

    /**
     * Resolves a stable plan for one started session; later datapack reloads do not mutate that session.
     */
    public ForgingPlan plan() {
        Map<Identifier, Integer> deltas = new LinkedHashMap<>();
        for (Holder<ForgingMethod> method : this.allowedMethods)
            deltas.put(methodId(method), method.value().valueDelta());
        return new ForgingPlan(this.meter.min(), this.meter.max(), this.meter.targetMin(), this.meter.targetMax(), this.finishPattern.steps().stream().map(ForgingBlueprint::methodId).toList(), this.finishPattern.requiredSuffixSteps(), deltas, this.maxSteps);
    }

    private static Identifier methodId(Holder<ForgingMethod> method) {
        return method.unwrapKey().map(ResourceKey::identifier)
                .orElseThrow(() -> new IllegalArgumentException("Forging method must be a registry reference"));
    }

    public Identifier qualityFor(int extraSteps) {
        if (extraSteps < 0) throw new IllegalArgumentException("extraSteps must be non-negative");
        return this.qualityByExtraSteps.stream().filter(entry -> extraSteps <= entry.maxExtraSteps()).findFirst().orElseThrow().quality();
    }

    public record MeterBounds(int min, int max, int targetMin, int targetMax) {
        public static final MapCodec<MeterBounds> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("meter_min").forGetter(MeterBounds::min),
                Codec.INT.fieldOf("meter_max").forGetter(MeterBounds::max),
                Codec.INT.fieldOf("target_min").forGetter(MeterBounds::targetMin),
                Codec.INT.fieldOf("target_max").forGetter(MeterBounds::targetMax)
        ).apply(instance, MeterBounds::new));

        public boolean valid() {
            return this.min < 0 && this.max > 0 && this.min <= this.targetMin && this.targetMin <= this.targetMax && this.targetMax <= this.max;
        }
    }

    public record FinishPattern(List<Holder<ForgingMethod>> steps, int requiredSuffixSteps, boolean showFinishPattern) {
        public static final MapCodec<FinishPattern> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                AutoIgnoreListCodec.create(ForgingMethod.CODEC).fieldOf("steps").forGetter(FinishPattern::steps),
                Codec.intRange(0, 6).optionalFieldOf("required_suffix_steps", 0).forGetter(FinishPattern::requiredSuffixSteps),
                Codec.BOOL.optionalFieldOf("show_finish_pattern", true).forGetter(FinishPattern::showFinishPattern)
        ).apply(instance, FinishPattern::new));

        public FinishPattern {
            steps = List.copyOf(steps);
        }

        public static FinishPattern none() {
            return new FinishPattern(List.of(), 0, true);
        }

        public boolean valid() {
            return this.requiredSuffixSteps == 0 ? this.steps.isEmpty() || this.steps.size() == 6 : this.steps.size() == 6;
        }
    }

    public record QualityThreshold(int maxExtraSteps, Identifier quality) {
        public static final Codec<QualityThreshold> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("max_extra_steps").forGetter(QualityThreshold::maxExtraSteps), Identifier.CODEC.fieldOf("quality").forGetter(QualityThreshold::quality)
        ).apply(instance, QualityThreshold::new));

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
     * this keeps one-item forging inputs meaningful without inventing fractional stacks.
     */
    public record FailureSettlement(Optional<Identifier> result, double inputReturnRatio, double materialLossRatio) {
        public static final MapCodec<FailureSettlement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.optionalFieldOf("result").forGetter(FailureSettlement::result),
                Codec.doubleRange(0, 1).optionalFieldOf("input_return_ratio", 0.0D).forGetter(FailureSettlement::inputReturnRatio),
                Codec.doubleRange(0, 1).optionalFieldOf("material_loss_ratio", 1.0D).forGetter(FailureSettlement::materialLossRatio)
        ).apply(instance, FailureSettlement::new));

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
