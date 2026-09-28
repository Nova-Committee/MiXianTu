package com.iafenvoy.mxt.screen.multiblock;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The scene camera, in one place: the renderer applies it as a pose and the view picks blocks with it, so the two
 * cannot drift apart. Structure coordinates are y-up, the box centre is the origin, and one block measures
 * {@code scale} GUI pixels; camera space is the usual +x right, +y up on screen, +z toward the viewer.
 */
record MultiblockSceneCamera(float yaw, float pitch, float scale, float centreX, float centreY, float centreZ) {
    // Far enough outside any structure to sit behind it, and inside the ortho's own depth range.
    private static final float FAR = 100.0F;
    private static final float PARALLEL_EPSILON = 1.0E-6F;

    static MultiblockSceneCamera of(float yaw, float pitch, float scale, MultiblockSceneRenderState.SceneBounds bounds) {
        return new MultiblockSceneCamera(yaw, pitch, scale, bounds.centreX(), bounds.centreY(), bounds.centreZ());
    }

    // Block models are y-up and the PIP texture is y-down, while the base pose has already negated z, so the prefix
    // negates y and z - the one the vanilla item renderers use. One negation short mirrors the scene, which leaves
    // every face culled from the wrong side.
    void apply(PoseStack poseStack) {
        poseStack.scale(1.0F, -1.0F, -1.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(this.pitch));
        poseStack.mulPose(Axis.YP.rotationDegrees(this.yaw));
        poseStack.translate(-this.centreX, -this.centreY, -this.centreZ);
    }

    // The cell under the mouse, or null when the ray misses every one of them. The rectangle is the scene's, in GUI
    // pixels, which is also what tells the camera which pixel the mouse is on.
    MultiblockSceneRenderState.@Nullable SceneBlock pick(List<MultiblockSceneRenderState.SceneBlock> blocks,
                                                        double mouseX, double mouseY,
                                                        int x0, int y0, int x1, int y1) {
        Matrix3f inverse = new Matrix3f().rotateX((float) Math.toRadians(this.pitch))
                .rotateY((float) Math.toRadians(this.yaw)).invert();
        float cameraX = (float) (mouseX - (x0 + x1) * 0.5D) / this.scale;
        float cameraY = -(float) (mouseY - (y0 + y1) * 0.5D) / this.scale;
        // Walked in from the far plane, so the first cell the ray meets is the one the player sees.
        Vector3f origin = inverse.transform(new Vector3f(cameraX, cameraY, FAR))
                .add(this.centreX, this.centreY, this.centreZ);
        Vector3f direction = inverse.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        MultiblockSceneRenderState.SceneBlock nearest = null;
        float nearestDistance = Float.MAX_VALUE;
        for (MultiblockSceneRenderState.SceneBlock block : blocks) {
            float distance = intersect(origin, direction, block);
            if (distance < 0.0F || distance >= nearestDistance) continue;
            nearest = block;
            nearestDistance = distance;
        }
        return nearest;
    }

    // A slab test against the cell's unit cube; a ray parallel to an axis only hits when the origin is inside it.
    private static float intersect(Vector3f origin, Vector3f direction, MultiblockSceneRenderState.SceneBlock block) {
        float near = 0.0F;
        float far = Float.MAX_VALUE;
        for (int axis = 0; axis < 3; axis++) {
            float start = origin.get(axis);
            float step = direction.get(axis);
            float low = coordinate(block, axis);
            float high = low + 1.0F;
            if (Math.abs(step) < PARALLEL_EPSILON) {
                if (start < low || start > high) return -1.0F;
                continue;
            }
            float first = (low - start) / step;
            float second = (high - start) / step;
            if (first > second) {
                float swap = first;
                first = second;
                second = swap;
            }
            near = Math.max(near, first);
            far = Math.min(far, second);
            if (near > far) return -1.0F;
        }
        return near;
    }

    private static float coordinate(MultiblockSceneRenderState.SceneBlock block, int axis) {
        return switch (axis) {
            case 0 -> block.x();
            case 1 -> block.y();
            default -> block.z();
        };
    }
}
