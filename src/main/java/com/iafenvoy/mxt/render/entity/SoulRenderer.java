package com.iafenvoy.mxt.render.entity;

import com.iafenvoy.mxt.entity.SoulEntity;
import com.iafenvoy.mxt.render.MxtRenderTypes;
import com.iafenvoy.mxt.render.entity.state.SoulRenderState;
import com.iafenvoy.mxt.runtime.world.SoulAppearance;
import com.iafenvoy.saturn.profiling.LoggerDispatcher;
import com.iafenvoy.saturn.profiling.LoggingLevel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class SoulRenderer extends EntityRenderer<SoulEntity, SoulRenderState> {
    private static final LoggerDispatcher LOGGER = LoggerDispatcher.of(LogUtils.getLogger(), LoggingLevel.WARN, LoggingLevel.WARN);
    private final PlayerSkinRenderCache skinCache;

    public SoulRenderer(Context context) {
        super(context);
        this.skinCache = context.getPlayerSkinRenderCache();
    }

    @Override
    public SoulRenderState createRenderState() {
        return new SoulRenderState();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void extractRenderState(SoulEntity entity, SoulRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.renderer = null;
        state.body = null;
        state.model = null;
        state.texture = null;
        state.renderFailure = null;
        SoulAppearance appearance = entity.appearance().orElse(null);
        if (appearance == null) return;
        Cached cached = entity.clientAppearance() instanceof Cached existing && existing.owner == this ? existing : this.create(appearance);
        entity.clientAppearance(cached);
        if (cached.entity == null || cached.failed) return;
        LivingEntity proxy = cached.entity;
        proxy.setPos(entity.position());
        proxy.setYRot(entity.getYRot());
        proxy.setXRot(entity.getXRot());
        proxy.setYBodyRot(entity.getYRot());
        proxy.setYHeadRot(entity.getYRot());
        proxy.yBodyRotO = proxy.yBodyRot;
        proxy.yHeadRotO = proxy.yHeadRot;
        proxy.tickCount = entity.tickCount;
        proxy.setOldPosAndRot();
        try {
            EntityRenderer renderer = this.entityRenderDispatcher.getRenderer(proxy);
            EntityRenderState body = renderer.createRenderState(proxy, partialTick);
            body.lightCoords = 15728880;
            body.nameTag = null;
            body.scoreText = null;
            body.shadowPieces.clear();
            body.leashStates = null;
            body.outlineColor = 0;
            body.isInvisible = false;
            if (body instanceof LivingEntityRenderState living) {
                living.deathTime = 0;
                living.hasRedOverlay = false;
                living.scale = appearance.scale();
            }
            state.renderer = renderer;
            state.body = body;
            state.renderFailure = failure -> {
                cached.failed = true;
                LOGGER.log("Unable to submit soul appearance {}; using a generic manifestation", appearance.entityType(), failure);
            };
            if (renderer instanceof LivingEntityRenderer living) {
                state.model = living.getModel();
                state.texture = living.getTextureLocation((LivingEntityRenderState) body);
            }
        } catch (RuntimeException failure) {
            cached.failed = true;
            state.renderer = null;
            LOGGER.log("Unable to render soul appearance {}; using a generic manifestation", appearance.entityType(), failure);
        }
    }

    private Cached create(SoulAppearance appearance) {
        try {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return new Cached(this, null);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(appearance.entityType()).orElse(null);
            Entity proxy;
            if (type == EntityType.PLAYER && appearance.profile().isPresent()) {
                PlayerInfo info = new PlayerInfo(appearance.profile().get().partialProfile(), false);
                proxy = new RemotePlayer(level, info.getProfile()) {
                    @Override
                    public @NonNull PlayerSkin getSkin() {
                        return info.getSkin();
                    }
                };
            } else if (type == EntityType.MANNEQUIN) {
                PlayerSkinRenderCache cache = this.skinCache;
                proxy = new ClientMannequin(level, cache) {
                    @Override
                    public @NonNull PlayerSkin getSkin() {
                        return cache.getOrDefault(this.getProfile()).playerSkin();
                    }
                };
            } else proxy = type == null ? null : type.create(level, EntitySpawnReason.LOAD);
            if (!(proxy instanceof LivingEntity living)) return new Cached(this, null);
            living.getEntityData().assignValues(appearance.metadata());
            appearance.equipment().forEach(living::setItemSlot);
            living.setPose(Pose.STANDING);
            living.setHealth(living.getMaxHealth());
            living.deathTime = living.hurtTime = 0;
            living.setDeltaMovement(Vec3.ZERO);
            living.setInvisible(false);
            living.setSilent(true);
            living.noPhysics = true;
            return new Cached(this, living);
        } catch (RuntimeException failure) {
            LOGGER.log("Unable to create soul appearance {}; using a generic manifestation", appearance.entityType(), failure);
            return new Cached(this, null);
        }
    }

    @Override
    public void submit(SoulRenderState state, @NonNull PoseStack pose, @NonNull SubmitNodeCollector collector, @NonNull CameraRenderState camera) {
        pose.pushPose();
        try {
            pose.translate(0, 0.12 + Math.sin(state.ageInTicks * 0.06) * 0.05, 0);
            SoulModelCollector ghost = new SoulModelCollector(collector, state.model, state.texture);
            if (state.renderer != null && state.body != null) {
                PoseStack bodyPose = new PoseStack();
                bodyPose.last().set(pose.last());
                try {
                    Vec3 offset = state.renderer.getRenderOffset(state.body);
                    bodyPose.translate(offset.x, offset.y, offset.z);
                    state.renderer.submit(state.body, bodyPose, ghost, camera);
                } catch (RuntimeException failure) {
                    if (state.renderFailure != null) state.renderFailure.accept(failure);
                }
            }
            if (!ghost.submitted()) {
                float width = state.boundingBoxWidth, height = state.boundingBoxHeight;
                collector.submitCustomGeometry(pose, MxtRenderTypes.swordBlade(), (transform, buffer) -> fallback(transform.pose(), buffer, width, height));
            }
        } finally {
            pose.popPose();
        }
    }

    @Override
    protected @NonNull AABB getBoundingBoxForCulling(SoulEntity entity) {
        return entity.getBoundingBox().inflate(0.5);
    }

    private static void fallback(Matrix4fc pose, VertexConsumer buffer, float width, float height) {
        float radius = Math.max(0.15F, width * 0.4F), middle = Math.max(0.25F, height * 0.5F);
        for (int side = 0; side < 4; side++) {
            double angle = side * Math.PI * 0.5, next = angle + Math.PI * 0.5;
            float x = (float) Math.cos(angle) * radius, z = (float) Math.sin(angle) * radius;
            float nx = (float) Math.cos(next) * radius, nz = (float) Math.sin(next) * radius;
            for (float tip : new float[]{0, middle * 2}) {
                buffer.addVertex(pose, 0, tip, 0).setColor(0x668FDFFF);
                buffer.addVertex(pose, x, middle, z).setColor(0x668FDFFF);
                buffer.addVertex(pose, nx, middle, nz).setColor(0x668FDFFF);
                buffer.addVertex(pose, 0, tip, 0).setColor(0x668FDFFF);
            }
        }
    }

    private static final class Cached {
        private final SoulRenderer owner;
        private final @Nullable LivingEntity entity;
        private boolean failed;

        private Cached(SoulRenderer owner, @Nullable LivingEntity entity) {
            this.owner = owner;
            this.entity = entity;
        }
    }
}
