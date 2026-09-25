package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.runtime.wheel.WheelSourceTypes;
import com.iafenvoy.mxt.attachment.ContractAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.creature.ContractBells;
import com.iafenvoy.mxt.runtime.creature.ContractFeedback;
import com.iafenvoy.mxt.runtime.creature.ContractService;
import com.iafenvoy.mxt.runtime.creature.Contracts;
import com.iafenvoy.mxt.screen.wheel.WheelMenuController;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.NotNull;

/**
 * The owner's tool for one bound beast: right-clicking a creature tunes the bell to it, and right-clicking with
 * nothing in front opens the order wheel on that beast's page.
 *
 * <p>Which orders exist belongs to the creature, so nothing here names one - the bell only says which beast is
 * being ordered. A bell that outlives its beast, or one carried onto a server where that beast is not loaded,
 * orders nothing: the wheel re-reads the creature and the record before it acts.</p>
 */
public final class BeastTamingBellItem extends Item {
    public BeastTamingBellItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult interactLivingEntity(@NotNull ItemStack stack, @NotNull Player player, @NotNull LivingEntity target, @NotNull InteractionHand hand) {
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer owner) || !(target instanceof Mob beast)) {
            ItemFeedback.send(player, ContractFeedback.of(ContractService.Failure.NOT_CONTRACTABLE));
            return InteractionResult.FAIL;
        }
        ContractAttachment contract = beast.getData(MxtAttachments.CONTRACT);
        if (!contract.bound() || Contracts.ownerOf(beast).filter(owner.getUUID()::equals).isEmpty()) {
            ItemFeedback.send(player, ContractFeedback.of(ContractService.Failure.NOT_BOUND));
            return InteractionResult.FAIL;
        }
        // A beast the framework cannot ask for anything is not a beast to point a bell at, even bound.
        if (Contracts.operations(beast).isEmpty()) {
            ItemFeedback.send(player, ContractFeedback.of(ContractService.Failure.NOT_CONTRACTABLE));
            return InteractionResult.FAIL;
        }
        ContractBells.select(stack, beast);
        ItemFeedback.send(player, Component.translatable("item.mxt.beast_taming_bell.selected", beast.getDisplayName()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        // The wheel is a screen, so it is the client that opens it; the server half of this use has nothing to do
        // with the order, which is sent from the wheel and checked again there.
        if (level.isClientSide() && FMLEnvironment.getDist() == Dist.CLIENT)
            WheelMenuController.openAt(WheelSourceTypes.CONTRACT,
                    Component.translatable("item.mxt.beast_taming_bell.no_target"));
        return InteractionResult.SUCCESS;
    }
}