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
 * Keeps a reading pose on screen until the read is actually over, instead of until a local timer runs out.
 *
 * <h2>The problem</h2>
 * A use cycle is timed twice: the server counts its own ticks and the client counts its own, and nothing
 * keeps the two in step. They start together and drift apart only as far as the server falls behind, so
 * on a server that is not holding twenty ticks a second the server's sixty ticks take longer in real time
 * than the client's sixty. The pose is drawn from the client's number and stops when it reaches zero,
 * while the technique is granted from the server's - which means the arm drops back to the ordinary
 * holding pose part way through, and the player is left holding a manual that looks finished and is not.
 *
 * <h2>What this does instead</h2>
 * The client's count still runs, and while it still has ticks left nothing here interferes. Once it drops
 * to zero the pose would end, so instead of reporting zero this reports a small positive count for as long
 * as the entity is genuinely still using the item. The pose therefore lasts exactly as long as the read
 * does: it ends when the player lets go, and it ends when the server says the read is finished, because
 * both of those clear the using flag.
 *
 * <h2>Why the number keeps moving</h2>
 * Reporting a constant would freeze the pose mid-sweep, which looks as broken as ending it. Reading the
 * count back into the range the pose code expects - see
 * {@code TechniqueItemService#loopingUseRemaining} - lets the repeating motion carry on from where it
 * stopped, so the extra time looks like more reading rather than a stuck frame or a stutter.</p>
 *
 * <h2>What it deliberately does not touch</h2>
 * The server's own count. It is never overridden, because it is the one that decides when the read is
 * over, when the technique is granted and what {@code Stop} reports - and the server audit pins all three.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    protected int useItemRemaining;

    @Inject(method = "getUseItemRemainingTicks", at = @At("HEAD"), cancellable = true)
    private void mxt$holdReadingPoseUntilTheReadEnds(CallbackInfoReturnable<Integer> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        // Server side: the count is the truth and is left alone. Not using: nothing is on screen to keep.
        // Still counting: the client has not run out yet, so the ordinary value is the right one.
        if (!self.level().isClientSide() || !self.isUsingItem() || this.useItemRemaining > 0) return;
        // An item that has already been let go is no longer being read, and its stack is empty by then.
        if (TechniqueHoldLookup.hold(self.getUseItem()) == null) return;
        cir.setReturnValue(TechniqueItemService.loopingUseRemaining(this.useItemRemaining));
    }
}
