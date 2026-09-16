package com.iafenvoy.mxt.mixin;

import com.iafenvoy.mxt.runtime.cultivation.TechniqueHoldLookup;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueItemService;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps a reading pose on screen until the read is actually over, instead of until a local timer runs out: a
 * use cycle is timed twice and nothing keeps the two counts in step, so the client's can finish first. Once
 * the client's count drops to zero this reports a small positive count while the entity is still using the
 * item. The count has to keep moving, or a constant would freeze the pose mid-sweep.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    protected int useItemRemaining;

    @Inject(method = "getUseItemRemainingTicks", at = @At("HEAD"), cancellable = true)
    private void mxt$holdReadingPoseUntilTheReadEnds(CallbackInfoReturnable<Integer> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        // Server side: the count is the truth. Otherwise: nothing on screen to keep, or still counting.
        if (!self.level().isClientSide() || !self.isUsingItem() || this.useItemRemaining > 0) return;
        // An item already let go is no longer being read, and its stack is empty by then.
        if (TechniqueHoldLookup.hold(self.getUseItem()) == null) return;
        cir.setReturnValue(TechniqueItemService.loopingUseRemaining(this.useItemRemaining));
    }
}
