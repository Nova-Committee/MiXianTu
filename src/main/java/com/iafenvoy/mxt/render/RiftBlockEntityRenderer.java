package com.iafenvoy.mxt.render;

import com.iafenvoy.mxt.config.MxtClientConfig;
import com.iafenvoy.mxt.item.block.entity.RiftBlockEntity;
import com.iafenvoy.mxt.render.RiftBlockEntityRenderer.State;
import com.iafenvoy.mxt.runtime.rift.RiftColors;
import com.iafenvoy.mxt.runtime.rift.RiftConnections;
import com.iafenvoy.mxt.runtime.rift.RiftConnections.Loop;
import com.iafenvoy.mxt.runtime.rift.RiftMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a rift as a point, the links to the rifts around it, and the triangles those links close.
 *
 * <p>Every block draws only its own share: one point at its centre, one half of every link, and one share of
 * every triangle it takes part in. The other halves and shares come from the neighbours, so nothing here has to
 * know the shape of the whole structure and nothing has to be recomputed when one block of it changes.
 *
 * <p>The three parts are drawn at the same thickness and the same colour, which is what makes the structure read
 * as one material: a cube at each node, a beam of the same width along each link, and a triangle filled with a
 * slab of that width rather than a sheet.
 *
 * <p>A rift is drawn fully opaque, and that is fixed rather than a setting: a translucent fill both hid the
 * points and links inside it and made the block read as glass, which is not what a hole into another dimension
 * should look like.
 */
public final class RiftBlockEntityRenderer implements BlockEntityRenderer<RiftBlockEntity, State> {
    /**
     * Alpha every part of a rift is submitted with. Locked at one on purpose; see the class comment.
     */
    private static final float ALPHA = 1.0F;

    /**
     * The provider hands every block entity renderer a context; a rift needs nothing from it, since its shape is
     * geometry and its texture belongs to the pipeline rather than to a model.
     */
    public RiftBlockEntityRenderer(Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RiftBlockEntity entity, State state, float partialTick, @NonNull Vec3 cameraPosition, CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(entity, state, breakProgress);
        state.parts.clear();
        double thickness = MxtClientConfig.INSTANCE.rifts.thickness.getValue();
        int color = RiftColors.resolve(entity);
        state.parts.add(new Part(RiftMesh.node(thickness), RiftColors.rim(color), ALPHA));
        Level level = entity.getLevel();
        if (level == null) return;
        BlockPos pos = entity.getBlockPos();
        List<BlockPos> links = RiftConnections.connected(level, pos);
        for (BlockPos link : links)
            state.parts.add(new Part(RiftMesh.linkShare(local(link, pos), thickness), color, ALPHA));
        for (Loop loop : RiftConnections.loops(pos, links))
            state.parts.add(new Part(RiftMesh.triangleShare(RiftMesh.CENTRE,
                    local(loop.forward(), pos), local(loop.backward(), pos), thickness), color, ALPHA));
    }

    @Override
    public void submit(State state, @NonNull PoseStack poseStack, @NonNull SubmitNodeCollector collector, @NonNull CameraRenderState camera) {
        if (state.parts.isEmpty()) return;
        collector.submitCustomGeometry(poseStack, MxtRiftRenderTypes.rift(),
                (pose, buffer) -> {
                    for (Part part : state.parts) emit(buffer, pose, part);
                });
    }

    /**
     * Emits one part. Every mesh is a whole number of quads, so the vertex list goes straight into the buffer
     * that both the rift pipeline and the plain fallback expect.
     */
    private static void emit(VertexConsumer buffer, Pose pose, Part part) {
        float red = ((part.argb() >> 16) & 0xFF) / 255.0F;
        float green = ((part.argb() >> 8) & 0xFF) / 255.0F;
        float blue = (part.argb() & 0xFF) / 255.0F;
        for (Vec3 point : part.vertices())
            buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                    .setColor(red, green, blue, part.alpha());
    }

    /**
     * A neighbour's centre in this block's coordinates. Neighbours are at most one block away on each axis, so
     * the difference is what is added to this block's own centre.
     */
    private static Vec3 local(BlockPos other, BlockPos self) {
        return RiftMesh.CENTRE.add(other.getX() - self.getX(), other.getY() - self.getY(), other.getZ() - self.getZ());
    }

    /**
     * One piece of the drawing: a mesh of quads in block-local coordinates, the colour it is tinted with and how
     * opaque it is.
     */
    private record Part(List<Vec3> vertices, int argb, float alpha) {
    }

    public static final class State extends BlockEntityRenderState {
        private final List<Part> parts = new ArrayList<>();
    }
}
