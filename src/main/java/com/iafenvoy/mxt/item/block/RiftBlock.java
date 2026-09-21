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
 * A rift: a block that leads to another dimension, drawn as a point linked to the rifts around it.
 *
 * <p>The block shows nothing itself, and it does not show up in the block's outline either: what is drawn is the
 * mesh the block entity renderer builds, which is why adjacency matters. A rift links to every rift in the
 * 3x3x3 blocks around it and draws a beam to each, and three of them close a filled triangle. It has no
 * direction of its own - a line between two points has nothing for a direction to decide - so nothing about the
 * way it was placed changes what it looks like or what it links to.
 *
 * <p>The block keeps a full-cube shape so it can be aimed at and right-clicked, while {@code noCollission} lets
 * entities pass through it: entering the block is what takes you through, and there is nothing else to line up
 * with.
 */
public final class RiftBlock extends BaseEntityBlock implements Portal {
    private static final MapCodec<RiftBlock> CODEC = simpleCodec(RiftBlock::new);
    private static final float PARTICLE_CHANCE = 0.25F;
   /**
     * Radius the ambient particles are scattered over, so they hang around the point rather than filling the
     * block.
     */
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

    /**
     * The placing stack carries the destination and colour; there is nothing else to take from the placer.
     */
    @Override
    public void setPlacedBy(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state, @Nullable LivingEntity placer, @NonNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level.getBlockEntity(pos) instanceof RiftBlockEntity rift)) return;
        RiftComponent component = stack.getOrDefault(MxtDataComponents.RIFT, RiftComponent.EMPTY);
        rift.configure(component.target(), component.color());
    }

    /**
     * A dye overrides the colour. Without an override a rift is coloured by where it leads, so this is the only
     * way to give one dimension a colour of its own.
     */
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

    /**
     * Touching the block is entering the rift: with no direction stored there is no plane to line up with, and a
     * point with lines around it has no inside to miss.
     */
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

    /**
     * A rift takes hold at once; there is no warm-up like a nether portal's.
     */
    @Override
    public int getPortalTransitionTime(@NonNull ServerLevel level, @NonNull Entity entity) {
        return 0;
    }

    @Override
    public @NonNull Transition getLocalTransition() {
        return Transition.CONFUSION;
    }

    /**
     * Breaking a rift hands back an anchor still aimed where the rift led, so a rift can be moved or duplicated
     * without losing its destination. There is no loot table: this drop is the only one.
     */
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

    /**
     * Particles gather around the point rather than anywhere in the cube, so a rift starts sparkling where it is
     * drawn and the effect grows with the rift's own thickness.
     *
     * <p>They are the mod's own particle, which is the vanilla portal particle wearing this rift's colour: a
     * door tinted by its destination should not sparkle in the nether's violet. Nothing here makes a sound - a
     * rift is silent until it takes someone through.
     */
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
