package com.iafenvoy.mxt.runtime.creature;

import com.iafenvoy.mxt.data.creature.ContractBehavior;
import com.iafenvoy.mxt.data.item.ContractBellComponent;
import com.iafenvoy.mxt.item.BeastTamingBellItem;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The taming bell and the beast it is tuned to, read and written in one place: the wheel the owner opens and the
 * server that acts on the order both go through here, so which stack counts and what a bell remembers exist once.
 */
public final class ContractBells {
    private ContractBells() {
    }

    // Main hand first: a bell is held rather than worn, and one bell names one beast however it is held.
    public static Optional<ItemStack> held(LivingEntity entity) {
        for (ItemStack stack : List.of(entity.getMainHandItem(), entity.getOffhandItem()))
            if (stack.getItem() instanceof BeastTamingBellItem) return Optional.of(stack);
        return Optional.empty();
    }

    public static Optional<ContractBellComponent> selection(LivingEntity entity) {
        return held(entity).map(stack -> stack.get(MxtDataComponents.CONTRACT_BELL));
    }

    // Tuning carries the creature's own answer about the orders it takes, so the owner's client can draw the page
    // without resolving the creature, which may not be loaded there at all.
    public static void select(ItemStack stack, Mob beast) {
        List<Identifier> behaviors = Contracts.operations(beast)
                .map(operations -> operations.behaviors().stream().map(ContractBehavior::id).toList())
                .orElse(List.of());
        stack.set(MxtDataComponents.CONTRACT_BELL, new ContractBellComponent(beast.getUUID(), beast.getDisplayName(), behaviors));
    }

    // The tuned beast, if it is loaded anywhere: a bell remembers a creature, not a place, so every level is asked.
    public static Optional<Mob> beast(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        UUID id = selection(player).map(ContractBellComponent::beast).orElse(null);
        if (id == null) return Optional.empty();
        for (ServerLevel level : server.getAllLevels())
            if (level.getEntity(id) instanceof Mob mob) return Optional.of(mob);
        return Optional.empty();
    }
}
