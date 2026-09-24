package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.api.ContractOperations;
import com.iafenvoy.mxt.attachment.ContractAttachment;
import com.iafenvoy.mxt.data.creature.ContractBehavior;
import com.iafenvoy.mxt.data.creature.ContractBehaviors;
import com.iafenvoy.mxt.data.creature.ContractContext;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Mob;

import java.util.UUID;

/**
 * The input path for orders: the owner's wheel, the bell and {@code /contract behavior} all land here, so who may
 * order what is decided once. The order in force is written on the creature's own contract record, which stays
 * its only copy; nothing here keeps state of its own.
 */
public final class ContractBehaviorService {
    private ContractBehaviorService() {
    }

    public static ContractService.Result request(Mob creature, UUID requester, ContractBehavior behavior, boolean force) {
        ContractAttachment data = creature.getData(MxtAttachments.CONTRACT);
        if (!data.bound()) return ContractService.Result.rejected(ContractService.Failure.NOT_BOUND);
        Holder<ContractType> type = data.contractType().orElseThrow();
        UUID owner = Contracts.ownerOf(creature).orElse(null);
        if (!force && (owner == null || !owner.equals(requester)))
            return ContractService.Result.rejected(ContractService.Failure.NOT_OWNER);
        ContractOperations operations = Contracts.operations(creature).orElse(null);
        if (operations == null) return ContractService.Result.rejected(ContractService.Failure.NOT_CONTRACTABLE);
        if (!operations.behaviors().contains(behavior))
            return ContractService.Result.rejected(ContractService.Failure.UNSUPPORTED_BEHAVIOR);
        // A recall is the one order whose state the framework owns: the latch and the cooldown live on the record,
        // so it goes through the same request the command always used instead of becoming the order in force.
        if (behavior == ContractBehaviors.RECALL)
            return ContractService.requestRecall(creature, requester, force);
        ContractContext context = Contracts.context(creature, type);
        if (!operations.onBehaviorSelected(context, behavior))
            return ContractService.Result.rejected(ContractService.Failure.BEHAVIOR_REFUSED);
        // Momentary orders stop here: the creature has acted on the request, and there is nothing to keep.
        if (behavior.momentary()) return ContractService.Result.applied();
        data.setBehavior(behavior);
        return ContractService.Result.applied();
    }
}
