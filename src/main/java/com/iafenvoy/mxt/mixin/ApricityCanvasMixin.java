package com.iafenvoy.mxt.mixin;

import com.sighs.apricityui.element.Canvas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = Canvas.class, remap = false)
public abstract class ApricityCanvasMixin {
    // Canvas hardcodes linear sampling; honor the same explicit opt-out as AUI images.
    @ModifyArg(method = "drawCanvas", at = @At(value = "INVOKE", target = "Lcom/sighs/apricityui/render/ImageDrawer;draw(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/sighs/apricityui/spi/TextureKey;FFFFZ)V"), index = 6, require = 1, allow = 1)
    private boolean mxt$canvasBlur(boolean blur) {
        return blur && !"false".equals(((Canvas) (Object) this).getAttribute("blur"));
    }
}
