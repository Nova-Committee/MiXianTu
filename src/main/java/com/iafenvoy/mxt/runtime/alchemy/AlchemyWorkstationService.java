package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.data.alchemy.AlchemyRecipeDefinition;
import com.iafenvoy.mxt.registry.MxtCriteriaTriggers;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession.Failure;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession.Snapshot;
import com.iafenvoy.mxt.runtime.alchemy.AlchemySession.StartResult;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyWorkstationService.TickResult.State;
import com.iafenvoy.mxt.runtime.behavior.BehaviorContext;
import com.iafenvoy.mxt.runtime.behavior.BehaviorContext.Kind;
import com.iafenvoy.mxt.runtime.behavior.DomainBehaviorService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side material locking and completion adapter for {@link AlchemyWorkstationState}.
 */
public final class AlchemyWorkstationService {
    private AlchemyWorkstationService() {
    }

    public static StartResult start(AlchemyWorkstationState state, Identifier recipeId,
                                                   AlchemyRecipeDefinition recipe, int furnaceTier, FormulaContext context) {
        if (state.active()) return StartResult.rejected(Failure.INPUTS);
        StartResult result = AlchemySession.start(recipeId, recipe, furnaceTier, itemIds(state.inputs()), context);
        if (result.started()) state.lock(result.session());
        return result;
    }

    /**
     * Restores the saved session, advances it once, and appends produced stacks exactly once.
     */
    public static TickResult tick(AlchemyWorkstationState state, AlchemyRecipeDefinition recipe,
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
        DomainBehaviorService.execute(MxtTypeRegistries.ALCHEMY_OUTCOME_BEHAVIOR, result.spoiled() ? recipe.failureBehavior() : recipe.successBehavior(),
                BehaviorContext.of(result.spoiled() ? Kind.ALCHEMY_FAILURE : Kind.ALCHEMY_SUCCESS,
                        snapshot.recipe(), null, context, !result.spoiled()));
        return TickResult.finished(outputs, result.spoiled());
    }

    /**
     * Owner-aware adapter for workstation block entities that can attribute a successful batch.
     */
    public static TickResult tick(ServerPlayer owner, AlchemyWorkstationState state, AlchemyRecipeDefinition recipe,
                                  double temperature, FormulaContext context) {
        Identifier recipeId = state.session().map(Snapshot::recipe).orElse(null);
        TickResult result = tick(state, recipe, temperature, context);
        if (result.state() == State.FINISHED && !result.spoiled() && recipeId != null) {
            MxtCriteriaTriggers.ALCHEMY.get().trigger(owner, recipeId);
        }
        return result;
    }

    private static List<Identifier> itemIds(List<ItemStack> stacks) {
        List<Identifier> result = new ArrayList<>();
        for (ItemStack stack : stacks) {
            Identifier id = ItemBindingService.identifier(stack);
            for (int count = 0; count < stack.getCount(); count++) result.add(id);
        }
        return List.copyOf(result);
    }

    private static List<ItemStack> toStacks(List<Identifier> ids) {
        List<ItemStack> result = new ArrayList<>();
        for (Identifier id : ids)
            ItemBindingService.create(id)
                    .or(() -> BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new))
                    .ifPresent(result::add);
        return List.copyOf(result);
    }

    public record TickResult(State state, long remainingTicks, boolean spoiled, List<ItemStack> outputs) {
        static TickResult idle() {
            return new TickResult(State.IDLE, 0L, false, List.of());
        }

        static TickResult running(long remaining, boolean spoiled) {
            return new TickResult(State.RUNNING, remaining, spoiled, List.of());
        }

        static TickResult finished(List<ItemStack> outputs, boolean spoiled) {
            return new TickResult(State.FINISHED, 0L, spoiled, List.copyOf(outputs));
        }

        static TickResult invalidOutput(boolean spoiled) {
            return new TickResult(State.INVALID_OUTPUT, 0L, spoiled, List.of());
        }

        public enum State {IDLE, RUNNING, FINISHED, INVALID_OUTPUT}
    }
}
