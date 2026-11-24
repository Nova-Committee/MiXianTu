package com.iafenvoy.mxt.mixin;

import com.iafenvoy.mxt.runtime.spirit.StackAuraMigration;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
public abstract class ItemStackAuraMigrationMixin {
    @Inject(method = "<init>(Lnet/minecraft/core/Holder;ILnet/minecraft/core/component/DataComponentPatch;)V", at = @At("RETURN"))
    private void mxt$normalizeAuraOnLoad(CallbackInfo ci) {
        StackAuraMigration.normalize((ItemStack) (Object) this);
    }

    @Inject(method = {"applyComponents(Lnet/minecraft/core/component/DataComponentPatch;)V",
            "applyComponentsAndValidate(Lnet/minecraft/core/component/DataComponentPatch;)V"}, at = @At("RETURN"))
    private void mxt$normalizeAuraAfterPatch(CallbackInfo ci) {
        StackAuraMigration.normalize((ItemStack) (Object) this);
    }
}
