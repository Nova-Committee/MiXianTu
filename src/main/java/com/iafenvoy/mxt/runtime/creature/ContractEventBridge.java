package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.api.ContractOperations;
import com.iafenvoy.mxt.attachment.ContractAttachment;
import com.iafenvoy.mxt.data.creature.ContractBehavior;
import com.iafenvoy.mxt.data.creature.ContractBehaviors;
import com.iafenvoy.mxt.data.creature.ContractContext;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;

/**
 * Drives a bound creature through {@link ContractOperations}. The framework owns the moment and the data action
 * that colours it; how the creature follows, strolls, holds or comes back belongs to the creature. The order in
 * force is read off the creature's own record every tick, so this class keeps no second copy of it.
 */
@EventBusSubscriber
public final class ContractEventBridge {
    private ContractEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob pet) || pet.level().isClientSide()) return;
        ContractAttachment contract = pet.getData(MxtAttachments.CONTRACT);
        if (!contract.bound()) return;
        Holder<ContractType> type = contract.contractType().orElse(null);
        if (type == null) return;
        ContractOperations operations = Contracts.operations(pet).orElse(null);
        if (operations == null) return;
        ContractContext context = Contracts.context(pet, type);
        if (contract.recalled()) {
            operations.recall(context);
            ContractService.completeRecall(pet);
            return;
        }
        if (context.player() == null || context.player().level() != pet.level()) return;
        ContractBehavior behavior = contract.behavior();
        operations.tick(context, behavior);
        // The data action colours the following moment, so it runs while the creature is following and not while
        // the owner has told it to do something else.
        if (behavior == ContractBehaviors.FOLLOW)
            type.value().followAction().execute(pet, FormulaContext.of(pet));
    }

    // Contract combat callbacks run after vanilla damage is resolved and never mutate the original hit.
    @SubscribeEvent
    public static void onLivingDamage(Post event) {
        if (event.getEntity().level().isClientSide() || !(event.getSource().getEntity() instanceof Mob pet)) return;
        ContractAttachment contract = pet.getData(MxtAttachments.CONTRACT);
        if (!contract.bound()) return;
        Holder<ContractType> type = contract.contractType().orElse(null);
        if (type == null) return;
        ContractOperations operations = Contracts.operations(pet).orElse(null);
        if (operations == null) return;
        ContractContext context = Contracts.context(pet, type);
        operations.onDealtDamage(context, event.getEntity(), event.getInflictedDamage());
        type.value().combatAction().execute(pet, event.getEntity(),
                FormulaContext.of(pet, Map.of("damage", (double) event.getInflictedDamage())));
    }

    // A pet death closes its record, exposes the death action and drops its row from the owner's index.
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof Mob pet)) return;
        ContractService.onDeath(pet);
    }
}
