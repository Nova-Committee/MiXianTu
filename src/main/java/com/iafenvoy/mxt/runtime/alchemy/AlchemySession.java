package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.data.alchemy.AlchemyRecipe;
import com.iafenvoy.mxt.event.AlchemyCraftEvent.Post;
import com.iafenvoy.mxt.event.AlchemyCraftEvent.Pre;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Server-side furnace session; the UI and block inventory adapt to this state instead of owning recipe
 * logic. The {@link RecipeHolder} is kept for event reporting, while snapshots persist only the recipe id.
 */
public final class AlchemySession {
    private final RecipeHolder<com.iafenvoy.mxt.recipe.AlchemyRecipe> holder;
    private long remainingTicks;
    private boolean spoiled;
    private boolean complete;

    private AlchemySession(RecipeHolder<com.iafenvoy.mxt.recipe.AlchemyRecipe> holder, long remainingTicks, boolean spoiled, boolean complete) {
        this.holder = holder;
        this.remainingTicks = remainingTicks;
        this.spoiled = spoiled;
        this.complete = complete;
    }

    public static StartResult start(RecipeHolder<com.iafenvoy.mxt.recipe.AlchemyRecipe> holder, int furnaceTier,
                                    List<Identifier> inputs, FormulaContext context) {
        return start(holder, furnaceTier, inputs, context, ItemQualityService.DEFAULT_MODIFIER);
    }

    /**
     * Starts a batch. The duration is read through the alchemy modifier of the batch's own ingredients, so
     * a recipe brewed from a quality-bearing herb finishes in less time: the modifier divides the declared
     * duration, because it is declared as an improvement (a grade above one is presented as raising the
     * alchemy effect) and the brewing time is the alchemy figure this settlement owns. The duration rather
     * than the success window, because the ingredients are released the moment the session locks - the
     * workstation clears its input list - while the temperature tolerance is re-read from the recipe on
     * every tick, so only a value settled here can still see what was put in. A modifier of exactly one,
     * which is the codec default and therefore every existing quality, leaves the recipe's own duration.
     */
    public static StartResult start(RecipeHolder<com.iafenvoy.mxt.recipe.AlchemyRecipe> holder, int furnaceTier,
                                    List<Identifier> inputs, FormulaContext context, double alchemyModifier) {
        AlchemyRecipe recipe = holder.value().definition();
        if (NeoForge.EVENT_BUS.post(new Pre(holder, inputs)).isCanceled())
            return StartResult.rejected(Failure.CANCELLED);
        if (furnaceTier < recipe.minimumFurnaceTier()) return StartResult.rejected(Failure.FURNACE_TIER);
        if (!sameMultiset(recipe.inputs(), inputs)) return StartResult.rejected(Failure.INPUTS);
        double duration = effectiveDuration(recipe.duration().evaluate(context), alchemyModifier);
        if (!Double.isFinite(duration) || duration <= 0.0D || duration > Long.MAX_VALUE)
            return StartResult.rejected(Failure.INVALID_FORMULA);
        return StartResult.started(new AlchemySession(holder, Math.max(1L, Math.round(duration)), false, false));
    }

    /**
     * The duration a batch actually runs for. A scaled duration that is not a positive finite number
     * leaves the declared one alone, so an unusable modifier can only ever mean "no change" and never a
     * batch that cannot be started. The result is deliberately not rounded here: the caller rounds the
     * tick count, and rounding twice would answer a question the recipe did not ask.
     */
    static double effectiveDuration(double duration, double alchemyModifier) {
        if (alchemyModifier == ItemQualityService.DEFAULT_MODIFIER) return duration;
        double scaled = duration / alchemyModifier;
        return Double.isFinite(scaled) && scaled > 0.0D ? scaled : duration;
    }

    /**
     * Restores runtime state only; the caller must resolve the recipe holder from the snapshot id.
     */
    public static AlchemySession restore(Snapshot snapshot, RecipeHolder<com.iafenvoy.mxt.recipe.AlchemyRecipe> holder) {
        if (snapshot.remainingTicks() < 0L)
            throw new IllegalArgumentException("Alchemy snapshot has negative remaining ticks");
        if (!holder.id().identifier().equals(snapshot.recipe()))
            throw new IllegalArgumentException("Alchemy snapshot recipe does not match the resolved recipe holder");
        return new AlchemySession(holder, snapshot.remainingTicks(), snapshot.spoiled(), snapshot.complete());
    }

    public Snapshot snapshot() {
        return new Snapshot(this.holder.id().identifier(), this.remainingTicks, this.spoiled, this.complete);
    }

    /**
     * Returns an output once, after the final tick. Temperature outside the tolerance makes the batch fail.
     */
    public TickResult tick(double temperature, FormulaContext context) {
        if (this.complete) return TickResult.idle();
        AlchemyRecipe recipe = this.holder.value().definition();
        double target = recipe.targetTemperature().evaluate(context);
        double tolerance = recipe.temperatureTolerance().evaluate(context);
        if (!Double.isFinite(target) || !Double.isFinite(tolerance) || tolerance < 0.0D) {
            this.spoiled = true;
        } else if (Math.abs(temperature - target) > tolerance) {
            this.spoiled = true;
        }
        this.remainingTicks--;
        if (this.remainingTicks > 0L) return TickResult.running(this.remainingTicks, this.spoiled);
        this.complete = true;
        List<Identifier> outputs = this.spoiled ? recipe.failureOutputs() : recipe.successOutputs();
        NeoForge.EVENT_BUS.post(new Post(this.holder, this.spoiled, outputs));
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
        public static final Codec<Snapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Identifier.CODEC.fieldOf("recipe").forGetter(Snapshot::recipe), Codec.LONG.fieldOf("remaining_ticks").forGetter(Snapshot::remainingTicks),
                Codec.BOOL.optionalFieldOf("spoiled", false).forGetter(Snapshot::spoiled), Codec.BOOL.optionalFieldOf("complete", false).forGetter(Snapshot::complete)
        ).apply(i, Snapshot::new));

        public Snapshot {
            if (remainingTicks < 0L)
                throw new IllegalArgumentException("Alchemy snapshot has negative remaining ticks");
        }
    }
}
