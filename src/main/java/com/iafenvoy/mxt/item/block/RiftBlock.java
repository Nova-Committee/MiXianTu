package com.iafenvoy.mxt.item.block;

import com.iafenvoy.mxt.data.item.RiftComponent;
import com.iafenvoy.mxt.item.ItemFeedback;
import com.iafenvoy.mxt.item.block.entity.RiftBlockEntity;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtParticleTypes;
import com.iafenvoy.mxt.runtime.rift.RiftColors;
import com.iafenvoy.mxt.runtime.rift.RiftTeleportService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.Portal.Transition;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/**
 * A rift: a block that leads to another dimension, drawn as a point linked to the rifts around it. The block
 * itself shows nothing (the block entity renderer draws the mesh), so adjacency is what decides the look: it links
 * to every rift in the 3x3x3 around it and has no direction of its own. It keeps a full-cube shape so it can be
 * aimed at and right-clicked, while {@code noCollission} lets entities pass through - entering is what takes you.
 */
public final class RiftBlock extends BaseEntityBlock implements Portal {
    private static final MapCodec<RiftBlock> CODEC = simpleCodec(RiftBlock::new);
    private static final float PARTICLE_CHANCE = 0.25F;
    // Particles are scattered over this radius so they hang around the point rather than filling the block.
    private static final double PARTICLE_RADIUS = 0.3;

    public RiftBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected @NonNull MapCodec<RiftBlock> codec() {
        return CODEC;
    }

    @Override
    public @NonNull BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new RiftBlockEntity(pos, state);
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public void setPlacedBy(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state, @Nullable LivingEntity placer, @NonNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level.getBlockEntity(pos) instanceof RiftBlockEntity rift)) return;
        RiftComponent component = stack.getOrDefault(MxtDataComponents.RIFT, RiftComponent.EMPTY);
        rift.configure(component.target(), component.color());
    }

    // Without an override a rift is coloured by where it leads, so a dye is the only way to give it a colour of its own.
    @Override
    protected @NonNull InteractionResult useItemOn(@NonNull ItemStack stack, @NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos, @NonNull Player player, @NonNull InteractionHand hand, @NonNull BlockHitResult hit) {
        DyeColor dye = stack.get(DataComponents.DYE);
        if (dye == null) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof RiftBlockEntity rift)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            rift.setColor(0xFF000000 | dye.getTextureDiffuseColor());
            if (!player.hasInfiniteMaterials()) stack.shrink(1);
            ItemFeedback.send(player, Component.translatable("block.mxt.rift.recolored", RiftColors.format(rift.color())));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void entityInside(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos, @NonNull Entity entity, @NonNull InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (!(level instanceof ServerLevel)) return;
        if (!entity.canUsePortal(false)) return;
        entity.setAsInsidePortal(this, pos);
    }

    @Nullable
    @Override
    public TeleportTransition getPortalDestination(@NonNull ServerLevel level, @NonNull Entity entity, @NonNull BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof RiftBlockEntity rift)) return null;
        return RiftTeleportService.destination(level, rift, entity, pos);
    }

    @Override
    public int getPortalTransitionTime(@NonNull ServerLevel level, @NonNull Entity entity) {
        return 0;
    }

    @Override
    public @NonNull Transition getLocalTransition() {
        return Transition.CONFUSION;
    }

    // Breaking hands back an anchor still aimed where the rift led. There is no loot table: this drop is the only one.
    @Override
    public void playerDestroy(@NonNull Level level, @NonNull Player player, @NonNull BlockPos pos, @NonNull BlockState state, @Nullable BlockEntity blockEntity, @NonNull ItemStack destroyedWith) {
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);
        if (level.isClientSide() || player.hasInfiniteMaterials()) return;
        if (!(blockEntity instanceof RiftBlockEntity rift)) return;
        ItemStack drop = new ItemStack(this);
        drop.set(MxtDataComponents.RIFT, new RiftComponent(rift.target(), rift.color()));
        popResource(level, pos, drop);
    }

    // The mod's own particle is the vanilla portal particle wearing this rift's colour, and the points gather around
    // the rift's centre rather than filling the cube. Nothing here makes a sound: a rift is silent until it takes someone.
    @Override
    public void animateTick(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos, @NonNull RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof RiftBlockEntity rift)) return;
        if (random.nextFloat() >= PARTICLE_CHANCE) return;
        Vec3 origin = Vec3.atLowerCornerOf(pos).add(rift.center());
        Vec3 direction = new Vec3(random.nextDouble() * 2.0 - 1.0, random.nextDouble() * 2.0 - 1.0,
                random.nextDouble() * 2.0 - 1.0).normalize();
        Vec3 point = origin.add(direction.scale(PARTICLE_RADIUS * random.nextDouble()));
        double drift = 0.4;
        level.addParticle(ColorParticleOption.create(MxtParticleTypes.RIFT.get(), RiftColors.resolve(rift)),
                point.x, point.y, point.z,
                (random.nextDouble() - 0.5) * drift, (random.nextDouble() - 0.5) * drift * 0.5,
                (random.nextDouble() - 0.5) * drift);
    }
}
