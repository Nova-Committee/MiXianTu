package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.forging.*;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint.FailureSettlement;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.forging.ForgingService.Failure;
import com.iafenvoy.mxt.runtime.forging.ForgingService.FinishResult;
import com.iafenvoy.mxt.runtime.forging.ForgingService.StartResult;
import com.iafenvoy.mxt.runtime.forging.ForgingService.StrikeResult;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

/**
 * Authoritative forge-table transaction boundary - material matching, tool/blueprint gating, the shared
 * session lifecycle and the strike rate limit - on a {@link ForgingSurface} rather than a block entity type. A
 * placed table holds one session any player standing at it may strike, and starting it stores the exact stacks
 * taken, so a cancel or a failure returns precisely those.
 */
public final class ForgingWorkstationService {
    // Squared distance from the table centre a player must stay within; compared against distanceToSqr.
    public static final double MAX_DISTANCE_SQUARED = 64.0D;

    private ForgingWorkstationService() {
    }

    // ------------------------------------------------------------------ listing

    // Takes the container rather than a ForgingSurface, because the client half of the menu has its own copy
    // of the slot contents.
    public static List<Identifier> selectableBlueprintIds(Container container, Provider access) {
        Set<Identifier> provided = new LinkedHashSet<>();
        for (int index = ForgingSurface.BLUEPRINT_START; index < ForgingSurface.BLUEPRINT_START + ForgingSurface.BLUEPRINT_SLOTS; index++)
            ForgingBindingService.blueprints(access, container.getItem(index))
                    .forEach(blueprint -> provided.add(HolderHelper.id(blueprint)));
        return List.copyOf(provided);
    }

    // In the tool slots' order and then each tool's own declaration order, deduplicated: a second tool can
    // only ever append, never reorder or remove.
    private static List<Identifier> toolMethodIds(Container container, Provider access) {
        Set<Identifier> unlocked = new LinkedHashSet<>();
        for (int index = ForgingSurface.TOOL_START; index < ForgingSurface.TOOL_START + ForgingSurface.TOOL_SLOTS; index++)
            ForgingBindingService.methods(access, container.getItem(index))
                    .forEach(method -> unlocked.add(HolderHelper.id(method)));
        return List.copyOf(unlocked);
    }

    // This blueprint's allowed_methods intersected with the placed tools' methods; the tools' union alone when
    // the blueprint declares nothing or no id is selected.
    public static List<Identifier> availableMethodIds(Container container, Provider access, Identifier blueprintId) {
        List<Identifier> unlocked = toolMethodIds(container, access);
        ForgingBlueprint blueprint = blueprintId == null ? null
                : MxtDatapackRegistries.get(access, MxtResourceKeys.FORGING_BLUEPRINT, blueprintId).orElse(null);
        if (blueprint == null || !blueprint.restrictsMethods()) return unlocked;
        Set<Identifier> allowed = new LinkedHashSet<>();
        blueprint.allowedMethods().stream().forEach(method -> allowed.add(HolderHelper.id(method)));
        return unlocked.stream().filter(allowed::contains).toList();
    }

    // ------------------------------------------------------------------ materials

    // One entry's count is a total and not a share: a blueprint's validation rejects a list that names the
    // same item twice.
    public static int availableCount(Container container, ForgingMaterial entry) {
        int available = 0;
        for (int index = ForgingSurface.INPUT_START; index < ForgingSurface.INPUT_START + ForgingSurface.INPUT_SLOTS; index++) {
            ItemStack stack = container.getItem(index);
            if (entry.matches(stack)) available += stack.getCount();
        }
        return available;
    }

    // Exactly the resolution start performs before it consumes anything, so the screen's greyed button and
    // this refusal cannot diverge.
    public static boolean materialsCovered(Container container, List<ForgingMaterial> requirement) {
        return StartupMaterials.resolve(container, requirement) != null;
    }

    // ------------------------------------------------------------------ cancellation policy

    // The default policy: everything the session took goes back to the input slots.
    public static final ForgingCancellation RETURN_EVERYTHING = (player, surface, state, blueprint) -> returnLocked(player, surface, state);

    private static volatile ForgingCancellation cancellation = RETURN_EVERYTHING;

    // Pass null to restore the default. Deliberately a plain installed value rather than datapack state: a
    // policy in the blueprint format would make every pack carry a field.
    public static void setCancellation(ForgingCancellation policy) {
        cancellation = policy == null ? RETURN_EVERYTHING : policy;
    }

    // ------------------------------------------------------------------ session

