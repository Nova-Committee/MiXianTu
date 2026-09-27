package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.recipe.AlchemyRecipe;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.Parameters;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Completion, events and the GUI read this recipe snapshot, never the live recipe manager.
public final class AlchemySession {
    private final Identifier recipeId;
    private final AlchemyRecipe recipe;
    private final Parameters parameters;
    private final List<ItemStack> successOutputs;
    private final List<ItemStack> failureOutputs;
    private final UUID operator;
    private final AlchemyMixture mixture;
    private AlchemyPhase phase;
    private long remainingTicks;
    private int badTicks;
    private @Nullable AlchemyFailure failure;
    private boolean settled;
    private final List<ItemStack> pending = new ArrayList<>(4);

    private AlchemySession(Identifier recipeId, AlchemyRecipe recipe, Parameters parameters,
                           List<ItemStack> successOutputs, List<ItemStack> failureOutputs,
                           UUID operator, AlchemyMixture mixture, AlchemyPhase phase, long remainingTicks, int badTicks,
                           @Nullable AlchemyFailure failure, boolean settled, List<ItemStack> pending) {
        this.recipeId = recipeId;
        this.recipe = recipe;
        this.parameters = parameters;
        this.successOutputs = copy(successOutputs);
        this.failureOutputs = copy(failureOutputs);
        this.operator = operator;
        this.mixture = mixture;
        this.phase = phase;
        this.remainingTicks = remainingTicks;
        this.badTicks = badTicks;
        this.failure = failure;
        this.settled = settled;
        pending.forEach(stack -> this.pending.add(stack.copy()));
    }

    public static AlchemySession start(Identifier recipeId, AlchemyRecipe recipe, Parameters parameters,
                                       List<ItemStack> success, List<ItemStack> failure, UUID operator, AlchemyMixture mixture) {
        return new AlchemySession(recipeId, recipe, parameters, success, failure, operator, mixture,
                AlchemyPhase.WARMING, parameters.durationTicks(), 0, null, false, List.of());
    }

    public static AlchemySession restore(Snapshot snapshot) {
        return new AlchemySession(snapshot.recipeId(), snapshot.recipe(), snapshot.parameters(), snapshot.successOutputs(), snapshot.failureOutputs(),
                snapshot.operator(), snapshot.mixture(), snapshot.phase(), snapshot.remainingTicks(), snapshot.badTicks(),
                snapshot.failure().orElse(null), snapshot.settled(), snapshot.pending());
    }

    public Snapshot snapshot() {
        return new Snapshot(this.recipeId, this.recipe, this.parameters,
                copy(this.successOutputs), copy(this.failureOutputs), this.operator, this.mixture, this.phase,
                this.remainingTicks, this.badTicks, this.failure(), this.settled, copy(this.pending));
    }

    public Identifier recipeId() {
        return this.recipeId;
    }

    public AlchemyRecipe recipe() {
        return this.recipe;
    }

    public Parameters parameters() {
        return this.parameters;
    }

    public double frozenTarget() {
        return this.parameters.targetTemperature();
    }

    public double frozenTolerance() {
        return this.parameters.temperatureTolerance();
    }

    public long totalTicks() {
        return this.parameters.durationTicks();
    }

    public long remainingTicks() {
        return this.remainingTicks;
    }

    public int maxBadTicks() {
        return this.parameters.maxBadTicks();
    }

    public int badTicks() {
        return this.badTicks;
    }

    public boolean failed() {
        return this.failure != null;
    }

    public Optional<AlchemyFailure> failure() {
        return Optional.ofNullable(this.failure);
    }

    public boolean settled() {
        return this.settled;
    }

    public UUID operator() {
        return this.operator;
    }

    public AlchemyMixture mixture() {
        return this.mixture;
    }

    public AlchemyPhase phase() {
        return this.phase;
    }

    public boolean inRange(double temperature) {
        return temperature >= this.frozenTarget() - this.frozenTolerance() && temperature <= this.frozenTarget() + this.frozenTolerance();
    }

