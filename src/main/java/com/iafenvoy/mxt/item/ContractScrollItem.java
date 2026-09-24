package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.data.item.ContractScrollComponent;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.creature.ContractFeedback;
import com.iafenvoy.mxt.runtime.creature.ContractService;
import com.iafenvoy.mxt.runtime.creature.ContractService.Result;
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
            ItemFeedback.send(player, Component.translatable("item.mxt.contract_scroll.unbound"));
            return InteractionResult.FAIL;
        }
        // Only a Mob can be a contract subject, and only a server player can own one; both are plain answers
        // rather than errors, so the scroll reports them the way it reports any other refusal.
        if (!(player instanceof ServerPlayer owner) || !(target instanceof Mob creature)) {
            ItemFeedback.send(player, ContractFeedback.of(ContractService.Failure.NOT_CONTRACTABLE));
            return InteractionResult.FAIL;
        }
        Result result = ContractService.bind(scroll.contractType().orElseThrow(), owner, creature, false);
        if (!result.changed()) {
            ItemFeedback.send(player, ContractFeedback.of(result.failure()));
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        ItemFeedback.send(player, Component.translatable("item.mxt.contract_scroll.bound"));
        return InteractionResult.SUCCESS;
    }
}
