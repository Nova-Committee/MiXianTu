package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.context.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * The formation currently being evaluated, handed to every per-entity action as {@link Context} extension data:
 * which formation it is, where its centre is and who owns it. Numbers go into the formula's explicit value map.
 */
public record FormationCarrier(Identifier id, BlockPos controller, double radius, Optional<UUID> owner) {
    // Read it through of(Context) rather than directly: the extension map is untyped.
    public static final String KEY = "mxt:formation";

    // Shared with the formula values handed to the same action, so a teleport target and the range test can
    // never disagree.
    public Vec3 center() {
        return this.controller.getCenter();
    }

    public static Optional<FormationCarrier> of(Context context) {
        return context.get(KEY);
    }
}
