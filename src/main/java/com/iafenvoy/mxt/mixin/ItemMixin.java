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
 * Makes any item a technique manual when its {@code technique_binding} declares a hold.
 *
 * <h2>Why this is a mixin rather than an item class</h2>
 * A hold rides the vanilla use cycle, and the cycle asks the <em>item</em> how long it lasts and what
 * pose it plays. Answering those from a subclass would mean only items of that subclass could ever be
 * manuals - which contradicts what this mod promises everywhere else, that a binding applies to any
 * registered item, including one registered by another mod or by KubeJS. Injecting the three questions
 * the cycle asks keeps that promise.
 *
 * <h2>What is deliberately not injected</h2>
 * {@code finishUsingItem} is left alone. A manual is an ordinary item with no {@code CONSUMABLE}
 * component, so vanilla's own implementation already returns the stack unchanged and nothing is eaten.
 * An earlier design put the hold on {@code CONSUMABLE} instead, which made the stack food and forced a
 * race to remove the component before the game consumed it; not using that component removes the race
 * rather than winning it.
 *
 * <p>An item that genuinely is food and also declares a hold is therefore still eaten when the hold
 * finishes. That is vanilla behaviour for that item, and the alternative - quietly disabling its food
 * behaviour - would be a larger surprise than leaving it alone.</p>
 *
 * <h2>Where the values come from</h2>
 * {@link TechniqueHoldLookup}, which is built on both the client and the server from their own copy of
 * the synced data pack registries. Nothing here is written onto a stack, so there is no state for the
 * two sides to disagree about.
 */
@Mixin(Item.class)
public abstract class ItemMixin {
    /**
     * Starts the reading gesture instead of letting the click fall through.
     */
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void mxt$startTechniqueHold(Level level, Player player, InteractionHand hand,
                                        CallbackInfoReturnable<InteractionResult> cir) {
        if (TechniqueHoldLookup.hold(player.getItemInHand(hand)) == null) return;
        player.startUsingItem(hand);
        cir.setReturnValue(InteractionResult.CONSUME);
    }

    /**
     * How long the hold lasts, from the binding rather than from a component.
     */
    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void mxt$techniqueHoldDuration(ItemStack stack, LivingEntity user,
                                           CallbackInfoReturnable<Integer> cir) {
        TechniqueBinding binding = TechniqueHoldLookup.hold(stack);
        if (binding != null) cir.setReturnValue(binding.learnTime());
    }

    /**
     * The pose the hold plays, from the binding rather than from a component.
     *
     * <p>This one matters most: it is called from the render loop and it is the question a plain item
     * cannot answer on its own, which is precisely what left the client showing nothing while the server
     * ran a hold.</p>
     */
    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void mxt$techniqueHoldAnimation(ItemStack stack, CallbackInfoReturnable<ItemUseAnimation> cir) {
        TechniqueBinding binding = TechniqueHoldLookup.hold(stack);
        if (binding != null) cir.setReturnValue(binding.holdAnimation());
    }
}
