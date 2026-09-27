package com.iafenvoy.mxt.api;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * A creature that may be perched on another body. Implementing this interface is the whole of what the framework
 * asks of it: the record, the seat point, the capacity and every admission check stay in the framework
 * ({@code PerchService}), so an addon ships the creature and its answer rather than a seat of its own.
 *
 * <p>The offset is in the vehicle's own frame: {@code x} is the vehicle's left, {@code z} is the way it faces, and
 * {@code y} counts <b>down from the vehicle's current top</b>, so a sneaking or smaller-pose vehicle carries the
 * passenger along without either side knowing a pose. It is scaled by whatever scale the platform hands the seat
 * hook, the way vanilla seats are.</p>
 *
 * <p>Not implementing it is a choice with a meaning: the creature can still be an ordinary passenger of anything
 * (that is vanilla), but nothing may be perched on a body through the framework.</p>
 */
public interface Perchable {
    /**
     * Where this creature wants to sit on that vehicle, in that vehicle's frame, or empty to refuse this vehicle
     * (the framework then writes nothing and reports a refusal). {@code claimed} holds the offsets the vehicle's
     * other perched passengers have already declared, so a creature with more than one seat to offer can pick a
     * free one; refusing because every seat is taken is a legitimate answer.
     */
    Optional<Vec3> perchOffset(Entity vehicle, List<Vec3> claimed);

    // Once the record is written, so the creature can settle into whatever it keeps while perched. A move to
    // another seat of the same vehicle is not a second boarding and does not call this.
    default void onPerched(Entity vehicle) {
    }

    // After the record is cleared and the ride has ended, however it ended: released by its own side, dropped by
    // the server's policy, or noticed as stale. The vehicle is the one it was on; a record whose vehicle is already
    // gone has none left to hand over, so ask PerchService.perchOffset when state has to be exact.
    default void onPerchReleased(Entity vehicle) {
    }
}
