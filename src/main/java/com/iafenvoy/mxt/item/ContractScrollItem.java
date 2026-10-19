package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.data.item.ContractScrollComponent;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.creature.ContractFeedback;
import com.iafenvoy.mxt.runtime.creature.ContractService;
import com.iafenvoy.mxt.runtime.creature.ContractService.Result;
import com.iafenvoy.mxt.runtime.failure.ContractFailure;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * One universal entry point for all datapack-defined creature contracts.
 */
public final class ContractScrollItem extends Item {
    public ContractScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, @NotNull Player player,
                                                           @NotNull LivingEntity target, @NotNull InteractionHand hand) {
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        ContractScrollComponent scroll = stack.getOrDefault(MxtDataComponents.CONTRACT_SCROLL, ContractScrollComponent.EMPTY);
        if (scroll.contractType().isEmpty()) {
            Component message = Component.translatable("item.mxt.contract_scroll.unbound");
            if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(message, true);
            return InteractionResult.FAIL;
        }
        // Only a Mob can be a contract subject, and only a server player can own one; both are plain answers
        // rather than errors, so the scroll reports them the way it reports any other refusal.
        if (!(player instanceof ServerPlayer owner) || !(target instanceof Mob creature)) {
            Component message = ContractFeedback.of(ContractFailure.NOT_CONTRACTABLE);
            if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(message, true);
            return InteractionResult.FAIL;
        }
        Result result = ContractService.bind(scroll.contractType().orElseThrow(), owner, creature, false);
        if (!result.changed()) {
            Component message = ContractFeedback.of(result.failure());
            if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(message, true);
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        Component message = Component.translatable("item.mxt.contract_scroll.bound");
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(message, true);
        return InteractionResult.SUCCESS;
    }
}
