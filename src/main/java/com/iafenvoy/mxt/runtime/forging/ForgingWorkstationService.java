package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.data.forging.BlueprintBinding;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint.FailureSettlement;
import com.iafenvoy.mxt.data.forging.ForgingMaterial;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.forging.ToolBinding;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.forging.ForgingService.Failure;
import com.iafenvoy.mxt.runtime.forging.ForgingService.FinishResult;
import com.iafenvoy.mxt.runtime.forging.ForgingService.StartResult;
import com.iafenvoy.mxt.runtime.forging.ForgingService.StrikeResult;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Authoritative forge-table transaction boundary.
 *
 * <p>The service owns material matching, tool/blueprint gating, the shared session lifecycle and
 * the strike rate limit. It never touches a block entity type directly: callers hand it a
 * {@link ForgingSurface}.</p>
 *
 * <h2>Shared sessions</h2>
 * A placed table holds exactly one session. Any player standing at the table may strike it,
 * which is what makes cooperative forging possible; {@code starter} is informational only.
 *
 * <h2>Materials</h2>
 * The blueprint input list is order independent. Starting a session removes the declared amounts
 * from the input slots and stores the exact removed stacks on the session, so a cancel or a
 * failure returns precisely what was taken.
 */
public final class ForgingWorkstationService {
    /**
     * Squared distance from the table centre a player must stay within.
     */
    public static final double MAX_DISTANCE_SQUARED = 64.0D;

    private ForgingWorkstationService() {
    }

    // ------------------------------------------------------------------ listing

    /**
     * Blueprint ids provided by the blueprint items currently placed in the blueprint slots.
     *
     * <p>Nothing placed means nothing offered, and that is the whole rule. The manual is what
     * <em>grants</em> a blueprint, so a selector that fell back to every registered blueprint inverted
     * it: the list began at "all of them" and inserting a manual could only ever take entries away,
     * which reads as a manual that restricts what you may forge rather than one that lets you forge it.
     * It was also a bypass - leaving the slot empty let a player start any session at all, and this same
     * list is what {@link #start} validates the pick against.
     *
     * <p>Takes the container rather than a {@link ForgingSurface} because the client half of the menu has
     * no surface and no way to reach one; it has its own copy of the slot contents, which is everything
     * this needs. The ids it returns on the client are for display - the server re-resolves and
     * re-validates whatever it is sent.
     */
    public static List<Identifier> selectableBlueprintIds(Container container) {
        Set<Identifier> provided = new LinkedHashSet<>();
        for (int index = ForgingSurface.BLUEPRINT_START; index < ForgingSurface.BLUEPRINT_START + ForgingSurface.BLUEPRINT_SLOTS; index++) {
            Holder<BlueprintBinding> binding = container.getItem(index).get(MxtDataComponents.BLUEPRINT_BINDING.get());
            if (binding == null) continue;
            binding.value().blueprints().forEach(blueprint -> provided.add(HolderHelper.id(blueprint)));
        }
        return List.copyOf(provided);
    }

    /**
     * Method ids unlocked by the tools currently placed in the tool slots.
     *
     * <p>The order is the tool slots' order, then each tool's own declaration order, deduplicated: a
     * second tool can only ever append, never reorder or remove.</p>
     */
    private static List<Identifier> toolMethodIds(Container container) {
        Set<Identifier> unlocked = new LinkedHashSet<>();
        for (int index = ForgingSurface.TOOL_START; index < ForgingSurface.TOOL_START + ForgingSurface.TOOL_SLOTS; index++) {
            Holder<ToolBinding> binding = container.getItem(index).get(MxtDataComponents.TOOL_BINDING.get());
            if (binding == null) continue;
            binding.value().methods().forEach(method -> unlocked.add(HolderHelper.id(method)));
        }
        return List.copyOf(unlocked);
    }

