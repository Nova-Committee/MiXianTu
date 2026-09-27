package com.iafenvoy.mxt.mixin;

import com.iafenvoy.mxt.attachment.PerchAttachment;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The seat a passenger declares for itself, in place of the one its vehicle would give it. Every vanilla vehicle
 * that wants a custom seat overrides this exact method; a player is the one vehicle that does not.
 */
@Mixin(Entity.class)
public abstract class EntityPerchMixin {
    @Inject(method = "getPassengerAttachmentPoint", at = @At("HEAD"), cancellable = true)
    private void mxt$perchSeat(Entity passenger, EntityDimensions dimensions, float scale, CallbackInfoReturnable<Vec3> cir) {
        PerchAttachment perch = passenger.getExistingData(MxtAttachments.PERCH).orElse(null);
        if (perch == null) return;
        Vec3 offset = perch.offset().orElse(null);
        if (offset == null) return;
        cir.setReturnValue(mxt$seat(offset, dimensions, scale, ((Entity) (Object) this).getYRot()));
    }

    // The riding position is the vehicle's plus this point, so the point is the whole answer. y is measured down
    // from the vehicle's current top rather than from its feet, which is what makes a crouching vehicle lower its
    // passenger without the seat having to know about poses, and the offset is scaled the way the vanilla seats
    // are so a smaller vehicle carries its perch proportionally. The rotation repeats the private
    // EntityAttachments.transformPoint the platform would otherwise apply; borrowing it would allocate an
    // EntityAttachments on every positioning tick.
    @Unique
    private static Vec3 mxt$seat(Vec3 offset, EntityDimensions dimensions, float scale, float yRot) {
        return new Vec3(offset.x * scale, dimensions.height() + offset.y * scale, offset.z * scale)
                .yRot(-yRot * (float) (Math.PI / 180.0));
    }
}
