package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.forging.ForgingBlueprint.FailureSettlement;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint.QualityThreshold;
import com.iafenvoy.mxt.runtime.forging.ForgingPlan;
import com.iafenvoy.mxt.runtime.forging.ForgingSession;
import com.iafenvoy.mxt.runtime.forging.ForgingSession.Snapshot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Player-owned server forging session snapshot. The input stack remains server-side.
 */
public final class ForgingSessionData {
    public static final MapCodec<ForgingSessionData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("blueprint").forGetter(ForgingSessionData::blueprint),
            ForgingPlan.CODEC.optionalFieldOf("plan").forGetter(ForgingSessionData::plan),
            Snapshot.CODEC.optionalFieldOf("session").forGetter(ForgingSessionData::session),
            ItemStack.CODEC.optionalFieldOf("input").forGetter(ForgingSessionData::input),
            Identifier.CODEC.optionalFieldOf("result").forGetter(ForgingSessionData::result),
            QualityThreshold.CODEC.listOf().optionalFieldOf("quality_by_extra_steps", List.of()).forGetter(ForgingSessionData::qualityByExtraSteps),
            FailureSettlement.CODEC.codec().optionalFieldOf("failure_settlement", FailureSettlement.destroyInput()).forGetter(ForgingSessionData::failureSettlement)
    ).apply(instance, ForgingSessionData::decode));
    public static final Codec<ForgingSessionData> CODEC = MAP_CODEC.codec();
    private Identifier blueprint;
    private ForgingPlan plan;
    private Snapshot session;
    private ItemStack input;
    private Identifier result;
    private List<QualityThreshold> qualityByExtraSteps;
    private FailureSettlement failureSettlement;

    public ForgingSessionData() {
        this.failureSettlement = FailureSettlement.destroyInput();
    }

    private ForgingSessionData(Optional<Identifier> blueprint, Optional<ForgingPlan> plan, Optional<Snapshot> session,
                               Optional<ItemStack> input, Optional<Identifier> result,
                               List<QualityThreshold> qualityByExtraSteps,
                               FailureSettlement failureSettlement) {
        this.blueprint = blueprint.orElse(null);
        this.plan = plan.orElse(null);
        this.session = session.orElse(null);
        this.input = input.map(ItemStack::copy).orElse(null);
        this.result = result.orElse(null);
        this.qualityByExtraSteps = List.copyOf(qualityByExtraSteps);
        this.failureSettlement = failureSettlement;
        if ((this.blueprint == null || this.plan == null || this.session == null || this.input == null || this.result == null) && this.blueprint != null) {
            throw new IllegalArgumentException("Incomplete forging session attachment");
        }
    }

    private static ForgingSessionData decode(Optional<Identifier> blueprint, Optional<ForgingPlan> plan, Optional<Snapshot> session,
                                             Optional<ItemStack> input, Optional<Identifier> result, List<QualityThreshold> qualityByExtraSteps,
                                             FailureSettlement failureSettlement) {
        return new ForgingSessionData(blueprint, plan, session, input, result, qualityByExtraSteps, failureSettlement);
    }

    public Optional<Identifier> blueprint() {
        return Optional.ofNullable(this.blueprint);
    }

    public Optional<ForgingPlan> plan() {
        return Optional.ofNullable(this.plan);
    }

    public Optional<Snapshot> session() {
        return Optional.ofNullable(this.session);
    }

    public Optional<ItemStack> input() {
        return Optional.ofNullable(this.input).map(ItemStack::copy);
    }

    public Optional<Identifier> result() {
        return Optional.ofNullable(this.result);
    }

    public List<QualityThreshold> qualityByExtraSteps() {
        return this.qualityByExtraSteps;
    }

    public FailureSettlement failureSettlement() {
        return this.failureSettlement;
    }

    public boolean active() {
        return this.blueprint != null;
    }

    public void start(Identifier id, ForgingPlan plan, ForgingSession value, ItemStack input, Identifier result,
                      List<QualityThreshold> qualityByExtraSteps,
                      FailureSettlement failureSettlement) {
        if (this.active()) throw new IllegalStateException("Forging session already active");
        this.blueprint = id;
        this.plan = plan;
        this.session = value.snapshot();
        this.input = input.copy();
        this.result = result;
        this.qualityByExtraSteps = List.copyOf(qualityByExtraSteps);
        this.failureSettlement = failureSettlement;
    }

    public void update(ForgingSession value) {
        this.session = value.snapshot();
    }

    public Identifier qualityFor(int extraSteps) {
        if (extraSteps < 0) throw new IllegalArgumentException("extraSteps must be non-negative");
        return this.qualityByExtraSteps.stream().filter(entry -> extraSteps <= entry.maxExtraSteps()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Forging quality snapshot has no terminal threshold")).quality();
    }

    public void clear() {
        this.blueprint = null;
        this.plan = null;
        this.session = null;
        this.input = null;
        this.result = null;
        this.qualityByExtraSteps = List.of();
        this.failureSettlement = FailureSettlement.destroyInput();
    }
}