    public static StartOutcome start(ServerPlayer player, ForgingSurface surface, Identifier blueprintId) {
        if (!canUse(player, surface)) return new StartOutcome(Failure.OUT_OF_RANGE, false);
        ForgingTableState state = surface.forgingState();
        if (state.active()) return new StartOutcome(Failure.ALREADY_ACTIVE, false);
        Holder<ForgingBlueprint> holder = MxtDatapackRegistries.holder(MxtResourceKeys.FORGING_BLUEPRINT, blueprintId).orElse(null);
        if (holder == null) return new StartOutcome(Failure.DISABLED, false);
        ForgingBlueprint blueprint = holder.value();
        if (!selectableBlueprintIds(surface.forgingContainer(), player.level().registryAccess()).contains(blueprintId))
            return new StartOutcome(Failure.BLUEPRINT_NOT_HELD, false);
        if (!outputEmpty(surface)) return new StartOutcome(Failure.OUTPUT_BLOCKED, false);

        StartupMaterials materials = StartupMaterials.resolve(surface.forgingContainer(), blueprint.input());
        if (materials == null) return new StartOutcome(Failure.INSUFFICIENT_MATERIALS, false);

        RegistryAccess registries = player.level().registryAccess();
        StartResult result = ForgingService.start(player, surface, blueprint, registries);
        if (!result.started()) return new StartOutcome(result.failure(), false);

        materials.consume(surface.forgingContainer());
        state.lock(blueprintId, blueprint.plan(registries), result.session(), materials.consumed(), player.getUUID());
        surface.forgingChanged();
        settleIfComplete(player, surface, state, result.session());
        return new StartOutcome(null, true);
    }

    // A strike that happens also plays the method's sound at the table; a refused one is silent.
    public static StrikeOutcome strike(ServerPlayer player, ForgingSurface surface, Identifier methodId) {
        if (!canUse(player, surface)) return new StrikeOutcome(Failure.OUT_OF_RANGE, false, 0);
        ForgingTableState state = surface.forgingState();
        if (!state.active()) return new StrikeOutcome(Failure.NO_SESSION, false, 0);
        if (!outputEmpty(surface)) return new StrikeOutcome(Failure.OUTPUT_BLOCKED, false, 0);
        Holder<ForgingMethod> method = MxtDatapackRegistries.holder(MxtResourceKeys.FORGING_METHOD, methodId).orElse(null);
        if (method == null) return new StrikeOutcome(Failure.DISABLED, false, 0);
        Identifier blueprintId = state.blueprint().orElse(null);
        if (!availableMethodIds(surface.forgingContainer(), player.level().registryAccess(), blueprintId).contains(methodId))
            return new StrikeOutcome(Failure.METHOD_NOT_AVAILABLE, false, 0);
        ForgingPlan plan = state.plan().orElse(null);
        ForgingSession session = state.session().map(snapshot -> ForgingSession.restore(plan, snapshot)).orElse(null);
        if (session == null || plan == null) return new StrikeOutcome(Failure.NO_SESSION, false, 0);

        long gameTime = player.level().getGameTime();
        if (!ForgingRateLimiter.tryAcquire(player, surface, method.value().cooldown(), gameTime))
            return new StrikeOutcome(Failure.COOLDOWN, false, session.value());

        if (session.steps() >= plan.maxSteps()) {
            fail(player, surface, state, session);
            return new StrikeOutcome(null, false, 0);
        }

        FormulaContext context = FormulaContext.of(player);
        boolean conditionsMet = method.value().condition().test(player, context);
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        StrikeResult result = ForgingService.strike(player, surface, session, method, resources, context, () -> conditionsMet);
        if (!result.struck()) return new StrikeOutcome(result.failure(), false, session.value());
        state.update(session);
        surface.forgingChanged();
        playMethodSound(player, surface, method.value());
        settleIfComplete(player, surface, state, session);
        return new StrikeOutcome(null, true, session.value());
    }

