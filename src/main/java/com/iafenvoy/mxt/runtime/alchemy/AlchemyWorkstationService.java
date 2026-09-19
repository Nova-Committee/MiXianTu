package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.alchemy.AlchemyRecipe;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtCriteriaTriggers;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession.Failure;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession.Snapshot;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession.StartResult;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.TickResult.State;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * Server-side material locking and completion adapter for {@link AlchemyWorkstationState}. The already
 * resolved {@link RecipeHolder} is passed through so craft events can expose it instead of an id pair.
 */
public final class AlchemyWorkstationService {
    private AlchemyWorkstationService() {
    }

    public static StartResult start(AlchemyWorkstationState state, RecipeHolder<com.iafenvoy.mxt.recipe.AlchemyRecipe> holder,
                                    int furnaceTier, FormulaContext context) {
        if (state.active()) return StartResult.rejected(Failure.INPUTS);
        StartResult result = AlchemySession.start(holder, furnaceTier, itemIds(state.inputs()), context, inputModifier(state.inputs(), context));
        if (result.started()) state.lock(result.session());
        return result;
    }

    /**
     * The alchemy modifier of a batch's own input: the lowest modifier among the ingredient stacks that
     * are in the workstation when the batch starts, and {@link ItemQualityService#DEFAULT_MODIFIER} when
     * none of them resolves a quality. The lowest, because a brew is only as good as its worst ingredient,
     * and because one graded herb must not be made to read as though the whole recipe were graded. The
     * stacks are read here rather than at completion because {@link AlchemyWorkstationState#lock} releases
     * them as soon as the session is stored; see {@link AlchemySession#start} for what the modifier
     * settles. The quality lookup is the server-side one, which is the only side a batch can start on.
     */
    static double inputModifier(List<ItemStack> inputs, FormulaContext context) {
        double modifier = ItemQualityService.DEFAULT_MODIFIER;
        boolean graded = false;
        for (ItemStack stack : inputs) {
            Optional<Holder<ItemQuality>> quality = ItemQualityService.find(stack);
            if (quality.isEmpty()) continue;
            double value = ItemQualityService.modifier(quality.orElseThrow(), ItemQuality::alchemyModifier, context);
            modifier = graded ? Math.min(modifier, value) : value;
            graded = true;
        }
        return modifier;
    }

    /**
     * Position-aware variant for concrete alchemy blocks; the plain overload skips the aura check.
     */
    public static StartResult start(Level level, BlockPos pos, AlchemyWorkstationState state, RecipeHolder<com.iafenvoy.mxt.recipe.AlchemyRecipe> holder,
                                    int furnaceTier, FormulaContext context) {
        AlchemyRecipe recipe = holder.value().definition();
        AuraResult aura = AuraService.getPositionAura(level, pos);
        // A zone can declare itself an alchemy environment, and then the place itself stands in for the aura the
        // recipe asks for: the flag is a plain yes/no with no magnitude to scale a pool by, so the only honest
        // reading is that the environment requirement is already answered. Without the flag the recipe's own
        // minimum is compared against the zone's pools exactly as before.
        boolean auraMet = aura.rules().alchemyEnvBonus() || recipe.minimumAura().entrySet().stream().allMatch(entry -> {
            double minimum = entry.getValue().evaluate(context);
            return Double.isFinite(minimum) && minimum >= 0.0D && aura.pool(entry.getKey()).amount() >= minimum;
        });
        if (!auraMet) return StartResult.rejected(Failure.ENVIRONMENT);
        return start(state, holder, furnaceTier, context);
    }

    /**
     * Restores the saved session, advances it once, and appends produced stacks at most once.
     */
    public static TickResult tick(AlchemyWorkstationState state, RecipeHolder<com.iafenvoy.mxt.recipe.AlchemyRecipe> holder,
                                  double temperature, FormulaContext context) {
        Snapshot snapshot = state.session().orElse(null);
        if (snapshot == null || snapshot.complete()) return TickResult.idle();
        AlchemySession session = AlchemySession.restore(snapshot, holder);
        AlchemySession.TickResult result = session.tick(temperature, context);
        state.update(session);
        if (!result.finished()) return TickResult.running(result.remainingTicks(), result.spoiled());
        List<ItemStack> outputs = toStacks(result.outputs());
        if (outputs.size() != result.outputs().size()) return TickResult.invalidOutput(result.spoiled());
        state.addOutputs(outputs);
        return TickResult.finished(outputs, result.spoiled());
    }

    /**
     * Completes an alchemy tick and applies the recipe's block-side behavior at the workstation.
     */
    public static TickResult tick(Level level, BlockPos pos, AlchemyWorkstationState state, RecipeHolder<com.iafenvoy.mxt.recipe.AlchemyRecipe> holder,
                                  double temperature, FormulaContext context) {
        TickResult result = tick(state, holder, temperature, context);
        if (result.state() == State.FINISHED) {
            AlchemyRecipe recipe = holder.value().definition();
            BlockAction action = result.spoiled() ? recipe.failureBlockAction() : recipe.successBlockAction();
            action.execute(level, pos, context);
        }
        return result;
    }

    /**
     * Owner-aware adapter for workstation block entities that can attribute a successful batch.
     */
    public static TickResult tick(ServerPlayer owner, AlchemyWorkstationState state, RecipeHolder<com.iafenvoy.mxt.recipe.AlchemyRecipe> holder,
                                  double temperature, FormulaContext context) {
        AlchemyRecipe recipe = holder.value().definition();
        TickResult result = tick(state, holder, temperature, context);
        if (result.state() == State.FINISHED) {
            EntityAction action = result.spoiled() ? recipe.failureAction() : recipe.successAction();
            action.execute(owner, context);
        }
        if (result.state() == State.FINISHED && !result.spoiled()) {
            MxtCriteriaTriggers.ALCHEMY.get().trigger(owner, holder.id().identifier());
        }
        return result;
    }

    private static List<Identifier> itemIds(List<ItemStack> stacks) {
        List<Identifier> result = new ArrayList<>();
        for (ItemStack stack : stacks) {
            Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            for (int count = 0; count < stack.getCount(); count++) result.add(id);
        }
        return result;
    }

    private static List<ItemStack> toStacks(List<Identifier> ids) {
        List<ItemStack> result = new ArrayList<>();
        for (Identifier id : ids)
            BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).ifPresent(result::add);
        return result;
    }

    public record TickResult(State state, long remainingTicks, boolean spoiled, List<ItemStack> outputs) {
        public TickResult {
            outputs = new LinkedList<>(outputs);
        }

        static TickResult idle() {
            return new TickResult(State.IDLE, 0L, false, List.of());
        }

        static TickResult running(long remaining, boolean spoiled) {
            return new TickResult(State.RUNNING, remaining, spoiled, List.of());
        }

        static TickResult finished(List<ItemStack> outputs, boolean spoiled) {
            return new TickResult(State.FINISHED, 0L, spoiled, outputs);
        }

        static TickResult invalidOutput(boolean spoiled) {
            return new TickResult(State.INVALID_OUTPUT, 0L, spoiled, List.of());
        }

        public enum State {IDLE, RUNNING, FINISHED, INVALID_OUTPUT}
    }
}
