package com.iafenvoy.mxt.render.talisman;

import com.iafenvoy.mxt.runtime.talisman.TalismanProjectileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;

/**
 * A thrown carrier drawn as the item itself, turning over in flight. The item is billboarded, so the spin is applied
 * after that orientation and about the camera's own X axis: the paper tumbles end over end the way a thrown card
 * does. Rate, direction and starting angle are drawn from the entity's id, so one throw keeps one spin for its whole
 * flight - the same one on every client - while each throw differs.
 */
public final class TalismanProjectileRenderer extends EntityRenderer<TalismanProjectileEntity, TalismanProjectileRenderState> {
    // Radians a tick: a turn every thirty ticks up to a turn every eleven, so the sigil is readable in the air.
    private static final float MIN_SPIN = 0.20F;
    private static final float MAX_SPIN = 0.55F;
    private final ItemModelResolver itemModelResolver;
    private final RandomSource random = RandomSource.create();

    public TalismanProjectileRenderer(Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    @Override
    public TalismanProjectileRenderState createRenderState() {
        return new TalismanProjectileRenderState();
    }

    @Override
    public void extractRenderState(TalismanProjectileEntity entity, TalismanProjectileRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        this.itemModelResolver.updateForNonLiving(state.item, entity.getItem(), ItemDisplayContext.GROUND, entity);
        // Seeded by the entity's id, the trick a dropped item uses for its own bob: this runs once a frame, so the
        // two numbers have to come out the same every time.
        this.random.setSeed(entity.getId());
        float speed = MIN_SPIN + this.random.nextFloat() * (MAX_SPIN - MIN_SPIN);
        state.spinSpeed = this.random.nextBoolean() ? speed : -speed;
        state.spinPhase = this.random.nextFloat() * (float) (Math.PI * 2.0D);
    }

    @Override
    public void submit(TalismanProjectileRenderState state, @NonNull PoseStack poseStack,
                       @NonNull SubmitNodeCollector collector, @NonNull CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(camera.orientation);
        // About the camera's own axis, so the tumble reads the same from wherever the player watches it.
        poseStack.mulPose(new Quaternionf().rotateX(state.spin()));
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
