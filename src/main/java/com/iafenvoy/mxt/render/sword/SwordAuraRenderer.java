package com.iafenvoy.mxt.render.sword;

import com.iafenvoy.mxt.runtime.sword.SwordAuraEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;

/**
 * Draws a parameterized sword and a layered, shader-driven flame around it.
 */
public final class SwordAuraRenderer extends EntityRenderer<SwordAuraEntity, SwordAuraRenderState> {
    private static final Vector3f SWORD_AXIS = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final Vec3 DEFAULT_DIRECTION = new Vec3(0.0D, 1.0D, 0.0D);
    private static final float SWORD_ROLL_RADIANS = (float) (Math.PI * 0.5D);
    private static final int FLAME_RINGS = 18;
    private static final int FLAME_SIDES = 12;

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
        state.velocity = velocity;
        state.animationTime = entity.tickCount + partialTicks;
        state.speed = (float) velocity.length();
        state.centerOffsetY = entity.getBbHeight() * 0.5F;
        state.color = entity.color();
        state.bladeAlpha = entity.bladeAlpha();
        state.auraAlpha = entity.auraAlpha();
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
        float red = channel(state.color, 16);
        float green = channel(state.color, 8);
        float blue = channel(state.color, 0);
        float scale = state.scale;
        float motion = Math.max(state.speed * 20.0F, 0.0F);

        poseStack.pushPose();
        poseStack.translate(0.0F, state.centerOffsetY, 0.0F);
        poseStack.mulPose(rotation);
        poseStack.scale(scale, scale, scale);
        float totalLength = state.length + state.handleLength;
        float auraRadius = Math.max(0.10F, Math.max(state.bladeWidth * 0.95F, state.guardWidth * 0.70F))
                + state.thickness * 1.25F + 0.10F;
        float auraLength = totalLength + auraRadius * 1.25F;

        collector.submitCustomGeometry(poseStack, MxtSwordAuraRenderTypes.swordAura(), (pose, buffer) ->
                emitFlameVolume(pose.pose(), buffer, auraLength, auraRadius, red, green, blue,
                        state.auraAlpha * 0.92F, state.animationTime, motion));
        collector.submitCustomGeometry(poseStack, MxtSwordAuraRenderTypes.swordAura(), (pose, buffer) ->
                emitFlameTongues(pose.pose(), buffer, auraLength, auraRadius, red, green, blue,
                        state.auraAlpha * 0.72F, state.animationTime, motion));
        collector.submitCustomGeometry(poseStack, MxtSwordAuraRenderTypes.swordBlade(), (pose, buffer) ->
                emitSword(pose.pose(), buffer, state.length, state.bladeWidth, state.thickness,
                        state.handleLength, state.guardWidth, red, green, blue, state.bladeAlpha));

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

    private static void emitFlameVolume(Matrix4fc pose, VertexConsumer buffer, float length, float maxRadius,
                                        float red, float green, float blue, float alpha,
                                        float animationTime, float motion) {
        for (int ring = 0; ring < FLAME_RINGS - 1; ring++) {
            float t0 = ring / (float) (FLAME_RINGS - 1);
            float t1 = (ring + 1) / (float) (FLAME_RINGS - 1);
            float y0 = flameY(-length * 0.5F + length * t0, t0, animationTime, motion);
            float y1 = flameY(-length * 0.5F + length * t1, t1, animationTime, motion);
            for (int side = 0; side < FLAME_SIDES; side++) {
                int next = (side + 1) % FLAME_SIDES;
                float a0 = (float) (side * Math.PI * 2.0D / FLAME_SIDES);
                float a1 = (float) (next * Math.PI * 2.0D / FLAME_SIDES);
                float r00 = flameRadius(t0, side, maxRadius, animationTime, motion);
                float r01 = flameRadius(t0, next, maxRadius, animationTime, motion);
                float r10 = flameRadius(t1, side, maxRadius, animationTime, motion);
                float r11 = flameRadius(t1, next, maxRadius, animationTime, motion);
                float offsetX0 = flameOffsetX(t0, animationTime, motion);
                float offsetX1 = flameOffsetX(t1, animationTime, motion);
                float offsetZ0 = flameOffsetZ(t0, animationTime, motion);
                float offsetZ1 = flameOffsetZ(t1, animationTime, motion);
                quad(pose, buffer,
                        offsetX0 + r00 * (float) Math.cos(a0), y0, offsetZ0 + r00 * (float) Math.sin(a0),
                        offsetX1 + r10 * (float) Math.cos(a0), y1, offsetZ1 + r10 * (float) Math.sin(a0),
                        offsetX1 + r11 * (float) Math.cos(a1), y1, offsetZ1 + r11 * (float) Math.sin(a1),
                        offsetX0 + r01 * (float) Math.cos(a1), y0, offsetZ0 + r01 * (float) Math.sin(a1),
                        mix(red, 1.0F, 0.34F), mix(green, 1.0F, 0.34F), mix(blue, 1.0F, 0.34F), alpha);
            }
        }
    }

