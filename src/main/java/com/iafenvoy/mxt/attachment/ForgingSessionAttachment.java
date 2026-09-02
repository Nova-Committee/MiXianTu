package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint.FailureSettlement;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint.QualityThreshold;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.runtime.forging.ForgingPlan;
import com.iafenvoy.mxt.runtime.forging.ForgingSession;
import com.iafenvoy.mxt.runtime.forging.ForgingSession.Snapshot;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Player-owned server forging session snapshot. The input stack remains server-side.
 */
public final class ForgingSessionAttachment extends ShouldSyncAttachment {
    public static final MapCodec<ForgingSessionAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ForgingBlueprint.CODEC.optionalFieldOf("blueprint").forGetter(ForgingSessionAttachment::blueprint),
            ForgingPlan.CODEC.optionalFieldOf("plan").forGetter(ForgingSessionAttachment::plan),
            Snapshot.CODEC.optionalFieldOf("session").forGetter(ForgingSessionAttachment::session),
            ItemStack.CODEC.optionalFieldOf("input").forGetter(ForgingSessionAttachment::input),
            Identifier.CODEC.optionalFieldOf("result").forGetter(ForgingSessionAttachment::result),
            QualityThreshold.CODEC.listOf().optionalFieldOf("quality_by_extra_steps", List.of()).forGetter(ForgingSessionAttachment::qualityByExtraSteps),
            FailureSettlement.CODEC.codec().optionalFieldOf("failure_settlement", FailureSettlement.destroyInput()).forGetter(ForgingSessionAttachment::failureSettlement)
    ).apply(i, ForgingSessionAttachment::new));
    private Holder<ForgingBlueprint> blueprint;
    private ForgingPlan plan;
    private Snapshot session;
    private ItemStack input;
    private Identifier result;
    private List<QualityThreshold> qualityByExtraSteps;
    private FailureSettlement failureSettlement;

    public ForgingSessionAttachment() {
        this.failureSettlement = FailureSettlement.destroyInput();
    }

    private ForgingSessionAttachment(Optional<Holder<ForgingBlueprint>> blueprint, Optional<ForgingPlan> plan, Optional<Snapshot> session, Optional<ItemStack> input, Optional<Identifier> result, List<QualityThreshold> qualityByExtraSteps, FailureSettlement failureSettlement) {
        this.blueprint = blueprint.orElse(null);
        this.plan = plan.orElse(null);
        this.session = session.orElse(null);
        this.input = input.map(ItemStack::copy).orElse(null);
        this.result = result.orElse(null);
        this.qualityByExtraSteps = new LinkedList<>(qualityByExtraSteps);
        this.failureSettlement = failureSettlement;
        this.markDirty();
        if ((this.blueprint == null || this.plan == null || this.session == null || this.input == null || this.result == null) && this.blueprint != null) {
            throw new IllegalArgumentException("Incomplete forging session attachment");
        }
    }

    public Optional<Holder<ForgingBlueprint>> blueprint() {
        return Optional.ofNullable(this.blueprint);
    }

    public Optional<ForgingPlan> plan() {
        return Optional.ofNullable(this.plan);
    }

    public Optional<Snapshot> session() {
        return Optional.ofNullable(this.session);
    }

    public Optional<ItemStack> input() {
        return Optional.ofNullable(this.input);
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

    public void start(Holder<ForgingBlueprint> blueprint, ForgingPlan plan, ForgingSession value, ItemStack input, Identifier result,
                      List<QualityThreshold> qualityByExtraSteps,
                      FailureSettlement failureSettlement) {
        if (this.active()) throw new IllegalStateException("Forging session already active");
        this.blueprint = blueprint;
        this.plan = plan;
        this.session = value.snapshot();
        this.input = input.copy();
        this.result = result;
        this.qualityByExtraSteps = new LinkedList<>(qualityByExtraSteps);
        this.failureSettlement = failureSettlement;
    }

    public void update(ForgingSession value) {
        this.session = value.snapshot();
        this.markDirty();
    }

    public Holder<ItemQuality> qualityFor(int extraSteps) {
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
        this.qualityByExtraSteps = new LinkedList<>();
        this.failureSettlement = FailureSettlement.destroyInput();
        this.markDirty();
    }
}
