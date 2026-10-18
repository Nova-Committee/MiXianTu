package com.iafenvoy.mxt.render.meteor;

import com.iafenvoy.mxt.entity.MeteorEntity;
import com.iafenvoy.mxt.render.sword.flame.BurningItemManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;

public final class MeteorRenderer extends EntityRenderer<MeteorEntity, MeteorRenderState> {
    private static final Vec3 DEFAULT_DIRECTION = new Vec3(0, -1, 0);

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public MeteorRenderState createRenderState() {
        return new MeteorRenderState();
    }

    @Override
    public void extractRenderState(MeteorEntity entity, MeteorRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        Vec3 velocity = entity.getDeltaMovement();
        if (velocity.lengthSqr() <= 1.0E-8D) velocity = entity.position().subtract(entity.oldPosition());
        state.entityId = entity.getId();
        state.velocity = velocity;
        state.animationTime = entity.tickCount + partialTicks;
        state.centerOffsetY = entity.getBbHeight() * 0.5F;
        state.auraColor = entity.getColor();
        state.scale = entity.getScale();
        state.meteor = true;
        state.flameShape.set(entity.getRadius(), entity.getTailLength(), 0, 0);
    }

    @Override
    public void submit(MeteorRenderState state, @NonNull PoseStack poseStack,
                       @NonNull SubmitNodeCollector collector, @NonNull CameraRenderState camera) {
        Vec3 direction = state.velocity.lengthSqr() > 1.0E-8D ? state.velocity.normalize() : DEFAULT_DIRECTION;
        Quaternionf rotation = new Quaternionf().rotationTo(0, 1, 0,
                (float) direction.x, (float) direction.y, (float) direction.z);
        BurningItemManager.submit(state, rotation, camera);
    }

    @Override
    protected @NonNull AABB getBoundingBoxForCulling(MeteorEntity entity) {
        double reach = (entity.getRadius() * 4.0 + entity.getTailLength() * 2.0) * entity.getScale();
        return entity.getBoundingBox().inflate(reach);
    }
}
