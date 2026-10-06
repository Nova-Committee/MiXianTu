package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.api.Contractable;
import com.iafenvoy.mxt.attachment.ContractAttachment;
import com.iafenvoy.mxt.data.cost.CostPayment;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.creature.ContractContext;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.event.SpiritContractEvent.Action;
import com.iafenvoy.mxt.event.SpiritContractEvent.Post;
import com.iafenvoy.mxt.event.SpiritContractEvent.Pre;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ability.AbilityGrantService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side contract lifecycle. Who may sign and what a bound creature does come from the creature's own
 * interfaces; this class owns the order of the steps, the record, the owner-side index and the events.
 */
public final class ContractService {
    private ContractService() {
    }

    /**
     * Check order is the contract: every question is asked before any money moves, because a cancelled event or a
     * refused payment must not leave a charge behind, and the script channel cannot be refunded.
     */
    public static Result bind(Holder<ContractType> type, ServerPlayer owner, Mob creature, boolean force) {
        ContractAttachment data = creature.getData(MxtAttachments.CONTRACT);
        if (data.bound()) return Result.rejected(Failure.ALREADY_BOUND);
        Contractable contractable = Contracts.of(creature).orElse(null);
        if (contractable == null) return Result.rejected(Failure.NOT_CONTRACTABLE);
        ContractContext context = Contracts.context(creature, owner, type);
        if (!contractable.acceptsContract(context)) return Result.rejected(Failure.CREATURE_CONDITIONS);
        ContractType definition = type.value();
        FormulaContext formula = FormulaContexts.forEntities(owner, creature, Map.of());
        if (!definition.ownerCondition().test(owner, formula)) return Result.rejected(Failure.OWNER_CONDITIONS);
        if (!definition.creatureCondition().test(creature, formula))
            return Result.rejected(Failure.CREATURE_CONDITIONS);
        MinecraftServer server = server(creature);
        if (!force) {
            BoundBeastService.prune(server, owner.getUUID());
            if (BoundBeastService.atLimit(server, owner.getUUID(), type))
                return Result.rejected(Failure.LIMIT_REACHED);
        }
        if (NeoForge.EVENT_BUS.post(new Pre(data, Optional.of(type), owner.getUUID(), Action.BIND)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        if (!force && !definition.costs().isEmpty() && !CostPayment.pay(definition.costs(),
                CostContext.of(owner, CostOrigin.CONTRACT).withFormula(formula)).paid())
            return Result.rejected(Failure.INSUFFICIENT_COST);
        data.bind(type, owner.level().getGameTime());
        contractable.setContractOwner(owner);
        // A bound beast that despawns would leave a row and a contract nobody can end; binding is what makes it stay.
        creature.setPersistenceRequired();
        BoundBeastService.add(server, owner, creature, type);
        contractable.onContractBound(context);
        // The owner side is set up before the event: the index now holds the row the grants are read from, so the
        // bind action already sees the abilities the contract gives it.
        AbilityGrantService.recalculate(owner);
        definition.ownerBindAction().execute(owner, FormulaContexts.forEntities(owner, creature, Map.of()));
        NeoForge.EVENT_BUS.post(new Post(data, Optional.of(type), owner.getUUID(), Action.BIND));
        return Result.bound();
    }

    public static Result release(Mob creature, UUID requester, boolean force) {
        ContractAttachment data = creature.getData(MxtAttachments.CONTRACT);
        // The interface is not required to end a record: a hand-edited save, or a creature whose class stopped
        // implementing it, must still be releasable, which is the operator's only way to clear such a row.
        if (!data.bound()) return Result.rejected(Failure.NOT_BOUND);
        Holder<ContractType> type = data.contractType().orElseThrow();
        UUID owner = Contracts.ownerOf(creature).orElse(null);
        if (!force && (owner == null || !owner.equals(requester))) return Result.rejected(Failure.NOT_OWNER);
        if (NeoForge.EVENT_BUS.post(new Pre(data, Optional.of(type), requester, Action.RELEASE)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        // Resolved before the creature's own hook, which is free to forget who owned it. An owner who is offline
        // is skipped: there is nobody to run their action on, and the grants are reconciled when they are back.
        ServerPlayer ownerPlayer = Contracts.onlineOwner(creature);
        ContractContext context = Contracts.context(creature, type);
        Contracts.of(creature).ifPresent(value -> value.onContractReleased(context));
        type.value().releaseAction().execute(creature, FormulaContext.of(creature));
        data.clear();
        // The bond paid for the creature's levels, so it takes them with it; this runs after the action, which
        // still reads the level it was released at.
        CreatureProgressionService.clear(creature);
        BoundBeastService.remove(server(creature), creature.getUUID());
        // The row is gone first, so the rebuild cannot hand back what this moment takes away; the action runs
        // while the owner still holds it.
        if (ownerPlayer != null) {
            type.value().ownerReleaseAction().execute(ownerPlayer, FormulaContexts.forEntities(ownerPlayer, creature, Map.of()));
            AbilityGrantService.recalculate(ownerPlayer);
        }
        NeoForge.EVENT_BUS.post(new Post(data, Optional.of(type), requester, Action.RELEASE));
        return Result.released();
    }

    // The bell asks for the latch to be set; the creature's next tick is what brings it over. Force is the
    // operator's bypass of the cooldown only, never of the record.
    public static Result requestRecall(Mob creature, UUID requester, boolean force) {
        ContractAttachment data = creature.getData(MxtAttachments.CONTRACT);
        if (!data.bound()) return Result.rejected(Failure.NOT_BOUND);
        Holder<ContractType> type = data.contractType().orElseThrow();
        UUID owner = Contracts.ownerOf(creature).orElse(null);
        if (!force && (owner == null || !owner.equals(requester))) return Result.rejected(Failure.NOT_OWNER);
        // A latch that is still set is its own answer rather than "nothing happened": the caller reports whatever
        // reason a refusal carries, so losing one here would leave it reading a null.
        if (data.recalled()) return Result.rejected(Failure.RECALL_PENDING);
        long gameTime = creature.level().getGameTime();
        if (!force && !data.canRecall(gameTime, type.value().recallCooldown()))
            return Result.rejected(Failure.RECALL_COOLDOWN);
        if (NeoForge.EVENT_BUS.post(new Pre(data, Optional.of(type), requester, Action.RECALL)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        data.requestRecall(gameTime);
        NeoForge.EVENT_BUS.post(new Post(data, Optional.of(type), requester, Action.RECALL));
        return Result.recalled();
    }

    // Completion is not cancellable: a cancel here would leave the latch set with nobody left to consume it.
    public static void completeRecall(Mob creature) {
        ContractAttachment data = creature.getData(MxtAttachments.CONTRACT);
        if (!data.recalled()) return;
        Optional<Holder<ContractType>> type = data.contractType();
        UUID owner = Contracts.ownerOf(creature).orElse(null);
        data.completeRecall();
        NeoForge.EVENT_BUS.post(new Post(data, type, owner, Action.RECALL_COMPLETED));
    }

    public static void onDeath(Mob creature) {
        ContractAttachment data = creature.getData(MxtAttachments.CONTRACT);
        if (!data.bound()) return;
        Holder<ContractType> type = data.contractType().orElseThrow();
        UUID owner = Contracts.ownerOf(creature).orElse(null);
        // Asked while the creature still answers with its owner, and skipped when there is nobody to run it on.
        ServerPlayer ownerPlayer = Contracts.onlineOwner(creature);
        ContractContext context = Contracts.context(creature, type);
        type.value().deathAction().execute(creature, FormulaContext.of(creature));
        Contracts.of(creature).ifPresent(value -> value.onContractDeath(context));
        data.clear();
        // A death ends the bond the same way a release does, so the levels go with it even though the body is
        // about to be gone: an action that brought the creature back must not bring the growth back with it.
        CreatureProgressionService.clear(creature);
        BoundBeastService.remove(server(creature), creature.getUUID());
        if (ownerPlayer != null) {
            type.value().ownerDeathAction().execute(ownerPlayer, FormulaContexts.forEntities(ownerPlayer, creature, Map.of()));
            AbilityGrantService.recalculate(ownerPlayer);
        }
        // The event names a requester, so a death with nobody left to name still ends the record without one.
        if (owner != null) NeoForge.EVENT_BUS.post(new Post(data, Optional.of(type), owner, Action.DEATH));
    }

    private static MinecraftServer server(Mob creature) {
        return creature.level() instanceof ServerLevel level ? level.getServer() : null;
    }

    public enum Failure {
        ALREADY_BOUND, NOT_CONTRACTABLE, OWNER_CONDITIONS, CREATURE_CONDITIONS, LIMIT_REACHED,
        INSUFFICIENT_COST, NOT_BOUND, NOT_OWNER, RECALL_COOLDOWN, RECALL_PENDING, CANCELLED,
        UNSUPPORTED_BEHAVIOR, BEHAVIOR_REFUSED
    }

    // Everything but a change names a reason: the callers report failure() whenever changed() is false, so no
    // result may carry a null failure.
    public record Result(boolean changed, Failure failure) {
        static Result bound() {
            return new Result(true, null);
        }

        static Result released() {
            return new Result(true, null);
        }

        static Result recalled() {
            return new Result(true, null);
        }

        // An order that was taken: recorded, or - for a momentary one - acted on by the creature.
        static Result applied() {
            return new Result(true, null);
        }

        static Result rejected(Failure failure) {
            return new Result(false, failure);
        }
    }
}