    public void enterRunning() {
        if (this.phase == AlchemyPhase.WARMING) this.phase = AlchemyPhase.RUNNING;
    }

    public void tickRunning(double temperature) {
        if (this.phase != AlchemyPhase.RUNNING || this.remainingTicks <= 0L || this.failed()) return;
        this.remainingTicks--;
        if (!this.inRange(temperature)) {
            if (this.badTicks >= this.maxBadTicks()) this.failure = AlchemyFailure.TEMPERATURE;
            if (this.badTicks < Integer.MAX_VALUE) this.badTicks++;
        }
    }

    public boolean due() {
        return this.failed() || this.remainingTicks <= 0L;
    }

    public void generatePending() {
        if (this.phase == AlchemyPhase.READY || this.settled) return;
        this.pending.addAll(copy(this.failed() ? this.failureOutputs : this.successOutputs));
        this.phase = AlchemyPhase.READY;
    }

    public void scrap(AlchemyFailure reason) {
        if (this.phase == AlchemyPhase.READY || this.settled) return;
        this.failure = reason;
        this.generatePending();
    }

    public List<ItemStack> pendingOutputs() {
        return copy(this.pending);
    }

    // The service only reads this list; event and public callers receive independent stack copies.
    List<ItemStack> pendingItems() {
        return this.pending;
    }

    public void clearPending() {
        this.pending.clear();
    }

    public void markSettled() {
        this.settled = true;
    }

    public EntityAction action() {
        return this.failed() ? this.recipe.failureAction() : this.recipe.successAction();
    }

    public BlockAction blockAction() {
        return this.failed() ? this.recipe.failureBlockAction() : this.recipe.successBlockAction();
    }

    private static List<ItemStack> copy(List<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) if (!stack.isEmpty()) result.add(stack.copy());
        return result;
    }

    public record Snapshot(Identifier recipeId, AlchemyRecipe recipe, Parameters parameters,
                           List<ItemStack> successOutputs, List<ItemStack> failureOutputs,
                           UUID operator, AlchemyMixture mixture, AlchemyPhase phase, long remainingTicks, int badTicks,
                           Optional<AlchemyFailure> failure, boolean settled, List<ItemStack> pending) {
        public static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                MiscCodecs.pair(Identifier.CODEC.fieldOf("recipe"), AlchemyRecipe.CODEC.codec().fieldOf("frozen_recipe"))
                        .forGetter(snapshot -> Pair.of(snapshot.recipeId(), snapshot.recipe())),
                Parameters.CODEC.fieldOf("parameters").forGetter(Snapshot::parameters),
                MiscCodecs.pair(ItemStack.CODEC.listOf(1, 4).fieldOf("success"), ItemStack.CODEC.listOf(0, 4).fieldOf("failure_outputs"))
                        .forGetter(snapshot -> Pair.of(snapshot.successOutputs(), snapshot.failureOutputs())),
                UUIDUtil.CODEC.fieldOf("operator").forGetter(Snapshot::operator),
                AlchemyMixture.CODEC.fieldOf("mixture").forGetter(Snapshot::mixture),
                AlchemyPhase.CODEC.fieldOf("phase").forGetter(Snapshot::phase),
                Codec.LONG.fieldOf("remaining").forGetter(Snapshot::remainingTicks),
                Codec.INT.fieldOf("bad_ticks").forGetter(Snapshot::badTicks),
                AlchemyFailure.CODEC.lenientOptionalFieldOf("failure").forGetter(Snapshot::failure),
                Codec.BOOL.lenientOptionalFieldOf("settled", false).forGetter(Snapshot::settled),
                ItemStack.OPTIONAL_CODEC.listOf(0, 4).lenientOptionalFieldOf("pending", List.of()).forGetter(Snapshot::pending)
        ).apply(i, (identity, parameters, outputs, operator, mixture, phase, remaining, bad, failure, settled, pending) ->
                new Snapshot(identity.getFirst(), identity.getSecond(), parameters,
                        outputs.getFirst(), outputs.getSecond(), operator, mixture, phase, remaining, bad, failure, settled, pending)));
    }
}
