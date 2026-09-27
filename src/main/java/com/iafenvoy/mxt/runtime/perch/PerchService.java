package com.iafenvoy.mxt.runtime.perch;

import com.iafenvoy.mxt.api.Perchable;
import com.iafenvoy.mxt.attachment.PerchAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The single place that decides whether one creature may perch on another and that keeps the record of it. The seat
 * itself belongs to the platform - {@code Entity#getPassengerAttachmentPoint} reads the offset back off this
 * passenger - so what is left here is admission, the record, and the count the capacity is measured against.
 */
public final class PerchService {
    private PerchService() {
    }

    // Who asks the creature: an addon implements {@link Perchable} and the framework does the rest, so no caller
    // has to know a seat coordinate.
    public static Result perch(Entity entity, Entity vehicle) {
        if (entity == vehicle) return Result.refused(Failure.SELF);
        if (!(entity instanceof Perchable perchable)) return Result.refused(Failure.NOT_WILLING);
        Vec3 offset = perchable.perchOffset(vehicle, claimedOffsets(vehicle, entity)).orElse(null);
        return offset == null ? Result.refused(Failure.NOT_WILLING) : perch(entity, vehicle, offset);
    }

    public static Result perch(Entity entity, Entity vehicle, Vec3 offset) {
        if (entity == vehicle) return Result.refused(Failure.SELF);
        if (!isFinite(offset)) return Result.refused(Failure.INVALID_OFFSET);
        if (entity.isPassenger() && entity.getVehicle() != vehicle) return Result.refused(Failure.ALREADY_RIDING);
        if (claimedOffsets(vehicle, entity).size() >= MxtServerConfig.INSTANCE.perch.maxPerches.getValue())
            return Result.refused(Failure.FULL);
        boolean boarding = perchOffset(entity).isEmpty();
        // Already riding this very vehicle is a move, not a boarding: the platform answers a second startRiding
        // with false, so that case only rewrites the offset.
        if (entity.getVehicle() != vehicle) {
            // Forced: the vanilla gates are one passenger per vehicle and a passenger that is not sneaking, while
            // how many may perch is this service's own count above.
            if (!entity.startRiding(vehicle, true, true)) return Result.refused(Failure.REFUSED);
        }
        entity.getData(MxtAttachments.PERCH).set(offset);
        if (boarding && entity instanceof Perchable perchable) perchable.onPerched(vehicle);
        return Result.perched();
    }

    public static Result release(Entity entity) {
        PerchAttachment perch = entity.getExistingData(MxtAttachments.PERCH).orElse(null);
        if (perch == null || !perch.perched()) return Result.unchanged();
        Entity vehicle = entity.getVehicle();
        // Before the ride ends, so a listener on the dismount still sees a perched creature.
        perch.clear();
        entity.stopRiding();
        if (vehicle != null && entity instanceof Perchable perchable) perchable.onPerchReleased(vehicle);
        return Result.released();
    }

    public static Optional<Vec3> perchOffset(Entity entity) {
        return entity.getExistingData(MxtAttachments.PERCH).flatMap(PerchAttachment::offset);
    }

    // The seats already taken, which is both what the capacity is measured against and what a creature answering
    // for itself sees. The creature asking for a seat is not one of them, so moving an already perched creature to
    // its other side is a move rather than a second slot.
    private static List<Vec3> claimedOffsets(Entity vehicle, Entity except) {
        List<Vec3> claimed = new ArrayList<>();
        for (Entity passenger : vehicle.getPassengers())
            if (passenger != except) perchOffset(passenger).ifPresent(claimed::add);
        return claimed;
    }

    private static boolean isFinite(Vec3 offset) {
        return Double.isFinite(offset.x) && Double.isFinite(offset.y) && Double.isFinite(offset.z);
    }

    // NOT_WILLING covers both "the creature refused this vehicle" and "the creature does not implement the
    // contract at all": either way no seat was offered.
    public enum Failure {SELF, INVALID_OFFSET, ALREADY_RIDING, FULL, REFUSED, NOT_WILLING}

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
