package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.attachment.ContractData;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.event.SpiritContractEvent.Action;
import com.iafenvoy.mxt.event.SpiritContractEvent.Post;
import com.iafenvoy.mxt.event.SpiritContractEvent.Pre;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
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

    public static Result bind(ContractData data, Identifier id, ContractType definition, UUID owner, long gameTime,
                              BooleanSupplier ownerAllowed, BooleanSupplier creatureAllowed) {
        if (data.bound()) return Result.rejected(Failure.ALREADY_BOUND);
        if (!ownerAllowed.getAsBoolean()) return Result.rejected(Failure.OWNER_CONDITIONS);
        if (!creatureAllowed.getAsBoolean()) return Result.rejected(Failure.CREATURE_CONDITIONS);
        if (NeoForge.EVENT_BUS.post(new Pre(data, Optional.of(id), owner, Action.BIND)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        data.bind(id, owner, gameTime);
        NeoForge.EVENT_BUS.post(new Post(data, Optional.of(id), owner, Action.BIND));
        return Result.bound();
    }

    /**
     * Evaluates both sides against the contract's fixed condition registry before binding.
     */
    public static Result bind(ContractData data, Identifier id, ContractType definition, LivingEntity owner,
                              LivingEntity creature, long gameTime, FormulaContext context) {
        boolean ownerAllowed = definition.ownerCondition().test(owner, context);
        boolean creatureAllowed = definition.creatureCondition().test(creature, context);
        return bind(data, id, definition, owner.getUUID(), gameTime, () -> ownerAllowed, () -> creatureAllowed);
    }

    public static Result breakContract(ContractData data, UUID requester, boolean force) {
        if (!data.bound()) return Result.rejected(Failure.NOT_BOUND);
        if (!force && !data.owner().orElseThrow().equals(requester)) return Result.rejected(Failure.NOT_OWNER);
        Optional<Identifier> type = data.contractType();
        if (NeoForge.EVENT_BUS.post(new Pre(data, type, requester, Action.BREAK)).isCanceled())
            return Result.rejected(Failure.CANCELLED);
        data.clear();
        NeoForge.EVENT_BUS.post(new Post(data, type, requester, Action.BREAK));
        return Result.broken();
    }

    public static Result setRecalled(ContractData data, UUID requester, boolean recalled, boolean force) {
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