    /**
     * Method ids the surface offers: this blueprint's {@code allowed_methods} intersected with the union
     * of the placed tools' methods.
     *
     * <p>Two axes, and neither replaces the other. The blueprint says which techniques this piece may be
     * made with; the tools say which techniques this player can perform. A player who can perform
     * nothing the blueprint wants cannot forge it, and a blueprint that allows nothing they can perform
     * is a dead end - which is why the tool slots stay open during a session, so a hammer can be added
     * and the intersection widened after the fact.</p>
     *
     * <p>A blueprint that declares nothing restricts nothing, and so does a null id - which is the state
     * the grid is in before anything is selected. The list is then just the tools' union, so the right
     * grid is populated from the moment a tool is placed.</p>
     *
     * <p>Takes the container and a registry access rather than a {@link ForgingSurface}, because the
     * client half of the menu has no surface and no way to reach one: it has its own copy of the slot
     * contents and its own synchronised registries, which is everything this needs. The ids it returns
     * on the client are for display; the server re-resolves and re-validates before acting.</p>
     */
    public static List<Identifier> availableMethodIds(Container container, RegistryAccess registries, Identifier blueprintId) {
        List<Identifier> unlocked = toolMethodIds(container);
        ForgingBlueprint blueprint = blueprintId == null ? null
                : MxtDatapackRegistries.get(registries, MxtResourceKeys.FORGING_BLUEPRINT, blueprintId).orElse(null);
        if (blueprint == null || !blueprint.restrictsMethods()) return unlocked;
        Set<Identifier> allowed = new LinkedHashSet<>();
        blueprint.allowedMethods().stream().forEach(method -> allowed.add(HolderHelper.id(method)));
        return unlocked.stream().filter(allowed::contains).toList();
    }

    // ------------------------------------------------------------------ materials

    /**
     * How many of one declared material the input slots hold.
     *
     * <p>One entry's count is a total and not a share: a blueprint's validation rejects a list that
     * names the same item twice, so two entries can never compete for the same stacks.</p>
     */
    public static int availableCount(Container container, ForgingMaterial entry) {
        int available = 0;
        for (int index = ForgingSurface.INPUT_START; index < ForgingSurface.INPUT_START + ForgingSurface.INPUT_SLOTS; index++) {
            ItemStack stack = container.getItem(index);
            if (entry.matches(stack)) available += stack.getCount();
        }
        return available;
    }

    /**
     * Whether the input slots cover a whole material list.
     *
     * <p>Exactly the resolution {@link #start} performs before it consumes anything, exposed so the
     * screen can disable the button and explain why without re-deriving the rule - which is how a greyed
     * button here and a refusal there are guaranteed to be the same verdict rather than two guesses.</p>
     */
    public static boolean materialsCovered(Container container, List<ForgingMaterial> requirement) {
        return StartupMaterials.resolve(container, requirement) != null;
    }

    // ------------------------------------------------------------------ cancellation policy

    /**
     * The default {@link ForgingCancellation}: everything the session took goes back to the input slots.
     */
    public static final ForgingCancellation RETURN_EVERYTHING = (player, surface, state, blueprint) -> returnLocked(player, surface, state);

    /**
     * What a cancellation settles with. See {@link ForgingCancellation} for the seam and
     * {@link #setCancellation} for how it is replaced.
     */
    private static volatile ForgingCancellation cancellation = RETURN_EVERYTHING;

    /**
     * Replaces the cancellation policy, or restores the default by passing null.
     *
     * <p>Deliberately a plain installed value rather than datapack state: the shape of a policy is still
     * being decided, and freezing one into the blueprint format now would mean every content pack carries
     * a field that only one implementation reads. When a variant proves itself, it can be given a field -
     * the policy is where it will be read from either way.</p>
     */
    public static void setCancellation(ForgingCancellation policy) {
        cancellation = policy == null ? RETURN_EVERYTHING : policy;
    }

    // ------------------------------------------------------------------ session

