package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.attachment.ContractAttachment;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.event.SpiritContractEvent.Action;
import com.iafenvoy.mxt.event.SpiritContractEvent.Post;
import com.iafenvoy.mxt.event.SpiritContractEvent.Pre;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Server-side contract lifecycle with predicates supplied by the entity/permission integration layer.
 */
public final class ContractService {
    private ContractService() {
    }

    /**
     * Applies predicates supplied by an integration layer. This overload never sees the creature itself, so it
     * cannot consult a creature profile; callers that hold the entity must use the overload below to get the
     * profile route of creature eligibility.
     */
    public static Result bind(ContractAttachment data, Holder<ContractType> type, UUID owner, long gameTime,
                              BooleanSupplier ownerAllowed, BooleanSupplier creatureAllowed) {
        if (data.bound()) return Result.rejected(Failure.ALREADY_BOUND);
        if (!ownerAllowed.getAsBoolean()) return Result.rejected(Failure.OWNER_CONDITIONS);
        if (!creatureAllowed.getAsBoolean()) return Result.rejected(Failure.CREATURE_CONDITIONS);
        if (NeoForge.EVENT_BUS.post(new Pre(data, Optional.of(type), owner, Action.BIND)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        data.bind(type, owner, gameTime);
        NeoForge.EVENT_BUS.post(new Post(data, Optional.of(type), owner, Action.BIND));
        return Result.bound();
    }

    /**
     * Evaluates both sides before binding. The owner side keeps the contract's fixed condition registry, while
     * the creature side is a union of two routes: the creature qualifies when the contract type's own
     * {@code creature_condition} passes, or when the creature profile applied to it lists that contract kind in
     * {@code contract_tags} - either as the contract type's id or as a native {@code mxt:contract_type} tag that
     * type declares. Both routes only ever add eligibility, so a creature that could be contracted before the
     * profile was read still can be, and a profile can widen a contract's reach but never narrow it. When no
     * profile is applied, its tag list is empty, or its entries name nothing, the profile route reports no match
     * and the contract type's own condition decides alone, exactly as it did before.
     */
    public static Result bind(ContractAttachment data, Holder<ContractType> type, LivingEntity owner,
                              LivingEntity creature, long gameTime, FormulaContext context) {
        ContractType definition = type.value();
        boolean ownerAllowed = definition.ownerCondition().test(owner, context);
        boolean creatureAllowed = definition.creatureCondition().test(creature, context)
                || CreatureProfileService.declaresContract(creature, type);
        return bind(data, type, owner.getUUID(), gameTime, () -> ownerAllowed, () -> creatureAllowed);
    }

    public static Result breakContract(ContractAttachment data, UUID requester, boolean force) {
        if (!data.bound()) return Result.rejected(Failure.NOT_BOUND);
        if (!force && !data.owner().orElseThrow().equals(requester)) return Result.rejected(Failure.NOT_OWNER);
        Optional<Holder<ContractType>> type = data.contractType();
        if (NeoForge.EVENT_BUS.post(new Pre(data, type, requester, Action.BREAK)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        data.clear();
        NeoForge.EVENT_BUS.post(new Post(data, type, requester, Action.BREAK));
        return Result.broken();
    }

    public static Result setRecalled(ContractAttachment data, UUID requester, boolean recalled, boolean force) {
        if (!data.bound()) return Result.rejected(Failure.NOT_BOUND);
        if (!force && !data.owner().orElseThrow().equals(requester)) return Result.rejected(Failure.NOT_OWNER);
        if (data.recalled() == recalled) return Result.unchanged();
        Action action = recalled ? Action.RECALL : Action.RELEASE;
        if (NeoForge.EVENT_BUS.post(new Pre(data, data.contractType(), requester, action)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        data.setRecalled(recalled);
        NeoForge.EVENT_BUS.post(new Post(data, data.contractType(), requester, action));
        return Result.recalled();
    }

    public enum Failure {ALREADY_BOUND, DISABLED, OWNER_CONDITIONS, CREATURE_CONDITIONS, NOT_BOUND, NOT_OWNER, CANCELLED}

    public record Result(boolean changed, Failure failure) {
        static Result bound() {
            return new Result(true, null);
        }

        static Result broken() {
            return new Result(true, null);
        }

        static Result recalled() {
            return new Result(true, null);
        }

        static Result unchanged() {
            return new Result(false, null);
        }

        static Result rejected(Failure failure) {
            return new Result(false, failure);
        }
    }
}
