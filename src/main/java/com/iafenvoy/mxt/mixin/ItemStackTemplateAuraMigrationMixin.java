package com.iafenvoy.mxt.mixin;

import com.iafenvoy.mxt.runtime.spirit.StackAuraMigration;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.ItemStackTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackTemplate.class)
public abstract class ItemStackTemplateAuraMigrationMixin {
    @Shadow @Final @Mutable private DataComponentPatch components;

    @Inject(method = "<init>(Lnet/minecraft/core/Holder;ILnet/minecraft/core/component/DataComponentPatch;)V", at = @At("RETURN"))
    private void mxt$normalizeAuraOnLoad(CallbackInfo ci) {
        this.components = StackAuraMigration.normalizedPatch((ItemStackTemplate) (Object) this, this.components);
    }
}
