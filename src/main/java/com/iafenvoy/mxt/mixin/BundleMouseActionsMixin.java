package com.iafenvoy.mxt.mixin;

import com.iafenvoy.mxt.item.SpiritStoneBagItem;
import net.minecraft.client.gui.BundleMouseActions;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BundleMouseActions.class)
public abstract class BundleMouseActionsMixin {
    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void mxt$matchSpiritStoneBag(Slot slot, CallbackInfoReturnable<Boolean> callback) {
        // The vanilla bundle tag doubles as the dyeing recipe input.
        if (slot.getItem().getItem() instanceof SpiritStoneBagItem) callback.setReturnValue(true);
    }
}