    /**
     * Opens a session for the selected blueprint, consuming the declared materials from the surface.
     */
    public static StartOutcome start(ServerPlayer player, ForgingSurface surface, Identifier blueprintId) {
        if (!canUse(player, surface)) return new StartOutcome(Failure.OUT_OF_RANGE, false);
        ForgingTableState state = surface.forgingState();
        if (state.active()) return new StartOutcome(Failure.ALREADY_ACTIVE, false);
        Holder<ForgingBlueprint> holder = MxtDatapackRegistries.holder(MxtResourceKeys.FORGING_BLUEPRINT, blueprintId).orElse(null);
        if (holder == null) return new StartOutcome(Failure.DISABLED, false);
        ForgingBlueprint blueprint = holder.value();
        if (!selectableBlueprintIds(surface.forgingContainer()).contains(blueprintId))
            return new StartOutcome(Failure.BLUEPRINT_NOT_HELD, false);
        if (!outputEmpty(surface)) return new StartOutcome(Failure.OUTPUT_BLOCKED, false);

        StartupMaterials materials = StartupMaterials.resolve(surface.forgingContainer(), blueprint.input());
        if (materials == null) return new StartOutcome(Failure.INSUFFICIENT_MATERIALS, false);

        RegistryAccess registries = player.level().registryAccess();
        StartResult result = ForgingService.start(blueprint, registries);
        if (!result.started()) return new StartOutcome(result.failure(), false);

        materials.consume(surface.forgingContainer());
        state.lock(blueprintId, blueprint.plan(registries), result.session(), materials.consumed(), player.getUUID());
        surface.forgingChanged();
        settleIfComplete(player, surface, state, result.session());
        return new StartOutcome(null, true);
    }

