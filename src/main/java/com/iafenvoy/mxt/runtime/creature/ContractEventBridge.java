package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.attachment.ContractComponent;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;

/**
 * Generic owner-follow and recall policy for bound mobs; contract definitions remain data driven.
 */
@EventBusSubscriber
public final class ContractEventBridge {
    private ContractEventBridge() {
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob pet) || pet.level().isClientSide()) return;
        ContractComponent contract = pet.getData(MxtAttachments.CONTRACT);
        ContractType definition = contract.contractType().map(Holder::value).orElse(null);
        if (!contract.bound() || definition == null) return;
        ServerPlayer owner = ((ServerLevel) pet.level()).getServer().getPlayerList().getPlayer(contract.owner().orElseThrow());
        if (owner == null || owner.level() != pet.level()) return;
        if (contract.recalled()) {
            pet.teleportTo(owner.getX(), owner.getY(), owner.getZ());
            ContractService.setRecalled(contract, owner.getUUID(), false, true);
            return;
        }
        double distance = pet.distanceToSqr(owner);
        if (distance > 32.0D * 32.0D) {
            pet.teleportTo(owner.getX(), owner.getY(), owner.getZ());
        } else if (distance > 4.0D * 4.0D) {
            pet.getNavigation().moveTo(owner, 1.0D);
        }
        definition.followAction().execute(pet, FormulaContext.of(pet));
    }

    /**
     * Contract combat callbacks run after vanilla damage is resolved and never mutate the original hit.
     */
    @SubscribeEvent
    public static void onLivingDamage(Post event) {
        if (event.getEntity().level().isClientSide() || !(event.getSource().getEntity() instanceof Mob pet)) return;
        ContractComponent contract = pet.getData(MxtAttachments.CONTRACT);
        contract.contractType().map(Holder::value).ifPresent(definition -> {
            FormulaContext context = FormulaContext.of(pet, Map.of("damage", (double) event.getInflictedDamage()));
            definition.combatAction().execute(pet, event.getEntity(), context);
        });
    }

    /**
     * A pet death closes its persisted contract and exposes both break and penalty hooks.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof Mob pet)) return;
        ContractComponent contract = pet.getData(MxtAttachments.CONTRACT);
        if (!contract.bound()) return;
        contract.contractType().map(Holder::value).ifPresent(definition -> {
            FormulaContext context = FormulaContext.of(pet);
            definition.breakAction().execute(pet, context);
            definition.penaltyAction().execute(pet, context);
        });
        contract.clear();
    }
}