    private static void emitFlameTongues(Matrix4fc pose, VertexConsumer buffer, float length, float maxRadius,
                                         float red, float green, float blue, float alpha,
                                         float animationTime, float motion) {
        float whiteRed = mix(red, 1.0F, 0.72F);
        float whiteGreen = mix(green, 1.0F, 0.72F);
        float whiteBlue = mix(blue, 1.0F, 0.72F);
        tongue(pose, buffer, length, maxRadius, 0.10F, 0.24F, 0.72F, -0.42F, whiteRed, whiteGreen, whiteBlue, alpha, animationTime, motion);
        tongue(pose, buffer, length, maxRadius, 0.29F, -0.52F, 0.48F, 0.38F, whiteRed, whiteGreen, whiteBlue, alpha * 0.86F, animationTime, motion);
        tongue(pose, buffer, length, maxRadius, 0.48F, 0.46F, 0.62F, -0.30F, whiteRed, whiteGreen, whiteBlue, alpha * 0.92F, animationTime, motion);
        tongue(pose, buffer, length, maxRadius, 0.67F, -0.36F, 0.52F, 0.45F, whiteRed, whiteGreen, whiteBlue, alpha * 0.80F, animationTime, motion);
        tongue(pose, buffer, length, maxRadius, 0.85F, 0.18F, 0.74F, -0.50F, whiteRed, whiteGreen, whiteBlue, alpha * 0.88F, animationTime, motion);
    }

    private static void tongue(Matrix4fc pose, VertexConsumer buffer, float length, float maxRadius,
                               float center, float side, float reach, float lean,
                               float red, float green, float blue, float alpha,
                               float animationTime, float motion) {
        float animatedCenter = Math.clamp(center + (float) Math.sin(animationTime * 0.37F + center * 13.0F) * 0.035F,
                0.04F, 0.96F);
        float animatedSide = side + (float) Math.sin(animationTime * 0.29F + center * 17.0F) * (0.08F + motion * 0.10F);
        float animatedReach = reach + (float) Math.sin(animationTime * 0.43F + center * 11.0F) * (0.08F + motion * 0.10F);
        float animatedLean = lean + (float) Math.cos(animationTime * 0.31F + center * 15.0F) * (0.10F + motion * 0.16F);
        float y = flameY(-length * 0.5F + length * animatedCenter, animatedCenter, animationTime, motion);
        float half = maxRadius * (0.17F + 0.08F * (float) Math.sin(animatedCenter * 19.0F + animationTime * 0.22F));
        float x = animatedSide * maxRadius * 0.92F;
        float z = (float) Math.sin(animatedCenter * 17.0F + animationTime * 0.18F) * maxRadius * 0.24F;
        float tipY = y + length * (animatedReach * 0.34F);
        float tipX = x + animatedLean * maxRadius * 0.92F;
        float tipZ = z + (float) Math.cos(animatedCenter * 13.0F + animationTime * 0.20F) * maxRadius * 0.35F;
        quad(pose, buffer,
                x - half, y - length * 0.16F, z,
                x + half, y + length * 0.06F, z,
                tipX + half * 0.12F, tipY, tipZ,
                tipX - half * 0.12F, tipY + length * 0.04F, tipZ,
                red, green, blue, alpha);
    }

    private static float flameRadius(float t, int side, float maxRadius, float animationTime, float motion) {
        float envelope = (float) Math.sin(Math.PI * Math.pow(t, 0.84D));
        float taper = 0.08F + envelope * 0.92F;
        float wave = 1.0F + 0.24F * (float) Math.sin(t * 19.0F + side * 1.73F + animationTime * 0.42F)
                + 0.14F * (float) Math.sin(t * 43.0F - side * 2.21F + animationTime * 0.27F);
        float bias = switch (side) {
            case 0, 1, 2 -> 1.10F;
            case 5, 6, 7 -> 0.78F;
            default -> 0.95F;
        };
        float spike = (float) Math.pow(Math.max(0.0F, Math.sin(t * 9.0F + side * 1.4F + animationTime * 0.55F)), 8.0D) * 0.55F;
        float flow = motion * 0.18F * (float) Math.sin(animationTime * 0.33F + t * 12.0F + side * 2.0F);
        return Math.max(0.025F, maxRadius * taper * (bias + wave * 0.13F + spike + flow));
    }

    private static float flameY(float base, float t, float animationTime, float motion) {
        float wave = (float) Math.sin(animationTime * 0.32F + t * 18.0F) * 0.045F
                + (float) Math.sin(animationTime * 0.17F + t * 41.0F) * 0.025F;
        float trailing = (1.0F - t) * motion * (0.12F + 0.04F * (float) Math.sin(animationTime * 0.23F + t * 12.0F));
        return base + wave - trailing;
    }

    private static float flameOffsetX(float t, float animationTime, float motion) {
        return (float) (Math.sin(t * 15.0D + animationTime * 0.47D) * (0.065D + motion * 0.025D)
                + Math.sin(t * 37.0D + 0.9D + animationTime * 0.31D) * 0.032D);
    }

    private static float flameOffsetZ(float t, float animationTime, float motion) {
        return (float) (Math.cos(t * 13.0D + 0.6D + animationTime * 0.39D) * (0.055D + motion * 0.025D)
                + Math.sin(t * 31.0D + animationTime * 0.26D) * 0.025D);
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
    protected boolean affectedByCulling(SwordAuraEntity entity) {
        return false;
    }
}
