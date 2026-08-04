package com.iafenvoy.mxt.util.math;

import com.mojang.serialization.Codec;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Coordinate space used by velocity actions.
 */
public enum Space implements StringRepresentable {
    WORLD(false, false, false, false),
    LOCAL(true, false, false, false),
    LOCAL_HORIZONTAL(true, false, true, false),
    LOCAL_HORIZONTAL_NORMALIZED(true, false, true, true),
    VELOCITY(true, true, false, false),
    VELOCITY_NORMALIZED(true, true, false, true),
    VELOCITY_HORIZONTAL(true, true, true, false),
    VELOCITY_HORIZONTAL_NORMALIZED(true, true, true, true);

    public static final Codec<Space> CODEC = StringRepresentable.fromEnum(Space::values);
    private final boolean process;
    private final boolean velocity;
    private final boolean horizontal;
    private final boolean normalize;

    Space(boolean process, boolean velocity, boolean horizontal, boolean normalize) {
        this.process = process;
        this.velocity = velocity;
        this.horizontal = horizontal;
        this.normalize = normalize;
    }

    public void toGlobal(Vector3f vector, Entity entity) {
        if (!this.process) return;
        Vec3 forward = this.velocity ? entity.getDeltaMovement() : entity.getLookAngle();
        if (this.horizontal) forward = new Vec3(forward.x, 0.0D, forward.z);
        transformVectorToBase(forward, vector, entity.getYRot(), this.normalize);
    }

    public static void transformVectorToBase(Vec3 forward, Vector3f vector, float yaw, boolean normalizeBase) {
        double scaleDouble = forward.length();
        if (scaleDouble <= 0.007D) {
            vector.zero();
            return;
        }
        float scale = (float) scaleDouble;
        Vec3 normalized = forward.normalize();
        double xX;
        double xZ;
        double zX = 0.0D;
        double zY = normalized.y;
        double zZ = 0.0D;
        if (Math.abs(zY) != 1.0D) {
            zX = normalized.x;
            zZ = normalized.z;
            xX = normalized.z;
            xZ = -normalized.x;
            float factor = (float) (1.0D / Math.sqrt(xX * xX + xZ * xZ));
            xX *= factor;
            xZ *= factor;
        } else {
            float radians = -yaw * Mth.DEG_TO_RAD;
            xX = Mth.cos(radians);
            xZ = -Mth.sin(radians);
        }
        Matrix3f matrix = new Matrix3f();
        matrix.set(0, 0, (float) xX).set(1, 0, 0.0F).set(2, 0, (float) xZ);
        matrix.set(0, 1, (float) (zY * xZ)).set(1, 1, (float) (zZ * xX - zX * xZ)).set(2, 1, (float) (-zY * xX));
        matrix.set(0, 2, (float) zX).set(1, 2, (float) zY).set(2, 2, (float) zZ);
        if (!normalizeBase) matrix.scale(scale, scale, scale);
        vector.mulTranspose(matrix);
    }

    @Override
    public @NotNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
