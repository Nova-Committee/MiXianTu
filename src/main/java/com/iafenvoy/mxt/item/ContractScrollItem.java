package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.registry.MxtDataComponents;

import com.iafenvoy.mxt.data.item.ContractScrollComponent;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.creature.ContractService;
import com.iafenvoy.mxt.runtime.creature.ContractService.Result;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

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
        Result result = ContractService.bind(target.getData(MxtAttachments.CONTRACT),
                scroll.contractType().orElseThrow(), player, target, player.level().getGameTime(),
                FormulaContexts.forEntities(player, target, Map.of()));
        if (!result.changed()) {
            ItemFeedback.send(player, Component.translatable("item.mxt.contract_scroll.failed", result.failure().name()));
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        ItemFeedback.send(player, Component.translatable("item.mxt.contract_scroll.bound"));
        return InteractionResult.SUCCESS;
    }
}
