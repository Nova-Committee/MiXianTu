package com.iafenvoy.mxt.mixin;

import com.iafenvoy.mxt.render.TribulationClientEffects;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The one place vanilla decides whether to darken the world: {@code GameRenderer#tick} asks the boss overlay
 * whether any of its bars wants it, and that answer is what drives the fog colour and the lightmap. Answering
 * yes here as well is therefore the whole feature - the fade vanilla applies to the answer comes along with it,
 * and no server-side flag, packet or boss bar is involved.
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
    @Inject(method = "shouldDarkenScreen", at = @At("HEAD"), cancellable = true)
    private void mxt$darkenForTribulation(CallbackInfoReturnable<Boolean> cir) {
        if (TribulationClientEffects.shouldDarken()) cir.setReturnValue(true);
    }
}
