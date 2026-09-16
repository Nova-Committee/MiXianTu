package com.iafenvoy.mxt.mixin;

import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueHoldLookup;
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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes any item a technique manual when its {@code technique_binding} declares a hold. A mixin rather than an
 * item subclass, so a binding applies to any registered item, including one from another mod or KubeJS. The
 * values come from {@link TechniqueHoldLookup}, built on each side from its own copy of the synced registries,
 * and nothing is written onto a stack, so there is no state for the two sides to disagree about.
 */
@Mixin(Item.class)
public abstract class ItemMixin {
    /**
     * Starts the reading gesture instead of letting the click fall through.
     */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void mxt$startTechniqueHold(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (TechniqueHoldLookup.hold(player.getItemInHand(hand)) == null) return;
        player.startUsingItem(hand);
        cir.setReturnValue(InteractionResult.CONSUME);
    }

    /**
     * How long the hold lasts, from the binding rather than from a component.
     */
    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void mxt$techniqueHoldDuration(ItemStack itemStack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        TechniqueBinding binding = TechniqueHoldLookup.hold(itemStack);
        if (binding != null) cir.setReturnValue(binding.learnTime());
    }

    /**
     * The pose the hold plays, which is the question a plain item cannot answer on its own.
     */
    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void mxt$techniqueHoldAnimation(ItemStack itemStack, CallbackInfoReturnable<ItemUseAnimation> cir) {
        TechniqueBinding binding = TechniqueHoldLookup.hold(itemStack);
        if (binding != null) cir.setReturnValue(binding.holdAnimation());
    }
}
