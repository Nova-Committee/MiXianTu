package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.attachment.ContractData;
import com.iafenvoy.mxt.data.creature.ContractTypeDefinition;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.runtime.behavior.BehaviorContext;
import com.iafenvoy.mxt.runtime.behavior.BehaviorContext.Kind;
import com.iafenvoy.mxt.runtime.behavior.DomainBehaviorService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Map;
import java.util.Optional;

/**
 * Generic owner-follow and recall policy for bound mobs; contract definitions remain data driven.
 */
public final class ContractEventBridge {
    private ContractEventBridge() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob pet) || pet.level().isClientSide()) return;
        ContractData contract = pet.getData(MxtAttachments.CONTRACT);
        ContractTypeDefinition definition = contract.contractType().flatMap(id -> MxtDatapackRegistries.get(MxtDatapackRegistries.CONTRACT_TYPE, id)).orElse(null);
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
        DomainBehaviorService.execute(MxtTypeRegistries.CONTRACT_LIFECYCLE_BEHAVIOR, definition.followBehavior(), BehaviorContext.of(
                Kind.CONTRACT_FOLLOW, contract.contractType().orElseThrow(), pet, FormulaContext.EMPTY, true));
    }

    /**
     * Contract combat callbacks run after vanilla damage is resolved and never mutate the original hit.
     */
    public static void onLivingDamage(Post event) {
        if (event.getEntity().level().isClientSide() || !(event.getSource().getEntity() instanceof Mob pet)) return;
        ContractData contract = pet.getData(MxtAttachments.CONTRACT);
        contract.contractType().flatMap(id -> MxtDatapackRegistries.get(MxtDatapackRegistries.CONTRACT_TYPE, id)).ifPresent(definition -> {
            FormulaContext context = new FormulaContext(Map.of("damage", (double) event.getInflictedDamage()));
            DomainBehaviorService.execute(MxtTypeRegistries.CONTRACT_LIFECYCLE_BEHAVIOR, definition.combatBehavior(), new BehaviorContext(
                    Kind.CONTRACT_COMBAT, contract.contractType().orElseThrow(),
                    Optional.of((ServerLevel) pet.level()), Optional.of(pet), Optional.of(event.getEntity()),
                    Optional.empty(), context, true));
        });
    }

    /**
     * A pet death closes its persisted contract and exposes both break and penalty hooks.
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof Mob pet)) return;
        ContractData contract = pet.getData(MxtAttachments.CONTRACT);
        if (!contract.bound()) return;
        contract.contractType().flatMap(id -> MxtDatapackRegistries.get(MxtDatapackRegistries.CONTRACT_TYPE, id)).ifPresent(definition -> {
            Identifier id = contract.contractType().orElseThrow();
            DomainBehaviorService.execute(MxtTypeRegistries.CONTRACT_LIFECYCLE_BEHAVIOR, definition.breakBehavior(),
                    BehaviorContext.of(Kind.CONTRACT_BREAK, id, pet, FormulaContext.EMPTY, false));
            DomainBehaviorService.execute(MxtTypeRegistries.CONTRACT_LIFECYCLE_BEHAVIOR, definition.penaltyBehavior(),
                    BehaviorContext.of(Kind.CONTRACT_PENALTY, id, pet, FormulaContext.EMPTY, false));
        });
        contract.clear();
    }
}