    /**
     * Executes one strike with the selected method.
     */
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
        StrikeResult result = ForgingService.strike(session, method, resources, context, () -> conditionsMet);
        if (!result.struck()) return new StrikeOutcome(result.failure(), false, session.value());
        state.update(session);
        surface.forgingChanged();
        settleIfComplete(player, surface, state, session);
        return new StrikeOutcome(null, true, session.value());
    }

    /**
     * Settles the session when it now satisfies the blueprint, and does nothing otherwise.
     *
     * <p>Checked wherever the session's state can <em>newly</em> satisfy it - after a strike and after a
     * session starts - because completion is a property of the state rather than of the action that
     * produced it. Two call sites, one predicate: a rule that only applied on the strike path would make
     * a session that is already complete behave differently depending on how it got there.</p>
     *
     * <p>That also means a blueprint whose target band already contains the starting value, {@code 0}, and
     * which asks for no suffix settles the moment it starts. That is a blueprint that asks for nothing, not
     * a hole in the rule, and it is the same predicate either way. The materials are still consumed, so it
     * is not free.</p>
     *
     * <p>There is nothing left to decide once it holds: the value is in the band and the last steps match,
     * and further strikes could only raise {@code extraSteps}, which the quality curve reads as strictly
     * worse. Leaving it open would be an opportunity to ruin a finished piece.</p>
     *
     * <p>A settlement that fails - a listener cancelling {@code CompletePre}, or a blueprint that vanished
     * under a reload - leaves the session exactly as it was. Nothing else in the system will settle it, so
     * whoever cancelled is responsible for the session they kept alive.</p>
     */
    private static void settleIfComplete(ServerPlayer player, ForgingSurface surface, ForgingTableState state, ForgingSession session) {
        if (session.canComplete()) settle(player, surface, state);
    }

    /**
     * Settles a session that is already complete, for entry points other than the action that completed
     * it - opening the GUI being the only one.
     *
     * <p>A complete session can outlive the strike that made it so: a listener that cancels
     * {@code CompletePre} leaves one behind, and so does any world written before settlement was
     * automatic. The other triggers all need the state to <em>change</em>, so without this the table
     * would sit locked with a finished piece that nothing ever produces.</p>
     *
     * <p>Guarded exactly like {@link #finish}, because it settles the same way: no session, no room in the
     * output slot, or a player out of range and nothing happens. An <em>incomplete</em> session is left
     * alone - settling one of those is the failure path, and it must never be reached by opening a menu.</p>
     */
    public static void settleIfComplete(ServerPlayer player, ForgingSurface surface) {
        if (!canUse(player, surface)) return;
        ForgingTableState state = surface.forgingState();
        if (!state.active() || !outputEmpty(surface)) return;
        ForgingPlan plan = state.plan().orElse(null);
        state.session().map(snapshot -> ForgingSession.restore(plan, snapshot)).ifPresent(session -> settleIfComplete(player, surface, state, session));
    }

    /**
     * Settles the session and writes the finished item into the output slot.
     */
    public static FinishOutcome finish(ServerPlayer player, ForgingSurface surface) {
        if (!canUse(player, surface)) return new FinishOutcome(Failure.OUT_OF_RANGE, false);
        ForgingTableState state = surface.forgingState();
        if (!state.active()) return new FinishOutcome(Failure.NO_SESSION, false);
        if (!outputEmpty(surface)) return new FinishOutcome(Failure.OUTPUT_BLOCKED, false);
        return settle(player, surface, state);
    }

    /**
     * The settlement itself, for callers that have already established that a session exists, that the
     * player is in range and that the output slot is free.
     *
     * <p>Split out because two callers arrive here - an explicit request, and a strike that has just
     * completed the piece - and they share the body rather than agreeing until one of them is edited.</p>
     */
    private static FinishOutcome settle(ServerPlayer player, ForgingSurface surface, ForgingTableState state) {
        Identifier blueprintId = state.blueprint().orElse(null);
        Holder<ForgingBlueprint> holder = MxtDatapackRegistries.holder(MxtResourceKeys.FORGING_BLUEPRINT, blueprintId).orElse(null);
        ForgingPlan plan = state.plan().orElse(null);
        ForgingSession session = state.session().map(snapshot -> ForgingSession.restore(plan, snapshot)).orElse(null);
        if (holder == null || session == null) return new FinishOutcome(Failure.DISABLED, false);

        ForgingBlueprint blueprint = holder.value();
        FinishResult result = ForgingService.finish(holder, session, blueprint::qualityFor);
        if (!result.finished()) {
            if (result.failure() == Failure.CANCELLED) return new FinishOutcome(Failure.CANCELLED, false);
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

    /**
     * Cancels the session, settling its locked materials through the installed {@link ForgingCancellation}.
     *
     * <p>The blueprint's {@code fail_action} still runs, as it did before there was a policy: cancelling is
     * a failure to produce anything, and a blueprint that wants to react to that writes it there. What a
     * policy owns is only the materials.</p>
     */
    public static CancelOutcome cancel(ServerPlayer player, ForgingSurface surface) {
        if (!canUse(player, surface)) return new CancelOutcome(Failure.OUT_OF_RANGE, false);
        ForgingTableState state = surface.forgingState();
        if (!state.active()) return new CancelOutcome(Failure.NO_SESSION, false);
        ForgingPlan plan = state.plan().orElse(null);
        ForgingSession session = state.session().map(snapshot -> ForgingSession.restore(plan, snapshot)).orElse(null);
        // The event decides *whether*; the policy decides *what it costs*. Refusing here leaves the
        // session exactly as it was, materials and all.
        if (session == null || !ForgingService.cancel(session)) return new CancelOutcome(Failure.CANCELLED, false);
        ForgingBlueprint blueprint = state.blueprint().flatMap(id -> MxtDatapackRegistries.get(MxtResourceKeys.FORGING_BLUEPRINT, id)).orElse(null);
        if (blueprint != null) blueprint.failAction().execute(player, FormulaContext.of(player));
        // After the policy, because its whole input is what the session took; and a full clear rather than
        // dropping just the session - see ForgingTableState#clear.
        cancellation.settle(player, surface, state, blueprint);
        state.clear();
        surface.forgingChanged();
        return new CancelOutcome(null, true);
    }

    /**
     * Fails the session: rolls the failure settlement and returns whatever survives.
     */
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

    /**
     * Puts the locked stacks back into the input slots, dropping what does not fit.
     */
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

    /**
     * Resolves the declared materials as an order-independent multiset over the input slots.
     *
     * <p>Resolution and removal are two separate phases: {@link #resolve} only inspects, so a
     * partially satisfiable blueprint never consumes anything. Package private so the server audit
     * can verify the matching rule without a live player.</p>
     */
    static final class StartupMaterials {
        private final List<ItemStack> declared;
        /**
         * Slot index to the number of items that will be removed from it.
         */
        private final Map<Integer, Integer> removals;

        private StartupMaterials(List<ItemStack> declared, Map<Integer, Integer> removals) {
            this.declared = declared;
            this.removals = removals;
        }

        /**
         * Returns {@code null} when the container cannot cover the requirement.
         */
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

        /**
         * The stacks that were taken, used for cancel and failure returns.
         */
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
