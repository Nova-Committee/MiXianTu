package com.iafenvoy.mxt.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.item.ItemStackRenderState.FoilType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

// Local to a soul submission: shared vanilla renderers and models are never patched or mutated.
final class SoulModelCollector implements SubmitNodeCollector {
    static final int TINT = 0x668FDFFF;
    private static final Identifier WHITE = Identifier.fromNamespaceAndPath("mxt", "textures/entity/soul/white.png");
    private final OrderedSubmitNodeCollector delegate;
    private final @Nullable Model<?> body;
    private final @Nullable Identifier texture;
    private final Submission count;

    SoulModelCollector(OrderedSubmitNodeCollector delegate, @Nullable Model<?> body, @Nullable Identifier texture) {
        this(delegate, body, texture, new Submission());
    }

    private SoulModelCollector(OrderedSubmitNodeCollector delegate, @Nullable Model<?> body, @Nullable Identifier texture, Submission count) {
        this.delegate = delegate;
        this.body = body;
        this.texture = texture;
        this.count = count;
    }

    boolean submitted() {
        return this.count.models > 0;
    }

    @Override
    public @NonNull OrderedSubmitNodeCollector order(int order) {
        return new SoulModelCollector(this.delegate instanceof SubmitNodeCollector root ? root.order(order) : this.delegate, this.body, this.texture, this.count);
    }

    @Override
    public <S> void submitModel(@NonNull Model<? super S> model, S state, @NonNull PoseStack pose, @NonNull RenderType type, int light, int overlay,
                                int tint, @Nullable TextureAtlasSprite sprite, int outline, @Nullable CrumblingOverlay crumbling) {
        Identifier texture = model == this.body && this.texture != null ? this.texture : WHITE;
        this.delegate.submitModel(model, state, pose, RenderTypes.entityTranslucent(texture), 15728880,
                OverlayTexture.NO_OVERLAY, ARGB.multiply(tint, TINT), null, 0, null);
        this.count.models++;
    }

    @Override
    public void submitModelPart(@NonNull ModelPart part, @NonNull PoseStack pose, @NonNull RenderType type, int light, int overlay,
                                @Nullable TextureAtlasSprite sprite, boolean sheeted, boolean foil, int tint,
                                @Nullable CrumblingOverlay crumbling, int outline) {
        this.delegate.submitModelPart(part, pose, RenderTypes.entityTranslucent(WHITE), 15728880, OverlayTexture.NO_OVERLAY,
                null, false, false, ARGB.multiply(tint, TINT), null, 0);
        this.count.models++;
    }

    @Override
    public void submitShadow(@NonNull PoseStack pose, float radius, @NonNull List<EntityRenderState.ShadowPiece> pieces) {
    }

    @Override
    public void submitNameTag(@NonNull PoseStack pose, @Nullable Vec3 attachment, int offset, @NonNull Component name, boolean seeThrough,
                              int light, double distance, @NonNull CameraRenderState camera) {
    }

    @Override
    public void submitText(@NonNull PoseStack pose, float x, float y, @NonNull FormattedCharSequence text, boolean shadow, Font.@NonNull DisplayMode mode,
                           int light, int color, int background, int outline) {
    }

    @Override
    public void submitFlame(@NonNull PoseStack pose, @NonNull EntityRenderState state, @NonNull Quaternionf rotation) {
    }

    @Override
    public void submitLeash(@NonNull PoseStack pose, EntityRenderState.@NonNull LeashState state) {
    }

    @Override
    public void submitMovingBlock(@NonNull PoseStack pose, @NonNull MovingBlockRenderState state) {
    }

    @Override
    public void submitBlockModel(@NonNull PoseStack pose, @NonNull RenderType type, @NonNull List<BlockStateModelPart> parts, int @NonNull [] tints,
                                 int light, int overlay, int outline) {
    }

    @Override
    public void submitBreakingBlockModel(@NonNull PoseStack pose, @NonNull BlockStateModel model, long seed, int progress) {
    }

    @Override
    public void submitItem(@NonNull PoseStack pose, @NonNull ItemDisplayContext context, int light, int overlay, int outline,
                           int @NonNull [] tints, @NonNull List<BakedQuad> quads, @NonNull FoilType foil) {
    }

    @Override
    public void submitCustomGeometry(@NonNull PoseStack pose, @NonNull RenderType type, @NonNull CustomGeometryRenderer renderer) {
    }

    @Override
    public void submitParticleGroup(@NonNull ParticleGroupRenderer renderer) {
    }

    private static final class Submission {
        private int models;
    }
}
