package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.context.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * The formation currently being evaluated, handed to every per-entity action as {@link Context}
 * extension data.
 *
 * <p>The split follows what {@link com.iafenvoy.mxt.util.formula.FormulaContext} documents for
 * itself: numbers go into its explicit value map ({@code formation_radius}, {@code distance},
 * {@code formation_x/y/z}), while everything that belongs to no number — which formation this is,
 * where its centre is, who owns it — travels here as an object.</p>
 *
 * <p>Actions read it through {@link #of(Context)} because the extension map is untyped: keeping the
 * key and the cast in one place is what stops every new condition from repeating the pair.</p>
 *
 * <p>Carries no level: the ticker only ever dispatches entities of the formation's own level, so a
 * condition that needs the level already has it from the entity it was handed.</p>
 */
public record FormationCarrier(Identifier id, BlockPos controller, double radius, Optional<UUID> owner) {
    /**
     * The extension-data key. Read it through {@link #of(Context)} rather than using it directly.
     */
    public static final String KEY = "mxt:formation";

    /**
     * The centre of the formation's spherical range. Shared with the formula values handed to the
     * same action, so a teleport target and the range test can never disagree by half a block.
     */
    public Vec3 center() {
        return this.controller.getCenter();
    }

    /**
     * The formation being evaluated, or empty when the caller is outside a formation context.
     */
    public static Optional<FormationCarrier> of(Context context) {
        return context.get(KEY);
    }
}
