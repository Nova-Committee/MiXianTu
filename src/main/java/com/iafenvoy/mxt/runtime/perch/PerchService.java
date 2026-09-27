package com.iafenvoy.mxt.runtime.perch;

import com.iafenvoy.mxt.attachment.PerchAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * The single place that decides whether one creature may perch on another and that keeps the record of it. The seat
 * itself belongs to the platform - {@code Entity#getPassengerAttachmentPoint} reads the offset back off this
 * passenger - so what is left here is admission, the record, and the count the capacity is measured against.
 */
public final class PerchService {
    private PerchService() {
    }

    public static Result perch(Entity entity, Entity vehicle, Vec3 offset) {
        if (entity == vehicle) return Result.refused(Failure.SELF);
        if (!isFinite(offset)) return Result.refused(Failure.INVALID_OFFSET);
        if (entity.isPassenger() && entity.getVehicle() != vehicle) return Result.refused(Failure.ALREADY_RIDING);
        if (perchedCountExcept(vehicle, entity) >= MxtServerConfig.INSTANCE.perch.maxPerches.getValue())
            return Result.refused(Failure.FULL);
        // Already riding this very vehicle is a move, not a boarding: the platform answers a second startRiding
        // with false, so that case only rewrites the offset.
        if (entity.getVehicle() != vehicle) {
            // Forced: the vanilla gates are one passenger per vehicle and a passenger that is not sneaking, while
            // how many may perch is this service's own count above.
            if (!entity.startRiding(vehicle, true, true)) return Result.refused(Failure.REFUSED);
        }
        entity.getData(MxtAttachments.PERCH).set(offset);
        return Result.perched();
    }

    public static Result release(Entity entity) {
        PerchAttachment perch = entity.getExistingData(MxtAttachments.PERCH).orElse(null);
        if (perch == null || !perch.perched()) return Result.unchanged();
        // Before the ride ends, so a listener on the dismount still sees a perched creature.
        perch.clear();
        entity.stopRiding();
        return Result.released();
    }

    public static Optional<Vec3> perchOffset(Entity entity) {
        return entity.getExistingData(MxtAttachments.PERCH).flatMap(PerchAttachment::offset);
    }

    // Who the capacity is counted over: one entry per perched passenger. The creature asking for a seat is not one
    // of the seats already taken, so moving an already perched creature to its other side is a move rather than a
    // second slot.
    private static int perchedCountExcept(Entity vehicle, Entity except) {
        int count = 0;
        for (Entity passenger : vehicle.getPassengers())
            if (passenger != except && perchOffset(passenger).isPresent()) count++;
        return count;
    }

    private static boolean isFinite(Vec3 offset) {
        return Double.isFinite(offset.x) && Double.isFinite(offset.y) && Double.isFinite(offset.z);
    }

    public enum Failure {SELF, INVALID_OFFSET, ALREADY_RIDING, FULL, REFUSED}

    public record Result(boolean changed, Failure failure) {
        static Result perched() {
            return new Result(true, null);
        }

        static Result released() {
            return new Result(true, null);
        }

        static Result unchanged() {
            return new Result(false, null);
        }

        static Result refused(Failure failure) {
            return new Result(false, failure);
        }
    }
}
