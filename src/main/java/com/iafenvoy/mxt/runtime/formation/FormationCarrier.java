package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.context.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * The formation currently being evaluated, handed to every per-entity action as {@link Context}
 * extension data. Which formation this is, where its centre is and who owns it travels here as an object;
 * numbers go into the formula's explicit value map. Read it through {@link #of(Context)}, because the
 * extension map is untyped.
 */
public record FormationCarrier(Identifier id, BlockPos controller, double radius, Optional<UUID> owner) {
    /**
     * The extension-data key; read it through {@link #of(Context)} rather than using it directly.
     */
    public static final String KEY = "mxt:formation";

    /**
     * The centre of the formation's spherical range, shared with the formula values handed to the same
     * action so a teleport target and the range test can never disagree.
     */
    public Vec3 center() {
        return this.controller.getCenter();
    }

    /**
     * The formation being evaluated, or empty outside a formation context.
     */
    public static Optional<FormationCarrier> of(Context context) {
        return context.get(KEY);
    }
}
