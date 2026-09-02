package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.alchemy.AlchemyRecipe;
import com.iafenvoy.mxt.registry.MxtCriteriaTriggers;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession.Failure;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession.Snapshot;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession.StartResult;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.TickResult.State;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.CollectionHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Server-side material locking and completion adapter for {@link AlchemyWorkstationState}.
 */
public final class AlchemyWorkstationService {
    private AlchemyWorkstationService() {
    }

    public static StartResult start(AlchemyWorkstationState state, Identifier recipeId,
                                    AlchemyRecipe recipe, int furnaceTier, FormulaContext context) {
        if (state.active()) return StartResult.rejected(Failure.INPUTS);
        StartResult result = AlchemySession.start(recipeId, recipe, furnaceTier, itemIds(state.inputs()), context);
        if (result.started()) state.lock(result.session());
        return result;
    }

    /**
     * Position-aware variant for concrete alchemy blocks.
     */
    public static StartResult start(Level level, BlockPos pos, AlchemyWorkstationState state, Identifier recipeId,
                                    AlchemyRecipe recipe, int furnaceTier, FormulaContext context) {
        AuraResult aura = AuraService.getPositionAura(level, pos);
        boolean auraMet = recipe.minimumAura().entrySet().stream().allMatch(entry -> {
            double minimum = entry.getValue().evaluate(context);
            return Double.isFinite(minimum) && minimum >= 0.0D && aura.pool(entry.getKey()).amount() >= minimum;
        });
        if (!auraMet || !CollectionHelper.containsAllFast(aura.auraKinds(), recipe.auraKinds()))
            return StartResult.rejected(Failure.ENVIRONMENT);
        return start(state, recipeId, recipe, furnaceTier, context);
    }

    /**
     * Restores the saved session, advances it once, and appends produced stacks exactly once.
     */
    public static TickResult tick(AlchemyWorkstationState state, AlchemyRecipe recipe,
                                  double temperature, FormulaContext context) {
        Snapshot snapshot = state.session().orElse(null);
        if (snapshot == null || snapshot.complete()) return TickResult.idle();
        AlchemySession session = AlchemySession.restore(snapshot, recipe);
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
    public static TickResult tick(Level level, BlockPos pos, AlchemyWorkstationState state, AlchemyRecipe recipe,
                                  double temperature, FormulaContext context) {
        TickResult result = tick(state, recipe, temperature, context);
        if (result.state() == State.FINISHED) {
            BlockAction action = result.spoiled() ? recipe.failureBlockAction() : recipe.successBlockAction();
            action.execute(level, pos, context);
        }
        return result;
    }

    /**
     * Owner-aware adapter for workstation block entities that can attribute a successful batch.
     */
    public static TickResult tick(ServerPlayer owner, AlchemyWorkstationState state, AlchemyRecipe recipe,
                                  double temperature, FormulaContext context) {
        Identifier recipeId = state.session().map(Snapshot::recipe).orElse(null);
        TickResult result = tick(state, recipe, temperature, context);
        if (result.state() == State.FINISHED) {
            EntityAction action = result.spoiled() ? recipe.failureAction() : recipe.successAction();
            action.execute(owner, context);
        }
        if (result.state() == State.FINISHED && !result.spoiled() && recipeId != null) {
            MxtCriteriaTriggers.ALCHEMY.get().trigger(owner, recipeId);
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
