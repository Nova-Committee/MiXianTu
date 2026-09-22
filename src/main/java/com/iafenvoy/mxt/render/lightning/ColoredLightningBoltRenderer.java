package com.iafenvoy.mxt.render.lightning;

import com.iafenvoy.mxt.runtime.lightning.ColoredLightningBolt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4fc;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * Vanilla lightning geometry with its hard-coded colour constants replaced by per-bolt values and an optional
 * palette that tints the strand seam by seam. Strand generation and the additive render type are copied from
 * {@code LightningBoltRenderer}, because that pipeline takes its colour from the vertices alone.
 */
public class ColoredLightningBoltRenderer extends EntityRenderer<ColoredLightningBolt, ColoredLightningBoltRenderState> {
    // The bolt spans eight blocks, so its geometry has nine horizontal seams to tint.
    private static final int SEAMS = 8;

    public ColoredLightningBoltRenderer(Context context) {
        super(context);
    }

    @Override
    public ColoredLightningBoltRenderState createRenderState() {
        return new ColoredLightningBoltRenderState();
    }

    @Override
    public void extractRenderState(ColoredLightningBolt entity, ColoredLightningBoltRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.seed = entity.seed;
        state.color = entity.color();
        state.alpha = entity.alpha();
        state.thickness = entity.thickness();
        state.palette = entity.palette();
    }

    @Override
    public void submit(ColoredLightningBoltRenderState state, @NonNull PoseStack poseStack, @NonNull SubmitNodeCollector collector, @NonNull CameraRenderState camera) {
        float[] reds = new float[SEAMS + 1];
        float[] greens = new float[SEAMS + 1];
        float[] blues = new float[SEAMS + 1];
        tints(state, reds, greens, blues);
        float[] xOffsets = new float[8];
        float[] zOffsets = new float[8];
        float xOffset = 0.0F;
        float zOffset = 0.0F;
        RandomSource random = RandomSource.createThreadLocalInstance(state.seed);
        for (int height = 7; height >= 0; height--) {
            xOffsets[height] = xOffset;
            zOffsets[height] = zOffset;
            xOffset += random.nextInt(11) - 5;
            zOffset += random.nextInt(11) - 5;
        }
        float finalXOffset = xOffset;
        float finalZOffset = zOffset;
        collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
            Matrix4fc matrix = pose.pose();
            for (int layer = 0; layer < 4; layer++) {
                // Every layer redraws the same bolt from the same seed, so the four strands stack onto each other.
                RandomSource layerRandom = RandomSource.createThreadLocalInstance(state.seed);
                for (int strand = 0; strand < 3; strand++) {
                    int from = strand > 0 ? 7 - strand : 7;
                    int to = strand > 0 ? from - 2 : 0;
                    float currentX = xOffsets[from] - finalXOffset;
                    float currentZ = zOffsets[from] - finalZOffset;
                    for (int height = from; height >= to; height--) {
                        float previousX = currentX;
                        float previousZ = currentZ;
                        // The trunk wanders less than the two branches hanging off it.
                        int spread = strand == 0 ? 11 : 31;
                        currentX += layerRandom.nextInt(spread) - spread / 2;
                        currentZ += layerRandom.nextInt(spread) - spread / 2;
                        float top = 0.1F + layer * 0.2F;
                        if (strand == 0) top *= height * 0.1F + 1.0F;
                        float bottom = 0.1F + layer * 0.2F;
                        if (strand == 0) bottom *= (height - 1.0F) * 0.1F + 1.0F;
                        float topWidth = top * state.thickness;
                        float bottomWidth = bottom * state.thickness;
                        quad(matrix, buffer, reds, greens, blues, currentX, currentZ, height, previousX, previousZ, state.alpha, topWidth, bottomWidth, false, false, true, false);
                        quad(matrix, buffer, reds, greens, blues, currentX, currentZ, height, previousX, previousZ, state.alpha, topWidth, bottomWidth, true, false, true, true);
                        quad(matrix, buffer, reds, greens, blues, currentX, currentZ, height, previousX, previousZ, state.alpha, topWidth, bottomWidth, true, true, false, true);
                        quad(matrix, buffer, reds, greens, blues, currentX, currentZ, height, previousX, previousZ, state.alpha, topWidth, bottomWidth, false, true, false, false);
                    }
                }
            }
        });
    }

    // One tint per seam, index zero at the ground and SEAMS at the top. A palette is read from its first entry at
    // the top seam to its last at the ground; branches reuse the trunk's seams, which keeps a branch the same
    // colour as the trunk it leaves.
    private static void tints(ColoredLightningBoltRenderState state, float[] reds, float[] greens, float[] blues) {
        List<Integer> palette = state.palette;
        if (palette.isEmpty()) {
            Arrays.fill(reds, channel(state.color, 16));
            Arrays.fill(greens, channel(state.color, 8));
            Arrays.fill(blues, channel(state.color, 0));
            return;
        }
        for (int seam = 0; seam <= SEAMS; seam++) {
            float position = (1.0F - seam / (float) SEAMS) * (palette.size() - 1);
            int index = Mth.floor(position);
            float fraction = position - index;
            int from = palette.get(index);
            int to = palette.get(Math.min(index + 1, palette.size() - 1));
            reds[seam] = mix(from, to, fraction, 16);
            greens[seam] = mix(from, to, fraction, 8);
            blues[seam] = mix(from, to, fraction, 0);
        }
    }

    private static float channel(int color, int shift) {
        return (color >> shift & 0xFF) / 255.0F;
    }

    private static float mix(int from, int to, float fraction, int shift) {
        int start = from >> shift & 0xFF, end = to >> shift & 0xFF;
        return (start + (end - start) * fraction) / 255.0F;
    }

    // One side of a bolt segment. The lower end is the wider one, which is what tapers the strand.
    private static void quad(Matrix4fc pose, VertexConsumer buffer, float[] reds, float[] greens, float[] blues,
                             float x0, float z0, int height, float x1, float z1, float alpha,
                             float topWidth, float bottomWidth,
                             boolean xFirst, boolean zFirst, boolean xSecond, boolean zSecond) {
        float bottomRed = reds[height], bottomGreen = greens[height], bottomBlue = blues[height];
        float topRed = reds[height + 1], topGreen = greens[height + 1], topBlue = blues[height + 1];
        buffer.addVertex(pose, x0 + (xFirst ? bottomWidth : -bottomWidth), height * 16.0F, z0 + (zFirst ? bottomWidth : -bottomWidth)).setColor(bottomRed, bottomGreen, bottomBlue, alpha);
        buffer.addVertex(pose, x1 + (xFirst ? topWidth : -topWidth), (height + 1) * 16.0F, z1 + (zFirst ? topWidth : -topWidth)).setColor(topRed, topGreen, topBlue, alpha);
        buffer.addVertex(pose, x1 + (xSecond ? topWidth : -topWidth), (height + 1) * 16.0F, z1 + (zSecond ? topWidth : -topWidth)).setColor(topRed, topGreen, topBlue, alpha);
        buffer.addVertex(pose, x0 + (xSecond ? bottomWidth : -bottomWidth), height * 16.0F, z0 + (zSecond ? bottomWidth : -bottomWidth)).setColor(bottomRed, bottomGreen, bottomBlue, alpha);
    }

    @Override
    protected boolean affectedByCulling(ColoredLightningBolt entity) {
        return false;
    }
}
