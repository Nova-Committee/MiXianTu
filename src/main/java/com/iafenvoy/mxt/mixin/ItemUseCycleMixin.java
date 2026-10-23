package com.iafenvoy.mxt.mixin;

import com.iafenvoy.mxt.runtime.hold.ItemUseCycles;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Item.class)
public abstract class ItemUseCycleMixin {
    @WrapMethod(method = "use")
    private InteractionResult mxt$startCycle(Level level, Player player, InteractionHand hand, Operation<InteractionResult> original) {
        return ItemUseCycles.use(player, hand, () -> original.call(level, player, hand));
    }

    @ModifyReturnValue(method = "getUseDuration", at = @At("RETURN"))
    private int mxt$cycleDuration(int original, ItemStack itemStack, LivingEntity user) {
        return ItemUseCycles.duration(user, itemStack, original);
    }

    @ModifyReturnValue(method = "getUseAnimation", at = @At("RETURN"))
    private ItemUseAnimation mxt$cycleAnimation(ItemUseAnimation original, ItemStack itemStack) {
        return ItemUseCycles.animation(itemStack, original);
    }

    @WrapMethod(method = "finishUsingItem")
    private ItemStack mxt$finishCycle(ItemStack itemStack, Level level, LivingEntity entity, Operation<ItemStack> original) {
        return ItemUseCycles.finish(level, entity, itemStack, () -> original.call(itemStack, level, entity));
    }
}
