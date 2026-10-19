package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.resulting.Result;
import com.iafenvoy.mxt.util.DefinitionText;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * A physical spirit root: using the stack grants the root its component names. The grant goes through the same
 * authoritative service the command and the action use, so a conflict or a repeat is refused rather than bypassed.
 */
public final class SpiritRootItem extends Item {
    public SpiritRootItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        Holder<SpiritRoot> root = stack.get(MxtDataComponents.SPIRIT_ROOT);
        if (root == null) {
            Component message = Component.translatable("item.mxt.spirit_root.unbound");
            if (player instanceof ServerPlayer serverPlayer1) serverPlayer1.sendSystemMessage(message, true);
            return InteractionResult.FAIL;
        }
        Result result = CultivationIdentityService.grantSpiritRoot(serverPlayer, root);
        if (result.failure() != null) {
            Component message = Component.translatable("item.mxt.spirit_root.failed", result.failure().message());
            if (player instanceof ServerPlayer serverPlayer1) serverPlayer1.sendSystemMessage(message, true);
            return InteractionResult.FAIL;
        }
        // Only a root actually gained is spent, and a creative player keeps the stack so the picker can be used freely.
        if (!player.hasInfiniteMaterials()) stack.shrink(1);
        Component message = Component.translatable("item.mxt.spirit_root.granted", DefinitionText.nameOrId(root));
        if (player instanceof ServerPlayer serverPlayer1) serverPlayer1.sendSystemMessage(message, true);
        return InteractionResult.SUCCESS_SERVER;
    }
}
