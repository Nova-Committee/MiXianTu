package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService.Failure;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService.Result;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
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
import org.jspecify.annotations.Nullable;

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
            ItemFeedback.send(player, Component.translatable("item.mxt.spirit_root.unbound"));
            return InteractionResult.FAIL;
        }
        Result result = CultivationIdentityService.grantSpiritRoot(serverPlayer, HolderHelper.id(root), root.value());
        if (!result.changed()) {
            ItemFeedback.send(player, failure(result.failure()));
            return InteractionResult.FAIL;
        }
        // Only a root actually gained is spent, and a creative player keeps the stack so the picker can be used freely.
        if (!player.hasInfiniteMaterials()) stack.shrink(1);
        ItemFeedback.send(player, Component.translatable("item.mxt.spirit_root.granted", DefinitionText.name(root)));
        return InteractionResult.SUCCESS_SERVER;
    }

    private static Component failure(@Nullable Failure failure) {
        if (failure == null) return Component.translatable("item.mxt.spirit_root.failed", "-");
        return switch (failure) {
            case ALREADY_HELD -> Component.translatable("item.mxt.spirit_root.already_held");
            case ELEMENT_CONFLICT -> Component.translatable("item.mxt.spirit_root.conflict");
            case DISABLED -> Component.translatable("item.mxt.spirit_root.disabled");
            default -> Component.translatable("item.mxt.spirit_root.failed", failure.name());
        };
    }
}
