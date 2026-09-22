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
 * Draws a rift as a point, the links to the rifts around it and the triangles those links close. Every block draws
 * only its own share - one point, half of every link, one share of every triangle - so no block needs to know the
 * shape of the whole structure. Fully opaque on purpose: a translucent fill hid the inner points and links.
 */
public final class RiftBlockEntityRenderer implements BlockEntityRenderer<RiftBlockEntity, State> {
    // Alpha every part is submitted with, locked at one on purpose; see the class comment.
    private static final float ALPHA = 1.0F;

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

    // Every mesh is a whole number of quads, which is what the rift pipeline and the plain fallback both expect.
    private static void emit(VertexConsumer buffer, Pose pose, Part part) {
        float red = ((part.argb() >> 16) & 0xFF) / 255.0F;
        float green = ((part.argb() >> 8) & 0xFF) / 255.0F;
        float blue = (part.argb() & 0xFF) / 255.0F;
        for (Vec3 point : part.vertices())
            buffer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                    .setColor(red, green, blue, part.alpha());
    }

    // Neighbours are at most one block away, so the difference added to CENTRE is their position in this block.
    private static Vec3 local(BlockPos other, BlockPos self) {
        return RiftMesh.CENTRE.add(other.getX() - self.getX(), other.getY() - self.getY(), other.getZ() - self.getZ());
    }

    // One piece of the drawing; the vertices are in block-local coordinates.
    private record Part(List<Vec3> vertices, int argb, float alpha) {
    }

    public static final class State extends BlockEntityRenderState {
        private final List<Part> parts = new ArrayList<>();
    }
}
