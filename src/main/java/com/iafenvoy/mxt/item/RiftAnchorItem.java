package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.data.item.RiftComponent;
import com.iafenvoy.mxt.item.block.entity.RiftBlockEntity;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * A rift anchor: it carries a destination and a colour and stamps them into rifts that already exist.
 *
 * <p>It deliberately places nothing - a rift block is placed with the rift's own block item, like any other
 * block. What is left here is the adjusting: used on a rift it re-aims and recolours that rift, and used while
 * sneaking it records the dimension the player is standing in, which is how a destination is chosen without any
 * command. The alternative spelling of all of this is {@code /mxt rift}.
 */
public final class RiftAnchorItem extends Item {
    public RiftAnchorItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        RiftComponent component = stack.getOrDefault(MxtDataComponents.RIFT, RiftComponent.EMPTY);
        if (level.getBlockEntity(pos) instanceof RiftBlockEntity rift) {
            if (!level.isClientSide()) {
                rift.setTarget(component.target());
                rift.setColor(component.color());
                if (player != null)
                    ItemFeedback.send(player, Component.translatable("block.mxt.rift.retargeted", component.target().toString()));
            }
            return InteractionResult.SUCCESS;
        }
        if (player != null && player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                Identifier dimension = level.dimension().identifier();
                stack.set(MxtDataComponents.RIFT, component.withTarget(dimension));
                ItemFeedback.send(player, Component.translatable("item.mxt.rift_anchor.bound", dimension.toString()));
            }
            return InteractionResult.SUCCESS;
        }
        // Nothing to do on any other block, and nothing to place: the block's own item is what places rifts.
        return InteractionResult.PASS;
    }
}
