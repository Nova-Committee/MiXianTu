package com.iafenvoy.mxt.render.sword;

import com.iafenvoy.mxt.runtime.sword.SwordAuraEntity;
import com.iafenvoy.mxt.render.sword.flame.BurningItemManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;

public final class SwordAuraRenderer extends EntityRenderer<SwordAuraEntity, SwordAuraRenderState> {
    private static final Vector3f SWORD_AXIS = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final Vec3 DEFAULT_DIRECTION = new Vec3(0.0D, 1.0D, 0.0D);
    private static final float SWORD_ROLL_RADIANS = (float) (Math.PI * 0.5D);

    public SwordAuraRenderer(Context context) {
        super(context);
    }

    @Override
    public SwordAuraRenderState createRenderState() {
        return new SwordAuraRenderState();
    }

    @Override
    public void extractRenderState(SwordAuraEntity entity, SwordAuraRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() <= 1.0E-8D) velocity = entity.position().subtract(entity.oldPosition());
        state.entityId = entity.getId();
        state.velocity = velocity;
        state.animationTime = entity.tickCount + partialTicks;
        state.centerOffsetY = entity.getBbHeight() * 0.5F;
        state.bladeColor = entity.bladeColor();
        state.auraColor = entity.auraColor();
        state.radialFlame = entity.isRadialFlame();
        state.length = entity.length();
        state.bladeWidth = entity.bladeWidth();
        state.thickness = entity.thickness();
        state.handleLength = entity.handleLength();
        state.guardWidth = entity.guardWidth();
        state.scale = entity.scale();
    }

    @Override
    public void submit(SwordAuraRenderState state, @NonNull PoseStack poseStack,
                       @NonNull SubmitNodeCollector collector, @NonNull CameraRenderState camera) {
        Vec3 direction = state.velocity.lengthSqr() > 1.0E-8D ? state.velocity.normalize() : DEFAULT_DIRECTION;
        Quaternionf rotation = new Quaternionf().rotationTo(SWORD_AXIS.x, SWORD_AXIS.y, SWORD_AXIS.z,
                        (float) direction.x, (float) direction.y, (float) direction.z)
                .rotateAxis(SWORD_ROLL_RADIANS, SWORD_AXIS.x, SWORD_AXIS.y, SWORD_AXIS.z);
        float red = channel(state.bladeColor, 16);
        float green = channel(state.bladeColor, 8);
        float blue = channel(state.bladeColor, 0);
        float alpha = channel(state.bladeColor, 24);
        float scale = state.scale;
        BurningItemManager.submit(state, rotation, camera);

        poseStack.pushPose();
        poseStack.translate(0.0F, state.centerOffsetY, 0.0F);
        poseStack.mulPose(rotation);
        poseStack.scale(scale, scale, scale);
        collector.submitCustomGeometry(poseStack, MxtSwordAuraRenderTypes.swordBlade(), (pose, buffer) ->
                emitSword(pose.pose(), buffer, state.length, state.bladeWidth, state.thickness,
                        state.handleLength, state.guardWidth, red, green, blue, alpha));

        poseStack.popPose();
    }

    private static void emitSword(Matrix4fc pose, VertexConsumer buffer, float length, float bladeWidth,
                                  float thickness, float handleLength, float guardWidth,
                                  float red, float green, float blue, float alpha) {
        float totalLength = length + handleLength;
        float bladeBase = -totalLength * 0.5F + handleLength;
        float guardThickness = Math.max(0.06F, thickness * 1.7F);
        float handleWidth = Math.max(bladeWidth * 0.28F, thickness * 2.0F);
        float handleDepth = Math.max(thickness * 1.15F, 0.06F);

        box(pose, buffer, handleWidth * 0.5F, -totalLength * 0.5F,
                bladeBase - guardThickness * 0.45F, handleDepth * 0.5F,
                red * 0.38F, green * 0.38F, blue * 0.38F, alpha);
        box(pose, buffer, guardWidth * 0.5F, bladeBase - guardThickness * 0.5F,
                bladeBase + guardThickness * 0.5F, thickness * 0.85F,
                red * 0.72F, green * 0.72F, blue * 0.72F, alpha);
        box(pose, buffer, handleWidth * 0.65F, -totalLength * 0.5F - 0.035F,
                -totalLength * 0.5F + 0.065F, handleDepth * 0.7F,
                red * 0.55F, green * 0.55F, blue * 0.55F, alpha);

        float shoulder = bladeBase + length * 0.80F;
        float tip = bladeBase + length;
        float baseWidth = bladeWidth * 0.62F;
        float[] baseX = {-baseWidth * 0.5F, 0.0F, baseWidth * 0.5F, 0.0F};
        float[] shoulderX = {-bladeWidth * 0.5F, 0.0F, bladeWidth * 0.5F, 0.0F};
        float[] baseZ = {0.0F, thickness * 0.5F, 0.0F, -thickness * 0.5F};
        float[] shoulderZ = {0.0F, thickness * 0.5F, 0.0F, -thickness * 0.5F};
        float bladeRed = mix(red, 1.0F, 0.42F);
        float bladeGreen = mix(green, 1.0F, 0.42F);
        float bladeBlue = mix(blue, 1.0F, 0.42F);
        for (int side = 0; side < 4; side++) {
            int next = (side + 1) & 3;
            float shade = side == 0 || side == 2 ? 0.78F : 1.0F;
            quad(pose, buffer,
                    baseX[side], bladeBase, baseZ[side],
                    shoulderX[side], shoulder, shoulderZ[side],
                    shoulderX[next], shoulder, shoulderZ[next],
                    baseX[next], bladeBase, baseZ[next],
                    bladeRed * shade, bladeGreen * shade, bladeBlue * shade, alpha);
            quad(pose, buffer,
                    shoulderX[side], shoulder, shoulderZ[side],
                    shoulderX[next], shoulder, shoulderZ[next],
                    0.0F, tip, 0.0F,
                    0.0F, tip, 0.0F,
                    bladeRed * shade, bladeGreen * shade, bladeBlue * shade, alpha);
        }
    }

    private static void box(Matrix4fc pose, VertexConsumer buffer, float x, float y0, float y1, float z,
                            float red, float green, float blue, float alpha) {
        quad(pose, buffer, -x, y0, z, x, y0, z, x, y1, z, -x, y1, z, red, green, blue, alpha);
        quad(pose, buffer, x, y0, -z, -x, y0, -z, -x, y1, -z, x, y1, -z, red, green, blue, alpha);
        quad(pose, buffer, x, y0, z, x, y0, -z, x, y1, -z, x, y1, z, red, green, blue, alpha);
        quad(pose, buffer, -x, y0, -z, -x, y0, z, -x, y1, z, -x, y1, -z, red, green, blue, alpha);
        quad(pose, buffer, -x, y0, -z, x, y0, -z, x, y0, z, -x, y0, z, red, green, blue, alpha);
        quad(pose, buffer, -x, y1, z, x, y1, z, x, y1, -z, -x, y1, -z, red, green, blue, alpha);
    }

    private static void quad(Matrix4fc pose, VertexConsumer buffer,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float red, float green, float blue, float alpha) {
        buffer.addVertex(pose, x0, y0, z0).setColor(red, green, blue, alpha);
        buffer.addVertex(pose, x1, y1, z1).setColor(red, green, blue, alpha);
        buffer.addVertex(pose, x2, y2, z2).setColor(red, green, blue, alpha);
        buffer.addVertex(pose, x3, y3, z3).setColor(red, green, blue, alpha);
    }

    private static float channel(int color, int shift) {
        return (color >> shift & 0xFF) / 255.0F;
    }

    private static float mix(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }

    @Override
    protected AABB getBoundingBoxForCulling(SwordAuraEntity entity) {
        double radius = (entity.length() + entity.handleLength() + entity.guardWidth()) * entity.scale() + 5;
        return entity.getBoundingBox().inflate(radius);
    }
}
