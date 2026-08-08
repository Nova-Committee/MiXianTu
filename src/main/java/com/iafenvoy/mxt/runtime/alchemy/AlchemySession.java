package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.alchemy.AlchemyRecipe;
import com.iafenvoy.mxt.event.AlchemyCraftEvent.Post;
import com.iafenvoy.mxt.event.AlchemyCraftEvent.Pre;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Server-side furnace session. UI and block inventory adapt to this state instead of owning recipe logic.
 */
public final class AlchemySession {
    private final Identifier recipeId;
    private final AlchemyRecipe recipe;
    private long remainingTicks;
    private boolean spoiled;
    private boolean complete;

    private AlchemySession(Identifier recipeId, AlchemyRecipe recipe, long remainingTicks, boolean spoiled, boolean complete) {
        this.recipeId = recipeId;
        this.recipe = recipe;
        this.remainingTicks = remainingTicks;
        this.spoiled = spoiled;
        this.complete = complete;
    }

    public static StartResult start(AlchemyRecipe recipe, int furnaceTier, List<Identifier> inputs, FormulaContext context) {
        return start(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "unknown"), recipe, furnaceTier, inputs, context);
    }

    public static StartResult start(Identifier recipeId, AlchemyRecipe recipe, int furnaceTier, List<Identifier> inputs, FormulaContext context) {
        if (NeoForge.EVENT_BUS.post(new Pre(recipeId, recipe, inputs)).isCanceled())
            return StartResult.rejected(Failure.CANCELLED);
        if (furnaceTier < recipe.minimumFurnaceTier()) return StartResult.rejected(Failure.FURNACE_TIER);
        if (!sameMultiset(recipe.inputs(), inputs)) return StartResult.rejected(Failure.INPUTS);
        double duration = recipe.duration().evaluate(context);
        if (!Double.isFinite(duration) || duration <= 0.0D || duration > Long.MAX_VALUE)
            return StartResult.rejected(Failure.INVALID_FORMULA);
        return StartResult.started(new AlchemySession(recipeId, recipe, Math.max(1L, Math.round(duration)), false, false));
    }

    /**
     * Restores only the runtime state; the caller must resolve the datapack recipe by its snapshot ID.
     */
    public static AlchemySession restore(Snapshot snapshot, AlchemyRecipe recipe) {
        if (snapshot.remainingTicks() < 0L)
            throw new IllegalArgumentException("Alchemy snapshot has negative remaining ticks");
        return new AlchemySession(snapshot.recipe(), recipe, snapshot.remainingTicks(), snapshot.spoiled(), snapshot.complete());
    }

    public Snapshot snapshot() {
        return new Snapshot(this.recipeId, this.remainingTicks, this.spoiled, this.complete);
    }

    /**
     * Returns an output once, after the final tick. Temperature outside the tolerance makes the batch fail.
     */
    public TickResult tick(double temperature, FormulaContext context) {
        if (this.complete) return TickResult.idle();
        double target = this.recipe.targetTemperature().evaluate(context);
        double tolerance = this.recipe.temperatureTolerance().evaluate(context);
        if (!Double.isFinite(target) || !Double.isFinite(tolerance) || tolerance < 0.0D) {
            this.spoiled = true;
        } else if (Math.abs(temperature - target) > tolerance) {
            this.spoiled = true;
        }
        this.remainingTicks--;
        if (this.remainingTicks > 0L) return TickResult.running(this.remainingTicks, this.spoiled);
        this.complete = true;
        List<Identifier> outputs = this.spoiled ? this.recipe.failureOutputs() : this.recipe.successOutputs();
        NeoForge.EVENT_BUS.post(new Post(this.recipeId, this.recipe, this.spoiled, outputs));
        return TickResult.finished(outputs, this.spoiled);
    }

    public boolean complete() {
        return this.complete;
    }

    public boolean spoiled() {
        return this.spoiled;
    }

    private static boolean sameMultiset(List<Identifier> expected, List<Identifier> actual) {
        List<Identifier> left = new ArrayList<>(expected);
        List<Identifier> right = new ArrayList<>(actual);
        left.sort(Identifier::compareTo);
        right.sort(Identifier::compareTo);
        return left.equals(right);
    }

    public enum Failure {DISABLED, FURNACE_TIER, INPUTS, ENVIRONMENT, INVALID_FORMULA, CANCELLED}

    public record StartResult(AlchemySession session, Failure failure) {
        static StartResult started(AlchemySession session) {
            return new StartResult(session, null);
        }

        static StartResult rejected(Failure failure) {
            return new StartResult(null, failure);
        }

        public boolean started() {
            return this.session != null;
        }
    }

    public record TickResult(boolean finished, boolean spoiled, long remainingTicks, List<Identifier> outputs) {
        public TickResult {
            outputs = new LinkedList<>(outputs);
        }

        static TickResult idle() {
            return new TickResult(false, false, 0L, List.of());
        }

        static TickResult running(long remaining, boolean spoiled) {
            return new TickResult(false, spoiled, remaining, List.of());
        }

        static TickResult finished(List<Identifier> outputs, boolean spoiled) {
            return new TickResult(true, spoiled, 0L, outputs);
        }
    }

    public record Snapshot(Identifier recipe, long remainingTicks, boolean spoiled, boolean complete) {
        public static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("recipe").forGetter(Snapshot::recipe), Codec.LONG.fieldOf("remaining_ticks").forGetter(Snapshot::remainingTicks),
                Codec.BOOL.optionalFieldOf("spoiled", false).forGetter(Snapshot::spoiled), Codec.BOOL.optionalFieldOf("complete", false).forGetter(Snapshot::complete)
        ).apply(instance, Snapshot::new));

        public Snapshot {
            if (remainingTicks < 0L)
                throw new IllegalArgumentException("Alchemy snapshot has negative remaining ticks");
        }
    }
}
