package com.iafenvoy.mxt.mixin;

import com.iafenvoy.mxt.api.MountVehicle;
import com.iafenvoy.mxt.runtime.artifact.FlightService;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mount that leaves the world for good gives its artifact back, whoever wrote the body: custody is the framework's,
 * so a vehicle an addon wrote never has to know about it and every way a flight can end lands in the same place.
 */
@Mixin(Entity.class)
public abstract class EntityMountRemovalMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void mxt$returnArtifact(Entity.RemovalReason reason, CallbackInfo ci) {
        // Only the reasons that end an entity for good, and only on the side that owns the item: an unloaded or
        // re-dimensioned mount keeps its artifact, so leaving a chunk cannot duplicate it.
        if (!reason.shouldDestroy()) return;
        Entity body = (Entity) (Object) this;
        if (body.level().isClientSide() || !(body instanceof MountVehicle vehicle)) return;
        FlightService.giveBack(vehicle);
        FlightService.clearSeatMarkers(body);
    }
}