    // Through the level with no excepted player, as SoundSource.BLOCKS so the block volume slider governs it.
    private static void playMethodSound(ServerPlayer player, ForgingSurface surface, ForgingMethod method) {
        player.level().playSound(null, surface.pos(), method.sound(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    // Checked after a strike and after a session start, because completion is a property of the state: a
    // blueprint already satisfied at start settles at once.
    private static void settleIfComplete(ServerPlayer player, ForgingSurface surface, ForgingTableState state, ForgingSession session) {
        if (session.canComplete()) settle(player, surface, state);
    }

    // For entry points other than the action that completed the session - opening the GUI being the only one -
    // without which the table would sit locked with a finished piece.
    public static void settleIfComplete(ServerPlayer player, ForgingSurface surface) {
        if (!canUse(player, surface)) return;
        ForgingTableState state = surface.forgingState();
        if (!state.active() || !outputEmpty(surface)) return;
        ForgingPlan plan = state.plan().orElse(null);
        state.session().map(snapshot -> ForgingSession.restore(plan, snapshot)).ifPresent(session -> settleIfComplete(player, surface, state, session));
    }

    public static FinishOutcome finish(ServerPlayer player, ForgingSurface surface) {
        if (!canUse(player, surface)) return new FinishOutcome(Failure.OUT_OF_RANGE, false);
        ForgingTableState state = surface.forgingState();
        if (!state.active()) return new FinishOutcome(Failure.NO_SESSION, false);
        if (!outputEmpty(surface)) return new FinishOutcome(Failure.OUTPUT_BLOCKED, false);
        return settle(player, surface, state);
    }

    // For callers that have already established the session, the range and the free output slot; two callers
    // share this body rather than agreeing by hand.
    private static FinishOutcome settle(ServerPlayer player, ForgingSurface surface, ForgingTableState state) {
        Identifier blueprintId = state.blueprint().orElse(null);
        Holder<ForgingBlueprint> holder = MxtDatapackRegistries.holder(MxtResourceKeys.FORGING_BLUEPRINT, blueprintId).orElse(null);
        ForgingPlan plan = state.plan().orElse(null);
        ForgingSession session = state.session().map(snapshot -> ForgingSession.restore(plan, snapshot)).orElse(null);
        if (holder == null || session == null) return new FinishOutcome(Failure.DISABLED, false);

        ForgingBlueprint blueprint = holder.value();
        // The locked materials are read here rather than at start because only the settlement turns them into
        // a quality: see ForgingService#materialModifier.
        double forgingModifier = ForgingService.materialModifier(player.level().registryAccess(), state.consumed(), FormulaContext.of(player));
        FinishResult result = ForgingService.finish(player, surface, holder, session, blueprint::qualityFor, forgingModifier);
        if (!result.finished()) {
            // A listener refusing is not a verdict on the piece, so it leaves the session where it is: see
            // Failure#refusedByListener.
            if (result.failure().refusedByListener()) return new FinishOutcome(result.failure(), false);
            fail(player, surface, state, session);
            return new FinishOutcome(null, true);
        }
        ItemStack output = BuiltInRegistries.ITEM.getOptional(blueprint.result()).map(ItemStack::new).orElse(ItemStack.EMPTY);
        if (output.isEmpty()) return new FinishOutcome(Failure.DISABLED, false);
        output.set(MxtDataComponents.FORGING_RESULT.get(), result.result());
        surface.forgingContainer().setItem(ForgingSurface.OUTPUT_SLOT, output);
        blueprint.completeAction().execute(player, FormulaContext.of(player));
        state.clear();
        surface.forgingChanged();
        return new FinishOutcome(null, true);
    }

    // The blueprint's fail_action still runs, because cancelling is a failure to produce anything.
    public static CancelOutcome cancel(ServerPlayer player, ForgingSurface surface) {
        if (!canUse(player, surface)) return new CancelOutcome(Failure.OUT_OF_RANGE, false);
        ForgingTableState state = surface.forgingState();
        if (!state.active()) return new CancelOutcome(Failure.NO_SESSION, false);
        ForgingPlan plan = state.plan().orElse(null);
        ForgingSession session = state.session().map(snapshot -> ForgingSession.restore(plan, snapshot)).orElse(null);
        // The event decides *whether*; the policy decides *what it costs*. Refusing here leaves the
        // session exactly as it was, materials and all.
        Failure refusal = session == null ? Failure.NO_SESSION : ForgingService.cancel(player, surface, session);
        if (refusal != null) return new CancelOutcome(refusal, false);
        ForgingBlueprint blueprint = state.blueprint().flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.FORGING_BLUEPRINT, id)).orElse(null);
        if (blueprint != null) blueprint.failAction().execute(player, FormulaContext.of(player));
        // After the policy, because its whole input is what the session took; and a full clear rather than
        // dropping just the session - see ForgingTableState#clear.
        cancellation.settle(player, surface, state, blueprint);
        state.clear();
        surface.forgingChanged();
        return new CancelOutcome(null, true);
    }

    private static void fail(ServerPlayer player, ForgingSurface surface, ForgingTableState state, ForgingSession session) {
        ForgingBlueprint blueprint = state.blueprint().flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.FORGING_BLUEPRINT, id)).orElse(null);
        if (blueprint == null) {
            returnLocked(player, surface, state);
        } else {
            FailureSettlement settlement = blueprint.failureSettlement();
            if (player.getRandom().nextDouble() < settlement.inputReturnRatio()) returnLocked(player, surface, state);
            if (player.getRandom().nextDouble() < settlement.failureProductRatio()) {
                settlement.result().flatMap(BuiltInRegistries.ITEM::getOptional).map(ItemStack::new)
                        .ifPresent(stack -> give(player, stack));
            }
            blueprint.failAction().execute(player, FormulaContext.of(player));
        }
        state.clear();
        surface.forgingChanged();
    }

    private static void returnLocked(ServerPlayer player, ForgingSurface surface, ForgingTableState state) {
        Container container = surface.forgingContainer();
        for (ItemStack stack : state.consumed()) {
            ItemStack remainder = insertIntoInputs(container, stack);
            if (!remainder.isEmpty()) give(player, remainder);
        }
    }

    private static ItemStack insertIntoInputs(Container container, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int index = ForgingSurface.INPUT_START; index < ForgingSurface.INPUT_START + ForgingSurface.INPUT_SLOTS; index++) {
            ItemStack existing = container.getItem(index);
            if (existing.isEmpty()) {
                container.setItem(index, remaining);
                return ItemStack.EMPTY;
            }
            if (!ItemStack.isSameItemSameComponents(existing, remaining)) continue;
            int room = Math.min(existing.getMaxStackSize(), container.getMaxStackSize(existing)) - existing.getCount();
            if (room <= 0) continue;
            int moved = Math.min(room, remaining.getCount());
            existing.grow(moved);
            remaining.shrink(moved);
            if (remaining.isEmpty()) return ItemStack.EMPTY;
        }
        return remaining;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    // ------------------------------------------------------------------ helpers

    public static boolean canUse(ServerPlayer player, ForgingSurface surface) {
        if (!(surface instanceof BlockEntity blockEntity)) return true;
        return !(player.distanceToSqr(blockEntity.getBlockPos().getCenter()) > MAX_DISTANCE_SQUARED);
    }

    private static boolean outputEmpty(ForgingSurface surface) {
        return surface.forgingContainer().getItem(ForgingSurface.OUTPUT_SLOT).isEmpty();
    }

    // Resolution only inspects, so a partially satisfiable blueprint never consumes anything.
    static final class StartupMaterials {
        private final List<ItemStack> declared;
        // Slot index to the number of items that will be removed from it.
        private final Map<Integer, Integer> removals;

        private StartupMaterials(List<ItemStack> declared, Map<Integer, Integer> removals) {
            this.declared = declared;
            this.removals = removals;
        }

        // Returns null when the container cannot cover the requirement.
        static StartupMaterials resolve(Container container, List<ForgingMaterial> requirement) {
            Map<Integer, Integer> removals = new LinkedHashMap<>();
            List<ItemStack> consumed = new ArrayList<>();
            for (ForgingMaterial entry : requirement) {
                ItemStack taken = entry.createStack();
                if (taken.isEmpty()) return null;
                int needed = taken.getCount();
                for (int index = ForgingSurface.INPUT_START; index < ForgingSurface.INPUT_START + ForgingSurface.INPUT_SLOTS && needed > 0; index++) {
                    ItemStack available = container.getItem(index);
                    if (available.isEmpty() || !entry.matches(available)) continue;
                    int already = removals.getOrDefault(index, 0);
                    int free = available.getCount() - already;
                    if (free <= 0) continue;
                    int moved = Math.min(needed, free);
                    removals.merge(index, moved, Integer::sum);
                    needed -= moved;
                }
                if (needed > 0) return null;
                consumed.add(taken);
            }
            return new StartupMaterials(consumed, removals);
        }

        // The stacks that were taken, used for cancel and failure returns.
        List<ItemStack> consumed() {
            return this.declared;
        }

        void consume(Container container) {
            this.removals.forEach((index, takenCount) -> {
                ItemStack available = container.getItem(index);
                int left = available.getCount() - takenCount;
                container.setItem(index, left <= 0 ? ItemStack.EMPTY : available.copyWithCount(left));
            });
        }
    }

    public record StartOutcome(Failure failure, boolean started) {
    }

    public record StrikeOutcome(Failure failure, boolean struck, int value) {
    }

    public record FinishOutcome(Failure failure, boolean finished) {
    }

    public record CancelOutcome(Failure failure, boolean cancelled) {
    }
}
